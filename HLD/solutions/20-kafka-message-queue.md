# 20. Design Kafka / Distributed Message Queue

> **Difficulty**: Very Hard | **Asked At**: LinkedIn (Kafka's birthplace), Uber, Netflix, Confluent
> **Time to Answer in Interview**: 40–45 minutes
> **Note**: You're designing the message queue itself, not using it.

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Point-to-point (queue) or publish-subscribe (topic)?
- What delivery guarantee: at-most-once, at-least-once, or exactly-once?
- Do consumers need to read messages in order?
- Message replay — can consumers re-read old messages?
- Push to consumers or pull by consumers?
- How long are messages retained?

**Scale:**
- How many messages per second?
- Average message size?
- Number of producers and consumers?

**Typical Interviewer Answer:**
- Publish-subscribe (topics with partitions)
- At-least-once delivery by default (producers can opt for exactly-once)
- Ordering within a partition (not globally)
- Message replay: yes (retained for 7 days)
- Pull-based consumers
- 1 million messages/sec, average 1 KB message
- Support for 1000+ topics, millions of consumer groups

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Producers publish messages to named topics
2. Topics divided into partitions for parallelism
3. Messages within a partition are ordered
4. Consumer groups pull messages from partitions
5. Each consumer group maintains its own offset (position)
6. Messages retained for configurable period (default 7 days)
7. Message replay: consumers can seek to any offset

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Throughput** | 1 million messages/sec write, 2 million/sec read |
| **Latency** | Message available to consumers within 10ms of publish |
| **Durability** | No message loss after acknowledgement |
| **Availability** | 99.99% |
| **Scalability** | Horizontal scaling by adding brokers/partitions |
| **Ordering** | Strict ordering within a partition |

### Out of Scope
- Kafka Streams (stream processing)
- Kafka Connect (source/sink connectors)
- Schema Registry

---

## SECTION 3 — Capacity Estimation

### Messages
- 1M messages/sec × 1 KB = **1 GB/s write throughput**
- With replication factor 3: **3 GB/s total write I/O**

### Retention Storage
- 1 GB/s × 86,400 sec × 7 days retention = **~605 TB per week**
- With replication × 3: **~1.8 PB** total storage

### Brokers
- Single broker: ~500 MB/s write throughput (SSD-bound)
- Brokers needed for write: 3 GB/s / 500 MB/s = **~6 broker nodes minimum**
- With replication: data distributed → 6 brokers with 300 TB SSD each

### Topics & Partitions
- 1000 topics × avg 20 partitions = **20,000 partitions total**
- Each partition = 1 append-only log on 1 broker (primary)
- Partitions distributed across 6 brokers: ~3,333 partitions per broker

---

## SECTION 4 — API Design

### Producer API
```python
# Create producer
producer = KafkaProducer(
    bootstrap_servers=["broker1:9092", "broker2:9092"],
    acks="all",               # wait for all in-sync replicas to ack
    enable_idempotence=True,  # exactly-once at producer level
    compression_type="snappy",
    batch_size=16384,         # batch messages up to 16KB
    linger_ms=5               # wait up to 5ms for more messages to batch
)

# Send message
future = producer.send(
    topic="user-events",
    key="user_12345".encode(),    # determines partition
    value=json.dumps(event).encode(),
    headers=[("event-type", b"purchase")]
)

# Block for ack (or use callback for async)
record_metadata = future.get(timeout=10)
# record_metadata.topic, record_metadata.partition, record_metadata.offset
```

### Consumer API
```python
consumer = KafkaConsumer(
    "user-events",                 # topic name
    bootstrap_servers=["broker1:9092"],
    group_id="analytics-service", # consumer group
    auto_offset_reset="earliest",  # start from beginning if no committed offset
    enable_auto_commit=False       # manual offset management for at-least-once
)

for message in consumer:
    # message.topic, message.partition, message.offset, message.value
    process(message.value)
    consumer.commit()              # commit AFTER processing (at-least-once)
```

### Admin API
```
// Create topic
CreateTopicRequest { topic="orders", partitions=20, replication_factor=3 }

// Describe consumer group lag
DescribeGroupRequest { group_id="analytics-service" }
→ { topic="orders", partition=5, current_offset=1000000, latest_offset=1000100, lag=100 }
```

---

## SECTION 5 — Core Data Structures

### The Log (Heart of Kafka)

Each partition is an **append-only log** stored on disk:

```
Partition 0 of topic "orders":
  
  Segment 1 (offsets 0 to 99,999):
    orders-0-00000000000000000000.log  ← binary file, sequential writes
    orders-0-00000000000000000000.index ← offset → file position mapping
    orders-0-00000000000000000000.timeindex ← timestamp → offset mapping

  Segment 2 (offsets 100,000 to 199,999):
    orders-0-00000000000000100000.log
    orders-0-00000000000000100000.index

  Active Segment (newest, being written to):
    orders-0-00000000000001234000.log  ← all writes go here

Message format in .log file:
  [offset(8B)][timestamp(8B)][key_size(4B)][value_size(4B)][key][value]

Message retrieval at offset N:
  1. Binary search .index file → find file position of offset N (O(log M))
  2. Seek to file position in .log file → read message (O(1) disk seek)
```

**Why append-only log?**
- Sequential disk writes: 500 MB/s (HDD) or 3 GB/s (SSD)
- vs Random writes: 0.5–100 MB/s
- Append-only is 10–100× faster than random writes

---

## SECTION 6 — High-Level Architecture

```
PRODUCERS                          KAFKA CLUSTER                    CONSUMERS
─────────                          ─────────────                    ─────────
Order Service                       ┌──────────────────────────┐    Analytics
Payment Service  ──────── push ──► │    Broker 1 (Leader)     │    Service
Inventory Svc                       │  Partition 0: Leader     │
                                    │  Partition 3: Replica    │ ◄── pull ── Email
                                    │  Partition 6: Replica    │             Service
                                    └──────────────────────────┘
                                    ┌──────────────────────────┐
                                    │    Broker 2              │    ┌──────────────────┐
                                    │  Partition 0: Replica    │    │  Consumer Groups │
                                    │  Partition 1: Leader     │    │                  │
                                    │  Partition 4: Replica    │    │  analytics-svc   │
                                    └──────────────────────────┘    │  C1: partition 0 │
                                    ┌──────────────────────────┐    │  C2: partition 1 │
                                    │    Broker 3              │    │  C3: partition 2 │
                                    │  Partition 2: Leader     │    │                  │
                                    │  Partition 0: Replica    │    │  email-svc       │
                                    │  Partition 5: Leader     │    │  C1: partition 0 │
                                    └──────────────────────────┘    │      to 2 (all)  │
                                                                     └──────────────────┘
                                    ┌──────────────────────────┐
                                    │   ZooKeeper / KRaft      │
                                    │   (Cluster coordinator)  │
                                    │   - Broker membership    │
                                    │   - Leader election      │
                                    │   - Consumer group coord │
                                    └──────────────────────────┘

WRITE PATH (Producer → Kafka):
  1. Producer serializes message
  2. Producer computes partition: hash(key) % num_partitions
     (or round-robin if no key)
  3. Producer sends to correct broker (leader of that partition)
  4. Leader writes to local log file (sequential I/O)
  5. Leader replicates to in-sync replicas (ISR)
  6. After ISR acks: leader sends ack to producer
  7. Message now "committed" (safe to read by consumers)

READ PATH (Consumer ← Kafka):
  1. Consumer sends FETCH request to broker
     { topic, partition, offset, max_bytes }
  2. Broker seeks to offset in .index file
  3. Reads up to max_bytes from .log file
  4. Returns batch of messages
  5. Consumer processes messages, then commits offset
  6. Consumer loop repeats from step 1
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Partitioning Strategy

**Why partitions?**
- A single partition = single-threaded writes → bottleneck
- More partitions → more parallelism for both producers and consumers

**Partition assignment:**
```
Producer with key:
  partition = hash(key) % num_partitions
  → All messages with same key → same partition → ordered per key

Example: user_id as key
  All "user_12345" events → partition 7 (always)
  All "user_99999" events → partition 3 (always)
  Consumers on partition 7 see all events for user_12345 in order

Producer without key:
  Round-robin across partitions (or sticky partitioner for batching)
  → Even distribution → higher throughput
  → No ordering guarantee across messages
```

**How many partitions?**
```
Rule of thumb: max(producer throughput, consumer throughput) / single-partition throughput

Example:
  Target: 1 GB/s write, single partition handles 50 MB/s
  Partitions: 1000/50 = 20 partitions minimum

More partitions = more parallelism BUT:
  - More files open (OS limits)
  - More replication overhead
  - Leader election during broker failure takes longer
  - Sweet spot: 20–100 partitions per topic for most use cases
```

---

### Deep Dive 2: Replication (In-Sync Replicas)

```
For each partition: 1 leader + N-1 followers (replicas)
  leader_epoch tracks which broker is current leader

Leader:
  - Handles all reads and writes (followers don't serve reads in Kafka)
  - Maintains ISR list (In-Sync Replicas) — followers that are caught up

Follower:
  - Continuously fetches messages from leader (same FETCH API as consumers)
  - If follower falls behind by >10 seconds: removed from ISR

Commit protocol:
  1. Leader writes message to local log
  2. Followers fetch and acknowledge
  3. When ALL ISR acknowledge: message is "committed" (safe to read)
  4. Leader updates high watermark (HW = offset of last committed message)
  5. Consumer can only read up to HW (committed messages only)

acks settings:
  acks=0: producer doesn't wait → fastest, possible data loss
  acks=1: leader acks immediately after local write → ISR lag = data loss risk
  acks=all: wait for all ISR → slowest, no data loss
```

---

### Deep Dive 3: Consumer Groups & Partition Assignment

```
Consumer Group: logical grouping of consumers that collectively read a topic

Rule: each partition assigned to exactly 1 consumer in a group
  
Example: Topic "orders" has 6 partitions
  Consumer Group "analytics" has 3 consumers:
    C1: handles partitions 0, 1
    C2: handles partitions 2, 3
    C3: handles partitions 4, 5

If C2 dies (consumer crash):
  → Group Coordinator (one broker acts as coordinator) detects missing heartbeat
  → Triggers REBALANCE
  → C1 now handles 0, 1, 2, 3 (or C1 and C3 split C2's partitions)

If C4 joins:
  → Triggers REBALANCE
  → Partitions redistributed: each consumer gets ~1.5 partitions
  → C1: 0,1; C2: 2,3; C3: 4; C4: 5

Offset management:
  Consumer commits offset to __consumer_offsets topic (internal Kafka topic)
  On restart: consumer reads its committed offset, resumes from there
  Consumer lag = latest_offset - committed_offset
```

---

### Deep Dive 4: Exactly-Once Semantics (EOS)

**The hardest problem in distributed messaging:**

```
Challenge: Network drops between produce and ack → producer retries → duplicate!

Solution: Idempotent Producer + Transactional API

Idempotent Producer (exactly-once per session):
  Producer ID (PID) assigned by broker on connect
  Each message tagged with: { PID, sequence_number }
  Broker deduplicates: if it sees same PID + sequence_number → discard duplicate
  Sequence numbers are per-partition, monotonically increasing

Transactional Producer (atomic across multiple partitions/topics):
  1. producer.begin_transaction()
  2. producer.send("orders", order_msg)
  3. producer.send("inventory", inventory_msg)
  4. producer.send_offsets_to_transaction(consumer_offsets)
  5. producer.commit_transaction()

  → Either ALL messages committed, or NONE
  → Enables "read-process-write" cycles to be atomic
  → Consumer with isolation_level=read_committed won't see messages from aborted transactions
```

---

### Deep Dive 5: Log Retention & Cleanup

**Time-based retention:**
```
log.retention.hours = 168  (7 days default)
When a log segment's newest message is older than 7 days:
  → Delete the entire segment file
  → Cannot delete individual messages (log is immutable)
```

**Size-based retention:**
```
log.retention.bytes = 1099511627776  (1 TB per partition)
When partition exceeds 1 TB:
  → Delete oldest segment until under limit
```

**Compaction (alternative to deletion):**
```
For topics where only the LATEST value per key matters (like a database changelog):

Original log:
  [user:1, "Alice"] [user:2, "Bob"] [user:1, "Alice Smith"] [user:3, "Charlie"]

After compaction:
  [user:1, "Alice Smith"] [user:2, "Bob"] [user:3, "Charlie"]
  
→ Only latest value per key retained
→ Used for: event sourcing, CDC (Change Data Capture), configuration topics
→ Compaction runs in background, doesn't block reads/writes
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**CP (Consistency + Partition Tolerance)** by default:
- With `acks=all`: consistent — no message loss even during network partition
- During partition: leader may pause and wait for ISR quorum → availability reduced
- **AP option**: with `acks=1` or `acks=0` → higher availability, risk of data loss

### Comparison with Alternatives

| Feature | Kafka | RabbitMQ | Amazon SQS | Pulsar |
|---|---|---|---|---|
| Model | Pub-Sub (log) | Queue + Pub-Sub | Queue | Pub-Sub (log) |
| Message Retention | 7 days (configurable) | Until consumed | 14 days | Tiered (unlimited) |
| Replay | Yes (seek to any offset) | No | No | Yes |
| Throughput | 1M+ msg/sec | 100K msg/sec | 100K msg/sec | 1M+ msg/sec |
| Ordering | Per-partition | Per-queue | Not guaranteed | Per-partition |
| Consumer Model | Pull | Push + Pull | Pull | Pull |
| Use When | High throughput, replay needed | Simple work queues | AWS-native | Kafka alternative with tiers |

### Key Trade-offs

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Storage | Append-only log | Random-access DB | Sequential writes 100× faster; enables replay |
| Consumer model | Pull | Push | Pull lets consumer control pace; push can overwhelm slow consumers |
| Replication | ISR quorum | All replicas | ISR avoids waiting for slow replicas while maintaining safety |
| Partition assignment | Static (hash of key) | Dynamic | Static is predictable, enables key-based ordering; dynamic is flexible but loses ordering |
| Leader reads | Leader only | Any ISR can serve reads | Leader-only simplifies consistency; follower reads possible in KIP-392 for geo-locality |

---

## Interview Flow Summary (Talk Track)

1. "Kafka's core abstraction is the **append-only commit log** per partition — sequential writes give 3 GB/s throughput"
2. "Topics → Partitions → Segments (files). Messages addressed by {topic, partition, offset}."
3. "**Producers** compute partition by `hash(key) % N` — ensures ordering per key"
4. "**Replication**: leader + ISR followers. `acks=all` → no data loss. Consumer reads only committed (HW) messages."
5. "**Consumer Groups**: each partition assigned to exactly 1 consumer. Rebalance on member join/leave."
6. "**Offset** is the consumer's bookmark — pulled, committed manually → at-least-once"
7. "**Exactly-once**: Idempotent Producer (PID + seq dedup) + Transactions (atomic cross-partition writes)"
8. "Retention: time-based (7 days), size-based (1 TB/partition), or compaction (keep latest per key)"

---

> **Previous**: [19 — Design Web Crawler](./19-web-crawler.md)
> **Next**: [21 — Design Recommendation System](./21-recommendation-system.md)
