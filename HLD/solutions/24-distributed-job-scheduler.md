# 24. Design Distributed Job Scheduler

> **Difficulty**: Hard | **Asked At**: Google, Amazon, Meta, Uber, Airbnb
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- One-time scheduled jobs (run at timestamp T) or recurring cron jobs (every 5 minutes)?
- Support for DAG (Directed Acyclic Graph) job dependencies (Job B runs after Job A completes)?
- Job execution duration: short-lived (seconds) or long-running (hours)?
- Priority queuing for jobs?
- Max delay / accuracy target for trigger time?

**Scale:**
- How many total scheduled jobs?
- How many jobs executed per second?

**Typical Interviewer Answer:**
- Support both one-off (delay queue) and recurring cron jobs
- Support job dependencies (DAG workflows like Airflow)
- 10 million total scheduled jobs; 10,000 jobs triggering per second
- Execution timing accuracy within ± 1 second
- At-least-once job execution guarantee

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Users/Services can submit jobs to run at a specific time or on a cron schedule
2. Jobs can define dependencies (DAG execution)
3. Support retries with exponential backoff on failure
4. Provide execution status tracking (Scheduled, Running, Succeeded, Failed, Retrying)
5. Support priority levels for critical jobs

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Accuracy** | Trigger jobs within ±1s of scheduled execution time |
| **Throughput** | Execute 10,000+ jobs/second |
| **Reliability** | At-least-once execution guarantee (no lost jobs) |
| **Fault Tolerance** | Master/worker crash should not drop running or scheduled jobs |
| **Scalability** | Horizontally scalable worker pool |

---

## SECTION 3 — Capacity Estimation

### Execution Volume
- 10,000 jobs/second
- Average job metadata payload: 2 KB
- Storage required per day: 10,000 × 86,400 × 2 KB ≈ **1.7 TB / day**

### Database IOPS
- Reads (polling scheduled jobs): ~10,000 reads/sec
- Writes (status updates): 20,000 writes/sec (Running + Succeeded/Failed state transitions)

---

## SECTION 4 — API Design

### 1. Submit Job
```
POST /api/v1/jobs
{
  "name": "nightly_report_generation",
  "type": "CRON",                           // ONESHOT | CRON
  "cron_expression": "0 2 * * *",           // Every night at 2:00 AM
  "payload": { "report_type": "sales" },
  "priority": "HIGH",
  "dependencies": ["job_id_100"],          // Parent DAG dependencies
  "retry_config": { "max_retries": 3, "backoff_sec": 60 }
}

Response 201:
{
  "job_id": "job_99812",
  "next_run_at": "2025-07-27T02:00:00Z",
  "status": "SCHEDULED"
}
```

### 2. Get Job Status
```
GET /api/v1/jobs/{job_id}
Response: { "job_id": "job_99812", "status": "RUNNING", "attempt": 1 }
```

---

## SECTION 5 — Data Model & Storage

### Table 1: `job_definitions` (PostgreSQL)
```
job_id           VARCHAR(64)  PRIMARY KEY
name             VARCHAR(200)
cron_expression  VARCHAR(50)  NULL
payload          JSONB
priority         INT          DEFAULT 0
retry_config     JSONB
created_at       TIMESTAMP
```

### Table 2: `job_instances` (Cassandra / PostgreSQL Partitioned)
```
instance_id      VARCHAR(64)  PRIMARY KEY
job_id           VARCHAR(64)  REFERENCES job_definitions
scheduled_time   TIMESTAMP    NOT NULL
status           ENUM('SCHEDULED', 'DISPATCHED', 'RUNNING', 'SUCCESS', 'FAILED')
assigned_worker  VARCHAR(100) NULL
attempt_count    INT          DEFAULT 0
execution_log    TEXT
updated_at       TIMESTAMP
```

### Data Structure for Delay Queue (Redis Sorted Set / Hierarchical Timing Wheel)
- **Key**: `scheduler:delay_queue`
- **Score**: Scheduled Unix Timestamp (`1722000000`)
- **Value**: `instance_id`

---

## SECTION 6 — High-Level Architecture

```
                  CLIENT / SERVICE
                         │
                         │ POST /api/v1/jobs
                         ▼
                ┌──────────────────┐
                │   API Gateway    │
                └────────┬─────────┘
                         │
                         ▼
                ┌──────────────────┐
                │ Scheduler API    │
                └────────┬─────────┘
                         │ 1. Persist Definition
                         ▼
                ┌──────────────────┐
                │   PostgreSQL     │
                └────────┬─────────┘
                         │ 2. Push next run timestamp
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  Delay Queue & Partitioned Scheduler (Hierarchical Wheel)    │
│  Pushes due jobs to Kafka where scheduled_time <= now()      │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  Kafka (Topics by Priority: jobs-high, jobs-med, jobs-low)   │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  Distributed Worker Pool (K8s pods / EC2 workers)            │
│  - Consumes jobs from Kafka                                  │
│  - Executes job logic / invokes target webhooks              │
│  - Emits execution status updates                            │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌──────────────────────────────────────────────────────────────┐
│  DAG & Dependency Manager Service                            │
│  Evaluates downstream jobs upon job completion events        │
└──────────────────────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: High-Precision Delay Queue (Timing Wheel vs Redis Sorted Set)

**Option A: Redis Sorted Set (`ZADD`)**
- `ZADD delay_queue <timestamp> <instance_id>`
- Poller thread runs `ZRANGEBYSCORE delay_queue 0 <now>` every 100ms.
- **Problem**: Hotspotting on a single Redis key when scaling beyond 50,000 jobs/sec.

**Option B: Hierarchical Timing Wheel (In-Memory on Scheduler Nodes)** ✅
- Time divided into buckets (e.g., 1-second ticks).
- O(1) insertion and O(1) tick execution.
- Shared state persisted to DB WAL (Write-Ahead Log) for disaster recovery.

---

### Deep Dive 2: Managing Dependencies (DAG Execution like Apache Airflow)

1. When Job A completes, it emits a `JobCompletedEvent` to Kafka.
2. **DAG Manager** listens to `JobCompletedEvent`.
3. Checks downstream table: `SELECT child_job FROM job_dependencies WHERE parent_job = 'Job_A'`.
4. Evaluates if all parents of `child_job` are in `SUCCESS` state.
5. If yes → pushes `child_job` into the execution queue.

---

### Deep Dive 3: Idempotency & At-Least-Once Delivery

- Workers use **distributed lock** (Redis / ZooKeeper) per `instance_id` when picking up a job to prevent duplicate execution.
- If worker dies mid-execution: lock expires after TTL → status remains `DISPATCHED` → Health Check Service re-enqueues the job.
- **Requirement for jobs**: Target job endpoints must be idempotent (e.g., using `idempotency_key = instance_id`).

---

## SECTION 8 — Summary Talk Track

1. "A Distributed Job Scheduler separates **Job Submission**, **Time-based Scheduling**, and **Distributed Execution**."
2. "Scheduling relies on a **Hierarchical Timing Wheel / Redis Delay Queue** to trigger jobs within ±1s of target time."
3. "Jobs flow into **Kafka priority topics**, consumed by a horizontally scalable worker pool."
4. "Dependencies are managed by an async **DAG Manager** tracking parent completion states."
5. "Guaranteed execution via **at-least-once delivery**, Redis distributed locks for worker dedup, and idempotent job design."
