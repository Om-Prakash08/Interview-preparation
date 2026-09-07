# 25. Design Fraud Detection System

> **Difficulty**: Hard | **Asked At**: Stripe, PayPal, Uber, Amazon, Meta
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Evaluation timing: Synchronous inline blocking during payment or post-transaction async evaluation?
- Inputs evaluated: Credit card payments, account signups, logins, money transfers?
- Decision engine strategy: Rule-based, Machine Learning ML model, or Hybrid?
- Latency SLA budget for decisioning: < 100ms?
- Analyst dashboard & human-in-the-loop review queue required?
- Scale: Peak Transactions Per Second (TPS) and historical feature data?

**Typical Interviewer Answer:** Real-time synchronous inline decisioning during card authorization. Hybrid strategy (Deterministic Rules Engine + Machine Learning Model scoring). Latency budget < 100ms. 10,000 TPS peak volume. 5 years historical feature storage.

### 1.2 Functional Requirements (FR)
1. **Real-time Synchronous Evaluation**: Evaluate incoming payment requests in < 100ms and return `ALLOW`, `BLOCK`, or `FLAG_FOR_REVIEW`.
2. **Deterministic Rules Engine**: Execute configurable business rules (e.g., "Card used > 3 times in 1 minute $\rightarrow$ BLOCK").
3. **ML Scoring Service**: Predict fraud probability score ($0.0$ to $1.0$) using Machine Learning models.
4. **Real-Time Feature Aggregation**: Calculate real-time velocity metrics (e.g., transaction count per IP/user in last 5 minutes).
5. **Analyst Review Dashboard**: Queue flagged transactions for human analyst review.

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Evaluation Latency**| $< 100\text{ms}$ p99 end-to-end SLA |
| **Throughput** | 10,000 TPS peak evaluation rate |
| **Availability** | 99.999% availability (Fail-open to `ALLOW` if fraud service times out) |
| **Precision** | Low False Positive Rate (< 0.1%) to prevent blocking legitimate buyers |
| **Data Consistency** | Real-time velocity counters updated in $< 1\text{ second}$ |

### 1.4 Out of Scope
- Chargeback disputation resolution portal
- Credit scoring / loan risk decisioning

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────────────┐       ┌──────────────────────────┐
│   Transaction Event      │       │   Real-Time Velocity     │
│                          │       │   (Feature Vector)       │
│  txn_id, user_id, amount │──────►│                          │
│  card_fingerprint        │       │  card_count_1m           │
│  ip_address, device_id   │       │  ip_count_5m             │
│  timestamp               │       │  user_amount_sum_1h      │
└────────────┬─────────────┘       └────────────┬─────────────┘
             │                                  │
             ▼                                  ▼
┌──────────────────────────┐       ┌──────────────────────────┐
│   Fraud Decision Log     │       │   Blacklist / Rule       │
│  txn_id                  │       │  rule_id                 │
│  decision (ALLOW/BLOCK)  │       │  type (IP/CARD/DEVICE)   │
│  risk_score (0.0 - 1.0)  │       │  value                   │
│  triggered_rules         │       └──────────────────────────┘
└──────────────────────────┘
```

### 2.2 Data Model / Schema

**1. `fraud_decisions_log` (ClickHouse / PostgreSQL Partitioned)**
```sql
CREATE TABLE fraud_decisions (
  txn_id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64),
  amount BIGINT,
  decision VARCHAR(20), -- 'ALLOW', 'BLOCK', 'FLAG_FOR_REVIEW'
  risk_score FLOAT,
  triggered_rules TEXT[], -- ['RULE_HIGH_VELOCITY_CARD', 'RULE_IP_SANCTION']
  evaluation_time_ms INT,
  created_at TIMESTAMP
);
```

**2. Real-Time Velocity Counter Schema (Redis Cluster Sorted Sets)**
```
Key: velocity:card:{card_fingerprint}
Data Structure: ZSET (Score = Timestamp, Value = Transaction_ID)
TTL: 3600 seconds (1 hour window)
```

> 🎯 **NFR addressed**: **Latency < 100ms** — Real-time velocities stored in Redis in-memory ZSETs provide sub-millisecond feature retrieval.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Synchronous Fraud Evaluation API
```
POST /api/v1/fraud/evaluate
{
  "transaction_id": "txn_998124",
  "user_id": "u_4410",
  "amount": 125000, -- amount in cents ($1,250.00)
  "currency": "USD",
  "payment_method": {
    "card_fingerprint": "fg_card_881",
    "bin": "411111",
    "country": "US"
  },
  "device_context": {
    "ip_address": "198.51.100.42",
    "device_fingerprint": "dev_mac_991"
  },
  "timestamp": 1722000000000
}

