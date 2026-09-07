# 8. Design Search Autocomplete / Typeahead

> **Difficulty**: Medium-Hard | **Asked At**: Google, Amazon, LinkedIn, Twitter, Yelp
> **Time to Answer in Interview**: 35–40 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Show top suggestions as user types — from which data source? (search history, popular searches, product catalog?)
- How many suggestions to show? (typically 5–10)
- Should suggestions be personalized (based on user history) or global top searches?
- Support multiple languages?
- Spell correction / fuzzy matching?
- Real-time trending (e.g., if a topic goes viral, should it appear in suggestions within minutes)?

**Scale:**
- How many requests per second?
- How large is the suggestion corpus? (millions of queries vs billions)
- How often does the corpus update?

**Typical Interviewer Answer:**
- Global top queries (not personalized for MVP)
- Show 5 suggestions, ranked by popularity
- 500 million DAU, each types ~5 searches/day
- Update suggestions based on last 7 days of search volume
- No fuzzy matching for MVP (prefix match only)
- Multiple languages in scope

### 1.2 Functional Requirements (FR)
1. As user types each character, return top 5 matching query suggestions
2. Suggestions ranked by search frequency/popularity
3. New queries and popularity updates reflected within 1 hour
4. Case-insensitive prefix matching

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Latency** | < 100ms end-to-end (human types fast; suggestions must keep up) |
| **Availability** | 99.99% — degraded gracefully (no suggestions ≠ broken search) |
| **Scalability** | Handle 100K+ autocomplete requests/sec |
| **Freshness** | New trending terms appear in suggestions within ~1 hour |

### 1.4 Out of Scope
- Spell correction (Levenshtein / BK-tree based fuzzy matching)
- Personalized suggestions
- Autocomplete for structured data (e.g., product names)

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  TrieNode        │      │  QueryScore      │      │  SearchEvent     │
│                  │      │  (Redis)         │      │  (Kafka)         │
│  children[]      │      │                  │      │                  │
│  is_end_of_word  │      │  query_text      │      │  query_text      │
│  top_k: [(query, │──────│  frequency_score │      │  user_id         │
│    score)]       │      │  locale          │      │  locale          │
│  total_count     │      │                  │      │  timestamp       │
└──────────────────┘      └──────────────────┘      └──────────────────┘
```

**Primary entities**: `TrieNode` (in-memory prefix tree for O(L) lookup), `QueryScore` (popularity scores in Redis sorted sets), `SearchEvent` (raw search events flowing through Kafka for aggregation).

### 2.2 Data Model / Core Data Structures

**Option A: Trie (Prefix Tree) — The Classic Answer**
```
            root
           / | \
          a  b  c ...
         / \
        p   n
        |   |
        p   d ...
        |
        l (apple, application, applebees)
        e
       / \
      (apple)  ...
```

**Each node stores:**
```
TrieNode {
  children: Map<char, TrieNode>   // 26 entries (or 256 for Unicode)
  is_end_of_word: bool
  top_k: List<(query, score)>     // pre-computed top 5 queries passing through this node
  total_count: long               // sum of all query frequencies under this prefix
}
```

**Top-K caching in each node** (critical optimization):
- Instead of traversing all children to find top 5 on every request, each node pre-computes and caches its top 5 suggestions
- On insert/update: propagate score change up to all ancestor nodes, update their top-5 lists
- Query time: O(prefix_length) — just traverse to the prefix node, return cached top-5

**Option B: Redis Sorted Set (Production Approach) ✅**
```
For each prefix, store a Redis Sorted Set:
Key: "autocomplete:en:app"
Members: { "apple": 9823456, "application": 6100200, ... }
Score: search frequency (higher = better)

Key: "autocomplete:en:ap"
Members: { "apple": 9823456, "application": 6100200, "app store": ... }
```

**Query:**
```
ZREVRANGEBYSCORE autocomplete:en:app +inf -inf LIMIT 0 5
→ Returns top 5 members by score (desc)
→ Sub-millisecond Redis lookup
```

**Hybrid Approach (Best in Interview) ✅**
```
Trie (in memory on Autocomplete Service):
  - Stores complete prefix tree
  - Each node has top-50 candidate queries (not final scores)

