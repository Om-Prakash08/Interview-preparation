# 24. Design Distributed Job Scheduler

> **Difficulty**: Hard | **Asked At**: Google, Amazon, Meta, Uber, Airbnb
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Job types: One-time delayed jobs, recurring cron jobs, or DAG workflows?
- Target execution accuracy / acceptable delay tolerance? (e.g., within $\pm 1$ second of scheduled time)
- Execution guarantees: At-least-once or At-most-once?
- Job execution duration: Short-lived (seconds) or long-running (hours)?
- Scale: Total scheduled jobs and peak triggering throughput?

**Typical Interviewer Answer:** Support both one-time delayed jobs and recurring cron jobs, plus DAG dependencies (like Apache Airflow). 10 Million total scheduled jobs, 10,000 jobs triggering per second. Execution timing accuracy within $\pm 1$ second. At-least-once execution guarantee.

### 1.2 Functional Requirements (FR)
1. **Submit Jobs**: Users/Services can submit one-time scheduled jobs or recurring cron expressions.
2. **DAG Dependencies**: Jobs can specify prerequisite parent job dependencies (Job B runs after Job A succeeds).
3. **Execution Tracking**: Track status of job runs (`SCHEDULED`, `DISPATCHED`, `RUNNING`, `SUCCESS`, `FAILED`, `RETRYING`).
4. **Retries & Backoff**: Automatic retry with exponential backoff configuration on failure.
5. **Priority Queuing**: Higher priority jobs executed first under system resource constraints.

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Trigger Accuracy**| Within $\pm 1$ second of scheduled target timestamp |
| **Throughput** | 10,000 jobs triggered & executed per second |
| **Reliability** | At-least-once job execution guarantee (no lost jobs) |
| **Scalability** | Horizontally scalable worker pool & delay queues |
| **Fault Tolerance** | Master node or worker crashes must not drop or duplicate active jobs |

### 1.4 Out of Scope
- Code compilation / Docker image building runtime environment
- User-facing real-time terminal output streaming

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────────────┐       ┌──────────────────────────┐
│   Job Definition         │       │   Job Instance (Run)     │
│                          │       │                          │
│  job_id                  │──────►│  instance_id             │
│  type (CRON / ONESHOT)   │       │  job_id                  │
│  cron_expression         │       │  scheduled_time          │
│  payload (JSON)          │       │  status (RUNNING/SUCCESS)│
│  priority                │       │  attempt_count           │
└────────────┬─────────────┘       └────────────┬─────────────┘
             │                                  │
             ▼                                  ▼
