# 1. Design URL Shortener (TinyURL / Bitly)

> **Difficulty**: Medium | **Asked At**: Google, Amazon, Meta, Microsoft, Twitter
> **Time to Answer in Interview**: 35–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

> Always spend the first 3–5 minutes asking these before drawing anything.
> It shows structured thinking and avoids wasted effort.

**Functional Scope:**
- Should the short URL be random or customizable (e.g., tinyurl.com/my-brand)?
- Do we need analytics — clicks, geo, device, referrer?
- Should URLs expire? If yes, after how long (TTL)?
- Should we support URL deduplication (same long URL → same short URL)?
- Do we need user accounts (private vs public links)?

**Scale:**
- How many URLs are created per day?
- How many redirects (reads) per day?
- What is the expected read-to-write ratio?

**Typical Interviewer Answer (assume this unless told otherwise):**
- 100 million URLs created per day
- 10 billion redirects per day (100:1 read-to-write ratio)
- Links expire after 5 years by default
- No user accounts needed for MVP
- Analytics: basic click count is enough

### 1.2 Functional Requirements (FR)
1. Given a long URL, generate a unique short URL (e.g., `tinyurl.com/abc123`)
2. Redirect users from short URL → original long URL
3. Short URL should be optionally customizable
4. Short URLs expire after a configurable TTL
5. Basic click analytics (total clicks per URL)

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Availability** | 99.99% (4 nines) — redirect must never fail |
| **Latency** | < 10ms for redirect (users must not feel it) |
| **Durability** | URLs must not be lost once created |
| **Scalability** | Handles 10B+ redirects/day without degradation |
| **Security** | No URL enumeration (can't guess other users' URLs) |

### 1.4 Out of Scope
- Payment, user authentication
- Link previews / social cards
- Bulk URL import

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│   URL        │        │  ClickEvent  │        │   User       │
│              │        │              │        │  (future)    │
│ short_code   │───────►│ short_code   │        │              │
│ long_url     │        │ timestamp    │        │ user_id      │
│ expires_at   │        │ user_agent   │        │ created_at   │
│ click_count  │        │ ip           │        │              │
│ created_at   │        │              │        │              │
└──────────────┘        └──────────────┘        └──────────────┘
```

**Primary entity**: `URL` — maps a short code to a long URL. The access pattern is purely key-value: `short_code → long_url`.

### 2.2 Data Model / Schema

**Core Table: `urls`**
```
urls
─────────────────────────────────────────────
short_code   VARCHAR(8)   PRIMARY KEY
long_url     TEXT         NOT NULL
created_at   TIMESTAMP    DEFAULT NOW()
expires_at   TIMESTAMP
click_count  BIGINT       DEFAULT 0
user_id      BIGINT       NULL  (future)
```

### 2.3 Database Choice: **NoSQL (Cassandra or DynamoDB)**

**Why not MySQL/PostgreSQL?**
- 115,000 reads/sec is too high for a single relational DB node
- We don't need JOINs — data access is purely key-value: `short_code → long_url`
- Horizontal scaling is hard with relational DBs

**Why Cassandra / DynamoDB?**
- Designed for key-value lookup at massive scale
- Horizontal sharding built-in
- High availability with replication
- `short_code` is a perfect partition key (distributed, unique)

**Sharding Key:** `short_code` — ensures even distribution across nodes.

**Indexing:** `short_code` is the primary key. No secondary indexes needed for the core path.

**For Analytics:** Use a separate **time-series DB** (e.g., ClickHouse or BigQuery) or stream click events to Kafka → analytics pipeline. Don't write to main DB on every click (would destroy performance at 115K rps).

> 🎯 **NFR addressed**: **Durability** — Cassandra replication factor 3 ensures URLs are never lost. **Scalability** — NoSQL horizontal sharding handles 10B+ reads/day. **Security** — Base62 random codes prevent URL enumeration.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Create Short URL
```
POST /api/v1/urls
Content-Type: application/json

Request Body:
{
  "long_url": "https://www.example.com/very/long/path?query=123",
  "custom_alias": "my-brand",       // optional
  "ttl_days": 365                   // optional, default 1825 (5 years)
}

