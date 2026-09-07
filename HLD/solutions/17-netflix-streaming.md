# 17. Design Netflix Streaming

> **Difficulty**: Hard | **Asked At**: Netflix, YouTube, Amazon, Apple, Disney+
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Video streaming (on-demand) or also live streaming?
- Multiple device types: TV, mobile, web?
- Adaptive bitrate? Download for offline?
- Content recommendations? Multiple profiles per account?

**Typical Interviewer Answer:** On-demand only. All devices with ABR. 220M subscribers, 100M concurrent streams at peak. 15,000 titles. Subtitles yes.

### 1.2 Functional Requirements (FR)
1. Browse and search catalog
2. Start streaming with < 2s startup
3. Adaptive bitrate (quality adjusts to bandwidth)
4. Resume from where you left off
5. Multiple profiles per account
6. Subtitles and multiple audio tracks

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Stream startup** | < 2 seconds from click to first frame |
| **Buffering** | < 0.1% of watch time |
| **Concurrent streams** | 100 million simultaneously |
| **Availability** | 99.99% |
| **Bandwidth efficiency** | Adaptive bitrate to maximize quality |

### 1.4 Out of Scope
- Content production, billing, live streaming, offline download

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Title      │   │   Episode    │   │  VideoAsset  │   │ WatchHistory │
│              │   │              │   │  (per encode)│   │              │
│ title_id     │──►│ episode_id   │──►│ asset_id     │   │ user_id      │
│ name         │   │ title_id     │   │ episode_id   │   │ profile_id   │
│ type         │   │ season_num   │   │ codec        │   │ title_id     │
│ genres[]     │   │ episode_num  │   │ resolution   │   │ position_sec │
│ rating       │   │ duration_sec │   │ bitrate_kbps │   │ last_watched │
└──────────────┘   └──────────────┘   │ s3_path      │   └──────────────┘
                                      │ cdn_url      │
                                      └──────────────┘
```

**Primary entities**: `Title` (movie/series), `Episode`, `VideoAsset` (each encoding variant — codec × resolution × bitrate), `WatchHistory` (resume position per user/title).

### 2.2 Data Model / Schema

**`titles` + `episodes`** — Cassandra (high read QPS, partition by genre)

**`video_assets`** — PostgreSQL (metadata linking to S3/CDN paths)

**`watch_history`** — Cassandra (partition by user_id; 1.1M writes/sec at peak)

**`user_profiles`** — PostgreSQL (account management)

**Media Storage**: S3 (all video segments) → CDN (Netflix Open Connect / CloudFront)

> 🎯 **NFR addressed**: **Concurrent 100M streams** — CDN serves 97% of video traffic; no backend bottleneck. **Stream startup < 2s** — CDN edge delivery. **Buffering < 0.1%** — ABR adapts quality to bandwidth.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Browse Catalog
```
GET /api/v1/catalog?genre=thriller&limit=20
Response: { "titles": [ { "title_id", "name", "match_score", ... } ] }
```

### 3.2 Get Streaming Manifest
```
GET /api/v1/titles/{title_id}/manifest?profile_id=p1&episode_id=e5
Response: {
  "manifest_url": "https://cdn.netflix.com/.../manifest.mpd",
  "audio_tracks": [...], "subtitles": [...],
  "resume_position_sec": 1847
}
```

### 3.3 Update Watch Position (heartbeat)
```
POST /api/v1/watch-history
{ "title_id": "t123", "episode_id": "e5", "position_sec": 1847 }
→ 200 OK
```

> 🎯 **NFR addressed**: **Stream startup** — manifest returns CDN URL; client fetches segments directly from CDN. **Resume** — position stored per heartbeat.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Bandwidth:** 100M streams × 3 Mbps = **300 Tbps** (Netflix = ~15% of global internet)

**Storage:** 15K titles × 20 encoding profiles × 4 GB avg = **~2.4 PB**

**CDN:** 97% hit rate → only 3% hits S3 origin

**Watch History:** 66M active streamers × 1 write/min = **~1.1M writes/sec** → Cassandra

### 4.2 Data Flow Through System

**Streaming Flow:**
```
User presses Play → Metadata Service → manifest URL
  → Client fetches MPEG-DASH manifest (index of segments + quality levels)
  → Client probes bandwidth → selects initial quality
  → Client fetches 4-second segments from CDN (97% cache hit)
  → Continuously monitors bandwidth → switches quality at segment boundaries
  → Heartbeat every 30-60s → Watch History Service → Cassandra
```

**Content Ingestion Flow:**
```
Studio delivers raw 4K master → S3
  → Transcoding Farm (1000s of instances):
    1 master → 20 profiles (resolution × bitrate × codec)
    Each output: split into 4-second segments (DASH/HLS)
  → All segments → S3 → CDN pre-warming (push to edge nodes overnight)
