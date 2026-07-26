# 9. Design Distributed Cache (Redis)

> **Difficulty**: Hard | **Asked At**: Google, Amazon, Meta, Microsoft, Uber
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Is this a general-purpose key-value cache, or specialized (session store, rate limiter, leaderboard)?
- Should we support different data structures (strings, hashes, lists, sorted sets) like Redis, or just strings?
- Do we need persistence (survive restarts) or purely in-memory (data loss OK on restart)?
- Eviction policy — what happens when cache is full? LRU? LFU? No eviction?
- Should we support TTL (time-to-live) on keys?
- Replication — single master or replicated?
- Consistency model: should reads from replicas be allowed (potential staleness)?

**Scale:**
- How large is the total dataset?
- What QPS for reads and writes?
- What's the expected latency target?

**Typical Interviewer Answer:**
- General-purpose key-value cache (strings for now)
- TTL support: yes
- Eviction: LRU when full
- Distributed across multiple nodes (horizontal scaling)
- High availability: replication
- Sub-millisecond reads (<1ms)
- Read-heavy: 10:1 read-to-write ratio

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. `GET key` — retrieve value by key (returns null if not found or expired)
2. `PUT key value [ttl]` — store key-value pair with optional TTL
3. `DELETE key` — remove a key
4. When cache is full: automatically evict least-recently-used keys
5. Automatic expiry of keys past their TTL

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Read Latency** | < 1ms (in-memory access) |
| **Write Latency** | < 5ms |
| **Availability** | 99.99% |
| **Scalability** | Horizontal scaling to handle petabytes of data across nodes |
| **Consistency** | Single-node writes consistent; replicas eventually consistent |