┌──────────────────────────┐       ┌──────────────────────────┐
│   Job Dependency (DAG)   │       │   Execution Log          │
│  parent_job_id           │       │  instance_id, worker_id  │
│  child_job_id            │       │  stdout, stderr, exit_code│
└──────────────────────────┘       └──────────────────────────┘
```

### 2.2 Data Model / Schema

**1. `job_definitions` (PostgreSQL)**
```sql
CREATE TABLE job_definitions (
  job_id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(255),
  type VARCHAR(20), -- 'ONESHOT', 'CRON'
  cron_expression VARCHAR(50),
  payload JSONB,
  priority INT DEFAULT 0,
  max_retries INT DEFAULT 3,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**2. `job_instances` (Cassandra / PostgreSQL Partitioned by Date)**
```sql
CREATE TABLE job_instances (
  instance_id VARCHAR(64) PRIMARY KEY,
  job_id VARCHAR(64) REFERENCES job_definitions,
  scheduled_time TIMESTAMP NOT NULL,
  status VARCHAR(20), -- 'SCHEDULED', 'DISPATCHED', 'RUNNING', 'SUCCESS', 'FAILED'
  assigned_worker VARCHAR(100),
  attempt_count INT DEFAULT 0,
  updated_at TIMESTAMP
);
```

> 🎯 **NFR addressed**: **Reliability** — Persistent storage of `job_instances` before dispatching ensures jobs survive scheduler node crashes.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Submit Job Request
```
POST /api/v1/jobs
{
  "name": "nightly_analytics",
  "type": "CRON",
  "cron_expression": "0 2 * * *",
  "payload": { "db": "analytics_prod" },
  "priority": 10,
  "dependencies": ["job_data_sync_01"],
  "retry_config": { "max_retries": 3, "backoff_sec": 60 }
}
Response 201 Created:
{ "job_id": "job_9918", "next_run_at": "2026-09-07T02:00:00Z", "status": "SCHEDULED" }
```

### 3.2 Get Job Instance Status
```
GET /api/v1/jobs/instances/{instance_id}
Response 200 OK:
{ "instance_id": "inst_4412", "status": "RUNNING", "attempt": 1, "worker": "worker-node-88" }
```

> 🎯 **NFR addressed**: **Accuracy** — `next_run_at` is calculated immediately upon job submission and pushed to the high-precision delay queue.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Trigger Volume**: 10,000 jobs / second.
- **DB Writes**: 10,000 `SCHEDULED` $\rightarrow$ 10,000 `DISPATCHED` $\rightarrow$ 10,000 `SUCCESS` = **30,000 state writes/sec** $\rightarrow$ Cassandra write scale required.
- **Payload Storage**: 10,000 jobs/sec × 2 KB = **20 MB/s data log write** = ~1.7 TB / day.

### 4.2 Data Flow Through System

```
JOB SUBMISSION & SCHEDULING PIPELINE
  User Client ──POST /jobs──► Scheduler API
    ├─ 1. Write job definition to PostgreSQL DB
    ├─ 2. Calculate next execution timestamp ($T$)
    └─ 3. Push job instance to High-Precision Delay Queue (Hierarchical Timing Wheel)

TIMING WHEEL & DISPATCHER PIPELINE (< 1s Accuracy)
  Timing Wheel Engine (1-Second Ticks)
    ├─ 1. Tick advances to current second ($T = \text{now()}$)
    ├─ 2. Pop all due job instances from Timing Wheel bucket
    ├─ 3. Update DB status to `DISPATCHED`
    └─ 4. Push job payloads into Kafka Topic (`jobs-high-priority`, `jobs-default`)

WORKER EXECUTION PIPELINE
  Distributed Worker Fleet (Kubernetes Pods)
    ├─ 1. Poll Kafka topic & acquire Distributed Redis Lock (`lock:instance_id`)
    ├─ 2. Execute job payload (invoke webhook or run task script)
    ├─ 3. Emit `JobCompletedEvent` to Kafka
    │
  DAG Manager Service
    └─ Listens to `JobCompletedEvent` -> Triggers child downstream jobs in DAG graph
```

> 🎯 **NFR addressed**: **Accuracy $\pm 1$s** — Hierarchical Timing Wheel in memory executes bucket callbacks at 1-second interval ticks.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                                  ┌───────────────────────────┐
                                  │      Client API Call      │
                                  └─────────────┬─────────────┘
                                                │ POST /jobs
                                                ▼
                                  ┌───────────────────────────┐
                                  │       Scheduler API       │
                                  └──────┬─────────────┬──────┘
                                         │ Save        │ Push timestamp
                                         ▼             ▼
                          ┌────────────────────┐ ┌───────────────────────────┐
                          │   PostgreSQL DB    │ │ High-Precision Delay Q    │
                          │ (Job Definitions)  │ │ (Hierarchical Timing Wheel│
                          └────────────────────┘ └─────────────┬─────────────┘
                                                               │ Due Jobs (T <= now)
                                                               ▼
                                                 ┌───────────────────────────┐
                                                 │ Kafka Priority Topics     │
                                                 │ (high, medium, low)       │
                                                 └─────────────┬─────────────┘
                                                               │ Poll Jobs
                                                               ▼
                                                 ┌───────────────────────────┐
                                                 │  Distributed Worker Pool  │
                                                 │  (Executes Task Payload)  │
                                                 └─────────────┬─────────────┘
                                                               │ Emit Completion
                                                               ▼
                                                 ┌───────────────────────────┐
                                                 │   DAG Manager Service     │
                                                 │   (Evaluates Child Jobs)  │
                                                 └───────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Timing Wheel** | High-precision delay queue | $O(1)$ tick execution handles 10,000 due jobs per second efficiently |
| **Kafka Priority Queues**| Buffer & queue dispatching | Decouples job triggering from execution worker capacity |
| **Distributed Workers**| Runs task logic | Horizontally scalable Kubernetes worker pods |
| **Redis Lock Manager** | Deduplication lock per job | Ensures at-most-one worker processes a specific job instance |
| **DAG Manager** | Manages workflow dependencies| Evaluates parent completion events to unlock dependent downstream jobs |

> 🎯 **NFR addressed**: **Fault Tolerance** — If worker dies mid-execution, Redis lock expires and a secondary worker re-enqueues the instance.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: High-Precision Delay Queue Architecture (Timing Wheel vs Redis ZSET)

```
Option A: Redis Sorted Set (`ZADD`)
  - Score = Scheduled Timestamp, Value = `instance_id`.
  - Poller runs `ZRANGEBYSCORE delay_queue 0 <now>` every 100ms.
  - Bottleneck: Redis single-thread CPU spikes at 10,000 pops/sec.

Option B: Hierarchical Timing Wheel (In-Memory Engine) ✅
  - Wheel 1: Seconds (0-59) -> Ticks every 1s.
  - Wheel 2: Minutes (0-59) -> Ticks every 1m.
  - Wheel 3: Hours (0-23)   -> Ticks every 1h.

  - Pushing a job to Timing Wheel takes O(1) time.
  - When second tick occurs, all jobs in the second bucket are popped in O(1) time and dispatched to Kafka.
  - State persisted via Write-Ahead Logging (WAL) on disk for crash recovery.
```

---

### Deep Dive 2: DAG Workflow Dependency Evaluation (Apache Airflow Style)

```
DAG Example: Job A -> Job B and Job C -> Job D

Evaluation Protocol:
  1. Job A finishes -> Worker emits `JobCompletedEvent(job_id='Job_A', status='SUCCESS')`.
  2. DAG Manager receives event.
  3. Query DAG Graph:
     SELECT child_id FROM job_dag WHERE parent_id = 'Job_A'; -- Returns Job B, Job C
  4. For each child job:
     Check if ALL parent dependencies are met:
     SELECT parent_id FROM job_dag WHERE child_id = 'Job_B';
     If all parents == SUCCESS -> Trigger Job B immediately by pushing to Kafka!
```

---

### Deep Dive 3: At-Least-Once Execution & Worker Fault Handling

```
Worker Pickup Lock Protocol:
  1. Worker polls instance `inst_100` from Kafka.
  2. Worker attempts atomic lock: `SET lock:inst_100 worker_88 NX EX 30` (30s TTL).
  3. If Lock Acquired -> Set DB status = 'RUNNING', process job.
  4. Heartbeat Thread extends lock TTL every 10s while job is active.
  5. Upon finish -> Set DB status = 'SUCCESS', release lock.

Failure Recovery:
  - If Worker 88 crashes -> Heartbeat stops -> Lock TTL expires in 30s.
  - Background Recovery Worker queries: `SELECT * FROM job_instances WHERE status = 'DISPATCHED' AND updated_at < NOW() - 60s`.
  - Re-enqueues orphan job instance into Kafka for another worker to execute.
```

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **Delay Queue** | Hierarchical Timing Wheel | Redis Sorted Set | Timing Wheel gives $O(1)$ pops vs $O(\log N)$ Redis ZSET sorting |
| **Worker Queue** | Kafka | RabbitMQ | Kafka provides durable log retention and partitioned scale for 10,000 TPS |
| **Execution** | At-Least-Once | Exactly-Once | Pure exactly-once across distributed networks is impossible without idempotent tasks |

---

### Summary Talk Track

1. "We design a Distributed Job Scheduler capable of triggering **10,000 jobs/sec** within a **$\pm 1$ second window**."
2. "Scheduling relies on an in-memory **Hierarchical Timing Wheel** for $O(1)$ dispatching to **Kafka Priority Queues**."
3. "Workers acquire **Redis Distributed Locks** before execution, while an async **DAG Manager** coordinates parent-child workflow dependencies."
4. "Reliability is maintained via persistent state logging in **Cassandra** and automatic crash recovery for orphan tasks."

---

> **Previous**: [23 — Design Live Streaming](./23-live-streaming.md)
> **Next**: [25 — Design Fraud Detection](./25-fraud-detection.md)