Response 200 OK:
{
  "transaction_id": "txn_998124",
  "decision": "BLOCK", -- ALLOW | BLOCK | FLAG_FOR_REVIEW
  "risk_score": 0.94,
  "triggered_rules": ["RULE_HIGH_VELOCITY_CARD", "RULE_LOCATION_MISMATCH"],
  "evaluation_time_ms": 24
}
```

> 🎯 **NFR addressed**: **Precision & Latency** — API returns explicit list of `triggered_rules` and `evaluation_time_ms` for instant developer debugging and compliance auditing.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Evaluation Volume**: 10,000 TPS.
- **Redis Cluster RAM**: 10,000 TPS × 5 KB feature state × 1-hour window = **~500 GB RAM** for real-time feature storage.
- **Offline Feature Storage**: 10,000 TPS × 5 KB = 50 MB/s log write = **~4.3 TB / day** into ClickHouse/Snowflake.

### 4.2 Data Flow Through System

```
SYNCHRONOUS EVALUATION PIPELINE (< 100ms SLA)
  Payment Gateway ──POST /evaluate──► Fraud Service Orchestrator
    │
    ├─ 1. Fetch Real-time Velocity Features from Redis Cluster (~5ms)
    │      (card_count_1m, ip_count_5m, amount_sum_1h)
    │
    ├─ 2. Pass Features to Hard Rules Engine (~5ms)
    │      ├─ Check Blacklists (IP, Card Fingerprint, Email)
    │      └─ If Hard Rule Triggered -> Immediately Return `BLOCK` (Fast-Path)
    │
    ├─ 3. Pass Features to ML Scoring Model (Triton / XGBoost Server) (~20ms)
    │      └─ Returns risk_score (0.0 to 1.0)
    │
    ├─ 4. Evaluate Threshold Rules (~2ms):
    │      ├─ risk_score < 0.30 -> `ALLOW`
    │      ├─ 0.30 <= risk_score < 0.85 -> `FLAG_FOR_REVIEW`
    │      └─ risk_score >= 0.85 -> `BLOCK`
    │
    └─ 5. Return Decision to Payment Gateway & Emit Event to Kafka

ASYNCHRONOUS FEATURE & RETRAINING PIPELINE
  Kafka Event Stream ──► Apache Flink ──► Feature Store & ClickHouse ──► PyTorch/XGBoost Retraining
```

> 🎯 **NFR addressed**: **Latency < 100ms** — Fast-Path hard rule checking skips ML model execution if blacklisted, saving 20ms.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                                 ┌───────────────────────────┐
                                 │      Payment Gateway      │
                                 └─────────────┬─────────────┘
                                               │ POST /evaluate (<100ms)
                                               ▼
                                 ┌───────────────────────────┐
                                 │ Fraud Service Orchestrator│
                                 └──────┬─────────────┬──────┘
                                        │             │
                    ┌───────────────────┘             └───────────────────┐
                    │ Fetch Velocities (5ms)                              │ Emit Event
                    ▼                                                     ▼
      ┌───────────────────────────┐                         ┌───────────────────────────┐
      │   Redis Cluster           │                         │   Kafka Event Stream      │
      │   (Real-Time Velocities)  │                         └─────────────┬─────────────┘
      └───────────────────────────┘                                       │
                                                                          ▼
      ┌───────────────────────────┐                         ┌───────────────────────────┐
      │   Hard Rules Engine       │                         │   Apache Flink Engine     │
      │   (Fast-Path Blacklists)  │                         │   (Calculates Window AGGs)│
      └─────────────┬─────────────┘                         └─────────────┬─────────────┘
                    │ If Pass                                             │
                    ▼                                                     ▼
      ┌───────────────────────────┐                         ┌───────────────────────────┐
      │   ML Scoring Service      │                         │   Feature Store           │
      │   (XGBoost / Triton)      │                         │   (ClickHouse / Feast)    │
      └───────────────────────────┘                         └───────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Fraud Orchestrator**| Coordinates evaluation steps | Enforces timeouts and circuit breakers within the 100ms budget |
| **Redis Cluster** | Real-time velocity feature store| Sliding-window ZSETs calculate card/IP velocities in sub-milliseconds |
| **Hard Rules Engine** | Fast-path deterministic filtering | Blocks known malicious IPs and blacklisted cards instantly |
| **XGBoost Service** | ML risk scoring engine | Extremely fast inference latency (< 10ms) compared to deep neural nets |
| **Kafka & Flink** | Async feature calculation | Continuously feeds velocity aggregations into Redis and Feature Store |

> 🎯 **NFR addressed**: **Availability 99.999%** — If ML Scoring Service times out (> 80ms), the system fails-open to `ALLOW` to preserve payment conversion.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Real-Time Velocity Aggregation via Redis Sliding Windows

**Problem**: Calculate "number of transactions on this card in the last 10 minutes" at 10,000 TPS in < 5ms.

**Solution: Redis Lua Script over Sorted Sets (ZSET)**
```lua
-- Atomic Lua Script executed in Redis
local key = "velocity:card:" .. KEYS[1]
local now = tonumber(ARGV[1])
local window = 600 -- 10 minutes in seconds

