# 17. Design Netflix Streaming

> **Difficulty**: Hard | **Asked At**: Netflix, YouTube, Amazon, Apple, Disney+
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Video streaming (on-demand) or also live streaming?
- Multiple device types: TV, mobile, web, game console?
- Video quality selection: manual or adaptive bitrate?
- Download for offline viewing?
- Content recommendations?
- Multiple profiles per account?
- Subtitles / audio tracks?

**Scale:**
- How many subscribers?
- Peak concurrent streams?
- How many titles?

**Typical Interviewer Answer:**
- On-demand streaming only (no live)
- All device types with adaptive bitrate (ABR)
- 220 million subscribers, 100 million concurrent streams at peak
- 15,000 titles (movies + shows)
- Recommendations: mention as extension
- Subtitles: yes (multiple languages)
- Offline download: mention as extension

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Users can browse and search the Netflix catalog
2. Start streaming a movie/episode with low startup latency
3. Adaptive bitrate: video quality adjusts to available bandwidth
4. Resume watching from where you left off (watch history)
5. Multiple profiles per account
6. Subtitles and multiple audio tracks
7. Continue watching across devices

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Stream startup** | < 2 seconds from click to first frame |
| **Buffering** | < 0.1% of total watch time spent buffering |
| **Concurrent streams** | 100 million simultaneous streams globally |
| **Availability** | 99.99% |
| **Bandwidth efficiency** | Adaptive bitrate to maximize quality without buffering |

### Out of Scope
- Content creation / production pipeline
- Billing / subscription management
- Live streaming
- Download for offline viewing (mention as extension)

---

## SECTION 3 — Capacity Estimation

### Streaming Bandwidth
- 100 million concurrent streams
- Average bitrate: 3 Mbps (1080p with HEVC encoding)
- Total bandwidth: 100M × 3 Mbps = **300 Tbps** 
- Netflix is ~15% of global internet traffic — CDN is the only solution

### Storage
- 15,000 titles × average 2 hours = 30,000 hours of content
- Each title encoded into 20 profiles (different resolutions + bitrates): × 20 = 600,000 video files
- Average file: 4 GB (1080p HEVC for 2 hours at 4.5 Mbps)
- Total: 600,000 × 4 GB = **~2.4 PB** of video storage

### CDN
- Popular titles cached at CDN edge nodes (97% of traffic served from CDN cache)
- Only 3% of requests → origin S3

### Metadata
- Browse catalog: 220M users × 10 catalog API calls/day = 2.2 billion calls/day
- = **~25,000 metadata reads/sec** — easily handled with caching

---

## SECTION 4 — API Design

### 1. Browse Catalog
```
GET /api/v1/catalog?genre=thriller&limit=20&cursor=...
Response: {
  "titles": [
    {
      "title_id": "t123",
      "name": "Stranger Things",
      "type": "series",
      "rating": "TV-14",
      "thumbnail_url": "https://cdn.netflix.com/images/t123/thumb.jpg",
      "match_score": 97,     // personalization score for this user
      "seasons": 4,
      "genres": ["Sci-Fi", "Drama"]
    }
  ]
}
```

### 2. Get Streaming Manifest
```
GET /api/v1/titles/{title_id}/manifest?profile_id=p1&episode_id=e5
Response: {
  "manifest_url": "https://cdn.netflix.com/content/t123/e5/manifest.mpd",
  "license_url": "https://drm.netflix.com/license",
  "audio_tracks": [ { "lang": "en", "label": "English" }, { "lang": "hi" } ],
  "subtitles": [ { "lang": "en", "url": "..." }, { "lang": "hi", "url": "..." } ],
  "resume_position_sec": 1847
}
```

### 3. Update Watch Position (heartbeat)
```
POST /api/v1/watch-history
Authorization: Bearer <token>
{
  "title_id": "t123",
  "episode_id": "e5",
  "position_sec": 1847,
  "device_type": "tv"
}
→ 200 OK
```