Response 201 Created:
{
  "short_url": "https://tinyurl.com/abc1234",
  "short_code": "abc1234",
  "expires_at": "2031-07-26T00:00:00Z"
}
```

### 3.2 Redirect
```
GET /{short_code}
→ HTTP 301 or 302 Redirect to long_url
```
> **301 vs 302 — Important Trade-off:**
> - **301 (Permanent)**: Browser caches it. Reduces server load. But you lose analytics (clicks not tracked after first visit).
> - **302 (Temporary)**: Browser always hits server. Analytics works perfectly. Slightly higher load.
> - **Recommendation**: Use **302** if analytics matters. Use **301** for pure performance.

### 3.3 Delete URL (optional)
```
DELETE /api/v1/urls/{short_code}
→ 204 No Content
```

### 3.4 Get Analytics
```
GET /api/v1/urls/{short_code}/stats
→ { "short_code": "abc1234", "total_clicks": 5820, "created_at": "..." }
```

> 🎯 **NFR addressed**: **Latency** — simple GET redirect with no body parsing keeps response < 10ms. **Security** — no API exposes the ability to enumerate short codes.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

> Interviewers LOVE this section. Walk through it out loud, step by step.

**Writes (URL creation):**
- 100 million URLs/day
- = 100M / 86,400 sec ≈ **~1,160 writes/sec (QPS)**

**Reads (redirects):**
- 10 billion redirects/day (100:1 ratio)
- = 10B / 86,400 ≈ **~115,000 reads/sec (QPS)**

**Storage:**
- Each URL record: ~500 bytes (long URL ~400B + metadata ~100B)
- Per day: 100M × 500B = **50 GB/day**
- Per year: 50GB × 365 = **~18 TB/year**
- Over 5 years: **~90 TB total**

**Short URL Length:**
- We need enough unique IDs for 100M/day × 365 × 5 = **~182 billion URLs**
- Base62 charset: [a-z A-Z 0-9] = 62 characters
- 62^7 = **3.5 trillion** → 7 characters is enough ✅

**Bandwidth:**
- Reads: 115,000 req/s × 500B ≈ **55 MB/s** outbound
- Manageable, no special bandwidth concern.

### 4.2 Data Flow Through System

**Write Path (Creating a short URL):**
```
Client → POST /api/v1/urls → Load Balancer → URL Creation Service
  → ID Generator (get unique 7-char code)
  → Store {short_code, long_url, expires_at} in Cassandra
  → Optionally cache in Redis
  → Return short URL to client
```

**Read Path (Redirecting):**
```
Client → GET /abc1234 → Load Balancer → Redirect Service
  → Check Redis Cache
  → ✅ Cache Hit → 302 Redirect immediately
  → ❌ Cache Miss → Query Cassandra → Cache result → 302 Redirect
  → Async: publish ClickEvent to Kafka for analytics
