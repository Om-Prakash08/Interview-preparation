# 13. Design Instagram

> **Difficulty**: Hard | **Asked At**: Meta, Google, Twitter, Snapchat
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

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
- Reels: mention but don't deep dive

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Upload photos / short videos
2. Follow / unfollow other users
3. View a feed of posts from followed accounts (ranked)
4. Like and comment on posts
5. View Stories (disappear after 24 hours)
6. User profile with their posts grid

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Availability** | 99.99% |
| **Feed Latency** | < 200ms feed generation |
| **Upload reliability** | Resumable uploads; photos never lost |
| **Scalability** | 100M photo uploads/day; 500M DAU |
| **Storage durability** | 11 nines (photos are permanent) |

### Out of Scope
- Reels recommendation algorithm
- Instagram Shopping
- DMs

---

## SECTION 3 — Capacity Estimation

### Uploads
- 100M photos/day
- Average compressed photo: 200 KB
- Storage: 100M × 200 KB = **20 TB/day**
- Multiple resolutions (thumbnail 50KB, medium 200KB, full 1MB): **~50 TB/day** with thumbnails
- 5-year storage: **~90 PB** — massive CDN/S3 requirement

### Feed Reads
- 500M DAU × 10 feed refreshes/day = **5 billion feed loads/day**
- = 5B / 86,400 ≈ **~58,000 feed reads/sec**

### Writes (Likes + Comments)
- 4 billion likes/day = **~46,000 likes/sec**
- 500M comments/day = **~5,800 comments/sec**

### Follows
- Social graph: 500M users × avg 500 follows = **250 billion follow edges**

---

## SECTION 4 — API Design

### 1. Upload Photo
```
POST /api/v1/media/upload/init
{
  "file_type": "image/jpeg",
  "file_size": 204800,
  "caption": "Sunset at Marina Beach #chennai",
  "location": { "lat": 13.0499, "lng": 80.2824 }
}
Response: { "upload_id": "up_xyz", "upload_url": "s3://..." }

PUT {upload_url}  ← direct upload to S3 (bypasses app servers)
→ 200 OK

POST /api/v1/media/upload/{upload_id}/complete
→ { "post_id": "post_123", "status": "processing" }
```

### 2. Get Feed
```
GET /api/v1/feed?limit=20&cursor={pagination_token}
Response: {
  "posts": [
    {
      "post_id": "post_123",
      "author": { "user_id": "u1", "username": "alice", "avatar_url": "..." },
      "image_url": "https://cdn.instagram.com/p/abc.jpg",
      "caption": "Sunset #chennai",
      "like_count": 1423,
      "comment_count": 87,
      "is_liked": false,
      "posted_at": "2025-07-26T10:00:00Z"
    }
  ],
  "next_cursor": "eyJ..."
}
```

### 3. Like a Post
```
POST /api/v1/posts/{post_id}/like    → 200 OK
DELETE /api/v1/posts/{post_id}/like  → 200 OK (unlike)
```

### 4. Post a Comment
```
POST /api/v1/posts/{post_id}/comments
{ "text": "Beautiful!" }
→ { "comment_id": "c_456", "text": "Beautiful!", "created_at": "..." }
```