Redis Sorted Set:
  - Stores exact query → frequency scores
  - Updated in near real-time from the analytics pipeline

Query path:
  1. Trie lookup: traverse to prefix node → get top-50 candidate queries
  2. Redis ZSCORE: get fresh scores for each candidate (batch lookup)
  3. Sort candidates by score → return top 5
```

> 🎯 **NFR addressed**: **Latency < 100ms** — Trie O(L) prefix lookup + Redis sub-ms batch scoring. **Freshness** — Redis scores updated from pipeline; trie rebuilt hourly. **Scalability** — hybrid distributes work between in-memory trie and Redis cluster.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Get Autocomplete Suggestions
```
GET /api/v1/autocomplete?q=app&limit=5&locale=en
Authorization: (optional for global suggestions)

Response 200:
{
  "query": "app",
  "suggestions": [
    { "text": "apple", "score": 9823456 },
    { "text": "apple store", "score": 7234100 },
    { "text": "application", "score": 6100200 },
    { "text": "apple music", "score": 5890000 },
    { "text": "applebees", "score": 3210000 }
  ]
}
```

### 3.2 Record Search (Internal — called when user submits search)
```
POST /api/v1/search/record
{
  "query": "apple iphone 15",
  "user_id": "u123",     // optional
  "locale": "en"
}
→ 202 Accepted (fire and forget)
```

> 🎯 **NFR addressed**: **Latency** — simple GET with CDN caching for popular prefixes. **Availability** — 202 Accepted for search recording; fire-and-forget so it never blocks the user.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Query Volume:**
- 500M DAU × 5 searches/day = 2.5 billion searches/day
- Each search = ~5 keystrokes on average before selecting suggestion
- Autocomplete requests: 2.5B × 5 = **12.5 billion autocomplete calls/day**
- = 12.5B / 86,400 ≈ **~145,000 requests/sec**
- Peak (2×): ~290,000 requests/sec

**Data Size:**
- Number of unique popular queries to store: ~10 million (top queries cover 99% of traffic)
- Average query length: 30 bytes
- Trie node size: ~50 bytes
- Full trie: 10M × avg 15 nodes × 50 bytes = **~7.5 GB** — fits in RAM!

**Update Volume:**
- New search events: 2.5B/day = ~29,000/sec → these feed into a stream to update popularity

### 4.2 Data Flow Through System

**Read Path (User types "app"):**
```
User keystroke → CDN Cache (CloudFront)
  → Hit? → Return cached suggestions (<5ms)
  → Miss → Autocomplete Service
    → Trie lookup → Get top-50 candidates
    → Redis batch ZSCORE → Get fresh scores
    → Sort, return top 5
```

**Write Path (User submits search):**
```
Search Event → Kafka (search_events topic)
  → Flink Aggregator (counts per 7-day sliding window)
  → Emits updated scores every 10 minutes
  → Redis ZADD (update scores)
  → Trie Rebuilder (hourly batch job)
    → Reads top 10M queries + scores from Redis
    → Rebuilds trie from scratch
    → Distributes to Autocomplete Service instances (blue-green swap)