-- 1. Remove transactions older than 10 minutes
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 2. Add current transaction
redis.call('ZADD', key, now, ARGV[2])

-- 3. Set Key Expiration to clean up inactive cards
redis.call('EXPIRE', key, window)

-- 4. Return count of transactions in the window
return redis.call('ZCARD', key)
```

---

### Deep Dive 2: Hybrid Decision Logic (Rules Engine + ML Model)

```
                  Incoming Transaction
                           │
                           ▼
                 Step 1: Hard Rules Check
            ┌──────────────┴──────────────┐
            │ Triggered?                  │
            ▼                             ▼
         YES: BLOCK                 NO: Step 2: ML Model Scoring
      (Fast-Path < 10ms)                  │ (XGBoost Prediction)
                                          ▼
                                    Risk Score (0.0 - 1.0)
                                          │
                  ┌───────────────────────┼───────────────────────┐
                  ▼                       ▼                       ▼
            Score < 0.30            0.30 <= Score < 0.85        Score >= 0.85
                  │                       │                       │
                  ▼                       ▼                       ▼
                ALLOW              FLAG_FOR_REVIEW              BLOCK
                                 (Analyst Queue)
```

---

### Deep Dive 3: Resiliency & Fail-Open Circuit Breaker Strategy

```
Why Fail-Open?
  - Blocking a legitimate $1,000 purchase costs $1,000 in immediate lost revenue + customer churn.
  - Undetected fraud costs $1,000 chargeback + $15 processing fee.
  - Standard merchant fraud rate is < 0.5%. Failing-OPEN during system outage preserves 99.5% legitimate sales!

Circuit Breaker Protocol:
  - Timeout budget for ML Scoring = 50ms.
  - If Fraud Service timeout or error rate > 5% in 1 minute:
      1. Trip Circuit Breaker to OPEN state.
      2. Bypass ML Scoring and default to `ALLOW` (or evaluate Fast-Path Hard Rules only).
      3. Emit alert to DevOps and log transaction for post-facto asynchronous fraud review.
```

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **ML Model** | XGBoost (GBDT) | Deep Neural Network | XGBoost provides sub-10ms inference and feature importance (SHAP values) |
| **Velocity Store** | Redis ZSET | SQL Database | SQL queries (`COUNT(*) WHERE created_at > NOW() - 10m`) cannot scale at 10,000 TPS |
| **Failure Mode** | Fail-Open (`ALLOW`)| Fail-Closed (`BLOCK`)| False positives (blocking good buyers) hurt business revenue far more than fraud |

---

### Summary Talk Track

1. "We design a **Hybrid Fraud Detection System** that evaluates 10,000 TPS in **< 100ms**."
2. "Real-time velocity features (e.g., card usage in last 10m) are calculated using atomic **Redis ZSET Lua scripts** in **< 5ms**."
3. "The decision engine combines **Fast-Path Hard Rules** with **XGBoost ML Scoring**, routing suspicious cases ($0.30 \le \text{score} < 0.85$) to an analyst review queue."
4. "Resilience is ensured via a **Fail-Open Strategy** with circuit breakers to preserve business payment conversion during system degradations."

---

> **Previous**: [24 — Design Distributed Job Scheduler](./24-distributed-job-scheduler.md)
