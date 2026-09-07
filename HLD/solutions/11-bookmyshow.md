# 11. Design BookMyShow / Ticketmaster

> **Difficulty**: Hard | **Asked At**: Amazon, Google, Uber, Flipkart
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Book movie tickets, concert tickets, or sports events — all?
- Should we support seat selection (user picks specific seat)?
- What happens when a seat is selected but payment not yet done? (seat hold / timeout)
- Do we need a waiting room for high-demand events (concert goes on sale at 10am)?
- Cancellation and refunds?
- Multiple cinemas, multiple cities?

**Scale:**
- How many bookings per day?
- How many concurrent users during peak (concert sale)?
- Number of seats per event?

**Typical Interviewer Answer:**
- Movie + concert ticketing
- Specific seat selection
- Seat hold: 10-minute window to complete payment after seat selected
- High-demand events: waiting room with queue
- 5 million bookings per day, peak during popular event launch: 100,000 concurrent users
- Up to 50,000 seats per event

### 1.2 Functional Requirements (FR)
1. Browse events (movies, concerts) by city, date, category
2. View available seats for an event + showtime
3. Select and temporarily hold seats (10-minute lock)
4. Book and pay for seats (complete the transaction)
5. Receive booking confirmation
6. Cancel booking (within policy window)

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Concurrency** | Handle thousands of users trying to book same seats simultaneously |
| **Consistency** | A seat must NEVER be double-booked — strong consistency required |
| **Availability** | 99.99% for browsing; consistency > availability for booking |
| **Latency** | Seat selection < 500ms; payment < 3s |
| **Scale** | 5 million bookings/day; 100K concurrent users at peak launch |

### 1.4 Out of Scope
- Recommendation engine
- Food ordering at cinema
- Seat pricing/surge (mention as extension)

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Event      │   │  Showtime    │   │    Seat      │   │   Booking    │
│              │   │              │   │  (critical)  │   │              │
│ event_id     │──►│ showtime_id  │──►│ seat_id      │   │ booking_id   │
│ name         │   │ event_id     │   │ showtime_id  │◄──│ showtime_id  │
│ venue_id     │   │ start_time   │   │ category     │   │ seat_ids[]   │
│ city         │   │ total_seats  │   │ price        │   │ user_id      │
│ category     │   │ avail_seats  │   │ status       │   │ total_price  │
└──────────────┘   └──────────────┘   │ hold_expires │   │ payment_ref  │
                                      └──────────────┘   └──────────────┘
                                      ┌──────────────┐
                                      │    Hold      │
                                      │              │
                                      │ hold_id      │
                                      │ user_id      │
                                      │ seat_ids[]   │
                                      │ expires_at   │
                                      │ status       │
                                      └──────────────┘
```

**Primary entities**: `Event` (what), `Showtime` (when), `Seat` (the critical resource — must never be double-booked), `Hold` (temporary lock), `Booking` (confirmed purchase).

### 2.2 Data Model / Schema

**Table 1: `seats` (The critical table)**
```
seat_id       VARCHAR(20)  -- e.g., "A1"
showtime_id   BIGINT
row_label     VARCHAR(5)
seat_number   INT
category      ENUM('economy', 'premium', 'vip')
price         DECIMAL
status        ENUM('available', 'held', 'booked')
hold_id       BIGINT       NULL
hold_expires  TIMESTAMP    NULL
PRIMARY KEY (showtime_id, seat_id)
```
**DB Choice**: **PostgreSQL** — we need ACID transactions for seat booking. Row-level locking is critical to prevent double-booking.

**Table 2: `holds`**
```
hold_id       BIGINT       PRIMARY KEY
user_id       BIGINT
showtime_id   BIGINT
seat_ids      TEXT[]
expires_at    TIMESTAMP
status        ENUM('active', 'expired', 'converted')
created_at    TIMESTAMP
```

**Table 3: `bookings`**
```
booking_id    BIGINT       PRIMARY KEY
user_id       BIGINT
showtime_id   BIGINT
seat_ids      TEXT[]
hold_id       BIGINT
total_price   DECIMAL
payment_ref   VARCHAR
status        ENUM('confirmed', 'cancelled')
booked_at     TIMESTAMP
```

**Table 4: `events` + `showtimes`** (standard metadata tables as shown in entity diagram)

> 🎯 **NFR addressed**: **Consistency** — PostgreSQL ACID + row-level locking prevents double-booking. **Concurrency** — `SELECT FOR UPDATE` serializes concurrent access to same seat. **Latency** — seat status cached in Redis for fast reads.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Search Events
```
GET /api/v1/events?city=Mumbai&date=2025-08-01&category=movie
Response: { "events": [ { "event_id", "name", "venue", "showtimes": [...] } ] }
```

### 3.2 Get Seat Map
```
GET /api/v1/events/{event_id}/showtimes/{showtime_id}/seats
Response: {
  "seats": [
    { "seat_id": "A1", "category": "premium", "price": 500, "status": "available" },
    { "seat_id": "A2", "category": "premium", "price": 500, "status": "held" },
    ...
  ]
}
```

### 3.3 Hold Seats (Temporary Lock)
```
POST /api/v1/bookings/hold
Authorization: Bearer <token>
{
  "showtime_id": "st_123",
  "seat_ids": ["A1", "A3"]
}

