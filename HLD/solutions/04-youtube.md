# 4. Design YouTube

> **Difficulty**: Hard | **Asked At**: Google, Meta, Netflix, Amazon, Apple
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Upload and stream videos — both?
- Do we need adaptive bitrate streaming (quality adjusts based on network)?
- Do we support comments, likes, subscriptions?
- Live streaming or only uploaded videos?
- Search functionality?
- Recommendations?

**Scale:**
- How many DAU?
- How many video uploads per day?
- How many video views per day?
- What is the average video length and size?

**Typical Interviewer Answer:**
- 2 billion DAU, 500 hours of video uploaded every minute
- 1 billion hours of video watched per day
- Focus on upload pipeline + streaming (not recommendations for now)
- Adaptive bitrate streaming: yes
- Comments and likes: basic support

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Users can upload videos (up to 10 GB, various formats)
2. Videos are transcoded into multiple resolutions (360p, 720p, 1080p, 4K)
3. Users can stream videos with adaptive bitrate (ABR)
4. Like, dislike, comment on videos
5. Subscribe to channels
6. View count tracking

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Upload reliability** | Resumable uploads — no restart on network failure |
| **Streaming latency** | Video starts playing within 2 seconds |
| **Availability** | 99.99% for streaming (reads) |
| **Scalability** | Handle 1B hours watched/day; 500 hrs uploaded/min |
| **Storage durability** | 99.999999999% (11 nines) — video must never be lost |

### Out of Scope (for MVP)
- Live streaming
- Recommendations
- Monetization / ads
- YouTube Shorts

---

## SECTION 3 — Capacity Estimation

### Upload Volume
- 500 hours of video/minute = 30,000 hours/hour = **720,000 hours/day**
- Average video: 300 MB (compressed, 10 min at 1080p)
- Raw upload: 720,000 hr × 60 min/hr × 300 MB / 10 min = **1.3 PB/day raw uploads**

### Transcoding
- Each video transcoded into 5 formats (360p, 480p, 720p, 1080p, 4K)
- Storage per video: ~1 GB (all formats combined)
- Storage per day: 720,000 hr × 6 × ~50 MB (avg across all formats) = **~216 TB/day**

### Streaming / Reads
- 1 billion hours watched/day = ~11.5 million hours/second
- Average bitrate: 1 Mbps (720p)
- Bandwidth: 11.5M × 1 Mbps = **~11.5 TB/s** — this is why CDN is non-negotiable

### View Counts
- 1 billion views/day ≈ 11,500 view events/second

---

## SECTION 4 — API Design

### 1. Initiate Upload (Resumable)
```
POST /api/v1/videos/upload/init
Authorization: Bearer <token>

Request:
{
  "title": "My Vlog Episode 5",
  "description": "A day in my life...",
  "file_size_bytes": 2147483648,    // 2 GB
  "file_type": "video/mp4"
}

Response 200:
{
  "upload_id": "upload_abc123",
  "upload_url": "https://upload.youtube.com/resumable/abc123",
  "chunk_size_bytes": 5242880       // upload in 5MB chunks
}
```

### 2. Upload Chunk
```
PUT /api/v1/videos/upload/{upload_id}/chunk
Content-Range: bytes 0-5242879/2147483648
Content-Type: application/octet-stream

[binary chunk data]

Response 200: { "bytes_received": 5242880 }
Response 308 Resume Incomplete: { "bytes_received": 0, "next_byte": 0 }
```

### 3. Stream Video
```
GET /api/v1/videos/{video_id}/manifest.m3u8
→ Returns HLS manifest file listing available quality segments

GET /api/v1/videos/{video_id}/segments/720p/segment_001.ts
→ Returns individual 10-second video segment (served by CDN)
```

### 4. Get Video Metadata
```
GET /api/v1/videos/{video_id}
Response: { "video_id", "title", "description", "duration", "views", "likes", "channel", ... }
```

### 5. Like / Comment
```
POST /api/v1/videos/{video_id}/like
POST /api/v1/videos/{video_id}/comments
  Body: { "text": "Great video!" }
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `videos`
```
video_id        BIGINT       PRIMARY KEY (Snowflake)
channel_id      BIGINT       NOT NULL
title           VARCHAR(200)
description     TEXT
status          ENUM('uploading', 'processing', 'ready', 'failed')
duration_sec    INT
view_count      BIGINT       DEFAULT 0
like_count      BIGINT       DEFAULT 0
s3_raw_path     TEXT         // original uploaded file
created_at      TIMESTAMP
```
**DB Choice**: PostgreSQL (relational metadata, moderate write volume)

### Table 2: `video_formats` (transcoded versions)
```
video_id        BIGINT
resolution      ENUM('360p', '480p', '720p', '1080p', '4K')
cdn_url         TEXT         // CDN URL for HLS segments
file_size_bytes BIGINT
status          ENUM('processing', 'ready', 'failed')
PRIMARY KEY (video_id, resolution)
```

### Table 3: `comments`
```
comment_id      BIGINT       PRIMARY KEY
video_id        BIGINT
user_id         BIGINT
text            TEXT
created_at      TIMESTAMP
like_count      BIGINT
```
**DB Choice**: Cassandra (partition by `video_id`, cluster by `comment_id DESC`)
- High write/read volume per video
- No complex joins needed

### Table 4: `view_events` (analytics)
```
Not stored in OLTP DB.
Published to Kafka → aggregated by Flink/Spark → stored in ClickHouse
```

### Blob Storage (Videos)
- Raw uploads: **Amazon S3** (object storage, 11 nines durability)
- CDN (streaming): **CloudFront / Akamai** caches HLS segments close to users
- Naming convention: `s3://youtube-videos/{video_id}/{resolution}/segment_{n}.ts`

