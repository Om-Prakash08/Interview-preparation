# 16. Design Payment System (Stripe / Razorpay)

> **Difficulty**: Very Hard | **Asked At**: Stripe, Amazon, PayPal, Google Pay, Uber
> **Time to Answer in Interview**: 40–45 minutes
> **Note**: The most critical question for fintech/senior roles. Correctness > performance.

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Accept payments from customers to merchants?
- Support for multiple payment methods (cards, UPI, wallets)?
- Payouts to merchants? Refunds?
- Recurring payments / subscriptions?
- Multi-currency support?
- How many transactions per day? Acceptable downtime?

**Typical Interviewer Answer:** Core card payments, refunds, multi-currency. 1M txn/day, peak 100 TPS. Zero tolerance for money loss or double charging.

### 1.2 Functional Requirements (FR)
1. Customer initiates payment (card, wallet, UPI)
2. Payment gateway validates and routes to card networks
3. Bank authorizes or declines
4. Merchant notified of payment outcome
5. Merchant can initiate refunds
6. Settlement: transfer money to merchant's bank (T+1 / T+2)

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Correctness** | NEVER charge twice for one transaction |
| **Consistency** | Money must never disappear or appear from nowhere |
| **Availability** | 99.999% for payment acceptance |
| **Latency** | Payment authorized in < 3 seconds |
| **Durability** | All payment events permanently logged |
| **Auditability** | Complete audit trail of every state change |
| **Security** | PCI-DSS compliance, no raw card data on our servers |

### 1.4 Out of Scope
- Fraud ML model, FX conversion, Card issuing

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐   ┌──────────────────┐   ┌──────────────────┐
│  PaymentIntent   │   │  PaymentEvent    │   │    Refund        │
│  (central)       │   │  (audit log)     │   │                  │
│                  │   │                  │   │                  │
│  pi_id           │──►│  event_id        │   │  refund_id       │
│  merchant_id     │   │  pi_id           │   │  pi_id           │
│  amount          │   │  event_type      │   │  amount          │
│  currency        │   │  payload (JSONB) │   │  status          │
│  status          │   │  created_at      │   │  idempotency_key │
│  idempotency_key │   │  (IMMUTABLE)     │   └──────────────────┘
│  payment_method  │   └──────────────────┘
└──────────────────┘                          ┌──────────────────┐
                                              │    Merchant      │
