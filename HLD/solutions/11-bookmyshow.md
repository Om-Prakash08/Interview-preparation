# 11. Design BookMyShow / Ticketmaster

> **Difficulty**: Hard | **Asked At**: Amazon, Google, Uber, Flipkart
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

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

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Browse events (movies, concerts) by city, date, category
2. View available seats for an event + showtime
3. Select and temporarily hold seats (10-minute lock)
4. Book and pay for seats (complete the transaction)
5. Receive booking confirmation
6. Cancel booking (within policy window)

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Concurrency** | Handle thousands of users trying to book same seats simultaneously |
| **Consistency** | A seat must NEVER be double-booked — strong consistency required |
| **Availability** | 99.99% for browsing; consistency > availability for booking |
| **Latency** | Seat selection < 500ms; payment < 3s |
| **Scale** | 5 million bookings/day; 100K concurrent users at peak launch |

### Out of Scope
- Recommendation engine
- Food ordering at cinema
- Seat pricing/surge (mention as extension)

---

## SECTION 3 — Capacity Estimation

### Bookings
- 5 million bookings/day
- Average 2 seats per booking = 10 million seat transactions/day
- = 10M / 86,400 ≈ **~115 bookings/sec** normal
- Peak (concert drops at 10am): **50,000+ concurrent users** in first minute

### Read vs Write
- Browsing (highly cacheable): 1000:1 vs booking
- Seat availability checks: 100:1 vs actual booking
- **Most load is reads** — but the writes (booking) must be ACID

### Storage
- Events: 1 million events × 1 KB = 1 GB
- Seats: 1 million events × avg 500 seats × 100 bytes = **50 GB**
- Bookings: 5M/day × 365 × 500 bytes = **~900 GB/year**
- Total: small enough for PostgreSQL to handle

---

## SECTION 4 — API Design

### 1. Search Events
```
GET /api/v1/events?city=Mumbai&date=2025-08-01&category=movie
Response: { "events": [ { "event_id", "name", "venue", "showtimes": [...] } ] }
```

### 2. Get Seat Map
```
GET /api/v1/events/{event_id}/showtimes/{showtime_id}/seats
Response: {
  "showtime_id": "st_123",
  "seats": [
    { "seat_id": "A1", "row": "A", "number": 1, "category": "premium", "price": 500, "status": "available" },
    { "seat_id": "A2", "row": "A", "number": 2, "category": "premium", "price": 500, "status": "held" },
    ...
  ]
}
```

### 3. Hold Seats (Temporary Lock)
```
POST /api/v1/bookings/hold
Authorization: Bearer <token>
{
  "showtime_id": "st_123",
  "seat_ids": ["A1", "A3"]
}

Response 200:
{
  "hold_id": "hold_abc",
  "seats": ["A1", "A3"],
  "expires_at": "2025-08-01T11:10:00Z",   // 10 minutes
  "total_price": 1000
}

Response 409 Conflict:
{
  "error": "SEAT_UNAVAILABLE",
  "unavailable_seats": ["A1"]
}
```

### 4. Confirm Booking (Payment)
```
POST /api/v1/bookings/confirm
{
  "hold_id": "hold_abc",
  "payment_token": "stripe_tok_xyz"
}

Response 200:
{
  "booking_id": "BKG-12345",
  "status": "confirmed",
  "seats": ["A1", "A3"],
  "total_paid": 1000,
  "confirmation_code": "XYZ789"
}
```

