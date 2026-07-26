# 25. Design Fraud Detection System

> **Difficulty**: Hard | **Asked At**: Stripe, PayPal, Uber, Amazon, Meta
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Real-time transaction blocking or post-transaction evaluation?
- What inputs do we evaluate? (Card payments, account signups, logins, transfers)
- Hybrid rules engine + ML model scoring?
- Human-in-the-loop audit dashboard for manual review?
- Latency budget for fraud decisioning?

**Scale:**
- How many transactions per second (TPS) at peak?
- How much historical data for feature generation?

**Typical Interviewer Answer:**
- Real-time decisioning during transaction authorization (inline blocking)
- Hybrid rules engine (hard limits) + Machine Learning model scoring
- Latency budget: < 100ms
- 10,000 TPS peak; 5 years of feature history

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Evaluate incoming financial transactions in real time (< 100ms)
2. Return decision: `ALLOW`, `BLOCK`, or `FLAG_FOR_REVIEW`
3. Execute configurable deterministic business rules (e.g., "> 3 card attempts in 1 min → BLOCK")
4. Execute ML scoring model (predict fraud probability score 0.0 to 1.0)
5. Aggregate real-time velocity features (e.g., number of transactions by user in last 5 minutes)
6. Provide audit logs and analyst dashboard for manual review

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Latency** | < 100ms p99 end-to-end evaluation time |
| **Throughput** | 10,000 TPS |
| **Availability** | 99.999% (Fail-open to `ALLOW` if fraud service times out) |
| **Accuracy** | High precision to avoid blocking legitimate customers (False Positives < 0.1%) |

---

## SECTION 3 — Capacity Estimation

### Evaluation Volume
- 10,000 TPS
- Average payload: 5 KB per evaluation
- Storage for real-time feature lookup (Redis Cluster): **500 GB RAM**
- Long-term feature store / event history (ClickHouse / Snowflake): **~4.3 TB / day**

---

## SECTION 4 — API Design

### 1. Evaluate Transaction (Synchronous Inline Check)
```
POST /api/v1/fraud/evaluate
{
  "transaction_id": "txn_882194",
  "user_id": "u_55102",
  "amount": 125000,                      // in cents ($1,250.00)
  "currency": "USD",
  "payment_method": {
    "card_fingerprint": "fg_abc123",
    "bin": "411111",
    "country": "US"
  },
  "device_context": {
    "ip_address": "198.51.100.42",
    "device_fingerprint": "dev_991823",
    "user_agent": "Mozilla/5.0..."
  },
  "timestamp": 1722000000000
}

Response 200:
{
  "transaction_id": "txn_882194",
  "decision": "BLOCK",                     // ALLOW | BLOCK | FLAG_FOR_REVIEW
  "risk_score": 0.94,                       // 0.0 to 1.0
  "triggered_rules": ["RULE_HIGH_VELOCITY_IP", "RULE_CARD_COUNTRY_MISMATCH"],
  "evaluation_time_ms": 28
}
```

---

## SECTION 5 — High-Level Architecture

```
                  PAYMENT GATEWAY / SERVICE
                             │
                             │ Synchronous Evaluation (POST /evaluate)
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ API Gateway & Fraud Service Orchestrator               │
 └───────────────────────────┬────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ Real-Time Feature Aggregator (Redis Cluster)           │
 │ Fetches real-time velocities (e.g. card_count_1m)     │
 └───────────────────────────┬────────────────────────────┘
                             │ Features
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ Deterministic Rules Engine                             │
 │ Evaluates static rules (Blacklists, Velocity limits)   │
 └───────────────────────────┬────────────────────────────┘
                             │ If not hard-blocked
                             ▼
 ┌────────────────────────────────────────────────────────┐
 │ ML Scoring Service (Triton / XGBoost Server)          │
 │ Predicts fraud probability score                       │
 └───────────────────────────┬────────────────────────────┘
                             │ Final Decision (ALLOW/BLOCK)
                             ▼
                  PAYMENT GATEWAY / SERVICE

 ASYNCHRONOUS EVENT STREAM (Offline Feature Store & Retraining)
 Fraud Service ──► Kafka ──► Flink ──► Feature Store (Feast / ClickHouse)
                                                │
                                                ▼
                                    Model Training (PyTorch/XGBoost)
```

---

## SECTION 6 — Deep Dives

### Deep Dive 1: Real-Time Feature Store & Velocity Aggregation

**Problem:** How to count "how many times has this credit card been used in the last 10 minutes" within 5ms at 10,000 TPS?

**Solution: Sliding Window Counters in Redis**
```lua
-- Lua script executed atomically in Redis
local key = "velocity:card:" .. KEYS[1]
local now = tonumber(ARGV[1])
local window = 600 -- 10 minutes

redis.call('ZREMRANGEBYSCORE', key, 0, now - window)
redis.call('ZADD', key, now, now)
redis.call('EXPIRE', key, window)
return redis.call('ZCARD', key)
```

---

### Deep Dive 2: Hybrid Decision Logic (Rules + ML)

1. **Fast-Path Hard Rules (0-5ms)**: Check blacklists (IP, email, device ID), country sanctions, hard velocity limits. If triggered → return `BLOCK` immediately without calling ML model.
2. **ML Scoring Path (5-30ms)**: Execute GBDT (XGBoost) or Deep Neural Network model.
3. **Threshold Rules**:
   - `risk_score` < 0.30 → `ALLOW`
   - 0.30 ≤ `risk_score` < 0.85 → `FLAG_FOR_REVIEW` (Sends to manual review queue)
   - `risk_score` ≥ 0.85 → `BLOCK`

---

### Deep Dive 3: Fallback & Circuit Breaker (Fail-Open)

- If Fraud Service latency exceeds 80ms or service errors out → Circuit Breaker trips.
- **Fail-Open Strategy**: Default to `ALLOW` for standard transactions, but log for post-transaction async review. (Payment conversion > small fraud risk).

---

## SECTION 7 — Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Model Choice | XGBoost (GBDT) | Deep Neural Net | XGBoost is ultra-fast (<5ms inference), highly interpretable (SHAP values for rules) |
| Feature Store | Redis (Real-time) | PostgreSQL | In-memory Redis provides sub-millisecond velocity aggregations necessary for 100ms SLA |
| Strategy | Fail-Open | Fail-Closed | Blocking valid buyers hurts revenue more than occasional undetected fraud |

---

## SECTION 8 — Summary Talk Track

1. "Fraud Detection combines **Inline Decisioning (<100ms)** with an **Asynchronous ML Feature Pipeline**."
2. "Real-time features (velocities) are calculated using sliding-window ZSETs in **Redis**."
3. "Hybrid Pipeline: Hard Rules → Fast-path Block → XGBoost ML Model Scoring → Final Decision (ALLOW/BLOCK/REVIEW)."
4. "Resilience via **Fail-Open strategy** and continuous model retraining driven by Kafka & Flink event streams."
