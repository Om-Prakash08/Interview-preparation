# 2. Design Twitter / X Feed

> **Difficulty**: Hard | **Asked At**: Meta, Twitter, Google, Amazon
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Should we support following users and seeing their tweets in a feed?
- Do we need to support likes, retweets, replies, or just the core feed?
- Should the feed be chronological or ranked (algorithmic)?
- Do we need to handle "celebrity" accounts (millions of followers)?
- Do we support hashtags, search, trending topics?
- Direct messages (DMs)?

**Scale:**
- How many daily active users (DAU)?
- How many tweets per day?
- What is the average number of followers per user?

**Typical Interviewer Answer (assume this unless told otherwise):**
- 200 million DAU
- 100 million tweets per day
- Average user follows ~200 accounts
- Feed is mostly chronological (with slight ranking)
- No DMs, no trending for MVP

### 1.2 Functional Requirements (FR)
1. Users can post tweets (text up to 280 chars, optional media)
2. Users can follow / unfollow other users
3. Users see a Home Feed: tweets from people they follow, sorted by time
4. Users can like and retweet tweets
5. User profile page showing their tweets

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Availability** | 99.99% — Feed must load even during partial failures |
| **Feed Latency** | < 200ms for feed generation |
| **Eventual Consistency** | A new tweet appearing in feed within 5 seconds is acceptable |
| **Durability** | Tweets must never be lost |
| **Scalability** | Handle 200M DAU, 100M tweets/day |

### 1.4 Out of Scope
- Twitter Spaces, Polls, DMs
- Trending topics, hashtag search
- Ad insertion

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│   Tweet      │        │   User       │        │   Follow     │
│              │        │              │        │  (edge)      │
│ tweet_id     │        │ user_id      │        │ follower_id  │
│ user_id      │◄───────│ username     │───────►│ followee_id  │
│ text         │        │ display_name │        │ created_at   │
│ media_urls   │        │ bio          │        │              │
│ like_count   │        │ follower_cnt │        └──────────────┘
│ retweet_cnt  │        │ following_cnt│
│ created_at   │        └──────────────┘        ┌──────────────┐
└──────────────┘                                │   Feed       │
                                                │  (cache)     │
                                                │ user_id      │
                                                │ tweet_id     │
                                                │ created_at   │
                                                └──────────────┘
```

**Primary entities**: `Tweet` (content), `User` (author), `Follow` (social graph edge), `Feed` (pre-computed timeline cache).

### 2.2 Data Model / Schema

**Table 1: `tweets`**
```
tweet_id     BIGINT       PRIMARY KEY  (Snowflake ID — time-sortable)
user_id      BIGINT       NOT NULL
text         VARCHAR(280)
media_urls   TEXT[]
created_at   TIMESTAMP
reply_to_id  BIGINT       NULL
retweet_of   BIGINT       NULL
like_count   BIGINT       DEFAULT 0
retweet_count BIGINT      DEFAULT 0
```
**DB Choice**: Cassandra (partition by `user_id`, cluster by `tweet_id` desc)

**Table 2: `follows` (Social Graph)**
```
follower_id  BIGINT
followee_id  BIGINT
created_at   TIMESTAMP
PRIMARY KEY (follower_id, followee_id)
```
**DB Choice**: Graph DB (Neo4j) or wide-column DB (Cassandra)
- For "get all followers of user X" → partition by `followee_id`
- For "get all people user X follows" → partition by `follower_id`

**Table 3: `feed` (Pre-computed user feed — the feed cache)**
```
user_id      BIGINT
tweet_id     BIGINT       (time-sortable = natural sort order)
created_at   TIMESTAMP
PRIMARY KEY (user_id, tweet_id DESC)
```
**DB Choice**: Redis Sorted Set (user_id → sorted list of tweet_ids by timestamp)
- This is the **fan-out write** target
- Each user has a list of tweet_ids in their feed

**Table 4: `users`**
```
user_id      BIGINT       PRIMARY KEY
username     VARCHAR(50)  UNIQUE
display_name VARCHAR(100)
bio          TEXT
follower_count  BIGINT
following_count BIGINT
```
**DB Choice**: PostgreSQL (user data is relational, low write volume)

> 🎯 **NFR addressed**: **Durability** — Cassandra replication ensures tweets are never lost. **Scalability** — Polyglot persistence (each DB for its strength). **Feed Latency** — Redis in-memory sorted sets for < 200ms feed reads.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Post a Tweet
```
POST /api/v1/tweets
Authorization: Bearer <token>