### 5. Cancel Booking
```
DELETE /api/v1/bookings/{booking_id}
Response: { "refund_amount": 900, "status": "cancelled" }
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `events`
```
event_id      BIGINT       PRIMARY KEY
name          VARCHAR(200)
venue_id      BIGINT
city          VARCHAR(100)
category      ENUM('movie', 'concert', 'sports')
description   TEXT
```

### Table 2: `showtimes`
```
showtime_id   BIGINT       PRIMARY KEY
event_id      BIGINT
start_time    TIMESTAMP
total_seats   INT
available_seats INT
```

### Table 3: `seats` (The critical table)
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
**DB Choice**: **PostgreSQL** — we need ACID transactions for seat booking.
Row-level locking is critical to prevent double-booking.

### Table 4: `holds`
```
hold_id       BIGINT       PRIMARY KEY
user_id       BIGINT
showtime_id   BIGINT
seat_ids      TEXT[]
expires_at    TIMESTAMP
status        ENUM('active', 'expired', 'converted')
created_at    TIMESTAMP
```

### Table 5: `bookings`
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

---

## SECTION 6 — High-Level Architecture

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
        │          │  (fast availability   │   │
        │          │   reads)              │   │
        │          └───────────────────────┘   │
        │                                      │
        └──────────────────┬───────────────────┘
                           │
              ┌────────────▼────────────────┐
              │    PostgreSQL (Primary DB)  │
              │    ACID transactions        │
              │    Row-level locking        │
              │    seats, bookings, holds   │
              └────────────┬────────────────┘
                           │
              ┌────────────▼────────────────┐
              │  Read Replicas (Postgres)   │
              │  For browse queries         │
              └─────────────────────────────┘

              ┌────────────────────────────────────────┐
              │        Payment Service                 │
              │  (Stripe/Razorpay integration)         │
              │  Called AFTER hold is confirmed        │
              └────────────────────────────────────────┘

              ┌────────────────────────────────────────┐
              │      Hold Expiry Worker                │
              │  Scans holds expiring in next 60sec    │
              │  Releases expired seats back to pool   │
              └────────────────────────────────────────┘

              ┌────────────────────────────────────────┐
              │    Notification Service                │
              │  Sends booking confirmation email/SMS  │
              └────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Preventing Double Booking (The Core Problem)

This is the MOST critical part of this interview question.

**Scenario**: 1000 users all try to book seat A1 simultaneously.

**Naive approach (wrong):**
```
1. Check if seat A1 is available  ← race condition!
2. Mark as held
3. Charge user

Problem: Two requests pass step 1 simultaneously → both book the same seat!
```

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

**Result**: 
- All concurrent requests are serialized at the DB level
- Only ONE transaction succeeds at a time per seat
- Guaranteed no double-booking

**For multiple seats** (user wants A1 AND A2):
```sql
SELECT * FROM seats
WHERE showtime_id = 123 AND seat_id IN ('A1', 'A2')
ORDER BY seat_id    -- IMPORTANT: always lock in same order to prevent deadlock
FOR UPDATE;
```

---

### Deep Dive 2: Seat Hold Expiry

- User selects seats → 10-minute hold
- User abandons (doesn't complete payment) → seats must be released

**Background Worker (runs every 30 seconds):**
```sql
UPDATE seats
SET status = 'available', hold_id = NULL, hold_expires = NULL
WHERE status = 'held'
AND hold_expires < NOW();

UPDATE holds SET status = 'expired'
WHERE status = 'active' AND expires_at < NOW();
```

**Alternative: Optimistic approach in Redis**
- Hold state stored in Redis with TTL
- Redis automatically expires the key after 10 minutes
- Seat status synced from Redis to Postgres only on booking confirmation
- But: Redis failure loses hold state → users re-select seats (annoying but safe)

---

### Deep Dive 3: High-Demand Event Launch (Waiting Room)

**Problem**: Taylor Swift concert goes on sale at 10am. 500,000 users hit the site simultaneously. Even holding would be chaotic — everyone fights for seats at once.

**Solution: Virtual Waiting Room / Queue**
```
10:00:00am: Sale opens
  → All 500K users are redirected to waiting room page
  → Each user gets a virtual position number (queue token with timestamp)
  → Queue system (Redis sorted set) maintains order

Entry rate: 
  → System processes 1000 users/minute from queue
  → User at position 1000 enters the booking flow at 10:01
  → User at position 10000 enters at 10:10
  → Users see estimated wait time on waiting room page

Benefits:
  - Booking system gets smooth, controlled load (not a spike)
  - First-come-first-served (fair)
  - Users don't need to frantically refresh