---

## SECTION 6 — High-Level Architecture

```
UPLOAD PIPELINE
═══════════════════════════════════════════════════════════════════════

  Creator → Upload Service → S3 (raw)
                ↓
           Kafka: VideoUploadedEvent
                ↓
         Transcoding Workers (fleet of EC2 / K8s jobs)
                ↓ (for each resolution: 360p, 720p, 1080p...)
         FFmpeg transcodes video + splits into 10-sec HLS segments
                ↓
         Segments stored back in S3
                ↓
         Metadata DB updated: status = 'ready'
                ↓
         CDN pre-warms popular videos (CloudFront → S3)

═══════════════════════════════════════════════════════════════════════

STREAMING PIPELINE
═══════════════════════════════════════════════════════════════════════

  Viewer → CDN (CloudFront) ──Hit?──→ Serve segment directly (<5ms)
               │
              Miss
               │
       S3 (origin) → CDN caches it → serve to viewer

  Viewer's Player:
    1. Fetch manifest.m3u8 (lists all segments + quality levels)
    2. Pick quality based on current bandwidth (ABR)
    3. Fetch next 2–3 segments ahead (buffer)
    4. If bandwidth drops: switch to lower resolution seamlessly

═══════════════════════════════════════════════════════════════════════

FULL SYSTEM DIAGRAM
═══════════════════════════════════════════════════════════════════════

 ┌──────────┐        ┌──────────────┐      ┌────────────────┐
 │ Creator  ├───────►│ Upload Svc   ├─────►│  S3 (raw)      │
 │ (browser)│        │ (chunked)    │      └───────┬────────┘
 └──────────┘        └──────┬───────┘              │
                            │                      │
                     Kafka event             Transcode trigger
                            │                      │
                     ┌──────▼───────┐     ┌────────▼────────────┐
                     │  Video       │     │  Transcoding Farm   │
                     │  Metadata DB │     │  (FFmpeg workers)   │
                     │  (Postgres)  │     │  360p/720p/1080p/4K │
                     └─────────────┘     └────────┬────────────┘
                                                  │
                                         HLS segments
                                                  │
                                         ┌────────▼────────┐
                                         │  S3 (segments)  │
                                         └────────┬────────┘
                                                  │
                                         ┌────────▼────────┐
                                         │   CDN           │
                                         │ (CloudFront /   │
                                         │  Akamai)        │
                                         └────────┬────────┘
                                                  │
                                         ┌────────▼──────┐
                                         │   Viewers      │
                                         │ (HLS player)   │
                                         └───────────────┘

                     ┌────────────────────────────────────────┐
                     │         View Count Pipeline            │
                     │  Click → API → Kafka → Flink           │
                     │  → aggregate view count per video      │
                     │  → write to Redis (fast counter)       │
                     │  → periodically flush to Postgres      │
                     └────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Video Upload — Resumable Chunked Upload

**Problem**: A 2 GB video upload on a spotty connection fails at 99% → user must restart.

**Solution: Resumable Upload Protocol**
1. Client calls `/upload/init` → gets an `upload_id` and target URL
2. Client splits file into 5 MB chunks
3. Uploads chunk 1: `PUT /upload/{upload_id}/chunk` with `Content-Range: 0-5MB`
4. Server writes chunk to S3, acknowledges
5. If connection drops at chunk 7: client queries resume point → server says "I have 0–35MB, send from 35MB"
6. Client resumes from last acknowledged chunk
7. After final chunk: server stitches together in S3, triggers transcoding

**This is exactly how Google Drive, YouTube, and AWS S3 multi-part upload work.**

---

### Deep Dive 2: Transcoding Pipeline

**Problem**: Raw video (e.g., 4K MOV from iPhone) must be converted to web-compatible formats at multiple resolutions.

**Pipeline:**
```
S3 raw file
    ↓
Transcoding Worker picks up job from queue (Kafka / SQS)
    ↓