Response 200: { "hold_id": "hold_abc", "expires_at": "...", "total_price": 1000 }
Response 409 Conflict: { "error": "SEAT_UNAVAILABLE", "unavailable_seats": ["A1"] }
```

### 3.4 Confirm Booking (Payment)
```
POST /api/v1/bookings/confirm
{
  "hold_id": "hold_abc",
  "payment_token": "stripe_tok_xyz"
}

Response 200: { "booking_id": "BKG-12345", "status": "confirmed", "confirmation_code": "XYZ789" }
```

### 3.5 Cancel Booking
```
DELETE /api/v1/bookings/{booking_id}
Response: { "refund_amount": 900, "status": "cancelled" }
```

> 🎯 **NFR addressed**: **Consistency** — Hold API returns 409 Conflict immediately if seat is taken. **Latency** — Hold + Confirm separation allows 10-min payment window without blocking other users.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Bookings:**
- 5 million bookings/day, average 2 seats = 10 million seat transactions/day
- = 10M / 86,400 ≈ **~115 bookings/sec** normal
- Peak (concert drops at 10am): **50,000+ concurrent users** in first minute

**Read vs Write:**
- Browsing (highly cacheable): 1000:1 vs booking
- Seat availability checks: 100:1 vs actual booking
- **Most load is reads** — but the writes (booking) must be ACID

**Storage:**
- Events + Seats + Bookings: **< 1 TB total** — PostgreSQL handles this fine

### 4.2 Data Flow Through System

**Seat Booking Flow:**
```
User browses events → Event Service (cached in Redis/CDN)
  → User selects showtime → Seat Service (Redis cached seat map)
  → User picks seats A1, A3 → POST /hold
    → Booking Service → PostgreSQL:
      BEGIN TRANSACTION;
      SELECT ... FOR UPDATE (lock seat rows)
      IF available → UPDATE status='held', set hold_expires
      COMMIT;
    → Return hold_id + 10-min timer
  → User enters payment → POST /confirm
    → Booking Service → Payment Service (Stripe)
    → Success → UPDATE seats status='booked', CREATE booking record
    → Send confirmation notification
```

**Hold Expiry Flow:**
```
Background Worker (every 30 seconds):
  → Scan for holds WHERE hold_expires < NOW() AND status='active'
  → Release seats: UPDATE status='available'
  → Mark holds as 'expired'
```

> 🎯 **NFR addressed**: **Consistency** — transaction + row lock guarantees no double-booking. **Concurrency** — locked seats are immediately visible as 'held' to other users. **Scale** — reads are 1000:1 cached; only the critical write path hits Postgres.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
               USERS (browsing + booking)
                        │
               ┌────────▼────────┐
               │   API Gateway   │
               │   (Auth, RL)    │
               └────────┬────────┘
                        │
        ┌───────────────┼──────────────────────┐
        │               │                      │
┌───────▼──────┐  ┌─────▼────────┐    ┌────────▼──────────┐
│ Event Service│  │ Seat Service │    │  Booking Service  │
│ (browse,     │  │ (seat map,   │    │  (hold, confirm,  │
│  search)     │  │  availability│    │   cancel)         │
└───────┬──────┘  └─────┬────────┘    └────────┬──────────┘
        │               │                      │
        │          ┌────▼──────────────────┐   │
        │          │  Redis Cache          │   │
        │          │  seat_status per show │   │
        │          └───────────────────────┘   │
        │                                      │
        └──────────────────┬───────────────────┘
                           │
              ┌────────────▼────────────────┐
              │    PostgreSQL (Primary DB)  │
              │    ACID transactions        │
              │    Row-level locking        │
              └────────────┬────────────────┘
                           │
              ┌────────────▼────────────────┐
              │  Read Replicas (Postgres)   │
              └─────────────────────────────┘

              ┌────────────────────────────────────────┐
              │  Payment Service + Hold Expiry Worker  │
              │  + Notification Service                │
              └────────────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Event Service** | Browse/search events (cacheable) | Read-heavy; CDN + Redis cache for < 100ms browsing |
| **Seat Service** | Returns seat map with real-time availability | Redis cache with 10s TTL; invalidated on seat status change |
| **Booking Service** | Hold → Confirm → Cancel flow | Orchestrates the critical path with PostgreSQL transactions |
| **PostgreSQL** | ACID storage for seats, holds, bookings | Row-level locking via `SELECT FOR UPDATE` — the core concurrency control |
| **Redis** | Seat map cache + hold status | Fast reads for browsing; reduces DB load by 100× |
| **Hold Expiry Worker** | Background job releasing expired holds | Simple polling; releases seats back to pool after 10-min timeout |
| **Payment Service** | Stripe/Razorpay integration | Called after hold; idempotency key = hold_id prevents double charges |

> 🎯 **NFR addressed**: **Consistency** — PostgreSQL ACID for booking; no double-booking possible. **Availability 99.99%** — browsing reads from cache/replicas even during booking spikes. **Latency** — seat selection < 500ms (Redis cache hit). **Scale** — 100K concurrent users handled by waiting room + cached reads.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Preventing Double Booking (The Core Problem)

This is the MOST critical part of this interview question.

**Scenario**: 1000 users all try to book seat A1 simultaneously.

**Solution: Database Row-Level Lock (Pessimistic Locking)**
```sql
BEGIN TRANSACTION;