┌──────────────────┐                          │                  │
│  Card Vault      │                          │  merchant_id     │
│  (PCI isolated)  │                          │  webhook_url     │
│                  │                          │  api_key_hash    │
│  card_token      │                          │  bank_account    │
│  encrypted_pan   │                          └──────────────────┘
│  card_last4      │
└──────────────────┘
```

**Primary entities**: `PaymentIntent` (the payment lifecycle), `PaymentEvent` (append-only audit log), `Refund`, `Merchant`, `CardVault` (PCI-isolated tokenization).

### 2.2 Data Model / Schema

**Table 1: `payment_intents`** — PostgreSQL (ACID, financial record)
```
pi_id, merchant_id, customer_id, amount BIGINT (smallest unit),
currency CHAR(3), status ENUM('created','processing','succeeded','failed','refunded'),
idempotency_key VARCHAR UNIQUE, payment_method_id, error_code, metadata JSONB
```
**Critical**: `idempotency_key` has UNIQUE constraint — duplicate requests return existing record.

**Table 2: `payment_events`** — PostgreSQL (append-only, IMMUTABLE)
```
event_id, pi_id, event_type, amount, currency, payload JSONB, created_at
```
**Rule**: NEVER UPDATE or DELETE. Only INSERT.

**Table 3: `refunds`** — PostgreSQL
```
refund_id, pi_id, amount, reason, status, idempotency_key UNIQUE
```

**Card Vault** — Air-gapped PCI system with AES-256 encrypted PANs, KMS key management.

> 🎯 **NFR addressed**: **Correctness** — idempotency_key UNIQUE prevents double charges. **Auditability** — append-only event log for complete audit trail. **Security** — card data in isolated PCI vault.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Create Payment Intent (idempotent)
```
POST /api/v1/payment-intents
Idempotency-Key: order_12345_attempt_1
{ "amount": 50000, "currency": "INR", "merchant_id": "m_abc" }
Response: { "payment_intent_id": "pi_xyz", "status": "created", "client_secret": "pi_xyz_secret" }
```

### 3.2 Confirm Payment
```
POST /api/v1/payment-intents/{pi_id}/confirm
{ "payment_method": { "type": "card", "card_token": "tok_visa_4242" } }
Response 200: { "status": "succeeded" }
Response 402: { "status": "failed", "error": { "code": "card_declined" } }
```

### 3.3 Refund
```
POST /api/v1/refunds
Idempotency-Key: refund_attempt_1
{ "payment_intent_id": "pi_xyz", "amount": 50000, "reason": "customer_request" }
```

### 3.4 Webhook (to merchant)
```
POST {merchant_webhook_url}
Stripe-Signature: t=...,v1=HMAC-SHA256(...)
{ "type": "payment_intent.succeeded", "data": { ... } }
```

> 🎯 **NFR addressed**: **Correctness** — all APIs require Idempotency-Key. **Latency < 3s** — confirm triggers synchronous bank auth. **Durability** — webhook retry with exponential backoff.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Transactions:** 1M/day = ~11.5 TPS avg; peak ~100 TPS

**Storage:** 730 GB/year for transactions; 10 GB/day for audit events — PostgreSQL handles fine

**Network:** Each transaction = ~5 network hops with external bank systems; 1-2s external latency

### 4.2 Data Flow Through System

**Payment Flow:**
```
Merchant backend → POST /payment-intents (idempotency check)
  → Create PI record in DB (status: created)
  → Merchant frontend shows card form (PCI iframe)
  → Frontend → POST /confirm with card_token
    → Payment Service:
      1. Validate PI status (CAS: created → processing)
      2. Card Vault: resolve token → encrypted PAN
      3. Fraud Service: score transaction
      4. Bank Connector: send auth request to Visa/MC → Issuer Bank
      5. Bank responds: authorized/declined
      6. Update PI status → succeeded/failed
      7. Append event to audit log
      8. Kafka → Webhook Delivery Service → POST to merchant webhook
  → Return result to customer
```

> 🎯 **NFR addressed**: **Correctness** — CAS update prevents concurrent processing. **Consistency** — single DB transaction for status update + event log. **Auditability** — every step logged.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
MERCHANT WEBSITE → POST /payment-intents → API Gateway (Auth + RL)
                                                │
                                    ┌───────────▼──────────────┐
                                    │    Payment Service       │
                                    │    (orchestrator)        │
                                    └───┬──────┬──────┬────────┘
                                        │      │      │
                               ┌────────▼┐ ┌───▼───┐ ┌▼──────────────┐
                               │Idempot. │ │Card   │ │Fraud Service  │
                               │Store    │ │Vault  │ │(Rules + ML)   │
                               │(Redis+  │ │(PCI)  │ └───────────────┘
                               │Postgres)│ └───────┘
                               └─────────┘
                                        │
                               ┌────────▼────────────────────┐
                               │  Bank Connector Service     │
                               │  Visa → Chase → Issuer Bank │
                               └────────┬────────────────────┘
                                        │
                               ┌────────▼────────────────────┐
                               │  PostgreSQL (Primary)       │
                               │  payment_intents (ACID)     │
                               │  payment_events (audit log) │
                               └─────────────────────────────┘

                               ┌──────────────────────────────┐
                               │  Webhook Delivery (Kafka)    │
                               │  At-least-once + exp. retry  │
                               └──────────────────────────────┘

                               ┌──────────────────────────────┐
                               │  Settlement Service (nightly)│
                               │  Aggregate → deduct fees →   │
                               │  bank transfer to merchant   │
                               └──────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Payment Service** | Orchestrates the payment flow | Single source of truth for payment state machine |
| **Idempotency Store** | Prevents double charges (UNIQUE constraint) | DB-level atomic check; no TOCTOU race condition |
| **Card Vault** | PCI-isolated card tokenization | Air-gapped from main system; reduces PCI scope |
| **Bank Connector** | Routes to correct card network/processor | Abstracts Visa/MC/UPI behind unified interface |
| **PostgreSQL** | ACID storage for financial records + audit log | Money correctness requires strong consistency |
| **Webhook Delivery** | Reliable merchant notification via Kafka | At-least-once with exponential retry |
| **Settlement Service** | Nightly payout to merchants | Batch aggregation; T+1/T+2 settlement cycles |

> 🎯 **NFR addressed**: **Correctness** — idempotency + CAS + ACID. **Availability 99.999%** — stateless payment service, DB replication. **Security** — PCI vault isolation. **Auditability** — append-only events. **Latency < 3s** — synchronous bank auth.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Idempotency (The Most Critical Concept)

**Problem**: Customer clicks "Pay" → request succeeds → network drops → browser retries → DOUBLE CHARGE!

**Solution:**
```
Every request includes Idempotency-Key header (UUID, typically order_id).

