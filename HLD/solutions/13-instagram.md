# 13. Design Instagram

> **Difficulty**: Hard | **Asked At**: Meta, Google, Twitter, Snapchat
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Photo + video upload and viewing?
- Feed: posts from accounts you follow (similar to Twitter's feed problem)?
- Stories (24-hour expiry content)?
- Direct Messages?
- Likes, comments, explore/discover tab?
- Reels (short video)?

**Scale:**
- How many DAU?
- How many photos uploaded per day?
- Feed refresh rate?

**Typical Interviewer Answer:**
- 500 million DAU
- 100 million photos uploaded per day
- Feed from followed accounts, ranked (not purely chronological)
- Stories: yes
- DMs: out of scope (same as WhatsApp design)
- Likes, comments: yes

### 1.2 Functional Requirements (FR)
1. Upload photos / short videos
2. Follow / unfollow other users
3. View a feed of posts from followed accounts (ranked)
4. Like and comment on posts
5. View Stories (disappear after 24 hours)
6. User profile with their posts grid

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Availability** | 99.99% |
| **Feed Latency** | < 200ms feed generation |
| **Upload reliability** | Resumable uploads; photos never lost |
| **Scalability** | 100M photo uploads/day; 500M DAU |
| **Storage durability** | 11 nines (photos are permanent) |

### 1.4 Out of Scope
- Reels recommendation algorithm
- Instagram Shopping
- DMs

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Post       │   │    Like      │   │   Comment    │   │   Follow     │
│              │   │              │   │              │   │   (edge)     │
│ post_id      │   │ post_id      │   │ comment_id   │   │ follower_id  │
│ user_id      │   │ user_id      │   │ post_id      │   │ followee_id  │
│ caption      │   │ created_at   │   │ user_id      │   │ created_at   │
│ media_urls   │   │              │   │ text         │   │              │
│ like_count   │   └──────────────┘   └──────────────┘   └──────────────┘
│ comment_count│
│ created_at   │   ┌──────────────┐   ┌──────────────┐
└──────────────┘   │   Story      │   │   Feed       │
                   │              │   │   (cache)    │
                   │ story_id     │   │ user_id      │
                   │ user_id      │   │ post_id      │
                   │ media_url    │   │ score        │
                   │ expires_at   │   │              │
                   └──────────────┘   └──────────────┘
```

**Primary entities**: `Post` (photo content), `Like` / `Comment` (engagement), `Follow` (social graph), `Story` (ephemeral content with 24h TTL), `Feed` (pre-computed timeline cache in Redis).

### 2.2 Data Model / Schema

**Table 1: `posts`** — PostgreSQL
```
post_id       BIGINT PRIMARY KEY (Snowflake)
user_id       BIGINT
caption       TEXT
media_urls    JSONB  -- { "thumb": "...", "medium": "...", "full": "..." }
like_count    BIGINT DEFAULT 0
comment_count BIGINT DEFAULT 0
created_at    TIMESTAMP
```

**Table 2: `likes`** — Cassandra (46K writes/sec)
```
PRIMARY KEY (post_id, user_id)
```

**Table 3: `comments`** — Cassandra (partition by post_id)
```
comment_id, post_id, user_id, text, created_at
```

**Table 4: `follows`** — Cassandra (bidirectional: partition by follower_id AND followee_id)

**Table 5: `stories`** — Redis with TTL + S3 for media
```
Key: stories:{user_id} → Sorted Set of { story_id: expiry_timestamp }
TTL: 24 hours (auto-expire)
```

**Media Storage**: S3 (original + thumbnail + medium) → CDN (CloudFront)

> 🎯 **NFR addressed**: **Storage durability 11 nines** — S3 for all media. **Scalability** — Cassandra for high-volume likes/comments; Redis for feed cache. **Feed Latency < 200ms** — pre-computed feed in Redis sorted sets.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Upload Photo
```
POST /api/v1/media/upload/init
{ "file_type": "image/jpeg", "file_size": 204800, "caption": "Sunset #chennai" }
Response: { "upload_id": "up_xyz", "upload_url": "s3://..." }

PUT {upload_url}  ← direct upload to S3 (bypasses app servers!)

POST /api/v1/media/upload/{upload_id}/complete
→ { "post_id": "post_123", "status": "processing" }
```

### 3.2 Get Feed
```
GET /api/v1/feed?limit=20&cursor={pagination_token}
Response: { "posts": [ { post_object } ], "next_cursor": "eyJ..." }
```

### 3.3 Like / Comment
```
POST   /api/v1/posts/{post_id}/like    → 200 OK
DELETE /api/v1/posts/{post_id}/like    → 200 OK (unlike)
POST   /api/v1/posts/{post_id}/comments  { "text": "Beautiful!" }
```

### 3.4 View Stories
```
GET /api/v1/users/{user_id}/stories
Response: { "stories": [ { "story_id", "media_url", "expires_at" } ] }
```

> 🎯 **NFR addressed**: **Upload reliability** — pre-signed S3 URL for direct upload; app servers never bottleneck. **Feed Latency** — cursor-based pagination avoids offset queries.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Uploads:** 100M photos/day × 200KB = **20 TB/day** (with thumbnails: ~50 TB/day)

**Feed Reads:** 500M DAU × 10 refreshes = **5B feed loads/day** = ~58,000/sec

**Likes:** 4B/day = **~46,000 likes/sec**

**Social Graph:** 500M users × avg 500 follows = **250 billion follow edges**

### 4.2 Data Flow Through System

**Upload Path:**
```
Client → S3 (direct, pre-signed URL)
  → S3 event → Media Processing Service (resize to thumb/medium/full)
  → Resized versions → S3 → CDN pre-warm
  → Metadata → PostgreSQL
  → Kafka: PostPublishedEvent → Fan-out Service
  → For each follower: push post_id to their Redis feed sorted set
  (Celebrity users: fan-out on READ, same hybrid as Twitter)
```

**Feed Read Path:**
```
User opens app → GET /feed → Feed Service
  → Read post_ids from Redis sorted set
  → Merge celebrity posts on-the-fly (fan-out on read)
  → ML ranking model scores ~500 candidates → return top 20
  → Fetch full post objects from cache/Postgres
```

> 🎯 **NFR addressed**: **Scalability** — direct-to-S3 upload offloads 20 TB/day from app servers. **Feed Latency** — pre-computed feed in Redis + ML re-ranking.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                  USERS (500M DAU)
                       │
              ┌────────▼────────┐
              │   API Gateway   │
              └────────┬────────┘
                       │
     ┌─────────────────┼─────────────────────────┐
     │                 │                         │
┌────▼─────┐    ┌───────▼──────┐      ┌──────────▼──────┐
│ Upload   │    │ Feed Service │      │ Social Graph    │
│ Service  │    │ (read-heavy) │      │ Service         │
└────┬─────┘    └───────┬──────┘      └──────────┬──────┘
     │                  │                        │
     ▼                  │                        │
  S3 + CDN       ┌──────▼───────────────────┐    │
  (media)        │  Feed Cache (Redis)       │    │
                 │  Pre-computed per user    │    │
                 └──────┬────────────────────┘    │
                        │                        │
          ┌─────────────┼───────────────┐        │
          │             │               │        │
   ┌──────▼──────┐ ┌────▼──────┐ ┌─────▼──────┐ │
   │  Cassandra  │ │PostgreSQL │ │  Cassandra │ │
   │  (likes,    │ │(posts,    │ │ (follows)  │◄┘
   │   comments) │ │ users)    │ │            │
   └─────────────┘ └───────────┘ └────────────┘

WRITE PATH (Upload):
  Photo → S3 (pre-signed URL) → Media Processing
  → Metadata → Postgres → Kafka → Fan-out → Redis feeds

CELEBRITY: > 1M followers → fan-out on READ (same hybrid as Twitter)
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Upload Service** | Issues pre-signed S3 URLs; triggers media processing | App servers never touch photo bytes; S3 scales to 20 TB/day |
| **Media Processing** | Generates thumbnail + medium + compressed original | Async pipeline; stateless workers scale horizontally |
| **Feed Service** | Returns ranked feed from Redis + ML scoring | Pre-computed candidates + real-time ranking for < 200ms |
| **Redis Feed Cache** | Sorted set of post_ids per user | Sub-ms read; hybrid fan-out handles celebrity problem |
| **Cassandra** | Likes (46K/sec), comments, follows | Write-optimized; handles social-scale engagement |
| **CDN** | Serves 289K photo requests/sec globally | 95% cache hit rate for popular photos |

> 🎯 **NFR addressed**: **Availability 99.99%** — CDN serves photos even if origin is slow. **Feed Latency < 200ms** — Redis cache + ML ranking. **Upload reliability** — S3 direct upload with 11 nines. **Scalability** — each component scales independently.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Photo Upload Pipeline

```
Client → Upload Service (init) → S3 pre-signed URL issued
Client → S3 directly (bypasses app servers!)
  ↓ (S3 event trigger)
S3 → Media Processing Service
  ├── Resize to thumbnail (150×150, 50KB)
  ├── Resize to medium (1080px wide, ~200KB)
  └── Compress original with MozJPEG (80% quality, 40% size reduction)
  ↓
All versions → S3 → CDN (CloudFront) pre-warms popular content
  ↓
Metadata updated in PostgreSQL → Kafka → Fan-out Service
```

**Why pre-signed S3 URL?** App server issues a time-limited (15 min) URL. Client uploads directly to S3 — app servers don't become a bottleneck for 20 TB/day of uploads.

---

### Deep Dive 2: Feed Ranking (Algorithmic Feed)

**Two-stage ranking:**
```
Stage 1 (Candidate retrieval): Pull ~500 post_ids from Redis feed cache
Stage 2 (Ranking): ML model scores each candidate

Features: recency, like velocity, viewer-author relationship strength,
          content type match with viewer preferences
```

---

### Deep Dive 3: Stories (24-hour TTL Content)

- Story metadata: Redis with TTL = 24 hours (auto-expires)
- Story media: S3 → Glacier lifecycle after 24h
- View tracking: Cassandra `(story_id, viewer_id, viewed_at)`

---

### Deep Dive 4: Like Count at Scale (46,000 likes/sec)

```
On like: Redis INCR like_count:{post_id}  (atomic, O(1))
Every 60 seconds: flush Redis delta → PostgreSQL UPDATE
API reads from Redis for real-time display
```

---

### Deep Dive 5: CDN Strategy for Photos

- 500M DAU × 50 photos/day = **289,000 photo requests/sec**
- CDN cache hit rate: ~95% for popular content
- S3 origin: only 5% of requests hit origin

---

### Trade-offs & Alternatives

**CAP Theorem Position:** **AP** — like counts can be slightly stale; feed can be slightly outdated; photos must never be lost (S3 durability).

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Feed generation | Hybrid fan-out | Pure write fan-out | Celebrity fan-out causes write amplification |
| Like counting | Redis counter + flush | Per-like DB write | DB can't handle 46K writes/sec |
| Photo upload | Direct-to-S3 (pre-signed URL) | Via app server | App server bottleneck at 20 TB/day |
| Feed ranking | ML model (two-stage) | Chronological | ML ranking drives 3× engagement |
| Stories expiry | Redis TTL | Background cleanup job | TTL is automatic, zero-maintenance |

---

### Summary Talk Track

1. "Instagram is **Twitter's feed problem + YouTube's media pipeline** combined."
2. "Core entities: **Post**, **Like**, **Comment**, **Follow** (social graph), **Story** (ephemeral), **Feed** (pre-computed cache)."
3. "For media: **direct-to-S3 upload** (pre-signed URL), then transcoding pipeline generates thumbnails."
4. "For feed: **hybrid fan-out** — write to Redis feeds for regular users, read on-demand for celebrities."
5. "Feed is **algorithmically ranked** (ML model, two-stage retrieval + scoring)."
6. "Like counts: **Redis INCR** + periodic flush — handles 46K likes/sec."
7. "Stories: **Redis TTL** for metadata (auto-expires at 24h), S3 media with Glacier lifecycle."

---

> **Previous**: [12 — Design Uber / Lyft](./12-uber-lyft.md)
> **Next**: [14 — Design Key-Value Store (DynamoDB)](./14-key-value-store.md)