### 5. View Stories
```
GET /api/v1/users/{user_id}/stories
Response: { "stories": [ { "story_id", "media_url", "expires_at", "viewer_count" } ] }
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `posts`
```
post_id       BIGINT       PRIMARY KEY (Snowflake)
user_id       BIGINT
caption       TEXT
media_urls    JSONB        -- { "thumb": "...", "medium": "...", "full": "..." }
like_count    BIGINT       DEFAULT 0
comment_count BIGINT       DEFAULT 0
location      GEOMETRY     NULL  (PostGIS for geo-tagged posts)
created_at    TIMESTAMP
is_deleted    BOOLEAN      DEFAULT false
```
**DB Choice**: **PostgreSQL** for metadata; **Cassandra** for likes/comments (high write volume)

### Table 2: `likes`
```
post_id       BIGINT
user_id       BIGINT
created_at    TIMESTAMP
PRIMARY KEY (post_id, user_id)
```
**DB Choice**: **Cassandra** (partition by `post_id`)
- 46,000 like writes/sec — Cassandra handles this easily

### Table 3: `comments`
```
comment_id    BIGINT       PRIMARY KEY (Snowflake)
post_id       BIGINT       (partition key in Cassandra)
user_id       BIGINT
text          TEXT
created_at    TIMESTAMP
like_count    INT          DEFAULT 0
```
**DB Choice**: **Cassandra** (partition by `post_id`, cluster by `created_at`)

### Table 4: `follows` (Social Graph)
```
follower_id   BIGINT
followee_id   BIGINT
created_at    TIMESTAMP
PRIMARY KEY (follower_id, followee_id)
```
Also maintain reverse index:
```
PRIMARY KEY (followee_id, follower_id)  -- to query "who follows user X"
```
**DB Choice**: **Cassandra** with two tables for bidirectional queries

### Table 5: `stories`
```
story_id      BIGINT       PRIMARY KEY
user_id       BIGINT
media_url     TEXT
created_at    TIMESTAMP
expires_at    TIMESTAMP    -- always created_at + 24 hours
viewer_count  INT
```
**Storage**: Stories live in Redis with TTL (auto-expire after 24h) + S3 for media

### Media Storage
- Original photo → S3 (permanent)
- Thumbnail (150×150px) + Medium (1080px) auto-generated by transcoding pipeline
- Served via **CDN (CloudFront)**

---

## SECTION 6 — High-Level Architecture

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
                 │  Pre-computed feed per    │    │
                 │  user (like Twitter)      │    │
                 └──────┬────────────────────┘    │
                        │                        │
          ┌─────────────┼───────────────┐        │
          │             │               │        │
   ┌──────▼──────┐ ┌────▼──────┐ ┌─────▼──────┐ │
   │  Cassandra  │ │PostgreSQL │ │  Cassandra │ │
   │  (likes,    │ │(posts,    │ │ (follows)  │◄┘
   │   comments) │ │ users)    │ │            │
   └─────────────┘ └───────────┘ └────────────┘

WRITE PATH (Upload a photo):
┌───────────────────────────────────────────────────────────────────┐
│  1. Photo uploaded to S3 via pre-signed URL (direct, fast)        │
│  2. App server notified of completion → stores metadata in Postgres│
│  3. Media Processing Service triggered:                           │
│     → Generate thumbnail + medium resize                          │
│     → Upload resized versions to S3                               │
│  4. Fan-out Service:                                              │
│     → Kafka event: PhotoPublished { post_id, user_id }            │
│     → Fan-out Worker: for each follower → push post_id to         │
│       their Redis feed sorted set (same as Twitter's fan-out)     │
└───────────────────────────────────────────────────────────────────┘

CELEBRITY PROBLEM (same as Twitter):
  → Instagram accounts with > 1M followers: fan-out on READ
  → Regular users: fan-out on WRITE (same hybrid strategy)
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Photo Upload Pipeline

```
Client → Upload Service (init) → S3 pre-signed URL issued
Client → S3 directly (large file, bypasses app servers!)
  ↓ (S3 event trigger)
S3 → Lambda / Media Processing Service
  ├── Resize to thumbnail (150×150, 50KB)
  ├── Resize to medium (1080px wide, ~200KB)
  └── Compress original with MozJPEG (80% quality, 40% size reduction)
  ↓
All versions → S3 under same post path:
  s3://instagram-media/{post_id}/thumb.jpg
  s3://instagram-media/{post_id}/medium.jpg
  s3://instagram-media/{post_id}/original.jpg
  ↓
CDN (CloudFront) caches all three variants
  ↓
Metadata updated in PostgreSQL: { "thumb": CDN_URL, "medium": CDN_URL }
  ↓
Kafka: PostPublishedEvent → Fan-out service
```

**Why pre-signed S3 URL?**
- App server issues a time-limited (15 min) URL signed with AWS credentials
- Client uploads directly to S3 — bypasses your app servers entirely
- App servers don't become a bottleneck for large file uploads
- This is how Instagram, Dropbox, and Slack all handle uploads

---

### Deep Dive 2: Feed Ranking (Algorithmic Feed)

Unlike Twitter (mostly chronological), Instagram's feed is ranked by an ML model.

**Features used for ranking:**
```
Post features:
  - Time since posted (recency)
  - Like/comment velocity in first 30 min (viral signal)
  - Historical like rate for this author's posts

Relationship features:
  - How often does viewer like/comment on this author's posts?
  - Do viewer and author have mutual friends?
  - Has viewer DM'd this author?

Viewer features:
  - What types of posts does viewer engage with? (travel, food, fashion)
  - Time of day (morning vs evening browsing patterns)
```

**Two-stage ranking:**
```
Stage 1 (Candidate retrieval):
  → Pull ~500 post_ids from viewer's Redis feed cache
  → Also pull top posts from Instagram Explore for diversity

Stage 2 (Ranking):
  → ML model scores each of the ~500 candidates
  → Return top 20 sorted by score
  → Pagination: next 20 on scroll