### 4. Get Continue Watching List
```
GET /api/v1/continue-watching?profile_id=p1
Response: { "titles": [ { title with resume_position_sec } ] }
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `titles`
```
title_id        VARCHAR(20)  PRIMARY KEY
name            VARCHAR(200)
type            ENUM('movie', 'series')
genres          TEXT[]
maturity_rating VARCHAR(10)
synopsis        TEXT
release_year    INT
```
**DB**: Cassandra (high read QPS for catalog browsing, partition by genre/category)

### Table 2: `episodes`
```
episode_id      VARCHAR(20)  PRIMARY KEY
title_id        VARCHAR(20)
season_num      INT
episode_num     INT
duration_sec    INT
synopsis        TEXT
```

### Table 3: `video_assets`
```
asset_id        VARCHAR(40)  PRIMARY KEY
episode_id      VARCHAR(20)  (or title_id for movies)
codec           ENUM('H264', 'H265/HEVC', 'AV1', 'VP9')
resolution      VARCHAR(20)  -- "3840x2160", "1920x1080", "1280x720"
bitrate_kbps    INT
audio_lang      VARCHAR(10)
s3_path         TEXT
cdn_url         TEXT         -- primary CDN URL
duration_sec    INT
```

### Table 4: `watch_history`
```
user_id         VARCHAR(20)
profile_id      VARCHAR(20)
title_id        VARCHAR(20)
episode_id      VARCHAR(20)
position_sec    INT
device_type     VARCHAR(20)
last_watched    TIMESTAMP
PRIMARY KEY (user_id, profile_id, title_id)
```
**DB**: **Cassandra** (partition by user_id — 220M users × many watch records)
- Write on every 30-second heartbeat (one write per user per 30 sec while watching)
- Read on resume or "Continue Watching" fetch

### Table 5: `user_profiles`
```
user_id         VARCHAR(20)  PRIMARY KEY
account_email   VARCHAR(200) UNIQUE
profiles        JSONB        -- { "profile_id": "p1", "name": "Alice", "avatar": "..." }
subscription    ENUM('basic', 'standard', 'premium')
```
**DB**: PostgreSQL (account management, billing relationship)

---

## SECTION 6 — High-Level Architecture

```
CONTENT INGESTION PIPELINE (Content Studio → Netflix)
═══════════════════════════════════════════════════════

  Studio delivers raw 4K master file (ProRes/ARRIRAW)
       ↓
  S3 (original master storage)
       ↓
  Video Encoding Service (massive fleet):
    ┌────────────────────────────────────────────────────────────────────┐
    │  Transcoding Farm (1000s of EC2 instances for each new title)     │
    │  Input: 1 master file                                             │
    │  Output:                                                          │
    │    Resolution × Bitrate × Codec = ~20 video streams              │
    │    360p@0.5Mbps, 480p@1Mbps, 720p@3Mbps, 1080p@8Mbps, 4K@15Mbps│
    │    × H.264 (old devices) × H.265/HEVC × AV1 (new devices)       │
    │    + Audio: Dolby Atmos, 5.1, stereo (per language)              │
    │    + Subtitles: WebVTT format (per language)                      │
    │  Each output: split into 4-second segments (DASH/HLS format)     │
    │  Total: 1 movie → ~2.4 PB of segments                            │
    └────────────────────────────────────────────────────────────────────┘
       ↓
  All segments stored in S3
       ↓
  CDN pre-warming: proactively push popular content to CDN edge nodes

═══════════════════════════════════════════════════════

STREAMING ARCHITECTURE
═══════════════════════════════════════════════════════

  Netflix CLIENT (TV / Mobile / Web)
       │
       │ 1. Browse catalog → Metadata Service → Cassandra (cached)
       │ 2. Press Play → Streaming Service → Get manifest URL
       │
       └──────► CDN (Netflix Open Connect / AWS CloudFront)
                      │ Hit? (~97%)
                      ▼
                Serve 4-second video segments directly
                (no Netflix backend involved!)
                      │ Miss? (~3%)
                      ▼
                S3 Origin → CDN caches → serves client

  Streaming Data Flow:
    User presses Play:
      ├─ Fetch MPEG-DASH manifest (index of all segments + quality levels)
      ├─ Client checks bandwidth (initial probe download)
      ├─ Picks appropriate quality (e.g., 1080p HEVC at 8 Mbps)
      ├─ Fetches segments 1,2,3 (buffer ahead)
      ├─ Continuously monitors download speed
      └─ Switches quality up/down at segment boundaries (seamless)