```

> 🎯 **NFR addressed**: **300 Tbps bandwidth** — CDN + Open Connect absorbs virtually all traffic. **Stream startup < 2s** — CDN edge delivery + manifest pre-fetch. **Buffering** — ABR algorithm adapts in real-time.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
CONTENT INGESTION PIPELINE
══════════════════════════
Studio raw 4K → S3 → Transcoding Farm (20 profiles)
  → 4-second segments → S3 → CDN pre-warming (nightly to edge)

STREAMING ARCHITECTURE
══════════════════════════
Netflix CLIENT (TV / Mobile / Web)
     │
     │ 1. Browse → Metadata Service → Cassandra (cached)
     │ 2. Play → Streaming Service → manifest URL
     │
     └──────► CDN (Netflix Open Connect)
                    │ Hit (~97%) → serve 4-second segments directly
                    │ Miss (~3%) → S3 Origin → CDN caches → serve

BACKEND SERVICES
══════════════════════════
┌───────────┐  ┌──────────────┐  ┌──────────────────┐
│ Metadata  │  │ Streaming    │  │ Watch History     │
│ Service   │  │ Service      │  │ Service           │
│ (catalog) │  │ (manifests,  │  │ (Cassandra,       │
│           │  │  DRM keys)   │  │  1.1M writes/sec) │
└───────────┘  └──────────────┘  └──────────────────┘

Netflix Open Connect (CDN):
  1000+ appliances inside ISP networks globally
  Pre-cache popular content nightly → 95%+ hit rate
  Traffic stays within ISP network (zero transit cost)
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Netflix Open Connect** | ISP-hosted CDN appliances | 90% cheaper than CloudFront; ultra-low latency inside ISP |
| **Transcoding Farm** | Encodes 1 master → 20 quality profiles | Per-title encoding with VMAF for optimal quality/bandwidth |
| **Metadata Service** | Catalog browsing + search | Cassandra for high read QPS; heavily cached |
| **Streaming Service** | Issues manifest URLs + DRM license keys | Manifest points client to CDN segments |
| **Watch History Service** | Stores resume position per user | Cassandra handles 1.1M writes/sec; partition by user_id |
| **DRM License Server** | Issues decryption keys (Widevine/FairPlay) | Segments encrypted in CDN; key only for valid subscribers |

> 🎯 **NFR addressed**: **100M concurrent streams** — Open Connect CDN absorbs 97% of traffic. **< 2s startup** — CDN edge + manifest pre-fetch. **< 0.1% buffering** — ABR algorithm + 30s buffer-ahead. **99.99% availability** — CDN serves even if backend is slow.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Netflix Open Connect

```
Netflix provides free hardware (OCAs) to ISPs
ISPs install in their data centers (free for ISP)
OCAs pre-cache popular content every night (off-peak hours)

When Jio customer watches Netflix:
  → Traffic stays within Jio's network (OCA → user)
  → < 5ms latency to OCA
  → Neither Jio nor Netflix pay for transit bandwidth
  → 95%+ hit rate from OCA

Content population: Netflix central system tells each OCA what to cache
  based on local popularity ("Top 1000 titles in Bangalore area")
```

---

### Deep Dive 2: Adaptive Bitrate Streaming (ABR)

```
On Play:
  1. Fetch MPEG-DASH manifest (lists all quality levels + segment URLs)
  2. Initial probe: download 1 segment, measure throughput
  3. Select quality matching bandwidth
  4. Maintain 30s buffer ahead
  5. Bandwidth estimation: EWMA (recent segments weighted more)
  6. Quality switch at 4-second segment boundaries (seamless)

Netflix uses BOLA algorithm (Buffer Occupancy + Lyapunov optimization)
```

---

### Deep Dive 3: DRM (Digital Rights Management)

```
Widevine (Android/Chrome), FairPlay (iOS/Safari), PlayReady (Windows)

Flow: Segments encrypted with AES-128 during encoding
  → On Play: client requests license from DRM server
  → DRM verifies subscription + device certification
  → Returns decryption key wrapped in hardware security key
  → Hardware decrypts in Trusted Execution Environment
  → Key never exposed to app code

CDN safely caches encrypted segments (useless without license)
```

---

### Deep Dive 4: Watch History at Scale

```
220M users × heartbeat every 60s (while watching) × 30% active = 1.1M writes/sec
  → Cassandra: partition by user_id, cluster by last_watched
  → Client buffers locally, flushes every 60s (reduces write volume)
  → Resume read: O(1) partition lookup, < 5ms
```

---

### Deep Dive 5: Per-Title Encoding with VMAF

```
Traditional: same encoding for all content
Netflix: optimize per title based on visual complexity

Simple cartoon (BoJack): 1080p at 2 Mbps (vs standard 8 Mbps)
Complex thriller (Squid Game): 1080p at 6 Mbps

VMAF score 93 at 2 Mbps (cartoon) = same quality as VMAF 93 at 8 Mbps (action)
Result: 50% bandwidth savings for simple content
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:** **AP** — streaming must never fail due to metadata inconsistency. Slightly stale watch history (resume 30s behind) is acceptable.

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| CDN | Open Connect (ISP-hosted) | CloudFront | 90% cheaper; ultra-low latency inside ISP |
| ABR protocol | MPEG-DASH | HLS | DASH is more flexible and codec-agnostic |
| Encoding | Per-title with VMAF | Fixed ladder | Saves 50% bandwidth for simple content |
| Watch history | Cassandra | PostgreSQL | Cassandra handles 1.1M writes/sec; PostgreSQL needs heavy sharding |
| Segment length | 4 seconds | 10 seconds | 4s = faster quality adaptation; 10s = fewer files |

---

### Summary Talk Track

1. "Netflix's core challenge: **300 Tbps of video globally with < 2s startup**."
2. "Core entities: **Title**, **Episode**, **VideoAsset** (20 encoding profiles), **WatchHistory** (Cassandra)."
3. "The answer is **Netflix Open Connect** — own CDN inside ISP networks, pre-warmed nightly."
4. "Content pipeline: Studio → S3 → Transcoding (20 profiles per title) → CDN."
5. "Streaming: **MPEG-DASH with ABR** — 4-second segments, quality switches seamlessly."
6. "**DRM**: segments encrypted in CDN, key from license server with valid subscription only."
7. "**Per-title encoding with VMAF**: optimize bitrate per visual complexity — saves 50% bandwidth."

---

> **Previous**: [16 — Design Payment System](./16-payment-system.md)
> **Next**: [18 — Design Google Maps](./18-google-maps.md)