```

> 🎯 **NFR addressed**: **Scalability** — CDN absorbs 80% of popular prefix traffic. **Freshness** — Kafka → Flink → Redis pipeline updates scores within minutes; trie rebuilt hourly. **Latency** — CDN + in-memory trie = sub-100ms.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
               USER TYPES "app..."
                      │
                      │ GET /autocomplete?q=app (every keystroke)
                      │
              ┌───────▼────────┐
              │  CDN Cache     │◄──── Cache popular prefixes at edge
              │  (CloudFront)  │      ("app" → same top 5 for everyone)
              └───────┬────────┘
                      │ Cache miss (rare)
              ┌───────▼────────────────┐
              │  Autocomplete Service  │
              │  (fleet of servers)    │
              │                        │
              │  1. Check local trie   │
              │  2. Get candidate list │
              │  3. Score from Redis   │
              │  4. Return top 5       │
              └───────┬────────────────┘
                      │
         ┌────────────┼────────────┐
         │                        │
┌────────▼──────┐        ┌────────▼──────────────┐
│  Trie Service │        │  Redis Cluster         │
│  (in-memory   │        │  Stores query scores   │
│   trie per    │        │  per locale            │
│   locale)     │        └────────────────────────┘
└───────────────┘

─────────────────────────────────────────────────────────────────────

USER SUBMITS SEARCH → Score Update Pipeline
─────────────────────────────────────────────────────────────────────

Search Event
     │
     ▼
Kafka (search_events topic)
     │
     ▼
Aggregator (Flink / Spark Streaming)
  - Counts query frequency per 7-day sliding window
  - Emits updated scores every 10 minutes
     │
     ▼
Redis ZADD (update scores)
     │
     ▼
Trie Rebuilder (hourly batch job)
  - Reads top 10M queries + scores from Redis
  - Rebuilds trie from scratch
  - Distributes new trie to all Autocomplete Service instances
  - Blue-green switch (no downtime)

─────────────────────────────────────────────────────────────────────
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **CDN (CloudFront)** | Caches popular prefix responses at edge | 80% hit rate for 1–3 char prefixes; reduces backend load by 5× |
| **Autocomplete Service** | Trie lookup + Redis scoring | Stateless (trie loaded on startup); horizontally scalable |
| **In-memory Trie** | O(prefix_length) candidate retrieval | 7.5 GB trie fits in RAM; sub-ms traversal |
| **Redis Cluster** | Stores per-query frequency scores | Sub-ms ZSCORE lookups; updated near real-time from pipeline |
| **Kafka** | Ingests search events at 29K/sec | Decouples search recording from score aggregation |
| **Flink Aggregator** | Counts query frequency over 7-day window | Stream processing for near real-time score updates |
| **Trie Rebuilder** | Hourly batch rebuild of trie | Fresh trie with blue-green deployment; zero downtime |

> 🎯 **NFR addressed**: **Latency < 100ms** — CDN cache hit + in-memory trie + Redis batch scoring. **Availability 99.99%** — CDN serves even if backend is temporarily slow; blue-green trie swap = zero downtime. **Scalability** — CDN absorbs 80% traffic; autocomplete service scales horizontally. **Freshness** — pipeline updates within 10 minutes; trie rebuilt hourly.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: CDN Caching for Autocomplete

**Key insight**: Autocomplete requests for the same prefix from different users return the same result (global ranking)!

```
GET /autocomplete?q=app → same result for ALL users
```

This means we can cache suggestions at the CDN edge with high hit rates:
- Short prefixes ("a", "ap", "app") → very high traffic → cache with TTL 10 minutes
- Longer prefixes ("apple i", "apple ip") → low traffic → serve from backend directly
- **Cache hit rate**: ~80% for popular 1–3 character prefixes

**Cache invalidation**: When Trie is rebuilt (hourly), purge CDN cache for affected prefixes.

---

### Deep Dive 2: Trie Distribution to Services

The in-memory trie needs to be available on all Autocomplete Service instances.

**Options:**

**A: Shared trie in Redis (serialized)**
- Trie rebuilt hourly as a serialized blob → stored in Redis
- Services load it into memory on startup or when notified of update
- Problem: 7.5 GB serialized trie → slow to distribute

**B: Trie precomputed per prefix range + sharded**
- Shard trie by first character: 'a-f' on Node 1, 'g-m' on Node 2, 'n-z' on Node 3
- Much smaller per-shard trie
- Load balancer routes request to correct shard based on `q[0]`
- Rebuild 1 shard at a time (no full-fleet reload)

**C: Rebuild in place (blue-green deployment)** ✅
- New trie built in background
- Atomic pointer swap to new trie (old trie GC'd)
- Zero downtime, no distribution complexity

---

### Deep Dive 3: Real-Time Updates vs Batch Updates

**Batch (offline) pipeline (simpler, recommended):**
```
Kafka search events → Flink aggregates last 7 days → hourly batch rebuild of trie
Pros: Simple, stable. No partial updates mid-query.
Cons: Up to 1 hour staleness (new trending topic takes ~1hr to appear)
```

**Real-time pipeline (low latency, complex):**
```
Kafka search events → Flink stream → update individual Redis scores in real-time
                                   → propagate score update up trie nodes (hard!)