Request:
{
  "text": "Hello world! #twitter",
  "media_ids": ["img_abc123"],    // optional
  "reply_to_tweet_id": null       // optional
}

Response 201 Created:
{
  "tweet_id": "1234567890",
  "text": "Hello world! #twitter",
  "created_at": "2025-07-26T10:00:00Z",
  "author": { "user_id": "u1", "username": "john" }
}
```

### 3.2 Get Home Feed
```
GET /api/v1/feed?limit=20&cursor=<pagination_token>
Authorization: Bearer <token>

Response 200:
{
  "tweets": [ { tweet_object }, { tweet_object }, ... ],
  "next_cursor": "eyJ0aW1l..."
}
```

### 3.3 Follow a User
```
POST /api/v1/users/{user_id}/follow
→ 200 OK

DELETE /api/v1/users/{user_id}/follow
→ 200 OK
```

### 3.4 Like a Tweet
```
POST /api/v1/tweets/{tweet_id}/like
→ 200 OK
```

> 🎯 **NFR addressed**: **Feed Latency** — cursor-based pagination avoids expensive offset queries. **Availability** — stateless APIs enable horizontal scaling.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Tweet Writes:**
- 100 million tweets/day
- = 100M / 86,400 ≈ **~1,160 writes/sec**
- Peak (2× average): ~2,300 tweets/sec

**Feed Reads:**
- 200M users × average 5 feed checks/day = **1 billion feed requests/day**
- = 1B / 86,400 ≈ **~11,600 reads/sec**
- Peak: ~30,000 reads/sec

**Storage:**
- Tweet size: ~300 bytes (text + metadata)
- Per day: 100M × 300B = **30 GB/day**
- Per year: **~10.95 TB/year**
- Media (photos/videos): separate blob store — not included in this estimate

**Fan-out write volume:**
- Average 200 followers per user
- 1,160 tweets/sec × 200 followers = **232,000 feed entries/sec to write**
- For celebrities (50M followers): 1 tweet → 50M feed insertions (the "celebrity problem")

### 4.2 Data Flow Through System

**Write Path (Posting a tweet):**
```
User posts tweet → Tweet Service
  → Persist tweet to Cassandra (tweet store)
  → Publish TweetCreatedEvent to Kafka
  → Fan-out Service consumes event
  → Lookup all followers in Social Graph
  → Write tweet_id into each follower's Redis Sorted Set (score = timestamp)
```

**Read Path (Loading feed):**
```
User opens app → GET /feed → Feed Service
  → Read top N tweet_ids from user's Redis Sorted Set
  → Fetch full tweet objects from Cassandra (or secondary cache)
  → Hydrate author info from User DB
  → Return assembled feed to client