═══════════════════════════════════════════════════════

  ┌─────────────────────────────────────────────────────┐
  │           Netflix Open Connect (CDN)                │
  │  1000+ Open Connect Appliances (OCAs) worldwide     │
  │  Deployed INSIDE ISP networks (zero last-mile cost) │
  │  Top ISPs: Jio, Airtel, Comcast, AT&T host OCAs     │
  │  Most Indian Netflix traffic never leaves Jio's     │
  │  network!                                           │
  └─────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────┐
  │     Backend Microservices                               │
  │  ┌───────────┐  ┌──────────────┐  ┌──────────────────┐ │
  │  │ Metadata  │  │ Streaming    │  │  Watch History   │ │
  │  │ Service   │  │ Service      │  │  Service         │ │
  │  │ (catalog) │  │ (manifests,  │  │  (position +     │ │
  │  │           │  │  DRM keys)   │  │   continue list) │ │
  │  └───────────┘  └──────────────┘  └──────────────────┘ │
  └─────────────────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Netflix Open Connect (Their Secret Weapon)

**Problem**: 300 Tbps of video bandwidth. AWS CloudFront alone would cost billions/year.

**Netflix's Solution: Build their own CDN**

```
Netflix Open Connect Program:
  - Netflix provides free hardware appliances (OCAs = Open Connect Appliances) to ISPs
  - ISPs install OCAs in their own data centers (free electricity, space)
  - OCAs pre-cache popular Netflix content every night (off-peak)
  - When Jio customer watches Netflix:
    → Traffic stays within Jio's network (OCA → user)
    → Jio doesn't pay for transit bandwidth
    → Netflix pays for hardware only, not bandwidth
    → Netflix gets ultra-low latency (< 5ms to OCA)

OCA content population:
  - Netflix's central system tells each OCA what to pre-cache (based on local popularity)
  - "Top 1000 movies in Bangalore area → cache on Bangalore OCAs"
  - Nightly replication: S3 → OCAs (during off-peak hours 2–6am)
  - Next morning: 95%+ of expected traffic can be served from OCA
```

---

### Deep Dive 2: Adaptive Bitrate Streaming (ABR) in Detail

**How a Netflix player works:**

```
On Play:
  1. Fetch MPEG-DASH MPD manifest:
     {
       periods: [{
         adaptationSets: [{
           representations: [
             { id:"1", bandwidth:500000, resolution:"640x360", codecs:"hev1" },
             { id:"2", bandwidth:3000000, resolution:"1920x1080", codecs:"hev1" },
             { id:"3", bandwidth:15000000, resolution:"3840x2160", codecs:"hev1" }
           ]
         }]
       }]
     }

  2. Initial segment fetch (quality probe): request 1 segment at 1080p
     Measure: downloaded 5MB in 0.5 sec = 10 Mbps throughput
     → Stable for 1080p (needs 8 Mbps) ✅

  3. Maintain buffer: pre-fetch next 30 seconds of segments ahead
     → If buffer > 60 seconds: pause fetching (don't over-buffer)
     → If buffer < 10 seconds: urgent mode, fetch as fast as possible

  4. Bandwidth estimation (EWMA - Exponential Weighted Moving Average):
     throughput[n] = α × last_segment_speed + (1-α) × throughput[n-1]
     α = 0.3 (recent segments weighted more)

  5. Quality switch decision:
     If estimated_throughput < bitrate × 1.2: switch to lower quality
     If buffer > 30s AND throughput supports: switch to higher quality
     Always switch at segment boundary (every 4 seconds) → seamless

ABR algorithm: Netflix uses BOLA (Buffer Occupancy-Based Lyapunov Algorithm)
  → Considers both throughput AND buffer level
  → Netflix open-sourced this in their Vmaf quality research
```

---

### Deep Dive 3: DRM (Digital Rights Management)

Studios won't license content without DRM protection.

```
Widevine (Google), FairPlay (Apple), PlayReady (Microsoft)
Netflix uses all three depending on device:
  Android/Chrome: Widevine
  iOS/Safari: FairPlay
  Windows/Edge: PlayReady

Encryption Flow:
  1. During encoding: each video segment encrypted with AES-128
     Encryption key stored in DRM License Server (not in S3/CDN)

  2. On Play: client requests license from DRM License Server
     DRM Server verifies:
       - Valid Netflix subscription
       - Device is certified (not jailbroken)
       - User is allowed to watch this title (regional licensing)
     Returns decryption key wrapped in device's hardware security module key

  3. Client's hardware (Widevine Level 1 / Secure Enclave):
     - Decrypts video key in a trusted execution environment
     - Decrypts video segments using that key
     - Renders video in a secure buffer (screen capture blocked)
     - Key NEVER exposed to app code or OS

  4. CDN serves encrypted segments (safe to cache — useless without license)
```

