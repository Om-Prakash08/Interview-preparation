# 15. Design Food Delivery App (Swiggy / Zomato / DoorDash)

> **Difficulty**: Hard | **Asked At**: Amazon, Flipkart, Google, Uber Eats
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Search + browse restaurants, view menus?
- Place orders, real-time delivery tracking?
- Multiple items from the same restaurant? Multiple restaurants per order?
- Restaurant onboarding (menus, availability)?
- Driver dispatch (similar to Uber)?
- Payments?
- Ratings and reviews?

**Scale:**
- How many orders per day?
- How many active delivery partners?
- How many restaurant partners?

**Typical Interviewer Answer:**
- Full scope: browse, order, track delivery in real-time
- Single restaurant per order (multi-restaurant adds massive complexity)
- 5 million orders per day
- 500,000 active delivery partners
- 100,000 restaurant partners
- Peak: dinner time (7–9pm), 3× normal volume

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Customer searches for restaurants near their location
2. Customer views restaurant menu and adds items to cart
3. Customer places order (restaurant receives it)
4. Restaurant confirms and prepares order
5. System dispatches delivery partner to restaurant
6. Customer tracks delivery partner in real-time
7. Order delivered, payment processed, ratings collected

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Order placement** | < 2 seconds (ACID transaction) |
| **Delivery tracking** | Real-time, updated every 5 seconds |
| **Restaurant search** | < 200ms |
| **Availability** | 99.99% for order placement; 99.9% for tracking |
| **Order consistency** | Never lose an order; no duplicate orders |

### Out of Scope
- Restaurant kitchen management (KMS - kitchen management system)
- Inventory management
- Multi-restaurant orders / cloud kitchens (mention as extension)

---

## SECTION 3 — Capacity Estimation

### Orders
- 5M orders/day
- = 5M / 86,400 ≈ **~58 orders/sec** average
- Peak (7–9pm): **~175 orders/sec**
- Average order: 3 items, total ₹350

### Location Updates (Delivery Partners)
- 500K delivery partners
- Active at peak: 30% = 150K partners
- Location update every 5 seconds = 150K / 5 = **30,000 location writes/sec**

### Read vs Write
- Restaurant searches: heavily read-heavy (1000:1 vs orders)
- Menu views: ~10× more than orders
- Order processing: write-heavy during 7–9pm peak

### Storage
- Order record: 2 KB (items, addresses, timestamps)
- 5M/day × 365 × 2 KB = **~3.6 TB/year** (manageable)
- Restaurant menus: 100K restaurants × 50 items × 500 bytes = **~2.5 GB** (tiny, fully cacheable)

---

## SECTION 4 — API Design

### 1. Search Restaurants
```
GET /api/v1/restaurants/search?lat=12.97&lng=77.59&radius=5&cuisine=indian&limit=20
Response: {
  "restaurants": [
    {
      "restaurant_id": "r1",
      "name": "Biryani House",
      "cuisine": ["Indian", "Biryani"],
      "rating": 4.5,
      "delivery_time_min": 35,
      "min_order_value": 150,
      "is_open": true,
      "distance_km": 1.2
    }
  ]
}
```

### 2. Get Restaurant Menu
```
GET /api/v1/restaurants/{restaurant_id}/menu
Response: {
  "restaurant_id": "r1",
  "categories": [
    { "name": "Biryani", "items": [
      { "item_id": "i1", "name": "Chicken Biryani", "price": 199, "is_available": true }
    ]}
  ]
}
```

### 3. Place Order
```
POST /api/v1/orders
Authorization: Bearer <token>
{
  "restaurant_id": "r1",
  "items": [ { "item_id": "i1", "quantity": 2 }, { "item_id": "i5", "quantity": 1 } ],
  "delivery_address": { "lat": 12.97, "lng": 77.59, "formatted": "123 MG Road, Bangalore" },
  "payment_method": "card",
  "payment_token": "stripe_tok_xyz"
}

Response 201:
{
  "order_id": "ORD-12345",
  "status": "confirmed",
  "estimated_delivery_time": "2025-07-26T20:15:00Z",
  "tracking_url": "https://swiggy.com/track/ORD-12345"
}
```

