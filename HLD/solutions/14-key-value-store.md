# 14. Design Key-Value Store (like DynamoDB / Cassandra)

> **Difficulty**: Hard | **Asked At**: Amazon, Google, Facebook, Uber, Netflix
> **Time to Answer in Interview**: 40–45 minutes
> **Note**: This is an infrastructure design question. You're designing the DB itself, not using it.

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

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
- Eventual consistency by default; configurable consistency levels (like Cassandra)
- No transactions for MVP
- 3 replicas per key
- Values up to 64 KB
- 10 PB total data, 1 million reads/sec, 100K writes/sec

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. `GET key → value` (with optional version)
2. `PUT key value [ttl]`
3. `DELETE key`
4. Configurable consistency: ONE (fastest), QUORUM, ALL (strongest)
5. Automatic key expiry (TTL)
6. Horizontal scalability (add nodes seamlessly)
7. Automatic replication to 3 nodes per key

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Read Latency** | < 10ms at p99 for quorum reads |
| **Write Latency** | < 20ms at p99 for quorum writes |
| **Availability** | 99.999% (5 nines) |
| **Durability** | No data loss — writes persisted to disk before ack |
| **Scalability** | Add nodes to cluster without downtime |
| **Data Size** | 10 PB across the cluster |

---

## SECTION 3 — Capacity Estimation

### Storage
- 10 PB total data
- Replication factor 3 → actual disk needed: **30 PB**
- Node size: 4 TB SSDs
- Nodes needed: 30 PB / 4 TB = **7,500 storage nodes**

### QPS
- 1M reads/sec, 100K writes/sec
- Single node handles: ~100K reads/sec, ~10K writes/sec
- Read nodes: 1M / 100K = **10 nodes** (data-bound: 7,500 nodes >> this)
- Data capacity dominates; 7,500 nodes handle QPS easily

### Network
- Average value: 1 KB
- Read bandwidth: 1M × 1 KB = **1 GB/s** outbound
- Write bandwidth: 100K × 1 KB = **100 MB/s** inbound

---

## SECTION 4 — API Design

### Client API (like DynamoDB SDK / Cassandra driver)

```python
# GET
result = client.get(key="user:12345", consistency=QUORUM)
# returns: { "value": bytes, "version": 3, "expires_at": None }

# PUT
client.put(key="user:12345", value=json_bytes, ttl_seconds=3600, consistency=QUORUM)
# returns: { "version": 4, "written_at": "2025-07-26T10:00:00Z" }

# DELETE
client.delete(key="user:12345", consistency=QUORUM)
# returns: { "deleted": True }

# CHECK AND SET (optimistic concurrency)
client.cas(key="user:12345", expected_version=4, new_value=bytes, consistency=QUORUM)
# returns: { "success": True } or { "success": False, "current_version": 5 }
```

### Consistency Levels (like Cassandra)
```
ONE:    Write/Read confirmed by 1 replica  → fastest, may be stale
QUORUM: Confirmed by (N/2)+1 replicas     → balanced (with N=3: 2 replicas)
ALL:    Confirmed by all N replicas        → slowest, always consistent
```

---

## SECTION 5 — Core Architecture Components

### Component 1: Consistent Hashing Ring

```
The ring maps keys to nodes using a hash function.

Nodes placed on ring:
  Node A → position 10
  Node B → position 40
  Node C → position 70

Virtual nodes (vnodes): each physical node has 150 positions on the ring
  → Prevents hotspots when nodes have different data sizes

Key placement:
  hash("user:12345") = 55 → clockwise → Node C owns this key

Replication:
  Primary replica: Node C (position 70, just after hash(key)=55)
  Replica 2:       Next node clockwise = Node A (position 10, wraps around)
  Replica 3:       Next = Node B (position 40)

When a node is added/removed: only ~1/N keys need to move
```

### Component 2: Data Storage Engine (LSM Tree)

**Why not B-Tree?**
- B-Trees require random writes (updating pages in place)
- SSDs handle sequential writes 10× faster than random writes
- LSM Tree is write-optimized: always writes sequentially

**LSM Tree (Log-Structured Merge Tree):**
```
Write path:
  1. Write to WAL (Write-Ahead Log) on disk → guarantees durability
  2. Write to MemTable (in-memory sorted structure, usually Red-Black Tree)
  3. When MemTable is full (~256 MB): flush to SSTable (Sorted String Table) on disk
  4. Return ack to client (after step 1: durable; after step 2: fast)

SSTable:
  - Immutable, sorted by key
  - Contains Bloom filter (fast "does key exist?" check without reading file)
  - Has sparse index: for every 1000 keys, one index entry (saves memory)

Read path:
  1. Check MemTable first (most recent writes)
  2. Check each SSTable (newest first) using Bloom filter
  3. Return first found value

Compaction (background):
  Periodically merge multiple SSTables into one:
  - Remove deleted/overwritten keys (tombstones)
  - Reduce number of SSTables to check on read
  - Reclaim disk space
```