```

> 🎯 **NFR addressed**: **Eventual Consistency** — async fan-out via Kafka means tweets appear within seconds, not instantly. **Scalability** — decoupled write/read paths scale independently.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                    ┌────────────────────────────────────────────────────┐
                    │                     CLIENTS                         │
                    │              (Web / iOS / Android)                  │
                    └─────────────────────┬──────────────────────────────┘
                                          │
                               ┌──────────▼──────────┐
                               │    Load Balancer     │
                               │   (AWS ALB / Nginx)  │
                               └──────────┬──────────┘
                                          │
                  ┌───────────────────────┼───────────────────────┐
                  │                       │                       │
        ┌─────────▼────────┐   ┌──────────▼────────┐  ┌──────────▼────────┐
        │   Tweet Service  │   │   Feed Service    │  │   User Service    │
        │  (write tweets)  │   │  (read feed)      │  │  (follow/profile) │
        └────────┬─────────┘   └──────────┬────────┘  └──────────┬────────┘
                 │                        │                       │
                 │                        │                       │
        ┌────────▼──────────┐    ┌────────▼────────┐    ┌────────▼────────┐
        │  Message Queue    │    │  Feed Cache      │    │  User DB        │
        │  (Kafka)          │    │  (Redis)         │    │  (PostgreSQL)   │
        └────────┬──────────┘    └─────────────────┘    └─────────────────┘
                 │
        ┌────────▼────────────────────────────┐
        │         Fan-out Service             │
        │   Consumes tweet events from Kafka  │
        │   Writes tweet_id to each           │
        │   follower's Redis feed list        │
        └────────┬──────────────────┬─────────┘
                 │                  │
        ┌────────▼──────┐  ┌────────▼───────────┐
        │  Social Graph │  │  Tweet Store       │
        │  (Followers   │  │  (Cassandra)       │
        │   lookup)     │  │  stores tweet data │
        └───────────────┘  └────────────────────┘

        ┌──────────────────────────────────────┐
        │         CDN (CloudFront)             │
        │   Serves media (photos, videos)      │
        │   from S3 blob store                 │
        └──────────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Tweet Service** | Persists tweets + publishes to Kafka | Write path isolated from reads for independent scaling |
| **Feed Service** | Reads pre-computed feed from Redis | Stateless, horizontally scalable; sub-200ms feed reads |
| **Fan-out Service** | Consumes Kafka events, populates follower feeds in Redis | Decoupled from tweet creation; can scale consumer group independently |
| **Redis (Feed Cache)** | Sorted Set per user with tweet_ids | In-memory for < 200ms reads; natural sorted ordering |
| **Cassandra (Tweet Store)** | Durable storage for all tweet data | Write-optimized (LSM tree), partition by user_id for profile pages |
| **Kafka** | Async event bus between Tweet Service and Fan-out | Buffers spikes, ensures no tweet events are lost during fan-out |
| **Social Graph** | Stores follow/follower edges | Partitioned for both "who do I follow" and "who follows me" queries |

> 🎯 **NFR addressed**: **Availability 99.99%** — Kafka buffers events if Fan-out Service is down; Redis replicas for cache reads. **Feed Latency < 200ms** — Redis in-memory sorted sets. **Scalability** — each service scales independently; Kafka partitions fan-out across consumer group.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: The Celebrity Problem (Fan-out on Write vs Read)

This is the MOST important deep dive for this question.

**Problem:**
- Fan-out on Write: When a tweet is posted, immediately write to all followers' feeds
- Works great for regular users (200 followers → 200 writes)
- Breaks for celebrities: Lady Gaga has 50M followers → 1 tweet = 50M Redis writes
- This creates massive spikes; Kafka consumer would be overwhelmed

**Solution: Hybrid Fan-out**
```
Regular users  (< 1M followers):  Fan-out on WRITE  → push tweet_id to all followers' Redis feeds immediately
Celebrity users (≥ 1M followers): Fan-out on READ   → DON'T pre-populate feeds; fetch from celebrity's tweet store on demand at read time
```

**At Read Time (Feed Service):**
1. Read pre-populated feed from Redis (from regular users)
2. Check if any followed accounts are "celebrities"
3. Fetch their recent tweets directly from Cassandra
4. Merge both lists, sort by timestamp
5. Return to user

This hybrid approach handles both extremes elegantly.

---

### Deep Dive 2: Feed Ranking (Chronological → Algorithmic)

**Pure chronological**: Sort by `tweet_id` (Snowflake IDs are time-ordered)

**Algorithmic ranking** (like Twitter's "For You"):
- Score each tweet: `score = recency_weight × (likes + 2×retweets + 3×replies)`
- Redis Sorted Set can use this score instead of timestamp
- ML model can re-rank feed based on user engagement history

For MVP: use chronological. Mention algorithmic as a future enhancement.

---

### Deep Dive 3: Feed Caching

- Redis Sorted Set per user: `feed:{user_id}` → sorted set of tweet_ids
- **Capacity**: If only active users have a feed cache:
  - 50M active users × 800 tweet_ids × 8 bytes = **~320 GB RAM**
  - Distribute across Redis Cluster nodes
- **TTL**: If user hasn't opened app in 3 days, evict their feed from Redis
  - On next open, **cold start**: backfill feed by querying all followed users' recent tweets from Cassandra, rebuild Redis feed

---

### Deep Dive 4: Handling Retweets

- A retweet creates a new tweet record with `retweet_of = original_tweet_id`
- Fan-out behaves the same way
- On feed display: fetch original tweet data, show "X retweeted"
- Like counts live on the original tweet only (avoid double-counting)

---

### Deep Dive 5: Availability & Fault Tolerance

- **Kafka**: Replicated topic partitions across multiple brokers. If Fan-out Service fails, events wait in Kafka — no data loss.
- **Redis failure**: Fall back to reading feed directly from Cassandra (slower, but correct)
- **Cassandra**: 3-replica replication across AZs. Quorum reads ensure consistency.
- **Idempotent fan-out**: If fan-out runs twice for same tweet, Redis Sorted Set's upsert semantics prevent duplicates.

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
**AP (Availability + Partition Tolerance)**
- Acceptable: A new tweet may take 5–10 seconds to appear in all followers' feeds (eventual consistency)
- Not acceptable: Feed fails to load entirely
- Cassandra and Redis are both AP systems — perfect fit

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Feed storage | Redis Sorted Set | Cassandra list | Redis is faster (in-memory), perfect for real-time feed reads |
| Fan-out strategy | Hybrid (write for regular, read for celebrity) | Pure write fan-out | Pure write fan-out breaks at celebrity scale |
| Async fan-out | Kafka queue | Synchronous writes | Sync writes would make posting a tweet slow (wait for all fan-outs) |
| Tweet ID generation | Snowflake (time-sortable) | UUID | Snowflake is naturally sortable by time — crucial for feed ordering |
| Social graph | Cassandra with two partition keys | Neo4j | Neo4j is powerful for deep graph traversal; for Twitter's simple follow relationship, Cassandra is sufficient and more scalable |

**What Would You Do Differently at Larger Scale?**
- Add **topic-based sharding** in Kafka (partition by `author_user_id` for ordered fan-out)
- Introduce **ML re-ranking layer** before returning feed
- **Multi-region deployment** with geo-routing for global low latency
- Add **read replicas** for Cassandra tweet store to handle peak read traffic

---

### Summary Talk Track

1. "First, let me clarify scope — specifically, how many followers does an average user have, and do we need to support celebrities?"
2. "The core entities are **Tweet**, **User**, **Follow graph**, and a pre-computed **Feed cache** in Redis."
3. "The core design challenge is the **fan-out problem** — I'll use a hybrid approach: fan-out on write for regular users, fan-out on read for celebrities."
4. "Write path: Tweet → Kafka → Fan-out Service → Redis. Read path: Redis feed → Cassandra tweet lookup → hydration → response."
5. "The key trade-off is eventual consistency — a tweet appearing within 5 seconds is fine for Twitter."
6. "CAP: **AP** — feed loading must never fail; a few seconds of staleness is acceptable."

---

> **Previous**: [01 — Design URL Shortener](./01-url-shortener.md)
> **Next**: [03 — Design WhatsApp Messenger](./03-whatsapp-messenger.md)
