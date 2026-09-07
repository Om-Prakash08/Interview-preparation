# 23. Design Live Streaming (Twitch / YouTube Live)

> **Difficulty**: Hard | **Asked At**: Twitch, YouTube, Meta (Facebook Live), Amazon
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Stream ingest protocol (RTMP / SRT / WebRTC)?
- Targeted end-to-end latency (Ultra-low < 3s or standard live ~10-15s)?
- Adaptive Bitrate Streaming (ABR) transcoding requirement?
- Live chat subsystem integration and massive fan-out scale?
- Automatic Video-on-Demand (VOD) recording archive?
- Scale: concurrent active streamers and concurrent viewers?

**Typical Interviewer Answer:** Ingest stream via RTMP; distribute via Low-Latency HLS (LL-HLS) with 3–5 seconds end-to-end latency. Include real-time chat. 100,000 active streams concurrently, 10 Million total viewers (peak single stream: 1 Million concurrent viewers). Automatically record streams for VOD.

### 1.2 Functional Requirements (FR)
1. **Stream Ingest**: Broadcasters push live video/audio streams via RTMP protocol.
2. **Real-time Transcoding**: Transcode raw stream into multiple quality levels (1080p60, 720p, 480p, 360p) in real time.
3. **Low-Latency Playback**: Viewers stream video with 3-5 seconds glass-to-glass latency via LL-HLS.
4. **Live Chat**: Viewers send and receive real-time chat messages per channel.
5. **VOD Archiving**: Automatically convert finished live streams into VOD recordings.

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Latency** | 3–5 seconds end-to-end glass-to-glass delay |
| **Availability** | 99.99% for video ingest & playback |
| **Scale** | 100K active streams, 10M concurrent viewers (1M viewers on single channel) |
| **Throughput** | 600 Gbps video ingest, 40 Tbps video egress |

### 1.4 Out of Scope
- DRM encryption / pay-per-view monetization
- Real-time video filters / AR effects

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────────────┐       ┌──────────────────────────┐
│   Stream Metadata        │       │   Media Segment (fMP4)   │
│                          │       │                          │
│  stream_id, stream_key   │──────►│  segment_id (seq num)    │
│  broadcaster_id          │       │  resolution (1080p/720p) │
│  status (LIVE/ENDED)     │       │  duration_ms (1000ms)    │
│  started_at              │       │  s3_url                  │
└──────────────────────────┘       └──────────────────────────┘
             │
             ▼
┌──────────────────────────┐
│   Chat Message           │
│  message_id, channel_id  │
│  sender_id, content      │
│  timestamp               │
└──────────────────────────┘
```

### 2.2 Data Model / Schema

**1. `live_streams` (PostgreSQL / DynamoDB)**
```sql
CREATE TABLE live_streams (
  stream_id VARCHAR(64) PRIMARY KEY,
  broadcaster_id VARCHAR(64),
  stream_key VARCHAR(128) UNIQUE,
  title VARCHAR(255),
  status VARCHAR(20), -- 'INITIALIZING', 'LIVE', 'ENDED'
  started_at TIMESTAMP,
  ended_at TIMESTAMP
);
```

**2. LL-HLS Master Playlist File (`master.m3u8`)**
```
#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=6000000,RESOLUTION=1920x1080
1080p/prog_index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=3000000,RESOLUTION=1280x720
720p/prog_index.m3u8
```

> 🎯 **NFR addressed**: **Latency < 5s** — Master playlist points to 1-second Fragmented MP4 (`fMP4`) partial chunks hosted on CDN edges.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Broadcaster Stream Ingest (RTMP)
```
rtmp://ingest.twitch.tv/app/{stream_key}
```

### 3.2 Playback Manifest Request (LL-HLS)
```
GET /api/v1/streams/{channel_name}/master.m3u8
Response 200 OK: Content-Type: application/x-mpegURL (Returns HLS variant playlist)
```

### 3.3 Live Chat WebSocket Protocol
```
WS wss://chat.twitch.tv/ws
Payload (Subscribe): { "action": "JOIN", "channel": "esports_main" }
Payload (Send Msg):  { "action": "SEND", "channel": "esports_main", "text": "POG!" }
```

> 🎯 **NFR addressed**: **Scale** — Persistent WebSocket connection offloads HTTP handshake overhead for 1M concurrent chatters.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Ingest Bandwidth**: 100,000 active streams × 6 Mbps (1080p60 RTMP) = **600 Gbps inbound**.
- **Viewer Playback Egress**: 10 Million viewers × 4 Mbps avg bitrate = **40 Tbps outbound** (Requires CDN distribution).
- **Mega-Channel Chat Fan-Out**: 1M viewers on single channel × 100 msgs/sec = **100 Million chat message pushes/sec**!

### 4.2 Data Flow Through System

```
VIDEO STREAMING PIPELINE
  Broadcaster (OBS) ──RTMP (6 Mbps)──► Ingest Edge Nodes 
    ├─ Validate stream_key
    └─ Push raw stream to GPU Transcoder Cluster
    │
  GPU Transcoder Cluster
    ├─ Transcode into multi-bitrate ABR streams (1080p, 720p, 480p)
    ├─ Slice video into 1-second Fragmented MP4 (fMP4) chunks
    └─ Push chunks to Origin Shield Server & S3 Storage
    │
  CDN Edge Fleet (CloudFront / Akamai)
    └─ Cache 1-second fMP4 chunks & serve LL-HLS to 10M Viewers (< 3-5s latency)

