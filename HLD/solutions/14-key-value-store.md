# 14. Design Key-Value Store (like DynamoDB / Cassandra)

> **Difficulty**: Hard | **Asked At**: Amazon, Google, Facebook, Uber, Netflix
> **Time to Answer in Interview**: 40–45 minutes
> **Note**: This is an infrastructure design question. You're designing the DB itself, not using it.

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- What operations? GET, PUT, DELETE only? Or also range scans?
- Do we need TTL on keys?
- Consistency model: strong or eventual?
- Do we need ACID transactions?
- Replication across data centers?
- What size values? (small < 64KB? or large blobs?)

**Scale:**
- Total data size?
- QPS for reads and writes?
- Latency target?
- Replication factor?

**Typical Interviewer Answer:**
- GET, PUT, DELETE (no range scans for MVP)
- TTL on keys: yes
- Eventual consistency by default; configurable consistency levels
- No transactions for MVP
- 3 replicas per key
- Values up to 64 KB
- 10 PB total data, 1 million reads/sec, 100K writes/sec

### 1.2 Functional Requirements (FR)
1. `GET key → value` (with optional version)
2. `PUT key value [ttl]`
3. `DELETE key`
4. Configurable consistency: ONE (fastest), QUORUM, ALL (strongest)
5. Automatic key expiry (TTL)
6. Horizontal scalability (add nodes seamlessly)
7. Automatic replication to 3 nodes per key

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Read Latency** | < 10ms at p99 for quorum reads |
| **Write Latency** | < 20ms at p99 for quorum writes |
| **Availability** | 99.999% (5 nines) |
| **Durability** | No data loss — writes persisted to disk before ack |
| **Scalability** | Add nodes to cluster without downtime |
| **Data Size** | 10 PB across the cluster |

### 1.4 Out of Scope
- Range scans / secondary indexes
- ACID transactions
- SQL query layer

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  CacheEntry      │      │  StorageNode     │      │  ConsistentHash  │
│  (per key)       │      │  (server)        │      │  Ring            │
│                  │      │                  │      │                  │
│  key             │      │  node_id         │      │  node → vnodes   │
│  value           │      │  WAL             │      │  key → 3 replicas│
│  version         │      │  MemTable        │      │  ring positions  │
│  ttl / expiry    │      │  SSTables[]      │      │                  │
│  vector_clock    │      │  BloomFilter     │      │                  │
└──────────────────┘      └──────────────────┘      └──────────────────┘
```

**Primary entities**: `Entry` (key-value data unit), `StorageNode` (server with LSM Tree storage engine), `ConsistentHashRing` (maps keys to 3 replica nodes).

### 2.2 Core Data Structures

**LSM Tree (Log-Structured Merge Tree) — Storage Engine:**
```
Write path:
  1. Write to WAL (Write-Ahead Log) on disk → durability
  2. Write to MemTable (in-memory Red-Black Tree)
  3. When MemTable full (~256 MB): flush to SSTable on disk
  4. Return ack to client

SSTable (Sorted String Table):
  - Immutable, sorted by key
  - Bloom Filter for fast existence checks
  - Sparse index: every 1000th key indexed

Read path:
  1. Check MemTable (most recent)
  2. Check SSTables (newest first) using Bloom filter
  3. Return first found value

Compaction (background):
  Merge multiple SSTables → remove deleted/overwritten keys → reclaim space
```

> 🎯 **NFR addressed**: **Write Latency < 20ms** — WAL + MemTable is sequential IO, very fast. **Read Latency < 10ms** — Bloom filters skip 99% of SSTables. **Durability** — WAL persists before ack.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Client API
```python
# GET
result = client.get(key="user:12345", consistency=QUORUM)
# returns: { "value": bytes, "version": 3 }

# PUT
client.put(key="user:12345", value=json_bytes, ttl_seconds=3600, consistency=QUORUM)
# returns: { "version": 4 }

# DELETE
client.delete(key="user:12345", consistency=QUORUM)

# CHECK AND SET (optimistic concurrency)
client.cas(key="user:12345", expected_version=4, new_value=bytes)
```

### 3.2 Consistency Levels
```
ONE:    Confirmed by 1 replica  → fastest, may be stale
QUORUM: Confirmed by (N/2)+1   → balanced (N=3: 2 replicas)
ALL:    Confirmed by all N      → slowest, always consistent

Guarantee: R + W > N → reads always see latest write
```

> 🎯 **NFR addressed**: **Availability 99.999%** — ONE consistency survives N-1 node failures. **Read/Write Latency** — tunable tradeoff via consistency levels.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Storage:** 10 PB × 3 replicas = **30 PB** → 4 TB SSDs → **~7,500 nodes**

**QPS:** 1M reads + 100K writes = 1.1M ops/sec → each node handles ~100K → data sizing dominates

**Network:** 1M × 1 KB = **1 GB/s** outbound

### 4.2 Data Flow Through System

**Write Flow (W=QUORUM, N=3):**
```
Client → Coordinator Node
  → Consistent hash → finds 3 nodes (A, B, C) for this key
  → Sends write to all 3 in parallel
  → Each node: WAL → MemTable → ack
  → Waits for 2 acks (QUORUM) → returns success
  → Node C may still be processing (hinted handoff if down)
```

**Read Flow (R=QUORUM, N=3):**
```
Client → Coordinator
  → Sends read to all 3 nodes
  → Waits for 2 responses (QUORUM)
  → Compares versions → return highest version
  → If mismatch → async READ REPAIR to stale node
