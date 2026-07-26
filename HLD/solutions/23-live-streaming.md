# 23. Design Live Streaming (Twitch / YouTube Live)

> **Difficulty**: Hard | **Asked At**: Twitch, YouTube, Meta (Facebook Live), Amazon
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Ingest live streams from broadcasters (OBS / RTMP / WebRTC)?
- Transcode stream into multiple quality levels (ABR) in real time?
- Real-time chat accompanying the stream?
- Recording/VOD replay after stream ends?
- Low-latency targets: Ultra-low latency (<3s) or standard live (~10-15s)?

**Scale:**
- How many concurrent active streamers?
- How many total concurrent viewers across all streams?
- Largest single stream viewer count (e.g., esports event / celebrity stream)?

**Typical Interviewer Answer:**
- RTMP ingest from broadcasters; Low-Latency HLS (LL-HLS) to viewers
- Targeted latency: 3–5 seconds end-to-end
- Live chat included (massive fan-out problem)
- 100,000 active streams concurrently; 10 million concurrent viewers total
- Peak single stream: 1 million concurrent viewers

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Broadcasters can stream video/audio via RTMP
2. Real-time transcoding into multi-bitrate streams (1080p60, 720p60, 480p, 360p)
3. Viewers can watch live streams with Low-Latency HLS
4. Real-time chat per stream (send/receive messages)
5. Save stream recording as VOD (Video on Demand) automatically after stream ends

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Latency** | 3–5 seconds end-to-end (broadcaster glass to viewer glass) |
| **Availability** | 99.99% for stream ingest and playback |
| **Scalability** | 1M+ viewers on a single channel without collapsing chat or stream |
| **Reliability** | Zero video stutter/buffering under stable network conditions |

---

## SECTION 3 — Capacity Estimation

### Ingest Bandwidth (Broadcasters)
- 100,000 active streams × 6 Mbps (1080p60 RTMP ingest) = **600 Gbps inbound**

### Playback Bandwidth (Viewers)
- 10 million viewers × 4 Mbps average bitrate = **40 Tbps outbound** (Requires CDN)

### Chat Fan-out
- Single mega stream with 1M viewers:
- If 100 chat messages sent per second → **100 million chat messages pushed/sec** to viewers for that channel alone!

---

## SECTION 4 — API & Protocols Design

### 1. Protocols Choice
- **Ingest**: **RTMP** (Real-Time Messaging Protocol over TCP) or **SRT** (Secure Reliable Transport over UDP)
- **Playback**: **LL-HLS** (Low-Latency HTTP Live Streaming) using 1-second chunked segments (fMP4)
- **Chat**: **WebSockets** (persistent bi-directional connection)

### 2. Ingest API (Broadcaster)
```
rtmp://ingest.twitch.tv/app/{stream_key}
```

### 3. Playback API (Viewer)
```
GET /api/v1/streams/{channel_name}/master.m3u8
→ Returns HLS playlist containing variants (1080p, 720p, 480p)
```

### 4. Chat WebSocket Connection
```
WS wss://chat.twitch.tv/ws
Subscribes to channel: #channel_name
Pushes/Receives chat payloads
```

---

## SECTION 5 — High-Level Architecture

