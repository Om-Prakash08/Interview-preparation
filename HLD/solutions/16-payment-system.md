# 16. Design Payment System (Stripe / Razorpay)

> **Difficulty**: Very Hard | **Asked At**: Stripe, Amazon, PayPal, Google Pay, Uber
> **Time to Answer in Interview**: 40–45 minutes
> **Note**: The most critical question for fintech/senior roles. Correctness > performance.

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Accept payments from customers to merchants?
- Support for multiple payment methods (cards, UPI, wallets, bank transfer)?
- Payouts to merchants?
- Refunds?
- Recurring payments / subscriptions?
- Multi-currency support?

**Scale:**
- How many transactions per day?
- What is acceptable downtime?

**Typical Interviewer Answer:**
- Core: accept card payments, process, settle to merchant
- Refunds: yes
- Multi-currency: yes
- Subscriptions: mention as extension
- 1 million transactions per day, peak 100 TPS
- Zero tolerance for money loss or double charging

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Customer initiates payment (card, wallet, UPI)
2. Payment gateway validates and routes to card networks (Visa, MasterCard)
3. Bank authorizes or declines
4. Merchant notified of payment outcome
5. Merchant can initiate refunds
6. Settlement: transfer collected money to merchant's bank account (T+1 or T+2)

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Correctness** | A user must NEVER be charged twice for one transaction |
| **Consistency** | Money must never disappear or appear from nowhere |
| **Availability** | 99.999% for payment acceptance |
| **Latency** | Payment authorized in < 3 seconds |
| **Durability** | All payment events permanently logged |
| **Auditability** | Complete audit trail of every state change |
| **Security** | PCI-DSS compliance, no raw card data on our servers |

### Out of Scope
- Fraud ML model (mention as extension)
- FX conversion
- Card issuing (like Stripe Issuing)

---

## SECTION 3 — Capacity Estimation

### Transactions
- 1 million transactions/day
- = 1M / 86,400 ≈ **~11.5 transactions/sec** average
- Peak (Black Friday): **~100 TPS**

### Storage
- Transaction record: 2 KB
- 1M/day × 365 × 2 KB = **~730 GB/year** — small, easily handled by PostgreSQL
- Payment events (audit log): 10 KB × 1M/day = **~10 GB/day** — kept forever

### Network
- Payment requests must be routed to card networks (Visa/MC) and banks
- Each transaction: ~5 network hops with external systems
- External systems add 1–2 seconds of latency

---

## SECTION 4 — API Design

### 1. Create Payment Intent (first step, idempotent)
```
POST /api/v1/payment-intents
Authorization: Bearer <merchant_api_key>
Idempotency-Key: order_12345_attempt_1   // CLIENT MUST SEND THIS

{
  "amount": 50000,         // in smallest currency unit (paise for INR: 50000 = ₹500)
  "currency": "INR",
  "merchant_id": "m_abc",
  "customer_id": "c_123",
  "metadata": { "order_id": "ORD-12345" }
}

Response 200:
{
  "payment_intent_id": "pi_xyz",
  "status": "created",
  "client_secret": "pi_xyz_secret_abc"   // sent to frontend for card entry
}
```

### 2. Confirm Payment (frontend calls with card details)
```
POST /api/v1/payment-intents/{pi_id}/confirm
{
  "payment_method": {
    "type": "card",
    "card_token": "tok_visa_4242"   // tokenized card from PCI-compliant vault
  }
}

Response 200 (success):
{
  "payment_intent_id": "pi_xyz",
  "status": "succeeded",
  "amount": 50000,
  "currency": "INR"
}

Response 402 (declined):
{
  "status": "failed",
  "error": { "code": "card_declined", "message": "Your card was declined" }
}
```

### 3. Refund
```
POST /api/v1/refunds
Authorization: Bearer <merchant_api_key>
Idempotency-Key: refund_attempt_1

{
  "payment_intent_id": "pi_xyz",
  "amount": 50000,   // full refund
  "reason": "customer_request"
}

Response 200:
{
  "refund_id": "re_abc",
  "status": "pending",
  "amount": 50000
}
```