```

> 🎯 **NFR addressed**: **Durability** — WAL persisted before ack on each node. **Availability** — quorum succeeds even with 1 node down. **Scalability** — add nodes, consistent hash remaps only ~1/N keys.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
              CLIENT APPLICATION
                     │
                     │ SDK / Coordinator Node
                     │
              ┌──────▼────────────────────────────────┐
              │        Consistent Hashing Ring         │
              │   Determines which 3 nodes own a key  │
              └──────┬────────────────────────────────┘
                     │
     ┌───────────────┼───────────────────────────────┐
     │               │                               │
┌────▼──────┐  ┌──────▼──────┐                ┌──────▼──────┐
│  Node A   │  │   Node B    │    ...         │  Node Z     │
│ WAL       │  │ WAL         │                │ WAL         │
│ MemTable  │  │ MemTable    │                │ MemTable    │
│ SSTables  │  │ SSTables    │                │ SSTables    │
│ BloomFltr │  │ BloomFltr   │                │ BloomFltr   │
└───────────┘  └─────────────┘                └─────────────┘

GOSSIP PROTOCOL (node membership):
  Each node periodically pings 3 random peers
  Cluster state propagates in O(log N) rounds
  Failure detected within ~10 seconds
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Consistent Hash Ring** | Maps keys to 3 replica nodes; vnodes for even distribution | Adding/removing nodes remaps only ~1/N keys |
| **WAL (Write-Ahead Log)** | Durability guarantee — persists write before ack | Sequential disk IO; survives node crash |
| **MemTable** | In-memory sorted buffer for recent writes | O(log N) insert; flushes to SSTable when full |
| **SSTables** | Immutable sorted files on disk | Sequential reads; efficient compaction |
| **Bloom Filters** | Probabilistic "does key exist?" check per SSTable | Skips 99% of unnecessary disk reads |
| **Gossip Protocol** | Decentralized node membership and failure detection | No central coordinator; scales to thousands of nodes |

> 🎯 **NFR addressed**: **Availability 99.999%** — leaderless replication, no SPOF, gossip-based failure detection. **Scalability** — add nodes seamlessly. **Durability** — WAL + SSTable persistence. **Read Latency** — Bloom filters + MemTable first.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Vector Clocks (Handling Write Conflicts)

**Problem**: Two clients write to same key simultaneously on different replicas.

**Approach 1: Last Write Wins (LWW)** — simple but risks data loss (clock drift)

**Approach 2: Vector Clocks ✅**
```
Each write includes a vector clock: { NodeA: 1, NodeB: 0, NodeC: 0 }
Conflict detection:
  V1 = { NodeA: 1, NodeB: 2 }
  V2 = { NodeA: 2, NodeB: 1 }
  Neither dominates → CONFLICT → store both as siblings
  Client resolves on next read
```

**Approach 3: CRDTs** — auto-merging data structures (G-Counter, OR-Set)

---

### Deep Dive 2: Handling Node Failures

**Hinted Handoff:**
```
Node C is down → Coordinator writes "hinted" copy to Node D
When C recovers → D forwards data to C → deletes hinted copy
Result: no data loss even during temporary failure
```

**Anti-Entropy (Merkle Trees):**
```
Background process between two nodes:
  Build Merkle tree of key hashes → compare trees
  Only sync subtrees that differ → efficient repair
  If roots match: nodes are in sync (no scanning needed)
```

---

### Deep Dive 3: Bloom Filters

```
For each SSTable, maintain Bloom Filter:
  On write: hash key K times → set K bits
  On read: check K bits → if any 0: key DEFINITELY NOT here (skip!)
  False positive rate: ~1% | False negative rate: 0%
  Size: ~10 bits per key → tiny
Result: 99% of SSTable reads skipped
```

---

### Deep Dive 4: Compaction Strategies

```
Size-Tiered Compaction: merge SSTables of similar size
  → Good for write-heavy workloads
  → Temporarily uses 2× disk during merge

Leveled Compaction: organize SSTables into levels (L0, L1, L2...)
  → Better read performance (fewer SSTables to check)
  → More frequent compactions (higher write amplification)
```

---

### Trade-offs & Alternatives

**CAP Theorem:** **Tunable** — QUORUM = CP; ONE = AP. Customer chooses.

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Storage engine | LSM Tree | B-Tree | LSM is write-optimized (sequential writes); B-tree better for read-heavy |
| Replication | Leaderless (Dynamo) | Single-leader | No SPOF; better write throughput |
| Consistency | Tunable (ONE/QUORUM/ALL) | Fixed strong | Different apps need different tradeoffs |
| Conflict resolution | Vector clocks | LWW | Vector clocks preserve info; LWW can lose data |
| Failure detection | Gossip/SWIM | ZooKeeper | Gossip scales without central coordinator |

---

### Summary Talk Track

1. "Designing a KV store requires **storage, distribution, and replication**."
2. "Core entities: **Entry** (key+value), **StorageNode** (LSM Tree engine), **ConsistentHashRing** (key→node mapping)."
3. "Distribution: **Consistent Hashing** — adding nodes remaps minimal keys."
4. "Storage: **LSM Tree** — WAL → MemTable → SSTable pipeline, write-optimized."
5. "Reads accelerated by **Bloom Filters** — skip 99% of SSTables."
6. "Replication: **Leaderless** — any node accepts writes, quorum for consistency."
7. "Consistency is **tunable**: R+W > N guarantees reading your own writes."
8. "Failures: **Hinted Handoff** + **Merkle Tree anti-entropy** for repair."

---

> **Previous**: [13 — Design Instagram](./13-instagram.md)
> **Next**: [15 — Design Food Delivery App](./15-food-delivery.md)