### Component 3: Replication (Leaderless)

```
Unlike Primary-Replica (which has a single leader):

Leaderless Replication (like Cassandra, DynamoDB):
  - Any node can accept writes (no single leader)
  - Client or coordinator node writes to N replicas directly
  - Consistency controlled by W (write quorum) and R (read quorum)

Write: send to all 3 replicas (or coordinator does it)
  - W=2 (QUORUM): wait for 2 acks → return success
  - Even if replica 3 is slow/down: still succeeds with 2 acks

Read: read from multiple replicas
  - R=2 (QUORUM): read from 2 replicas, return latest version
  - Compare versions: if different → use higher version, repair stale replica (read repair)

Guarantee: as long as R + W > N (e.g., 2+2 > 3): reads always see latest write
```

---

## SECTION 6 — High-Level Architecture

```
              CLIENT APPLICATION
                     │
                     │ SDK (routes request to correct node)
                     │ OR
                     │ Coordinator Node (any node can be coordinator)
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
│           │  │             │                │             │
│ WAL       │  │ WAL         │                │ WAL         │
│ MemTable  │  │ MemTable    │                │ MemTable    │
│ SSTables  │  │ SSTables    │                │ SSTables    │
│ BloomFltr │  │ BloomFltr   │                │ BloomFltr   │
└────────────┘  └─────────────┘                └─────────────┘

WRITE FLOW (W=QUORUM, N=3):
Client → Coordinator
  → Consistent hash → finds 3 nodes (A, B, C) for this key
  → Sends write to all 3 in parallel
  → Waits for 2 acks (QUORUM = ceil(3/2)+1 = 2)
  → Returns success to client
  → Node C may still be processing (hinted handoff if down)

READ FLOW (R=QUORUM, N=3):
Client → Coordinator
  → Finds 3 nodes (A, B, C) for this key
  → Sends read to all 3 in parallel
  → Waits for 2 responses (QUORUM)
  → Compares versions: return highest version value
  → If version mismatch: sends READ REPAIR to stale node asynchronously

GOSSIP PROTOCOL (node membership):
Each node periodically sends heartbeat to 3 random peers:
  "I am Node A, alive, my neighbors are B, C, D, E"
  "Node F has been unreachable for 3 rounds"
→ Cluster state propagates without central coordinator
→ Failure detected within ~3 heartbeat cycles (~10 seconds)
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Vector Clocks (Handling Write Conflicts)

**Problem**: Two clients write to the same key simultaneously on different replicas.
```
Client 1: writes "foo" → bar (to Node A, timestamp 10:00:01)
Client 2: writes "foo" → baz (to Node B, timestamp 10:00:01)
Now Node A has "bar", Node B has "baz" — which is correct?
```

**Approach 1: Last Write Wins (LWW)**
```
Use wall clock timestamp. Higher timestamp wins.
Problem: Clocks on different machines are NOT perfectly synchronized (NTP drift = 100ms)
→ Concurrent writes on the same millisecond → arbitrary winner
→ Risk: data loss
```

**Approach 2: Vector Clocks** ✅
```
Each write includes a vector clock: { NodeA: 1, NodeB: 0, NodeC: 0 }
Next write from same client: { NodeA: 2, NodeB: 0, NodeC: 0 }

Conflict detection:
  V1 = { NodeA: 1, NodeB: 2 }
  V2 = { NodeA: 2, NodeB: 1 }
  V1 and V2 are concurrent (neither dominates) → CONFLICT

Resolution: 
  - Store both versions (siblings)
  - Return both to client on read
  - Client or application resolves: keep both, take latest, or custom merge logic
  - Amazon Dynamo uses this approach
```

**Approach 3: CRDT (Conflict-free Replicated Data Types)**
```
Design data structures that automatically merge without conflicts:
  - G-Counter: increment-only counter (never conflict)
  - LWW-Register: last-write-wins with per-element timestamps
  - OR-Set: set with add/remove without conflicts
Redis CRDT, Riak use this approach
```

---

### Deep Dive 2: Handling Node Failures

**Hinted Handoff:**
```
Node C (one of 3 replicas) is temporarily down.
Coordinator still writes to A and B (QUORUM success).
Coordinator also writes "hinted" copy to Node D with metadata:
  "This data belongs to C, forward when C comes back"

When C recovers:
  → Node D detects C is alive (via gossip)
  → Forwards the hinted data
  → C acknowledges
  → D deletes its hinted copy