On receiving request:
  INSERT INTO idempotency_keys (key_hash, pi_id, response)
  ON CONFLICT (key_hash) DO NOTHING

  If INSERT succeeds: process payment, store response
  If INSERT fails (key exists): return stored response (same result)

Result: Client can safely retry any number of times. No double charge.
```

---

### Deep Dive 2: The Payment State Machine

```
CREATED ──authorize──► AUTHORIZED ──capture──► CAPTURED
                            │ decline              │ refund
                            ▼                      ▼
                         FAILED               REFUNDED

Double-submit protection:
  UPDATE payment_intents SET status = 'processing'
  WHERE pi_id = ? AND status = 'created'  -- CAS (Compare And Swap)
  If 0 rows updated → another request already processing → return conflict
```

---

### Deep Dive 3: Exactly-Once Payment Processing

```
The Danger Zone:
  Payment Service → bank auth request → bank processes → network drops
  → Payment Service retries → DOUBLE CHARGE?

Solutions:
  1. Bank-side idempotency: send unique transaction_reference_id
     → Bank deduplicates by reference_id → no double charge

  2. Reconciliation: background job queries bank for pending transactions
     → If bank says "authorized" but our DB says "processing" → update DB

  3. Distributed Saga: each step has compensating transaction
     If payment fails → void authorization → release inventory
```

---

### Deep Dive 4: PCI-DSS Compliance

```
Never touch raw card data:
  1. Card entry in PCI iframe (Stripe.js / Razorpay.js) — THEIR domain
  2. Their JS sends card to PCI-certified servers → returns token
  3. Your server receives ONLY the token → exchanges for payment_method_id
  4. Your server is OUT OF PCI SCOPE for card data

Even vault stores encrypted PAN: AES-256-GCM with AWS KMS key management
```

---

### Deep Dive 5: Webhook Delivery

```
Payment succeeds → Kafka event → Webhook Delivery Service
  → POST to merchant's webhook_url with HMAC-SHA256 signature
  → If 200: mark delivered
  → If failure: exponential backoff (10s, 30s, ... 24h) → 10 attempts
  → Merchant MUST verify signature AND be idempotent on their end
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:** **CP (Strong Consistency)** — Money must be correct. 503 during DB failure is acceptable; wrong balance is not.

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Database | PostgreSQL | Cassandra | ACID is non-negotiable for financial records |
| Idempotency | DB UNIQUE constraint | Application check | DB constraint is atomic; app check has TOCTOU race |
| Card data | Third-party vault | Self-managed | PCI scope reduction; third-party handles audits |
| Audit log | Append-only event table | Mutable status column | Append-only provides full history |
| Webhooks | At-least-once (Kafka) | At-most-once | At-most-once may miss critical events |

---

### Summary Talk Track

1. "Payment systems are about **correctness above everything** — no double charges, no lost money."
2. "Core entities: **PaymentIntent** (lifecycle), **PaymentEvent** (immutable audit log), **Refund**, **CardVault** (PCI isolated)."
3. "**Idempotency key** is the single most important concept — all payment APIs must be idempotent."
4. "State machine: CREATED → PROCESSING → SUCCEEDED/FAILED — transitions are atomic CAS updates."
5. "**PCI compliance**: never touch raw card data — use PCI iframe + tokenization."
6. "**Audit log**: append-only event table — every state change recorded, immutable."
7. "**Reconciliation**: background job syncs our DB with bank records — catches network drops."
8. "CAP: **CP** — money correctness > availability."

---

> **Previous**: [15 — Design Food Delivery App](./15-food-delivery.md)
> **Next**: [17 — Design Netflix Streaming](./17-netflix-streaming.md)
