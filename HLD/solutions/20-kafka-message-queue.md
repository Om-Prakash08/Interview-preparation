# 20. Design Kafka / Distributed Message Queue

> **Difficulty**: Very Hard | **Asked At**: LinkedIn, Uber, Netflix, Amazon, Confluent
> **Time to Answer in Interview**: 40–45 minutes
> **Note**: You are designing the message queue platform itself, not using one.

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Point-to-point (Queue) or Publish-Subscribe (Topic with Partitioning)?
- What delivery guarantees: At-most-once, At-least-once, or Exactly-once?
- Ordering requirements? (Per partition vs Global)
- Message replay ability? (Retain messages on disk)
- Push-based or Pull-based consumer model?
- Target scale (Throughput, message size)?

**Typical Interviewer Answer:** Publish-Subscribe model with topic partitioning. At-least-once delivery by default (support producer idempotency). Ordering guaranteed per partition. Pull-based consumers. Messages retained for 7 days. Target: 1 Million messages/sec, 1 KB avg message size.

### 1.2 Functional Requirements (FR)
1. **Publish**: Producers publish messages to named topics with optional partitioning keys.
2. **Partitioned Storage**: Topics divided into partitions for parallel throughput; messages ordered sequentially per partition.
3. **Consume**: Consumer groups pull messages from assigned partitions and commit offsets.
4. **Message Replay**: Consumers can seek to any valid offset within the 7-day retention period.
5. **Replication**: High availability & fault tolerance via Primary-Replica partition replication.

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Write Throughput** | 1 Million msgs/sec write ($1\text{ GB/s}$ payload, $3\text{ GB/s}$ disk write I/O with $3\times$ replication) |
| **Read Throughput** | 2 Million msgs/sec read |
| **End-to-End Latency** | $< 10\text{ms}$ write ack and consumer availability |
| **Durability** | Zero message loss when producer `acks=all` |
| **Scalability** | Horizontal scaling by adding brokers or partitions |

### 1.4 Out of Scope
- Stream processing engine (Kafka Streams / Flink)
- Schema Registry / Protobuf compiler integrations

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────────────┐
│   Broker                 │
│  broker_id, host, port   │
└────────────┬─────────────┘
             │ Hosts 1 or more
             ▼
┌──────────────────────────┐       ┌──────────────────────────┐
│   Partition Leader       │──────►│   Partition Replica      │
│  topic_name, part_id     │       │  follower_broker_id      │
│  high_watermark (HW)     │       │  log_end_offset (LEO)    │
└────────────┬─────────────┘       └──────────────────────────┘
             │ Manages
             ▼
┌──────────────────────────┐       ┌──────────────────────────┐
│   Log Segment (.log)     │──────►│   Index File (.index)    │
│  base_offset             │       │  relative_offset         │
│  binary_records_seq      │       │  physical_file_position  │
└──────────────────────────┘       └──────────────────────────┘
```

### 2.2 Data Model / Schema

**Physical Disk Log Structure (Per Partition Directory on Broker)**
```
/var/lib/kafka/data/orders-partition-0/
  ├── 00000000000000000000.log        <-- Append-only binary log segment file
  ├── 00000000000000000000.index      <-- Sparse index (offset -> position)
  ├── 00000000000000000000.timeindex  <-- Sparse index (timestamp -> offset)
  ├── leader-epoch-checkpoint         <-- Leader election fencing