-- Lock the specific seat row (other requests block here)
SELECT * FROM seats
WHERE showtime_id = 123 AND seat_id = 'A1'
FOR UPDATE;    -- ← key: acquires exclusive row lock

-- Check status (we now have exclusive access)
IF status = 'available':
    UPDATE seats
    SET status = 'held', hold_id = hold_abc, hold_expires = NOW() + INTERVAL '10 min'
    WHERE showtime_id = 123 AND seat_id = 'A1';
    
    COMMIT;   -- lock released, other requests can now proceed
    RETURN success
ELSE:
    ROLLBACK;
    RETURN SEAT_UNAVAILABLE
```

**For multiple seats** (user wants A1 AND A2):
```sql
SELECT * FROM seats
WHERE showtime_id = 123 AND seat_id IN ('A1', 'A2')
ORDER BY seat_id    -- IMPORTANT: always lock in same order to prevent deadlock
FOR UPDATE;
```

---

### Deep Dive 2: High-Demand Event Launch (Waiting Room)

**Problem**: Taylor Swift concert goes on sale at 10am. 500,000 users hit the site simultaneously.

**Solution: Virtual Waiting Room / Queue**
```
10:00:00am: Sale opens
  → All 500K users are redirected to waiting room page
  → Each user gets a virtual position number (queue token with timestamp)
  → Queue system (Redis sorted set) maintains order

Entry rate: 
  → System processes 1000 users/minute from queue
  → Users see estimated wait time on waiting room page

Implementation:
  → ZADD waiting_room:event_123 {timestamp} {user_id}
  → Every second: ZPOPMIN waiting_room:event_123 COUNT 20 (admit 20 users)
  → Each admitted user gets a time-limited booking token (JWT, valid 15 min)
  → Only users with valid booking token can call hold/book APIs
```

---

### Deep Dive 3: Seat Hold Expiry

- User selects seats → 10-minute hold
- User abandons (doesn't complete payment) → seats must be released

**Background Worker (runs every 30 seconds):**
```sql
UPDATE seats SET status = 'available', hold_id = NULL, hold_expires = NULL
WHERE status = 'held' AND hold_expires < NOW();

UPDATE holds SET status = 'expired'
WHERE status = 'active' AND expires_at < NOW();
```

---

### Deep Dive 4: Payment Failure Handling

```
1. User holds seats (10 min window)
2. User submits payment → Booking Service calls Payment Service (Stripe)
3a. Payment SUCCESS → UPDATE seats status='booked', CREATE booking
3b. Payment FAILURE → Don't release hold; user can retry within 10 min
    → If 10 min expires: hold cleanup worker releases seats

Idempotency: payment_idempotency_key = hold_id
→ Same hold_id retried → Stripe returns same result, no double charge
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
**CP (Consistency + Partition Tolerance)**
- A seat must NEVER be double-booked — consistency is non-negotiable
- Better to return "seat unavailable" than to book the same seat twice

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Locking | Pessimistic (SELECT FOR UPDATE) | Optimistic (version check) | Pessimistic is simpler and correct for high-contention seat booking |
| Database | PostgreSQL (ACID) | Cassandra | Cassandra doesn't support row-level locking; ACID is essential |
| Seat cache | Redis (10s TTL) | Always read from DB | DB can't handle 100K seat map fetches/sec |
| Peak load | Virtual waiting room | No queue (direct access) | Without waiting room, 500K concurrent users overwhelm the cluster |
| Hold expiry | Background worker | Redis key TTL + sync | Background worker is more reliable than Redis-Postgres sync |

---

### Summary Talk Track

1. "The core problem in ticket booking is **preventing double-booking under high concurrency**."
2. "Core entities: **Event**, **Showtime**, **Seat** (the critical resource), **Hold**, **Booking**."
3. "The solution: **PostgreSQL row-level locks** — `SELECT FOR UPDATE` serializes access to each seat."
4. "Flow: Browse (cached) → Select seats → Hold (DB lock, 10-min TTL) → Pay → Confirm."
5. "Seat availability is cached in Redis (10s TTL) to handle read spikes."
6. "For popular events: **virtual waiting room** — smooth the traffic spike with a queue."
7. "CAP choice: **CP** — consistency over availability. A double booking is unacceptable."

---

> **Previous**: [10 — Design an API Gateway](./10-api-gateway.md)
> **Next**: [12 — Design Uber / Lyft](./12-uber-lyft.md)