### 4. Track Order
```
GET /api/v1/orders/{order_id}/track
Response: {
  "order_id": "ORD-12345",
  "status": "out_for_delivery",
  "delivery_partner": { "name": "Ravi K.", "phone": "+91-9876543210" },
  "partner_location": { "lat": 12.975, "lng": 77.595 },
  "eta_minutes": 8
}
```

### 5. Restaurant confirms order (Restaurant App API)
```
PUT /api/v1/orders/{order_id}/status
Authorization: Bearer <restaurant_token>
{ "status": "preparing", "estimated_ready_minutes": 20 }
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `restaurants`
```
restaurant_id   BIGINT       PRIMARY KEY
name            VARCHAR(200)
owner_user_id   BIGINT
lat             DOUBLE
lng             DOUBLE
geohash         VARCHAR(10)  -- for geo queries
cuisines        TEXT[]
rating          FLOAT
is_open         BOOLEAN
min_order_value DECIMAL
avg_prep_time_min INT
```
**DB**: PostgreSQL + PostGIS for geospatial queries

### Table 2: `menu_items`
```
item_id         BIGINT       PRIMARY KEY
restaurant_id   BIGINT
category        VARCHAR(100)
name            VARCHAR(200)
price           DECIMAL
is_available    BOOLEAN
description     TEXT
```
**DB**: PostgreSQL (read via cache)

### Table 3: `orders` (Critical ACID table)
```
order_id        BIGINT       PRIMARY KEY (Snowflake)
customer_id     BIGINT
restaurant_id   BIGINT
delivery_partner_id BIGINT  NULL (assigned after dispatch)
status          ENUM('pending', 'confirmed', 'preparing', 'ready', 'picked_up', 'out_for_delivery', 'delivered', 'cancelled')
items           JSONB        -- snapshot of items + prices at order time
delivery_address JSONB
total_price     DECIMAL
platform_fee    DECIMAL
payment_status  ENUM('pending', 'paid', 'refunded')
payment_ref     VARCHAR
estimated_delivery TIMESTAMP
created_at      TIMESTAMP
confirmed_at    TIMESTAMP
picked_up_at    TIMESTAMP
delivered_at    TIMESTAMP
```
**DB**: **PostgreSQL** (ACID, never lose an order)

### Table 4: `delivery_partner_locations` (Real-time)
```
Not in PostgreSQL. Stored in Redis Geo (same as Uber):
GEOADD partners:available:bangalore 77.59 12.97 partner_id
```

### Table 5: `ratings`
```
rating_id       BIGINT       PRIMARY KEY
order_id        BIGINT       UNIQUE
customer_id     BIGINT
restaurant_id   BIGINT
partner_id      BIGINT
food_rating     INT          (1-5)
delivery_rating INT          (1-5)
comment         TEXT
```
**DB**: PostgreSQL (append-only, moderate volume)

---

## SECTION 6 — High-Level Architecture

```
                    ┌────────────────────────────────────────┐
                    │      CUSTOMER APP / RESTAURANT APP     │
                    └──────────────────┬─────────────────────┘
                                       │
                               ┌───────▼────────┐
                               │   API Gateway  │
                               │  (Auth + RL)   │
                               └───────┬────────┘
                                       │
     ┌─────────────────────────────────┼──────────────────────────────────┐
     │                                 │                                  │
┌────▼───────────┐    ┌────────────────▼──────┐           ┌──────────────▼──────┐
│ Search Service │    │   Order Service       │           │ Delivery Service    │
│ (restaurant    │    │   (place, track,      │           │ (dispatch, track    │
│  discovery)    │    │    manage orders)     │           │  delivery partner)  │
└────┬───────────┘    └───────────┬───────────┘           └──────────────┬──────┘
     │                           │                                       │
     │                           │                                       │
┌────▼──────────────┐    ┌───────▼────────┐               ┌─────────────▼──────┐
│ Elasticsearch     │    │  PostgreSQL    │               │ Redis Geo Index     │
│ Restaurant index  │    │  Orders        │               │ partners:available  │
│ Geospatial +      │    │  (ACID)        │               │ (partner locations) │
│ Full-text search  │    └───────┬────────┘               └────────────────────┘
└───────────────────┘            │
                                 │
                         ┌───────▼────────┐
                         │  Kafka         │
                         │  order_events  │
                         └───────┬────────┘
                                 │
         ┌───────────────────────┼─────────────────────────────┐
         │                       │                             │