```

**Binary Record Header Format in `.log` File**
```
[Offset: 8B] [Timestamp: 8B] [KeySize: 4B] [ValueSize: 4B] [CRC: 4B] [KeyBytes] [ValueBytes]
```

**Consumer Offset Tracking Internal Topic (`__consumer_offsets`)**
```
Key:   { "group_id": "analytics-svc", "topic": "orders", "partition": 0 }
Value: { "offset": 1004523, "timestamp": 1722000000 }
```

> 🎯 **NFR addressed**: **Durability & Throughput** — Sequential binary disk logging enables $3\text{ GB/s}$ I/O; Zero-Copy `sendfile()` serves consumers without kernel memory copies.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Producer API (`ProduceRequest`)
```
POST /broker/produce
Headers: { Acks: "all" | "1" | "0", TimeoutMs: 5000 }
Payload:
{
  "topic": "orders",
  "partition": 2, // or computed via hash(key) % total_partitions
  "messages": [
    { "key": "user_101", "value": "{\"order_id\": 555}", "timestamp": 1722000000 }
  ]
}
Response: { "partition": 2, "base_offset": 1004523, "error_code": 0 }
```

### 3.2 Consumer API (`FetchRequest`)
```
POST /broker/fetch
Payload:
{
  "group_id": "analytics-svc",
  "topic": "orders",
  "partition": 2,
  "fetch_offset": 1004500,
  "max_bytes": 1048576 // 1MB batch
}
Response: { "high_watermark": 1004523, "messages": [ ... ] }
```

### 3.3 Offset Commit API
```
POST /broker/commit_offset
Payload: { "group_id": "analytics-svc", "topic": "orders", "partition": 2, "offset": 1004523 }
```

> 🎯 **NFR addressed**: **Latency < 10ms** — Batching multiple records inside `ProduceRequest` & `FetchRequest` drastically cuts TCP network overhead.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Write Rate**: 1M msgs/sec × 1 KB payload = **1 GB/s ingress payload**.
- **Replication**: Factor of 3 ($1\text{ GB/s} \times 3 = 3\text{ GB/s}$ disk write load across brokers).
- **Storage**: 1 GB/s × 86,400s × 7 days = **~605 TB raw logs**, with $3\times$ replication = **~1.8 PB total cluster storage**.
- **Broker Count**: A high-end server SSD writes ~500 MB/s sequential. 3 GB/s / 500 MB/s = **Minimum 6 Broker Nodes**.

### 4.2 Data Flow Through System

```
PRODUCER                                BROKER LEADER                              CONSUMER
   │                                         │                                         │
   ├─── 1. Send ProduceBatch(Partition 0)───►│                                         │
   │                                         ├── 2. Append sequentially to             │
   │                                         │      active .log file                   │
   │                                         │                                         │
   │                                         ├── 3. Replicate to ISR Replicas          │
   │                                         │      (In-Sync Replicas)                 │
   │                                         │                                         │
   │◄── 4. Ack (after ISR quorum acked) ─────┤                                         │
   │    High Watermark (HW) advanced         │                                         │
   │                                         │◄── 5. FetchRequest(offset=100) ──────────┤
   │                                         │                                         │
   │                                         ├── 6. Zero-Copy `sendfile()`             │
   │                                         │      transfer disk -> NIC               │
   │                                         │                                         │
   │                                         ├──── 7. Return Batch Data ──────────────►│
   │                                         │                                         │
   │                                         │◄── 8. CommitOffset(offset=150) ─────────┤
```

> 🎯 **NFR addressed**: **Durability & Latency** — Zero-Copy bypassing OS user space makes network transfers bound only by NIC limits.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
PRODUCERS                            KAFKA BROKER CLUSTER                   CONSUMER GROUPS
 ┌─────────────────┐                 ┌───────────────────────────┐          ┌─────────────────┐
 │ Order Service   │                 │ Broker 1                  │          │ Analytics Group │
 │ (Producer App)  ├───── Produce───►│  - Topic A (Part 0 Leader)│◄──Fetch──┤  - Consumer 1   │
 └─────────────────┘                 │  - Topic A (Part 1 Follow)│          │  - Consumer 2   │
                                     └─────────────┬─────────────┘          └─────────────────┘
 ┌─────────────────┐                               │ Replication
 │ Payment Service │                 ┌─────────────▼─────────────┐          ┌─────────────────┐
 │ (Producer App)  ├───── Produce───►│ Broker 2                  │          │ Email Group     │
 └─────────────────┘                 │  - Topic A (Part 1 Leader)│◄──Fetch──┤  - Consumer 1   │
                                     │  - Topic A (Part 0 Follow)│          └─────────────────┘
                                     └───────────────────────────┘
                                                   │
                                     ┌─────────────▼─────────────┐
                                     │ Cluster Coordinator       │
                                     │ (KRaft / ZooKeeper)       │
                                     │ - Leader election         │
                                     │ - Broker heartbeat        │
                                     └───────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Broker** | Manages partition logs, handles IO | Sequential append-only disk storage yields massive I/O throughput |
| **Partition Leader** | Serves all writes and reads | Simplifies strict ordering guarantees per partition |
| **ISR (In-Sync Replicas)**| Active follower replicas | Ensures zero data loss on leader crash ($acks=all$) |
| **KRaft / ZooKeeper** | Metadata & Controller Election | Manages metadata state, partition re-assignment, and leader failover |
| **Consumer Group** | Scalable consumption team | Partitions are dynamically load-balanced among active consumers in a group |

> 🎯 **NFR addressed**: **Scalability & Availability 99.99%** — Horizontal partition re-assignment during broker failure via KRaft controller.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: High-Performance Disk I/O Architecture

**Why Kafka is fast on traditional disk storage:**

```
1. Sequential Disk Writes:
   - Random Disk IO: 100-200 IOPS (~1 MB/s).
   - Sequential Disk Writes: ~500 MB/s (HDD/SSD).
   - Append-only log architecture transforms all writes into fast sequential operations.