LIVE CHAT PIPELINE (1M Concurrent Viewers Fan-Out)
  User Chat Msg ──WebSocket──► Chat Gateway Service ──► Kafka Stream
    │
    ▼
  NATS / Redis Pub/Sub Cluster (Channel Tree Topic)
    │
    ▼
  WebSocket Edge Push Workers ──► Pushes batched JSON chat payloads to 1M Viewers
```

> 🎯 **NFR addressed**: **Reliability & Scalability** — CDN edge caching offloads origin server load for video playback.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                                 ┌───────────────────────────┐
                                 │   Broadcaster (OBS App)   │
                                 └─────────────┬─────────────┘
                                               │ RTMP Push (6 Mbps)
                                               ▼
                                 ┌───────────────────────────┐
                                 │   Ingest Server Fleet     │
                                 └─────────────┬─────────────┘
                                               │ Raw Video
                                               ▼
                                 ┌───────────────────────────┐
                                 │  GPU Transcoder Workers   │
                                 │  (Generate ABR fMP4 1s)   │
                                 └─────────────┬─────────────┘
                                               │ fMP4 Chunks
                                               ▼
                                 ┌───────────────────────────┐
                                 │       Origin Shield       │
                                 └─────────────┬─────────────┘
                                               │ HTTP Cache Miss
                                               ▼
                                 ┌───────────────────────────┐
                                 │   Global CDN Edge Fleet   │
                                 └─────────────┬─────────────┘
                                               │ LL-HLS GET (<5s Latency)
                                               ▼
                                 ┌───────────────────────────┐
                                 │       Viewer Player       │
                                 └───────────────────────────┘

LIVE CHAT SUBSYSTEM (Separate Path)
Chat Msg ──► WS Gateway ──► Kafka ──► NATS Pub/Sub Tree ──► WS Push Workers ──► Viewers
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Ingest Fleet** | Terminates RTMP connections | Lightweight edge protocol termination and authentication |
| **GPU Transcoder** | Real-time multi-bitrate encoding | Hardware acceleration converts RTMP into 1-sec ABR fMP4 chunks |
| **Origin Shield** | Origin cache for live segments | Protects transcoders from thousands of CDN edge pull requests |
| **CDN Edges** | Global video chunk distribution | Caches 1-second chunks locally; handles 40 Tbps playback egress |
| **NATS / Redis Pub/Sub**| Live chat fan-out tree | High-throughput pub/sub engine distributing chat to WS edge nodes |

> 🎯 **NFR addressed**: **Latency 3-5s** — LL-HLS partial segments served over HTTP/2 chunked transfer from CDN edges.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Ultra-Low Latency via Low-Latency HLS (LL-HLS)

```
Traditional HLS (10-30s Latency):
  - 6-second segment duration.
  - Player waits for 3 full segments before playback = 18 seconds latency!

LL-HLS Architecture (3-5s Latency):
  1. Divide 2-second segments into 200ms-500ms Partial Segments (fMP4 format).
  2. HTTP/2 Blocking Preload Requests: Client requests the NEXT partial segment BEFORE it is written; server responds as soon as byte chunks land.
  3. Result: Player decodes partial frames immediately, cutting latency down to 3 seconds.
```

---

### Deep Dive 2: Chat Fan-Out at Mega Scale (1 Million Viewers on Single Stream)

**Challenge:** 1 Million concurrent viewers in a single Twitch channel with 100 chat msgs/sec = **100 Million messages pushed/sec**.

```
1. Hierarchical Pub/Sub Tree:
   Kafka -> NATS Core Cluster -> Fan-Out NATS Edge Nodes -> WebSocket Workers.

2. Message Throttling & Batching:
   - When chat rate > 20 msgs/sec: Group messages into 500ms batch arrays.
   - Send 1 batch payload every 500ms instead of 100 separate WebSocket pushes per second.

3. Slow-Mode & Tiered Sampling:
   - Force non-subscriber users to wait 30 seconds between messages during viral events.
```

---

### Deep Dive 3: Automatic VOD Archiving Pipeline

```
Live Transcoder -> 1s fMP4 chunks -> Origin Shield -> Async S3 Uploader 
  -> Stores chunks in s3://vod-archive/{stream_id}/

When Stream Ends:
  1. Ingest server sends StreamEnded event to Kafka.
  2. VOD Indexing Worker compiles all S3 fMP4 chunks into a single static master.m3u8 VOD manifest.
  3. Stream status updated to 'VOD_READY' in Database.
```

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **Playback Protocol**| LL-HLS | WebRTC | WebRTC gives sub-second latency but CDN costs for 1M viewers are 10x higher |
| **Ingest Protocol**  | RTMP / SRT | HTTP Chunked Upload | RTMP is natively supported by 99% of streaming software (OBS, Streamlabs) |
| **Chat Protocol**    | WebSockets | Long Polling | WebSockets eliminate HTTP header overhead for high-frequency chat |

---

### Summary Talk Track

1. "Live streaming requires isolating the **Video Processing Pipeline** from the **Live Chat Subsystem**."
2. "Ingest uses **RTMP**, transcoded via GPU workers into 1-second **fMP4 chunks** and served over **LL-HLS** via CDNs for **3-5s latency**."
3. "Chat fan-out handles 1M concurrent channel viewers using a **NATS Pub/Sub tree** with 500ms message batching."
4. "VOD archiving is achieved asynchronously by writing live fMP4 chunks directly to **S3**."

---

> **Previous**: [22 — Design Google Search](./22-google-search.md)
> **Next**: [24 — Design Distributed Job Scheduler](./24-distributed-job-scheduler.md)