┌────────▼──────┐    ┌───────────▼──────┐         ┌───────────▼─────────┐
│ Restaurant    │    │  Dispatch         │         │ Notification Svc    │
│ Notification  │    │  Service          │         │ (customer SMS/push  │
│ (push to      │    │  (find nearest    │         │  order updates)     │
│  Restaurant   │    │  partner, assign) │         └─────────────────────┘
│  App)         │    └──────────────────┘
└───────────────┘

┌────────────────────────────────────────────────────────┐
│ Real-time Tracking (WebSocket / SSE)                   │
│ Delivery partner location → Kafka → Redis Pub/Sub      │
│ → Customer app WebSocket (live map update every 5s)    │
└────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────┐
│ Menu Cache (Redis)                                      │
│ All restaurant menus cached (1 hour TTL)               │
│ 100K restaurants × 2.5 KB = 250 MB (tiny)             │
│ Restaurant updates menu → invalidate cache             │
└────────────────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Order State Machine

This is crucial for food delivery. The order goes through a strict sequence:

```
PENDING → CONFIRMED → PREPARING → READY → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED
                                                                        ↘ FAILED
At any point: → CANCELLED (with rules: can't cancel after PICKED_UP)

Each state transition triggers:
  PENDING → CONFIRMED:
    - Restaurant app notified (push notification)
    - Payment authorized (hold on card, not charged yet)

  CONFIRMED → PREPARING:
    - Restaurant taps "Start Cooking"
    - Dispatch Service notified to find delivery partner

  PREPARING → READY:
    - Restaurant taps "Ready for Pickup"
    - Delivery partner gets notification to head to restaurant
    - Payment captured (charged to customer)

  READY → PICKED_UP:
    - Driver taps "Picked Up"
    - Customer gets "Your food is on the way!" notification

  PICKED_UP → DELIVERED:
    - Driver taps "Delivered"
    - Rating prompt shown to customer
    - Driver payout initiated

State stored in PostgreSQL. State transitions are atomic DB updates with validation.
```

---

### Deep Dive 2: Restaurant Search (Geo + Relevance)

**What customer needs**: restaurants within 5km radius, serving their cuisine preference, currently open, ranked by relevance.

**Solution: Elasticsearch (ES)**
```
Index: restaurants
Document: {
  restaurant_id, name, cuisines, rating, is_open,
  avg_delivery_time, min_order,
  location: { "lat": 12.97, "lon": 77.59 }   // ES geo_point
}

Query:
{
  "query": {
    "bool": {
      "filter": [
        { "geo_distance": { "distance": "5km", "location": { "lat": 12.97, "lon": 77.59 } } },
        { "term": { "is_open": true } }
      ],
      "should": [
        { "match": { "cuisines": "biryani" } },       // text relevance
        { "term": { "cuisines": "indian" } }
      ]
    }
  },
  "sort": [
    "_score",                    // text relevance first
    { "rating": { "order": "desc" } },
    "_geo_distance"              // then by proximity
  ]
}
```

**Cache**: Top searches (city + cuisine combinations) cached in Redis with 5-min TTL.

---

### Deep Dive 3: Delivery Dispatch (Finding Nearest Partner)

```
Trigger: Order status → PREPARING (restaurant started cooking)

Dispatch Service:
  1. Get restaurant location (lat, lng)
  2. GEORADIUS partners:available:{city} {restaurant_lat} {restaurant_lng} 3km COUNT 10 ASC
     → Returns 10 nearest available delivery partners within 3km

  3. Filter: check if partner is not already at max orders (≤1 order at a time for MVP)

  4. Calculate ETA to restaurant for each candidate:
     → Google Maps Distance Matrix API (batch ETA for all candidates)

  5. Rank: partner with lowest ETA to restaurant wins

  6. Send ride offer to partner app (push notification)
     → Partner has 30 seconds to accept
     → If rejected: try next candidate

  7. On acceptance:
     → GEOREM partners:available:{city} {partner_id}  (mark as unavailable)
     → UPDATE orders SET delivery_partner_id = {id} WHERE order_id = ?

  8. If no partner found within 3km: expand radius to 6km, retry
```

---

### Deep Dive 4: ETA Estimation

ETA is critical for customer trust. A bad ETA = a bad experience.