```

---

### Deep Dive 3: Stories (24-hour TTL Content)

**Storage:**
- Story media: S3 (same as posts)
- Story metadata: Redis with TTL = 24 hours
  ```
  Key: stories:{user_id} → Sorted Set of { story_id: expiry_timestamp }
  ZREMRANGEBYSCORE removes expired entries on every read
  ```
- At expiry: S3 object moved to Glacier (cold storage) — user can never re-access but stored for compliance

**Who viewed my story:**
- View events stream to Cassandra: `(story_id, viewer_id, viewed_at)`
- Story author can see viewer list in real-time
- Expires with story (purged after 24h+)

**Story deletion:**
- Redis TTL expires → story disappears from all viewers
- S3 object gets lifecycle rule (Glacier after 24h, Delete after 90 days)
- Simple, automatic, no background cleanup job needed

---

### Deep Dive 4: Like Count at Scale (46,000 likes/sec)

**Problem**: Cannot do `UPDATE posts SET like_count = like_count + 1` at 46K/sec on PostgreSQL.

**Solution: Redis Counter + Periodic Flush**
```
On like: INCR like_count:{post_id}  (O(1), atomic)
On unlike: DECR like_count:{post_id}

Every 60 seconds: background job
  → GETSET like_count:{post_id} 0   (read and reset in one operation)
  → UPDATE posts SET like_count = like_count + {delta} WHERE post_id = {id}

API response: read like_count from Redis (real-time) + PostgreSQL base (synced periodically)
```

**Why this works:**
- Redis handles 46K incr/sec easily (hundreds of thousands ops/sec per node)
- PostgreSQL only gets 1 write per post per minute (not per like)
- Like counts are approximate within 60 seconds — acceptable

---

### Deep Dive 5: Content Delivery (Photos)

- 500M DAU × avg 50 photos viewed/day = **25 billion photo serves/day**
- = 25B / 86,400 ≈ **~289,000 photo requests/sec**
- Average photo: 200KB → **~58 GB/s bandwidth**

**CDN Strategy (CloudFront/Akamai):**
- Global PoPs cache hot photos (popular posts = same photo requested by millions of followers)
- Cache hit rate: ~95% for popular content (same photo requested by many users)
- Only 5% of requests hit S3 origin
- S3 origin traffic: 289,000 × 5% × 200KB = ~2.9 GB/s (manageable)

**URL structure:**
```
https://instagram.com/p/{post_id}/  ← post page
https://cdn.instagram.com/media/{post_id}/thumb.jpg   ← CDN served
https://cdn.instagram.com/media/{post_id}/medium.jpg
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP (Availability + Partition Tolerance)**
- Like counts can be slightly stale (eventual consistency acceptable)
- Feed can be slightly outdated (a new post appearing 5s late is fine)
- Photos must never be lost (S3 durability, not AP-related)

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Feed generation | Hybrid fan-out (write for regular, read for celebrity) | Pure write fan-out | Same as Twitter — celebrity fan-out causes write amplification |
| Like counting | Redis counter + flush | Per-like DB write | DB can't handle 46K writes/sec per column |
| Photo upload | Direct-to-S3 (pre-signed URL) | Via app server | App server becomes bottleneck for 20 TB/day of uploads |
| Feed ranking | ML model (two-stage) | Chronological | ML ranking drives 3× engagement vs chronological (Meta's research) |
| Stories expiry | Redis TTL | Background cleanup job | TTL is automatic, zero-maintenance; cleanup jobs can lag or miss items |

### What Would You Do Differently at Larger Scale?
- **NSFW detection**: every uploaded photo run through CNN classifier before publishing
- **Duplicate detection**: perceptual hashing to detect exact same photo uploaded twice
- **Video compression**: encode Reels in H.265 (50% smaller than H.264) at upload time
- **AR filters**: run on-device (no server needed) — mention as client-side feature

---

## Interview Flow Summary (Talk Track)

1. "Instagram is essentially Twitter's feed problem + YouTube's media pipeline problem combined"
2. "For media: **direct-to-S3 upload** (pre-signed URL), then transcoding pipeline generates thumbnails"
3. "For feed: **hybrid fan-out** — write to followers' Redis feed for regular users, read on-demand for celebrities"
4. "Feed is **algorithmically ranked** (ML model, two-stage retrieval + scoring)"
5. "Like counts: **Redis INCR** + periodic flush to PostgreSQL — handles 46K likes/sec"
6. "Photos served via **CDN** — 95% cache hit rate for popular content"
7. "Stories: **Redis TTL** for metadata (auto-expires at 24h), S3 media with Glacier lifecycle"

---

> **Previous**: [12 — Design Uber / Lyft](./12-uber-lyft.md)
> **Next**: [14 — Design Key-Value Store (DynamoDB)](./14-key-value-store.md)