### Out of Scope
- Persistence (AOF/RDB snapshots) — mention as extension
- Pub/Sub messaging
- Lua scripting
- Cluster auto-resharding (mention but don't deep dive)

---

## SECTION 3 — Capacity Estimation

### Scale
- 100 billion items cached across the fleet
- Average key-value pair: 100 bytes (key: 20 bytes, value: 80 bytes)
- Total data: 100B × 100 bytes = **10 TB of data**
- 10 TB / 128 GB RAM per server = **~80 cache nodes**

### QPS
- 500,000 reads/sec + 50,000 writes/sec = **550,000 ops/sec**
- Single Redis node handles ~100,000 ops/sec
- Minimum nodes (write capacity): 550K / 100K = **~6 nodes**
- But we need 80 nodes for data, so data sizing dominates

### Latency
- In-memory access: ~0.1ms
- Network round trip: ~0.5ms
- Total: **< 1ms** ✅

---

## SECTION 4 — API Design

### Client-Facing API

```
// GET: retrieve a value
GET(key: string) → value: bytes | null

// PUT: store a value (with optional TTL in seconds)
PUT(key: string, value: bytes, ttl: int?) → OK

// DELETE: remove a key
DELETE(key: string) → OK | NOT_FOUND

// EXISTS: check if key exists
EXISTS(key: string) → bool
```

### Internal Node Communication (for replication)
```
// Primary → Replica replication commands (internal, binary protocol)
REPLICATE(key, value, ttl, timestamp)  // sent after every write
REPLICATE_DELETE(key, timestamp)

// Heartbeat between nodes
PING → PONG
```

---

## SECTION 5 — Core Components & Data Structures

### Internal Data Structure per Node

**Hash Table + Doubly Linked List (LRU Cache)**

```
Hash Table: key → (value, doubly_linked_list_node, expiry_timestamp)
Doubly Linked List: MRU head ←→ [node] ←→ ... ←→ LRU tail

On GET(key):
  - O(1) lookup in hash table
  - Move accessed node to HEAD of doubly linked list (most recently used)
  - Check expiry: if now() > expiry → delete, return null
  - Return value

On PUT(key, value, ttl):
  - If key exists: update value, move to HEAD
  - If key is new:
    - If at capacity: evict TAIL node (least recently used), delete from hash table
    - Insert new node at HEAD
    - Add to hash table

On DELETE(key):
  - Remove from hash table
  - Remove from doubly linked list
  - O(1) deletion because we have direct pointer to node

Expiry Check (lazy deletion):
  - On every GET: check if key is expired → delete if so
  - Background thread: periodically scans a sample of keys, deletes expired ones
    (Redis does this every 100ms, sampling 20 random keys)
```

**Visual:**
```
Hash Table              Doubly Linked List (MRU → LRU)
──────────────          ─────────────────────────────────────
"user:1" → ptr ──────► [user:1] ⟷ [order:5] ⟷ [prod:2] ⟷ [cart:9]
"order:5" → ptr ─────────────────────────────────────▲
"prod:2" → ptr    (HEAD = most recently used)    (TAIL = evict this next)
"cart:9" → ptr
```

---

## SECTION 6 — High-Level Architecture

```
         CLIENT APPLICATIONS
         ─────────────────────
         [Web Server 1]  [Web Server 2]  [Web Server N]
               │                │               │
               └────────────────┼───────────────┘
                                │
                                │ Cache requests
                                │
                    ┌───────────▼────────────┐
                    │    Cache Client Library │
                    │    (Consistent Hashing) │
                    │    Determines which     │
                    │    node holds the key   │
                    └───────────┬────────────┘
                                │
              ┌─────────────────┼─────────────────┐
              │                 │                 │
     ┌────────▼──────┐ ┌────────▼──────┐ ┌────────▼──────┐
     │  Cache Node 1 │ │  Cache Node 2 │ │  Cache Node 3 │
     │  (Primary)    │ │  (Primary)    │ │  (Primary)    │
     │  Shard A      │ │  Shard B      │ │  Shard C      │
     └────────┬──────┘ └────────┬──────┘ └────────┬──────┘
              │                 │                 │
     ┌────────▼──────┐ ┌────────▼──────┐ ┌────────▼──────┐
     │  Replica 1a   │ │  Replica 2a   │ │  Replica 3a   │
     │  (standby)    │ │  (standby)    │ │  (standby)    │
     └───────────────┘ └───────────────┘ └───────────────┘

     ┌──────────────────────────────────────────────────────┐
     │          Cluster Coordinator (ZooKeeper)             │
     │  - Node membership (which nodes are alive?)          │
     │  - Node → shard mapping                              │
     │  - Leader election for each shard                    │
     │  - Client configuration updates                      │
     └──────────────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Consistent Hashing (How to distribute keys across nodes)

**Naive approach: hash(key) % N**
```
N = 3 nodes: "user:1" → hash % 3 = 1 → Node 1
If Node 4 added: N = 4 → hash % 4 = different node for almost every key!
→ Cache misses for ~75% of all keys on expansion/shrinkage
```

**Consistent Hashing:**
```
Concept: Map both keys AND nodes onto a circular ring (0 to 2^32-1)

Node placement:
  Node1 → hash("node1") → position 100
  Node2 → hash("node2") → position 200
  Node3 → hash("node3") → position 300

Key placement:
  "user:1" → hash("user:1") = 150 → go clockwise → hit Node2 at 200

Adding Node4 at position 250:
  Keys that were between 200 and 250 → now go to Node4
  Everything else: UNCHANGED
  Only ~25% of keys need remapping (vs 75% with modulo)
```

**Virtual Nodes (Vnodes):**
- Assign each physical node to 150 positions on the ring
- Avoids hotspots when node capacities differ
- More even key distribution

---

### Deep Dive 2: Replication (High Availability)

**Primary-Replica Replication:**
```
Write → Primary Node only
Primary → asynchronously replicates to Replica(s)
Read → can go to Primary (consistent) or Replica (slightly stale)

Failover:
  - Cluster Coordinator (ZooKeeper) monitors primaries via heartbeats
  - Primary missed 3 heartbeats → ZooKeeper triggers leader election
  - Replica with most up-to-date data becomes new primary
  - Clients notified of new primary address
  - Old primary (if it recovers) becomes replica of new primary
```

**Problem: Split Brain**
- Network partition → two nodes both think they're primary
- Solution: **Quorum-based writes**
  - Write confirmed only after acknowledged by majority (N/2+1) of replicas
  - Prevents data divergence during partition

---

### Deep Dive 3: LRU Eviction — Exact vs Approximate

**Exact LRU**: the doubly linked list approach (O(1) per operation)
- Perfect eviction order
- Memory overhead: each key needs 3 pointers (prev, next, hash table pointer)
- For 100B keys × 24 bytes = **2.4 TB just for pointers** — too much overhead

**Approximate LRU (Redis approach):**
- Every key has a `last_accessed_timestamp` (4 bytes, not 24)
- On eviction: sample 5 random keys, evict the oldest one among them
- 99.9% as accurate as exact LRU with a tiny fraction of the memory overhead

**Alternative eviction policies:**
```
LFU (Least Frequently Used): evict least-often-accessed key
  - Better for some access patterns (avoids evicting popular keys just accessed once long ago)
  - Redis 4.0+ supports LFU

TTL-based: only evict expired keys (no size limit eviction)
  - Risk: cache fills up if keys don't have TTLs set

No eviction: return error when full (useful for session store — don't lose session data)
```

---

### Deep Dive 4: Cache Stampede / Thundering Herd

**Problem:** Popular key expires. Simultaneously, thousands of requests try to regenerate it.
```
key "trending_products" expires at t=0
t=0: 10,000 concurrent requests all get cache MISS
t=0: All 10,000 hit the database simultaneously
Database collapses.
```

**Solutions:**

**Option A: Mutex / Lock**
```
First request: acquires lock, regenerates value, stores in cache, releases lock
Other requests: wait, then read from cache
Problem: lock contention under high load
```

**Option B: Probabilistic Early Expiration** (recommended)
```
Before key actually expires, slightly early, start regenerating it:
  effective_ttl = actual_ttl - random(0, delta)
  One request regenerates proactively while old value still serves traffic
  New value replaces old before expiry
```

**Option C: Stale-While-Revalidate**
```
Return the stale (old) value immediately to all requests
Asynchronously trigger exactly ONE background revalidation
New value stored in cache
Zero latency spike, no stampede
```

**Option D: Key-level locking in Redis (SETNX)**
```
SETNX "lock:trending_products" "1" EX 5
→ If successful: this request owns regeneration
→ If not: return stale value while waiting
```

---

### Deep Dive 5: Write Strategies (Cache + DB Coordination)

| Strategy | How It Works | Pros | Cons |
|---|---|---|---|
| **Cache-Aside (Lazy Loading)** | App reads cache. On miss: read DB, populate cache, return. | Simple, cache only holds requested data | Cache miss = 2 reads (cache + DB). Stale data possible. |
| **Write-Through** | Every write: update cache AND DB synchronously. | Cache always fresh. | Slow writes (two writes per operation). Cache may fill with infrequently read data. |
| **Write-Behind (Write-Back)** | Write to cache only. Async flush to DB later. | Very fast writes. | Risk of data loss if cache node dies before flush. |
| **Refresh-Ahead** | Proactively refresh cache entries before expiry (if accessed recently). | No latency spike on refresh. | May cache data that won't be needed again. |

**Recommended for most use cases**: **Cache-Aside** (simple, widely understood) with **Write-Through** for critical data.

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP (Availability + Partition Tolerance)**
- Cache exists to improve performance, not to be the source of truth
- Stale data from a replica is far better than a failed cache read
- During partition: nodes serve possibly stale data (acceptable for a cache)

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Key distribution | Consistent hashing | Modulo hashing | Consistent hashing limits remapping on node changes to ~1/N keys |
| Replication | Async primary-replica | Synchronous (all replicas confirm) | Sync replication adds latency to every write; async is faster at cost of brief staleness |
| Eviction | Approximate LRU | Exact LRU | Approximate saves ~80% of the memory overhead of exact LRU |
| Expiry | Lazy deletion + background sampling | Eager scanning | Lazy is O(1); eager scanning (TTL sorted set) adds overhead but is more precise |
| Stampede | Stale-While-Revalidate | Mutex | Mutex adds latency under high load; stale-while-revalidate is zero-latency |

### What Would You Do Differently at Larger Scale?
- **Multi-tier caching**: L1 local in-process cache (< 1μs) → L2 distributed Redis cache (< 1ms) → L3 DB (tens of ms)
- **Geo-distributed caches**: separate Redis clusters per region (US, EU, Asia) to avoid cross-regional latency
- **Cache warming**: on deployment or cold start, preload popular keys from DB before serving traffic

---

## Interview Flow Summary (Talk Track)

1. "A distributed cache has three core problems: **how to store**, **how to distribute**, and **how to replicate**"
2. "Each node uses a **Hash Table + Doubly Linked List** for O(1) LRU get/put/evict"
3. "Distribution via **Consistent Hashing** — adding/removing nodes only remaps ~1/N keys"
4. "Replication: **Primary-Replica** with ZooKeeper for leader election and failover"
5. "Eviction: **Approximate LRU** (sample 5 random keys, evict oldest) — Redis's actual approach"
6. "Cache stampede: **Stale-While-Revalidate** — serve stale, async regenerate, no spike"
7. "CAP: AP — cache staleness is acceptable; cache unavailability is not"

---

> **Previous**: [08 — Design Search Autocomplete](./08-search-autocomplete.md)
> **Next**: [10 — Design an API Gateway](./10-api-gateway.md)