FFmpeg command: converts to H.264 / H.265 + splits into HLS segments
    ↓ (for each target resolution in parallel)
    ├── 360p → 10-sec .ts segments → S3
    ├── 720p → 10-sec .ts segments → S3
    ├── 1080p → 10-sec .ts segments → S3
    └── 4K → 10-sec .ts segments → S3
    ↓
Generate master manifest.m3u8 (index of all quality levels)
    ↓
Update metadata DB: status = 'ready'
    ↓
Notify uploader: "Your video is live!"
```

**Key design points:**
- Workers are stateless — can scale horizontally (auto-scaling group)
- Transcode a 1-hour video in parallel: split into 60 segments of 1 min each, transcode all 60 segments in parallel → finish in minutes
- Use **DAG (Directed Acyclic Graph)** workflow orchestration (Apache Airflow) to manage dependencies between transcoding steps

---

### Deep Dive 3: Adaptive Bitrate Streaming (ABR)

**Problem**: User's bandwidth varies (WiFi → 4G → 3G). Video must not buffer.

**Solution: HLS (HTTP Live Streaming)**
- Video is split into 10-second `.ts` (transport stream) segments
- A `manifest.m3u8` file lists all segments and available quality levels:
```
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
360p/index.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
720p/index.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080
1080p/index.m3u8
```
- Player monitors download speed after each segment
- If speed drops: switch to lower quality manifest on next segment boundary
- If speed improves: switch to higher quality
- **Seamless**: User sees a brief quality drop, not a buffer spinner

---

### Deep Dive 4: CDN Strategy

- **Problem**: Serving video to 2B users worldwide from a single origin (S3) is impossible
- **CDN Distribution**:
  - CDN has **Points of Presence (PoPs)** in 100+ locations worldwide
  - Popular videos are cached at edge nodes close to users
  - Cache hit → segment served in <5ms from nearest PoP
  - Cache miss → CDN fetches from S3 origin, caches for subsequent requests
- **Cache warming**: Viral or newly published videos can be **pre-pushed** to CDN before user demand spikes
- **Cache key**: `video_id + resolution + segment_number` → unique per segment

---

### Deep Dive 5: View Count — High-Write Counter

**Problem**: 1B views/day = 11,500 increments/sec on `view_count`. You cannot run `UPDATE videos SET view_count = view_count + 1` on PostgreSQL at this rate.

**Solution:**
1. On each view, write a `ViewEvent` to **Kafka** (fire and forget — no DB write)
2. **Flink/Spark Streaming** consumer aggregates view counts per `video_id` in a time window (e.g., every 60 seconds)
3. Aggregated counts are written to **Redis** (fast in-memory counter, `INCRBY`)
4. Every 5 minutes, a background job flushes Redis counts to PostgreSQL (`UPDATE videos SET view_count = view_count + delta`)
5. API reads view count from Redis for real-time display

This gives **approximate real-time counts** without overwhelming PostgreSQL.

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
- Video streaming: **AP** — better to serve slightly stale metadata than fail the stream
- View counts: **Eventual consistency** — exact count within 5 minutes is fine
- Upload pipeline: **CP** — must confirm chunk receipt before discarding

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Streaming format | HLS | DASH (MPEG-DASH) | HLS has broader device support; DASH is more open standard — both are valid |
| Transcoding | Async worker pool | Synchronous at upload time | Sync transcoding would make upload feel slow; async decouples concerns |
| View counting | Kafka + Redis counter | DB increment per view | DB can't handle 11,500 writes/sec without sharding |
| Video storage | S3 | Self-managed distributed FS | S3 provides 11 nines durability; self-managed is expensive to operate |
| Manifest caching | CDN cached | Generated on-the-fly | Pre-generated and cached manifests are faster; on-the-fly adds latency |

### What Would You Do Differently at Larger Scale?
- **Thumbnail generation** as part of the transcoding pipeline (extract frames at regular intervals)
- **DRM (Digital Rights Management)** for paid content — encrypt segments, license server for decryption keys
- **A/B testing** different transcoding settings to optimize quality vs file size
- **Regional transcoding farms** to reduce latency for creators in different geographies

---

## Interview Flow Summary (Talk Track)

1. "YouTube has two fundamentally different problems: **the upload pipeline** and **the streaming pipeline**"
2. "For uploads: chunked resumable upload → S3 → async transcoding pipeline → multiple resolutions"
3. "For streaming: HLS protocol with 10-second segments, adaptive bitrate, served via CDN"
4. "The transcoding farm is a horizontally scalable worker pool — stateless workers pulling from a queue"
5. "CDN is absolutely critical — we cannot serve 11.5 TB/s from a single origin"
6. "View counts use an eventually-consistent counter: Kafka → Redis → periodic flush to DB"
7. "The key trade-off: we accept eventual consistency on view counts and metadata for massive write throughput"

---

> **Previous**: [03 — Design WhatsApp Messenger](./03-whatsapp-messenger.md)
> **Next**: [05 — Design a Rate Limiter](./05-rate-limiter.md)