---

### Deep Dive 4: Watch History & Resume

**Problem**: 220M users × heartbeat every 30 seconds while watching = 220M × 2 writes/min = **~7.3M writes/min** at peak.

**Solution:**
```
Client sends heartbeat every 30 seconds:
  { user_id, profile_id, title_id, episode_id, position_sec, timestamp }

But don't write every heartbeat to DB:
  → Client buffers locally on device
  → Every 60 seconds (or on pause/quit): flush to server
  → Server writes to Cassandra (high write throughput, partition by user_id)

Actual writes: 220M users × 1 write/min × only active streamers (30% = 66M)
  = 66M / 60 = **~1.1M writes/sec** at absolute peak — Cassandra handles this

Read (Resume position):
  SELECT position_sec FROM watch_history
  WHERE user_id = ? AND profile_id = ? AND title_id = ? AND episode_id = ?
  → O(1) partition lookup, < 5ms

Continue Watching List:
  SELECT title_id, episode_id, position_sec, last_watched
  FROM watch_history
  WHERE user_id = ? AND profile_id = ?
  ORDER BY last_watched DESC LIMIT 20
  → Partition by (user_id, profile_id), cluster by last_watched DESC
  → Fast, no full scan
```

---

### Deep Dive 5: Encoding Pipeline & Quality (VMAF Score)

**Netflix's encoding innovation: Per-Title Encoding**

Traditional: same encoding settings for all content
Netflix: optimize encoding per title based on visual complexity

```
Simple cartoon (BoJack Horseman):
  → Low visual complexity → compress more aggressively
  → 1080p at only 2 Mbps (vs standard 8 Mbps) — looks identical

Complex dark thriller (Squid Game):
  → High visual complexity, dark scenes → needs more bits
  → 1080p at 6 Mbps for acceptable quality

Netflix uses VMAF (Video Multimethod Assessment Fusion) to measure perceptual quality:
  VMAF 93 at 2 Mbps (cartoon) = same visual experience as VMAF 93 at 8 Mbps (live action)

Result:
  → Same user experience at 50% lower bandwidth for simple content
  → 300 Tbps × 50% savings for 20% of library = significant cost savings
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP (Availability + Partition Tolerance)**
- Streaming must never fail due to metadata inconsistency
- If watch history is slightly stale (resume 30s behind): acceptable
- Better to start streaming 10% into episode (wrong position) than show error

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| CDN | Netflix Open Connect (ISP-hosted) | AWS CloudFront | 90% cheaper; ultra-low latency (inside ISP); requires hardware investment |
| ABR protocol | MPEG-DASH | HLS | DASH is more flexible and codec-agnostic; HLS is Apple-preferred but improving |
| Encoding | Per-title encoding with VMAF | Fixed encoding ladder | Per-title saves 50% bandwidth for simple content at same quality |
| Watch history | Cassandra | PostgreSQL | Cassandra handles 1M write/sec easily; PostgreSQL would need heavy sharding |
| Streaming format | 4-second segments | 10-second segments | 4-second segments = faster quality adaptation; 10-second = fewer file overhead |

### What Would You Do Differently at Larger Scale?
- **AV1 codec**: 30% more efficient than HEVC; being rolled out for newer devices
- **Spatial audio**: Dolby Atmos encoding in the pipeline
- **Download for offline**: encrypt with device-specific key, TTL-limited playback license
- **Game streaming**: same CDN infrastructure can serve interactive content (Netflix Games)

---

## Interview Flow Summary (Talk Track)

1. "Netflix's core challenge is delivering **300 Tbps of video globally with < 2s startup and no buffering**"
2. "The answer is **Netflix Open Connect** — their own CDN inside ISP networks, pre-warmed nightly"
3. "Content pipeline: Studio → S3 → Transcoding Farm (20 quality profiles per title) → CDN"
4. "Streaming: **MPEG-DASH with ABR** — 4-second segments, quality switches seamlessly at segment boundaries"
5. "**DRM (Widevine/FairPlay)**: segments encrypted in CDN, key fetched from license server only with valid subscription"
6. "Watch history: **Cassandra** with 30-second heartbeat, partition by user_id for fast resume reads"
7. "**Per-title encoding with VMAF**: optimize bitrate per title's visual complexity — saves 50% bandwidth"

---

> **Previous**: [16 — Design Payment System](./16-payment-system.md)
> **Next**: [18 — Design Google Maps](./18-google-maps.md)