**Two-part ETA:**
```
Total ETA = Preparation Time + Pickup Time + Delivery Time

Preparation Time:
  - Restaurant provides avg_prep_time_min (stored in DB)
  - ML model adjusts based on: current queue at restaurant, time of day, item count
  - Updated in real-time as order flows through system

Pickup Time:
  - Google Maps API: time for partner to travel from current position → restaurant
  - Updated live as partner moves

Delivery Time:
  - Google Maps API: time from restaurant → customer address
  - Adjusted for current traffic conditions

ETA updates pushed to customer via WebSocket every 2 minutes or on significant change (>5 min).
```

---

### Deep Dive 5: Handling Order Failures & Refunds

**Scenario 1: Restaurant cancels order** (out of stock)
```
ORDER: CONFIRMED → CANCELLED
  → Payment authorization voided (not charged)
  → Customer notified + refund issued
  → System attempts to find alternative restaurant (optional)
```

**Scenario 2: Delivery partner can't deliver** (accident, breakdown)
```
ORDER: OUT_FOR_DELIVERY → ASSIGNED NEW PARTNER
  → Dispatch Service assigns new partner
  → Customer notified: "Your delivery partner changed, slight delay"
  → ETA recalculated
```

**Scenario 3: Customer claims non-delivery** (driver marked delivered but food not received)
```
Cross-check:
  - Was partner's GPS near delivery address at time of marking delivered?
  - Did customer's phone GPS show them at home?
  - Photo proof of delivery (optional feature)
→ If fraud suspected: flag for manual review
→ Default: trust customer, issue refund, flag partner
```

**Payment rollback pattern:**
```
Order placement uses Saga Pattern (compensating transactions):
  1. Place order (PostgreSQL)
  2. Authorize payment (Stripe)
  3. Notify restaurant (Kafka)

If step 2 fails: undo step 1 (cancel order in DB)
If step 3 fails: void payment, cancel order
Each step has a defined compensating action.
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
- **CP for orders**: Never lose an order. PostgreSQL with ACID guarantees.
- **AP for restaurant search**: Slightly stale Elasticsearch index is acceptable.
- **AP for delivery tracking**: Eventual consistency in partner location (5s lag is fine).

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Restaurant search | Elasticsearch | PostgreSQL PostGIS + full-text | ES handles combined geo + text relevance search better; PostGIS is more accurate but harder to rank |
| Order DB | PostgreSQL | Cassandra | Orders are financial records requiring ACID; Cassandra's eventual consistency is unsuitable |
| Real-time tracking | Kafka + Redis Pub/Sub + WebSocket | Polling | WebSocket gives < 1s lag; polling every 5s feels janky |
| Partner dispatch | Geo-proximity + ETA ranking | Pure proximity | ETA considers traffic; pure proximity can assign a far-but-fast partner over a close-but-stuck one |
| Order failure handling | Saga Pattern | 2PC (Two-Phase Commit) | 2PC is slow and not suitable for microservices; Saga with compensating transactions is the modern approach |

### What Would You Do Differently at Larger Scale?
- **Slot-based delivery windows**: for grocery delivery, offer 30-min slots instead of live tracking
- **Route optimization**: assign multiple consecutive deliveries to same partner (DoorDash's approach)
- **Kitchen queue management**: predict restaurant overload, delay dispatch, auto-expand delivery radius
- **Dark stores**: micro-warehouses for 10-min delivery of groceries/essentials

---

## Interview Flow Summary (Talk Track)

1. "Food delivery has 3 distinct systems: **restaurant discovery**, **order management (ACID)**, and **delivery dispatch + tracking** (same as Uber)"
2. "Restaurant search uses **Elasticsearch** for geo + text relevance — cached heavily for popular city+cuisine combos"
3. "Order placement is a **Saga pattern**: place order → authorize payment → notify restaurant (each step has compensation)"
4. "Order state machine: PENDING → CONFIRMED → PREPARING → READY → PICKED_UP → DELIVERED"
5. "Dispatch: **Redis Geo + ETA ranking** — find nearest available partner, send offer, retry if rejected"
6. "Tracking: **Kafka → Redis Pub/Sub → WebSocket** — customer sees partner move every 5 seconds"
7. "CAP: CP for orders (ACID PostgreSQL), AP for search and tracking"

---

> **Previous**: [14 — Design Key-Value Store](./14-key-value-store.md)
> **Next**: [16 — Design Payment System](./16-payment-system.md)