```
 BROADCASTER (OBS / Camera)
      │
      │ RTMP Stream (TCP)
      ▼
 ┌────────────────────────────────────────────────────────┐
 │ Ingest Server Fleet (Edge PoPs)                        │
 │ Terminates RTMP, validates stream key                 │
 └──────────────────────────┬─────────────────────────────┘
                            │ Raw Stream Push
                            ▼
 ┌────────────────────────────────────────────────────────┐
 │ Real-Time Transcoding Workers (GPU/ASIC Fleet)         │
 │ Transcodes raw RTMP → fMP4 (1s segments) in parallel   │
 │ Generates: 1080p60, 720p60, 480p, 360p                 │
 └──────────────────────────┬─────────────────────────────┘
                            │ Writes segments
                            ▼
 ┌────────────────────────────────────────────────────────┐
 │ Origin Shield / Transcoder Cache                       │
 │ Temporary 30s buffer of live fMP4 chunks               │
 └──────────────────────────┬─────────────────────────────┘
                            │ HTTP Pull
                            ▼
 ┌────────────────────────────────────────────────────────┐
 │ CDN Edge Nodes (Global PoPs)                           │
 │ Serves LL-HLS chunks to millions of viewers           │
 └──────────────────────────┬─────────────────────────────┘
                            │ LL-HLS HTTP GET
                            ▼
                      VIEWERS (Browser/App)

 LIVE CHAT SUBSYSTEM (Separate Path)
 Broadcaster/Viewer Chat ──► WebSocket Gateway ──► Kafka ──► Chat Worker
                                                                 │
                                                       Pub/Sub (Redis/NATS)
                                                                 │
                                                          WebSocket Push
                                                                 │
                                                                 ▼
                                                        Viewers Chat Window
```

---

## SECTION 6 — Deep Dives

### Deep Dive 1: Ultra-Low Latency Streaming (LL-HLS & fMP4)

Standard HLS has 10–30s latency because segments are 6 seconds long, and players buffer 3 segments.

**Low-Latency HLS (LL-HLS) Solution:**
1. Split 2-second segments into sub-parts called **partial segments** (e.g., 200–500ms duration).
2. Use **Fragmented MP4 (fMP4)** container.
3. Serve partial segments via **HTTP/2 Push** or Blocking Preload Requests before the main playlist updates.
4. Player decodes partial segments as soon as they arrive, cutting latency down to **2–3 seconds**.

---

### Deep Dive 2: Chat Fan-out at Mega-Scale (1M Concurrent Viewers)

**Problem:** 1M viewers in a single channel = 100 msgs/sec × 1M viewers = 100M pushes/sec.

**Solution Architecture:**
1. **Tree-structured Pub/Sub**: Message sent to Chat Service → published to Kafka → distributed to a tree of **NATS / Redis Pub/Sub** brokers → pushed via WebSocket Edge Gateways.
2. **Chat Throttling & Sampling**: If chat rate > 20 msgs/sec, drop low-tier user messages or combine messages into 500ms batch payloads (`Message Batching`).
3. **Slow Mode**: Force users to wait N seconds between messages during high-volume events.

---

### Deep Dive 3: Video-on-Demand (VOD) Archiving

1. Live transcoder writes fMP4 segments to Origin Cache.
2. As segments pass through, a background process asynchronously uploads chunks to **AWS S3**.
3. Once stream ends, an indexing job compiles all uploaded S3 chunks into a single VOD index (`master.m3u8` manifest for VOD playback).

---

## SECTION 7 — Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Ingest Protocol | RTMP / SRT | WebRTC | RTMP/SRT are industry standards for OBS/encoders, reliable over TCP/UDP |
| Playback Protocol | LL-HLS | WebRTC | WebRTC gives <1s latency but CDN scale for 1M viewers is prohibitively expensive |
| Chat Protocol | WebSockets | Long Polling | WebSockets maintain persistent lightweight duplex connection necessary for live chat |

---

## SECTION 8 — Summary Talk Track

1. "Live streaming requires distinct pipelines for **Video Ingest/Transcoding**, **CDN Distribution**, and **Real-Time Chat**."
2. "Ingest via RTMP → GPU Transcoding Fleet into 1s fMP4 chunks → LL-HLS served via Edge CDNs for 3s latency."
3. "Chat handles 1M+ viewer fan-out using WebSocket edge servers fed by NATS Pub/Sub and message batching."
4. "VOD archiving is achieved by asynchronously syncing 1s live segments from origin cache directly to S3."

---

> **Previous**: [22 — Design Google Search](./22-google-search.md)
> **Next**: [24 — Design Distributed Job Scheduler](./24-distributed-job-scheduler.md)