### 4. Webhook (Stripe sends to merchant)
```
POST {merchant_webhook_url}
Stripe-Signature: t=1722000000,v1=abc123...

{
  "type": "payment_intent.succeeded",
  "data": { "payment_intent_id": "pi_xyz", "amount": 50000 }
}
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `payment_intents` (Central ledger entity)
```
pi_id               VARCHAR(40)  PRIMARY KEY   -- e.g., pi_1234567890abcdef
merchant_id         VARCHAR(40)
customer_id         VARCHAR(40)
amount              BIGINT       NOT NULL      -- always in smallest unit
currency            CHAR(3)      NOT NULL      -- "INR", "USD"
status              ENUM('created', 'processing', 'succeeded', 'failed', 'refunded')
idempotency_key     VARCHAR(200) UNIQUE        -- prevents duplicate creation
payment_method_id   VARCHAR(40)  NULL
error_code          VARCHAR(100) NULL
created_at          TIMESTAMP
updated_at          TIMESTAMP
metadata            JSONB
```
**DB Choice**: **PostgreSQL** (ACID, financial record)
**Critical**: `idempotency_key` has UNIQUE constraint — duplicate requests return existing record.

### Table 2: `payment_events` (Append-only audit log — NEVER update/delete)
```
event_id            BIGINT       PRIMARY KEY (Snowflake)
pi_id               VARCHAR(40)
event_type          VARCHAR(50)  -- 'created', 'auth_requested', 'authorized', 'captured', 'failed', 'refunded'
amount              BIGINT
currency            CHAR(3)
payload             JSONB        -- full event data (request + response from bank)
created_at          TIMESTAMP    NOT NULL
```
**DB**: PostgreSQL (append-only, index on pi_id for audit trail lookup)
**Rule**: This table is IMMUTABLE. New events are only INSERTed, never UPDATEd or DELETEd.

### Table 3: `refunds`
```
refund_id           VARCHAR(40)  PRIMARY KEY
pi_id               VARCHAR(40)  REFERENCES payment_intents
amount              BIGINT
reason              VARCHAR(100)
status              ENUM('pending', 'succeeded', 'failed')
idempotency_key     VARCHAR(200) UNIQUE
created_at          TIMESTAMP
```

### Table 4: `merchants`
```
merchant_id         VARCHAR(40)  PRIMARY KEY
business_name       VARCHAR(200)
api_key_hash        VARCHAR(64)  -- bcrypt hash of API key (never store plain text)
webhook_url         TEXT
bank_account_no     VARCHAR(50)  -- encrypted at rest
settlement_cycle    ENUM('T+1', 'T+2')
is_active           BOOLEAN
```

### Tokenization Vault (separate, PCI-compliant system)
```
card_token          VARCHAR(40)  PRIMARY KEY -- opaque reference
card_last4          CHAR(4)
card_network        ENUM('visa', 'mastercard', 'amex')
card_exp_month      INT
card_exp_year       INT
encrypted_pan       BYTEA       -- AES-256 encrypted Primary Account Number
encryption_key_id   VARCHAR(40) -- points to KMS key version
```
**This vault is air-gapped from the rest of the system. Only the Vault Service can decrypt.**

---

## SECTION 6 — High-Level Architecture

```
CUSTOMER CHECKOUT FLOW
═══════════════════════════════════════════════════════════════════════

  MERCHANT WEBSITE                       PAYMENT GATEWAY (YOU)
  ───────────────                        ───────────────────────
  1. Customer clicks Pay                 
     ↓
  2. Merchant backend → POST /payment-intents
                                         Idempotency check → DB
                                         Create PI record → DB
                                         Return client_secret
  3. Merchant frontend shows card form
     (Stripe.js / Razorpay JS — card entered in PCI iframe)
  
  4. Frontend calls POST /confirm (with card token)
                                         ┌──────────────────────────┐
                                         │  Payment Service         │
                                         │  1. Validate PI status   │
                                         │  2. Tokenize card        │
                                         │  3. Fraud check          │
                                         │  4. Call bank            │
                                         └──────────┬───────────────┘
                                                    │
                                         ┌──────────▼───────────────┐
                                         │  Card Network Processor  │
                                         │  (Visa/MC gateway)       │
                                         │  → Issues Auth Request   │
                                         └──────────┬───────────────┘
                                                    │
                                         ┌──────────▼───────────────┐
                                         │  Issuer Bank             │
                                         │  (Customer's bank)       │
                                         │  → Authorize/Decline     │
                                         └──────────┬───────────────┘
                                                    │
                                         Response: Authorized / Declined
                                                    │
                                         Update PI status in DB
                                         Append event to audit log
                                         Send webhook to merchant
                                                    │
  5. Show success/failure to customer   ◄───────────┘

═══════════════════════════════════════════════════════════════════════

FULL SYSTEM DIAGRAM
═══════════════════════════════════════════════════════════════════════

 Merchant    ─────────────────────────────────────────────────────────
 Backend          API Gateway (Auth + Rate Limit)
                        │
                        │
              ┌─────────▼──────────────────────────────┐
              │         Payment Service                │
              │  (orchestrates the payment flow)       │
              └────┬────────────┬────────────┬──────────┘
                   │            │            │
          ┌────────▼───┐  ┌─────▼──────┐  ┌─▼──────────────┐
          │ Idempotency│  │  Tokenizer  │  │ Fraud Service  │
          │ Store      │  │  (Card Vault│  │ (Rules + ML)   │
          │ (Redis +   │  │   Service) │  └────────────────┘
          │  Postgres) │  └─────────────┘
          └────────────┘
                   │
          ┌────────▼────────────────────────────────────┐
          │  Bank Connector Service                     │
          │  Routes to correct processor:               │
          │  Visa → Chase gateway → Issuing bank        │
          │  UPI → NPCI → Customer UPI bank             │
          └────────┬────────────────────────────────────┘
                   │
          ┌────────▼────────────────────────────────────┐
          │  PostgreSQL (Primary)                       │
          │  payment_intents, payment_events, refunds   │
          └──────────────────────────────────────────────┘

          ┌────────────────────────────────────────────────┐
          │  Webhook Delivery Service                      │
          │  Kafka → retry queue → merchant webhook URL   │
          │  At-least-once delivery with exponential retry │
          └────────────────────────────────────────────────┘

          ┌─────────────────────────────────────────────────┐
          │  Settlement Service (runs nightly)              │
          │  Aggregate all succeeded payments by merchant   │
          │  Deduct fees → calculate payout amount          │
          │  Initiate bank transfer (NEFT/RTGS/ACH)         │
          └─────────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Idempotency (The Most Critical Concept)

**Problem**: Customer clicks "Pay" → request succeeds → network drops before response → browser retries → DOUBLE CHARGE!

**Solution: Idempotency Key**

```
Every payment request must include Idempotency-Key header (UUID, typically order_id).

On receiving request:
  1. Hash idempotency_key
  2. Try to INSERT into idempotency_store:
     { key_hash, pi_id, response_body, created_at }
     WITH unique constraint on key_hash

  3a. INSERT succeeds (new key):
     → Process payment normally
     → Store response in idempotency_store
     → Return response to client

  3b. INSERT fails (key already exists = retry):
     → Fetch stored response from idempotency_store
     → Return SAME response as first time
     → Payment is NOT processed again

Implementation in PostgreSQL:
  INSERT INTO idempotency_keys (key_hash, pi_id, response)
  VALUES (?, ?, ?)
  ON CONFLICT (key_hash) DO NOTHING
  RETURNING pi_id, response;
  → If returns nothing: key already existed → return stored response
```

**Result**: Client can safely retry any number of times. Same outcome, no double charge.

---

### Deep Dive 2: The Payment State Machine

```
CREATED ──authorize──► AUTHORIZED ──capture──► CAPTURED (money held)
                            │                      │
                            │ decline              │ refund
                            ▼                      ▼
                         FAILED               REFUNDED
                         
CREATED ──► PROCESSING ──► SUCCEEDED (for direct/UPI where auth+capture is one step)
                       └──► FAILED

Valid transitions only (enforced in DB with check constraint):
  Can only REFUND from SUCCEEDED state
  Can only CAPTURE from AUTHORIZED state
  Can only mark FAILED from PROCESSING/AUTHORIZED states

Double-submit protection:
  UPDATE payment_intents
  SET status = 'processing'
  WHERE pi_id = ? AND status = 'created'  -- CAS (Compare And Swap)
  
  If 0 rows updated: another request is already processing → return conflict
```

---

### Deep Dive 3: Exactly-Once Payment Processing

The hardest distributed systems problem in payments.

**The Danger Zone:**
```
1. Payment Service sends auth request to bank
2. Bank processes and authorizes (charges customer)
3. Network drops BEFORE response returns to Payment Service
4. Payment Service retries...

Options:
  A. Retry → bank sees second request → DOUBLE CHARGE ❌
  B. Don't retry → payment status unknown → customer charged but not fulfilled ❌
```

**Solution: Bank-Side Idempotency**
- Payment Service sends a unique `transaction_reference_id` with every bank request
- Bank deduplicates by `transaction_reference_id`
- Retry with same ID → bank returns same authorization response (no double charge)

**Solution: Reconciliation**
- Even with idempotency, have a background job that queries the bank for pending transactions
- If a transaction shows "authorized" at bank but "processing" in our DB → update our DB
- This is called **payment reconciliation** — runs every 5 minutes

**Solution: Distributed Saga**
```
Step 1: Create PI record in DB (local, durable)
Step 2: Deduct inventory / reserve product
Step 3: Call bank for authorization
Step 4: Confirm order + notify merchant

If Step 3 fails: 
  Compensate Step 2: release inventory
  Update PI to FAILED
  
If Step 4 fails:
  Compensate Step 3: void authorization at bank
  Compensate Step 2: release inventory
  Update PI to FAILED
```

---

### Deep Dive 4: PCI-DSS Compliance (Never Touch Raw Card Numbers)

**PCI-DSS** = Payment Card Industry Data Security Standard. Storing raw card numbers requires level-1 PCI compliance (audit every year, extremely expensive).

**Solution: Never touch raw card data**
```
1. Card entry happens in PCI-compliant iframe (Stripe.js / Razorpay.js)
   → JavaScript runs in THEIR domain, not yours
   → Your server NEVER sees the raw card number

2. Their JS sends card data directly to PCI-certified servers
   → Returns a one-time token (tok_1234, valid for 10 minutes)

3. Your server receives only the TOKEN
   → Token is exchanged for a permanent payment_method_id in the vault
   → All future charges use payment_method_id (opaque reference)

4. Your server is now OUT OF PCI SCOPE for card data
   → Much cheaper and simpler compliance story
```

**Even your vault stores encrypted PAN:**
```
Raw PAN: 4111111111111234
Encrypted PAN (AES-256-GCM): base64_encoded_ciphertext
Encryption key: stored in AWS KMS (never touches your servers)
Decryption only possible with KMS key + vault service credentials
```

---

### Deep Dive 5: Webhook Delivery (Reliable Merchant Notification)

```
Payment succeeds → Payment Service publishes event to Kafka
                            ↓
Webhook Delivery Service consumes event:
  1. Look up merchant's webhook_url from DB
  2. POST to webhook_url with payment event payload
  3. Sign payload with HMAC-SHA256 using merchant's webhook secret
     → Merchant verifies signature before processing
  
  If merchant's server returns 200: mark webhook as delivered
  If 4xx/5xx or timeout:
    → Retry with exponential backoff:
      Attempt 1: 10 sec, Attempt 2: 30 sec, ..., Attempt 10: 24 hours
    → After 10 failures: mark as permanently failed, alert merchant

  All webhook attempts logged in DB (idempotency: merchant may receive same event twice → must be idempotent on their end)

Merchant's responsibility:
  - Verify Stripe-Signature header
  - Check payment_intent_id not already processed (their own idempotency)
  - Respond with 200 quickly (< 5 sec) or webhook times out
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**CP (Strong Consistency)**
- Money must be correct. A payment cannot be both "succeeded" and "failed" simultaneously.
- PostgreSQL with ACID transactions. No eventual consistency for financial records.
- Availability trade-off: during DB failure, payments fail gracefully (503) rather than risk data corruption.

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Database | PostgreSQL | Cassandra | ACID is non-negotiable for financial records; Cassandra's eventual consistency is unsuitable |
| Idempotency | DB-level unique constraint | Application-level check | DB constraint is atomic; application check has TOCTOU race condition |
| Card data | Third-party vault (Stripe.js) | Self-managed vault | PCI scope reduction; third-party handles security audits |
| Audit log | Append-only event table | Mutable status column | Append-only provides full audit trail; mutable can lose history |
| Webhook delivery | At-least-once (Kafka + retry) | At-most-once | At-most-once may miss critical events; at-least-once with merchant idempotency is safe |

### What Would You Do Differently at Larger Scale?
- **Fraud ML model**: score every transaction 0–100 for fraud risk (rule-based + ML)
- **3D Secure**: additional bank authentication for high-risk transactions
- **Multi-PSP routing**: route card to cheapest processor, fail over to backup if primary down
- **Smart retry**: if bank temporarily down, retry in 30 seconds with same idempotency key

---

## Interview Flow Summary (Talk Track)

1. "Payment systems are about **correctness above everything** — no double charges, no lost money"
2. "**Idempotency key** is the single most important concept — all payment APIs must be idempotent"
3. "State machine: CREATED → PROCESSING → SUCCEEDED/FAILED — transitions are atomic DB updates"
4. "**PCI compliance**: never touch raw card data — use PCI iframe + tokenization"
5. "**Audit log**: append-only event table — every state change recorded, immutable"
6. "**Reconciliation**: background job to sync our DB with bank records — catches network drops"
7. "**Webhook delivery**: Kafka + exponential retry — at-least-once, merchant must be idempotent"
8. "CAP: **CP** — money correctness > availability. 503 during DB failure is acceptable; wrong balance is not."

---

> **Previous**: [15 — Design Food Delivery App](./15-food-delivery.md)
> **Next**: [17 — Design Netflix Streaming](./17-netflix-streaming.md)