```

> 🎯 **NFR addressed**: **Latency** — Redis cache hit path delivers < 10ms redirects. **Scalability** — 100:1 read-to-write ratio handled by cache-heavy architecture.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                           ┌─────────────────────────────────────────────┐
                           │                   CLIENTS                    │
                           │        (Browsers, Mobile Apps, API)          │
                           └─────────────────────┬───────────────────────┘
                                                 │
                                          HTTPS Requests
                                                 │
                           ┌─────────────────────▼───────────────────────┐
                           │              LOAD BALANCER                   │
                           │          (AWS ALB / NGINX / HAProxy)         │
                           └──────┬──────────────────────────┬───────────┘
                                  │                          │
                    POST /api/v1/urls               GET /{short_code}
                                  │                          │
                  ┌───────────────▼──────┐    ┌─────────────▼─────────────┐
                  │   URL Creation       │    │    Redirect Service        │
                  │   Service            │    │    (Read-heavy)            │
                  └───────────┬──────────┘    └──────────┬────────────────┘
                              │                          │
                              │               ┌──────────▼──────────┐
                              │               │   Cache Layer        │
                              │               │   (Redis Cluster)    │
                              │               │   short_code→long_url│
                              │               └──────────┬──────────┘
                              │                    Hit?  │  Miss?
                              │                  ────────┴────────
                              │                  ↓               ↓
                              │             Return URL      DB Lookup
                              │                             + Cache it
                              │
                  ┌───────────▼────────────────────────────────────┐
                  │           Database (Cassandra / DynamoDB)       │
                  │           Partitioned by short_code             │
                  │           Replicated across 3 DCs               │
                  └────────────────────────────────────────────────┘
                              │
                  ┌───────────▼──────────────┐
                  │   ID Generator Service    │
                  │   (Snowflake / ZooKeeper) │
                  └──────────────────────────┘

                  ┌──────────────────────────────────────────────┐
                  │              Analytics Pipeline               │
                  │  Click Event → Kafka → Stream Processor       │
                  │             → ClickHouse / BigQuery           │
                  └──────────────────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Load Balancer** | Distributes traffic across service instances | Prevents single point of failure; enables horizontal scaling |
| **URL Creation Service** | Handles short URL generation + storage | Separated from read path for independent scaling |
| **Redirect Service** | Handles 302 redirects (read-heavy, 100:1) | Scales independently; stateless — add more instances for throughput |
| **Redis Cache** | In-memory cache for hot URLs | Sub-millisecond lookups; 20% of URLs serve 80% of traffic (Pareto) |
| **Cassandra** | Persistent storage for all URL mappings | Key-value access pattern, horizontal sharding, multi-DC replication |
| **ID Generator** | Produces unique 7-char Base62 codes | Range-based KGS via ZooKeeper — no collision, no coordination overhead |
| **Analytics Pipeline** | Kafka → Flink → ClickHouse | Decouples click tracking from redirect latency; handles 115K events/sec |

> 🎯 **NFR addressed**: **Availability 99.99%** — no single point of failure; multi-DC Cassandra + Redis replicas. **Latency < 10ms** — Redis cache hit for hot URLs. **Scalability** — each component scales horizontally. **Durability** — Cassandra RF=3 across DCs.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: How to Generate Unique Short Codes?

This is often the trickiest part. There are 3 approaches:

#### Approach A: Random + Collision Check (Bad at Scale)
- Generate random 7-char string, check if it exists in DB
- Problem: At 182B URLs, collision probability becomes significant. DB check on every write = slow.

#### Approach B: MD5/SHA256 Hash of Long URL (Decent)
- Hash the long URL, take first 7 chars
- Problem: Different long URLs can map to same 7 chars (hash collision)
- Also: same long URL → same short URL (deduplication, could be a feature or bug)

#### Approach C: Unique ID Generator → Base62 Encode ✅ (Best)
- Base62 character set: `[a-z, A-Z, 0-9]` (62 unique characters).
- 7 characters can hold: $62^7 = 3,521,614,606,208 \approx \mathbf{3.52 \text{ trillion unique IDs}}$.
- **Important Bit-Length Math**:
  - $3.52 \text{ trillion} \approx 2^{41.67}$ (fits in a **41-bit or 42-bit integer**).
  - A standard 64-bit Snowflake ID (up to $2^{64}-1 \approx 18.4 \text{ quintillion}$) produces **10–11 Base62 characters**.
- **To support 2,000+ writes/sec while maintaining strictly $\le 7$ Base62 characters**:

  1. **Option 1: Range-based Key Generation Service (KGS + ZooKeeper) — Recommended ✅**:
     - **Why it handles uneven traffic effortlessly**:
       - Central ZooKeeper allocates ranges (e.g. Server 1 gets `1–1,000,000`, Server 2 gets `1,001,000–2,000,000`).
       - If traffic is uneven (e.g. Server 1 gets 90% of requests and Server 2 gets 10%), Server 1 simply consumes its 1M IDs faster and requests a new range (`2,000,001–3,000,000`).
       - **No per-second rate limit per server, no sequence overflow risk, and no dependence on even load balancing**.
     - Converts any assigned numeric ID $\le 3.52 \text{ trillion}$ into **strictly $\le 7$ Base62 characters**.
     - If a server node crashes, its unassigned in-memory sub-range is discarded; new IDs continue from next block (harmless gap).

  2. **Option 2: Custom 42-bit Timestamp ID (Suffers from Uneven Traffic Flaw ⚠️)**:
     - **31 bits**: Timestamp in seconds (~68 years from custom epoch).
     - **4 bits**: Worker / Machine ID (16 server nodes).
     - **7 bits**: Sequence counter per second ($2^7 = 128$ IDs/sec per node).
     - **Drawback / Flaw**: If load balancer traffic is uneven and 1 server receives a burst of >128 req/sec, its 7-bit counter overflows during that second!
     - *Conclusion*: **Option 1 (Range-based KGS)** is vastly superior in real-world production.

---

### Deep Dive 2: Caching Strategy

- **What to cache**: `short_code → long_url` (most popular 20% of URLs = 80% of traffic, Pareto principle)
- **Cache tool**: **Redis Cluster** (in-memory, sub-millisecond lookups)
- **TTL**: Cache entries expire matching URL's `expires_at` field
- **Eviction Policy**: LRU (Least Recently Used) — evict cold URLs first
- **Cache size estimation**: 
  - Hot URLs = 20% of 182B = 36B entries... but not all active at once
  - Assume 10M hot URLs at any time: 10M × 500B = **~5 GB** — very manageable for Redis

---

### Deep Dive 3: Handling URL Expiry

- **Problem**: Expired URLs must stop redirecting, and storage should be reclaimed
- **Solution 1 (Lazy Deletion)**: On every redirect, check `expires_at < now()`. If expired, return 404. Delete in background. Simple, but expired records stay in DB.
- **Solution 2 (TTL in Redis)**: Set Redis key TTL to match URL expiry. Expired URLs auto-evict from cache. DB cleanup is a separate offline job.
- **Solution 3 (Scheduled Cleanup Job)**: Background job runs nightly, deletes all records where `expires_at < now()`. Clean DB. Slightly delayed deletion.
- **Recommended**: Combine Lazy Deletion (for correctness) + Scheduled Cleanup (for storage reclamation).

---

### Deep Dive 4: Availability & Fault Tolerance

- **Multiple Redirect Service instances** behind load balancer — if one dies, others serve traffic
- **Cassandra** replication factor = 3 across multiple availability zones. Even if one DC goes down, reads/writes continue.
- **Redis** with replica nodes — if primary fails, replica is promoted automatically
- **No single point of failure** anywhere in the critical read path

---

### Deep Dive 5: Analytics at Scale

- **Problem**: 115,000 clicks/sec. You CANNOT write to main DB on every click (would kill it)
- **Solution**: 
  1. On redirect, publish a `ClickEvent {short_code, timestamp, user_agent, ip}` to **Kafka**
  2. Kafka buffers the events (handles spikes)
  3. **Stream processor** (Flink / Spark Streaming) aggregates click counts per short_code per time window
  4. Writes aggregated results to **ClickHouse** or **DynamoDB** (analytics store)
  5. Analytics API reads from analytics store (never from main Cassandra)

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
This system chooses **AP (Availability + Partition Tolerance)** over CP:
- A redirect that returns stale data (old long URL) for a few milliseconds is acceptable
- A redirect that fails entirely (returns 500) is NOT acceptable
- Cassandra is AP by design — perfect fit

**Key Trade-offs Table:**

| Decision | Choice Made | Alternative | Why Not Alternative? |
|---|---|---|---|
| Database | Cassandra | PostgreSQL | Postgres can't horizontally scale to 115K rps easily |
| ID Generation | Snowflake | UUID | UUID is 128-bit, wasteful. Snowflake is shorter, sortable, time-ordered |
| Redirect Type | 302 Temporary | 301 Permanent | 301 breaks analytics (browser caches, no server hit) |
| Short Code | Base62 (7 chars) | Base10 | Base10 needs more chars for same cardinality |
| Cache Eviction | LRU | LFU | LFU is complex; LRU works well for URL patterns |

**What Would You Do Differently at Larger Scale?**
- Add **CDN layer** (e.g., CloudFront) in front of Redirect Service — cache redirects at edge, sub-5ms globally
- **Pre-warm cache** for known viral URLs (marketing campaigns)
- **Geo-distributed** DB deployment with local reads for global users

---

### Summary Talk Track

> Practice saying this out loud:

1. "Let me start by asking a few clarifying questions..." *(ask Step 1 questions)*
2. "The core entity is a URL mapping — `short_code → long_url`. I'd use **Cassandra** for key-value lookups at scale."
3. "The API is simple — POST to create, GET to redirect with a 302."
4. "At 115K reads/sec, we need a **Redis cache** in front of Cassandra — hot URLs served in < 1ms."
5. "Here's the overall architecture..." *(draw Step 5 diagram)*
6. "The most interesting deep dive is **ID generation** — I'd use a Range-based KGS with ZooKeeper for collision-free, 7-char Base62 codes."
7. "The key trade-off is **301 vs 302** for redirects and **AP over CP** since stale redirect data is acceptable but downtime is not."

---

> **Next**: [02 — Design Twitter / X Feed](./02-twitter-feed.md)