```

**Implementation:**
```
On entering waiting room:
  → ZADD waiting_room:event_123 {timestamp} {user_id}

Every second, admission worker:
  → ZPOPMIN waiting_room:event_123 COUNT 20   // admit next 20 users
  → Issue each user a time-limited booking token (JWT, valid 15 min)
  → Only users with valid booking token can call hold/book APIs
```

---

### Deep Dive 4: Seat Map Performance

- 50,000 seats per event × availability status
- At 100,000 concurrent users browsing: 100,000 × seat map fetch/sec

**Strategy:**
1. **Cache full seat map in Redis** per showtime: `seat_map:{showtime_id}` → JSON of all seats + statuses
2. **TTL: 10 seconds** (short enough for freshness, long enough to absorb load)
3. On seat status change (hold, book, release): **invalidate Redis cache** for that showtime
4. 99% of requests → Redis; only cache misses hit Postgres

**Alternative: WebSocket push**
- When a seat is held/booked, push update to all browsers viewing that seat map
- Seat icons update in real-time without polling
- Implementation: Server-Sent Events from a Seat Update Service (subscribes to Kafka events)

---

### Deep Dive 5: Payment Failure Handling

**Flow:**
```
1. User holds seats (10 min window)
2. User submits payment → Booking Service calls Payment Service (Stripe)
3a. Payment SUCCESS:
    - UPDATE seats SET status='booked'
    - CREATE booking record
    - DELETE hold record
    - Publish event → Notification Service (email/SMS confirmation)

3b. Payment FAILURE:
    - Don't release hold immediately (user can retry within 10 min)
    - Return payment error to user
    - User can retry with different card
    - If 10 min expires: hold cleanup worker releases seats automatically
```

**Idempotency** (critical for payments):
- Payment request includes `idempotency_key = hold_id`
- If payment API is called twice (retry after timeout): Stripe detects same key → returns same result, doesn't charge twice

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**CP (Consistency + Partition Tolerance)**
- A seat must NEVER be double-booked — consistency is non-negotiable
- Better to return "seat unavailable" than to book the same seat twice
- PostgreSQL is CP by design — perfect for this use case

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Locking | Pessimistic (SELECT FOR UPDATE) | Optimistic (version check) | Pessimistic is simpler and correct for high-contention seat booking; optimistic causes many retries |
| Database | PostgreSQL (ACID) | Cassandra | Cassandra doesn't support row-level locking; ACID is essential for seat booking |
| Seat cache | Redis (10s TTL) | Always read from DB | DB can't handle 100K seat map fetches/sec; Redis absorbs the read load |
| Peak load | Virtual waiting room | No queue (direct access) | Without waiting room, 500K concurrent users overwhelm even the largest cluster |
| Hold expiry | Background worker | Redis key TTL + sync | Redis TTL is simpler but depends on Redis-Postgres sync; background worker is more reliable |

### What Would You Do Differently at Larger Scale?
- **Dynamic pricing**: surge pricing (higher demand → higher price)
- **Anti-touting**: detect and block bots buying bulk tickets for resale
- **Partial booking**: if user wants 4 seats and only 3 remain, offer alternative shows
- **Waitlisting**: if event is sold out, user can join waitlist (gets notified on cancellation)

---

## Interview Flow Summary (Talk Track)

1. "The core problem in ticket booking is **preventing double-booking under high concurrency**"
2. "The solution: **PostgreSQL row-level locks** — `SELECT FOR UPDATE` serializes access to each seat"
3. "Flow: Browse (cached) → Select seats → Hold (DB lock, 10-min TTL) → Pay → Confirm"
4. "Seat availability is cached in Redis (10s TTL) to handle read spikes"
5. "For popular events: **virtual waiting room** — smooth the traffic spike with a queue"
6. "Hold expiry is handled by a background worker — seats return to pool after 10 minutes"
7. "CAP choice: **CP** — consistency over availability. A double booking is unacceptable."

---

> **Previous**: [10 — Design an API Gateway](./10-api-gateway.md)
> **Next**: [12 — Design Uber / Lyft](./12-uber-lyft.md)