Pros: Trending topics appear in minutes
Cons: Concurrent trie updates require locking or eventual consistency handling
```

**Recommendation for interview**: Start with batch (simpler, 1-hour freshness acceptable). Mention real-time as an enhancement if trending is critical.

---

### Deep Dive 4: Multi-Language Support

- Maintain separate tries and Redis namespaces per locale:
  - `trie:en`, `trie:hi`, `trie:de`, `autocomplete:en:*`, `autocomplete:hi:*`
- Detect locale from HTTP `Accept-Language` header or user profile
- For CJK languages (Chinese, Japanese, Korean): no prefix concept (characters are meaningful individually) → use different algorithm (n-gram index)
- For right-to-left languages (Arabic, Hebrew): same prefix approach works, just RTL rendering in UI

---

### Deep Dive 5: Personalization (Future Enhancement)

For personalized suggestions:
```
Final score = global_popularity × 0.7 + user_personal_score × 0.3

user_personal_score:
  - Queries user has searched before → boost
  - Queries similar to user's history → boost
  - User's location, language → filter

Implementation:
  - After step 3 (Redis score lookup), merge with user's personal Redis sorted set
  - user_personal:{user_id} → sorted set of their frequent queries
  - Weighted blend → rerank top candidates
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
**AP (Availability + Partition Tolerance)**
- Autocomplete is non-critical: returning slightly stale suggestions is fine
- Better to return old suggestions than to fail entirely
- Redis is AP; trie is purely in-memory (no distributed consensus needed)

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Data structure | Hybrid Trie + Redis | Pure Redis sorted sets | Trie gives O(L) prefix lookup; Redis gives fresh scores — hybrid wins |
| Update strategy | Hourly batch rebuild | Real-time streaming updates | Batch is simpler and sufficient; real-time adds significant complexity |
| CDN caching | Yes, for popular prefixes | No CDN (serve all from backend) | 80% cache hit rate reduces backend load by 5×; critical at 145K rps |
| Prefix key storage | Per-prefix Redis key | Single sorted set + range scan | Per-prefix key = O(1) lookup; range scan = O(log N) — per-prefix wins |
| Distribution | Blue-green trie swap | Real-time trie sync | Blue-green is atomic, safe, zero-downtime |

**What Would You Do Differently at Larger Scale?**
- Add **spell correction**: if prefix has no good matches, try Levenshtein distance 1 variants
- **Query understanding**: expand acronyms ("ml" → "machine learning")
- **Clickthrough feedback**: if users never click suggestion A but always click B, demote A
- **Abuse prevention**: filter out queries being artificially inflated (search farm attacks)

---

### Summary Talk Track

1. "Autocomplete is a **read-heavy, latency-sensitive** system — the key is caching at every layer."
2. "Core entities: **Trie** (in-memory prefix tree), **Redis Sorted Sets** (live scores), **SearchEvent** (Kafka pipeline)."
3. "Core data structure: a **Trie** where each node caches its top-K suggestions to avoid traversal."
4. "For distribution at scale: precompute per-prefix **Redis Sorted Sets** for sub-millisecond lookup."
5. "Scores update via **Kafka → Flink → Redis** pipeline, trie rebuilt **hourly from batch**."
6. "**CDN caches** popular prefixes — short prefixes ('a', 'ap', 'app') hit CDN 80% of the time."
7. "Freshness: 1-hour lag acceptable for MVP. For trending topics, can add real-time score updates."
8. "CAP choice: **AP** — old suggestions are fine; empty suggestions are not."

---

> **Previous**: [07 — Design Notification System](./07-notification-system.md)
> **Next**: [09 — Design Distributed Cache (Redis)](./09-distributed-cache.md)