2. OS Page Cache & Zero-Copy:
   Standard Read Path (4 Context Switches + 2 Memory Copies):
   Disk -> OS Page Cache -> JVM User Space Buffer -> Socket Buffer -> NIC

   Kafka Zero-Copy sendfile() Path (0 CPU Memory Copies):
   Disk -> OS Page Cache ------------------------> NIC Transmit Buffer (via DMA Engine)
   Result: CPU utilization remains near zero even at 2 GB/s read rates!
```

---

### Deep Dive 2: Sparse Indexing & Offset Lookup Mechanics

```
Log File Segment (orders-00000.log):
Offset 0:     [Record 0, Payload]
Offset 100:   [Record 100, Payload]
Offset 200:   [Record 200, Payload]

Sparse Index File (orders-00000.index):
Relative Offset: 0   -> Physical File Position: 0 bytes
Relative Offset: 100 -> Physical File Position: 4096 bytes
Relative Offset: 200 -> Physical File Position: 8192 bytes

Lookup Algorithm for Offset N = 150:
1. Binary search index file -> Find index entry closest to 150 (Offset 100 -> Pos 4096).
2. Seek directly to position 4096 in .log file.
3. Scan sequentially from 4096 until Offset 150 is read.
```

---

### Deep Dive 3: Producer Idempotency & Exactly-Once Semantics (EOS)

```
Problem: Network timeout occurs after Leader appends message, but before Ack reaches Producer.
Producer retries -> Duplicate message appended!

Solution: Producer ID (PID) + Sequence Number Deduplication
  1. Each Producer is assigned a 64-bit PID on initialization.
  2. Every message sent to a Partition contains: { PID, Sequence_Number }.
  3. Broker tracks the highest sequence number per PID for each Partition.
  4. If Incoming Sequence_Number <= Broker Stored Sequence_Number -> Broker discards duplicate, sends Ack!
```

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **Consumer Model** | Pull-based | Push-based | Pull allows consumers to process at their own pace without buffer overflow |
| **Storage Engine** | Append-only Log | RocksDB / InnoDB B-Tree | B-Trees cause random disk IO and page splits; Append-only log is purely sequential |
| **Ordering** | Per-Partition | Global Topic Ordering | Global ordering forces a single thread/partition, destroying horizontal scaling |

---

### Summary Talk Track

1. "We design Kafka as an **append-only, partitioned commit log** optimized for sequential disk I/O."
2. "To achieve **1 Million msgs/sec write throughput**, we utilize sequential logging, OS Page Cache, and batch compression."
3. "To achieve massive read performance, we implement **Zero-Copy `sendfile()`**, bypassing JVM user space to stream disk cache directly to NIC buffers."
4. "Durability is guaranteed using **In-Sync Replicas (ISR)** with `acks=all`, while idempotency is enforced via **PID + Sequence Numbers**."

---

> **Previous**: [19 — Design Web Crawler](./19-web-crawler.md)
> **Next**: [21 — Design Recommendation System](./21-recommendation-system.md)