Result: No data loss even when replica is temporarily down
```

**Anti-Entropy (full repair):**
```
Background process between two nodes:
  1. Build Merkle tree of all key hashes
  2. Compare Merkle trees between nodes
  3. Only sync the subtrees that differ (not all data!)
  4. Efficiently detect and repair inconsistencies

Merkle tree: binary hash tree where each parent = hash(left_child + right_child)
  If root hashes match: nodes are in sync (no scanning needed)
  If root hashes differ: recurse into subtrees to find specific divergence
```

---

### Deep Dive 3: Bloom Filters (Speeding Up Reads)

**Problem:** In LSM tree, a GET must check MemTable + potentially many SSTables.
Worst case: key doesn't exist → must read ALL SSTables = slow.

**Solution: Bloom Filter** (probabilistic data structure)
```
For each SSTable, maintain a Bloom Filter:
  - Compact bit array (a few MB for millions of keys)
  - On write: hash key K times → set K bits in array
  - On read: hash key K times → check if ALL K bits are set
    - If any bit is 0: key is DEFINITELY NOT in this SSTable (skip!)
    - If all bits are 1: key MAY be in SSTable (go read it)

Properties:
  - False positive rate: ~1% (sometimes says "may be there" when it's not)
  - False negative rate: 0% (never says "not there" when it is)
  - Size: ~10 bits per key (tiny!)

Result: 99% of SSTable reads skipped with Bloom filters
Read performance improves 10-100× for non-existent keys
```

---

### Deep Dive 4: TTL (Time-to-Live) Implementation

```
On write: store (key, value, expiry_timestamp)
  MEMTABLE: entries include expiry_timestamp
  SSTable: expiry_timestamp stored alongside value

Read path:
  On GET: after finding value, check if now() > expiry_timestamp
  If expired: return NOT_FOUND (lazy deletion)

Compaction:
  During SSTable merge: skip keys with expiry_timestamp < now()
  → Expired keys removed from disk during compaction
  → No separate cleanup process needed

Background scanning:
  Periodically scan MemTable for expired keys, evict them
  → Frees memory before compaction picks them up
```

---

### Deep Dive 5: Membership & Failure Detection (SWIM/Gossip Protocol)

```
SWIM Protocol (Scalable Weakly-consistent Infection-style Membership):

Every T seconds, each node:
  1. Selects one random node M to ping
  2. If M responds: M is alive, reset failure count
  3. If M doesn't respond in T/2 seconds:
     → Ask K other random nodes to ping M (indirect ping)
     → If any of them gets a response: M is alive (false alarm)
     → If none get a response after T seconds: mark M as SUSPECT
  4. If M suspected for two cycles: declare M FAILED
     → Remove from ring
     → Trigger rebalancing (keys that M owned redistribute)

Gossip-based dissemination:
  Every node periodically sends its membership list to 3 random nodes
  New information (node joined, node failed) propagates to entire cluster
  in O(log N) rounds → scales to thousands of nodes
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem
This system is **tunable** between CP and AP based on consistency level chosen:
- QUORUM reads + writes: **CP** (consistent but may reject during partition)
- ONE read + ONE write: **AP** (always available, eventually consistent)
- Customer chooses based on their use case

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Storage engine | LSM Tree | B-Tree | LSM is write-optimized (sequential writes, SSD-friendly); B-tree has better read performance for low-write workloads |
| Replication | Leaderless (Dynamo-style) | Single-leader | Leaderless: no single point of failure, better write throughput; Leader: simpler consistency |
| Consistency | Tunable (ONE/QUORUM/ALL) | Fixed strong consistency | Different applications have different needs; tunable lets users choose their tradeoff |
| Conflict resolution | Vector clocks + siblings | LWW | Vector clocks preserve more information; LWW can lose data |
| Failure detection | SWIM/Gossip | ZooKeeper-based | Gossip scales to thousands of nodes without a central coordinator |

---

## Interview Flow Summary (Talk Track)

1. "Designing a KV store requires understanding **storage, distribution, and replication**"
2. "Distribution: **Consistent Hashing** ring — adding/removing nodes remaps minimal keys"
3. "Storage engine: **LSM Tree** — MemTable → SSTable pipeline, write-optimized, SSD-friendly"
4. "Reads accelerated by **Bloom Filters** — skip 99% of SSTables for missing keys"
5. "Replication: **Leaderless** (Dynamo/Cassandra style) — any node accepts writes, quorum for consistency"
6. "Consistency is **tunable**: W+R > N guarantees reading your own writes"
7. "Conflict resolution: **Vector Clocks** detect concurrent writes; application resolves or auto-merge with CRDTs"
8. "Failures: **Hinted Handoff** for temporary failures, **Anti-Entropy with Merkle Trees** for full repair"

---

> **Previous**: [13 — Design Instagram](./13-instagram.md)
> **Next**: [15 — Design Food Delivery App](./15-food-delivery.md)
