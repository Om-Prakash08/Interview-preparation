# 15. Design Food Delivery App (Swiggy / Zomato / DoorDash)

> **Difficulty**: Hard | **Asked At**: Amazon, Flipkart, Google, Uber Eats
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Search + browse restaurants, view menus?
- Place orders, real-time delivery tracking?
- Multiple items from the same restaurant? Multiple restaurants per order?
- Restaurant onboarding (menus, availability)?
- Driver dispatch (similar to Uber)?
- Payments? Ratings and reviews?

**Scale:**
- How many orders per day?
- How many active delivery partners?
- How many restaurant partners?

**Typical Interviewer Answer:**
- Full scope: browse, order, track delivery in real-time
- Single restaurant per order
- 5 million orders per day
- 500,000 active delivery partners, 100,000 restaurant partners
- Peak: dinner time (7–9pm), 3× normal volume

### 1.2 Functional Requirements (FR)
1. Customer searches for restaurants near their location
2. Customer views restaurant menu and adds items to cart
3. Customer places order (restaurant receives it)
4. Restaurant confirms and prepares order
5. System dispatches delivery partner to restaurant
6. Customer tracks delivery partner in real-time
7. Order delivered, payment processed, ratings collected

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Order placement** | < 2 seconds (ACID transaction) |
| **Delivery tracking** | Real-time, updated every 5 seconds |
| **Restaurant search** | < 200ms |
| **Availability** | 99.99% for order placement |
| **Order consistency** | Never lose an order; no duplicate orders |

### 1.4 Out of Scope
- Kitchen management system (KMS)
- Inventory management
- Multi-restaurant orders / cloud kitchens

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│  Restaurant  │   │  MenuItem    │   │    Order     │   │  Delivery    │
│              │   │              │   │  (critical)  │   │  Partner     │
│ restaurant_id│──►│ item_id      │   │ order_id     │   │ partner_id   │
│ name         │   │ restaurant_id│   │ customer_id  │   │ is_available │
│ lat, lng     │   │ name, price  │   │ restaurant_id│   │ current_lat  │
│ cuisines[]   │   │ is_available │   │ partner_id   │   │ current_lng  │
│ rating       │   │ category     │   │ status       │   │ rating       │
│ is_open      │   └──────────────┘   │ items (JSONB)│   └──────────────┘
└──────────────┘                      │ total_price  │
                                      │ payment_ref  │
                                      └──────────────┘
```

**Primary entities**: `Restaurant` (discovery), `MenuItem` (catalog), `Order` (critical ACID state machine), `DeliveryPartner` (real-time location tracking, like Uber drivers).

### 2.2 Data Model / Schema

**Table 1: `orders` (Critical ACID table)** — PostgreSQL
```
order_id, customer_id, restaurant_id, delivery_partner_id NULL,
status ENUM('pending','confirmed','preparing','ready','picked_up','out_for_delivery','delivered','cancelled'),
items JSONB, delivery_address JSONB, total_price DECIMAL,
payment_status, payment_ref, created_at, delivered_at
```

**Table 2: `restaurants`** — PostgreSQL + PostGIS (geo queries)

**Table 3: `menu_items`** — PostgreSQL (read via Redis cache, 250 MB total)

**Table 4: `delivery_partner_locations`** — Redis Geo (same as Uber)

> 🎯 **NFR addressed**: **Order consistency** — PostgreSQL ACID for orders. **Restaurant search < 200ms** — Elasticsearch for geo+text; Redis cache. **Delivery tracking** — Redis Geo for real-time partner positions.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Search Restaurants
```
GET /api/v1/restaurants/search?lat=12.97&lng=77.59&radius=5&cuisine=indian
Response: { "restaurants": [ { restaurant_object } ] }
```

### 3.2 Get Menu
```
GET /api/v1/restaurants/{restaurant_id}/menu
Response: { "categories": [ { "name": "Biryani", "items": [ ... ] } ] }
```

### 3.3 Place Order
```
POST /api/v1/orders
{ "restaurant_id": "r1", "items": [...], "delivery_address": {...}, "payment_token": "..." }
Response 201: { "order_id": "ORD-12345", "status": "confirmed", "estimated_delivery_time": "..." }
```

### 3.4 Track Order
```
GET /api/v1/orders/{order_id}/track
Response: { "status": "out_for_delivery", "partner_location": {...}, "eta_minutes": 8 }
```

> 🎯 **NFR addressed**: **Order placement < 2s** — synchronous ACID write with payment authorization. **Availability** — restaurant search heavily cached.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Orders:** 5M/day = **~58 orders/sec** avg; peak: **~175/sec**

**Location Updates:** 150K active partners × update/5s = **30,000 writes/sec**

**Storage:** Orders ~3.6 TB/year; menus ~2.5 GB total (fully cacheable)

### 4.2 Data Flow Through System

**Order Flow (Saga Pattern):**
```
Customer places order → Order Service (PostgreSQL ACID)
  → Authorize payment (Stripe hold)
  → Notify restaurant (Kafka → push notification)
  → Restaurant confirms → status: PREPARING
  → Dispatch Service triggered → find nearest delivery partner (Redis Geo)
  → Partner accepts → status: PICKED_UP
  → Real-time tracking: Kafka → Redis Pub/Sub → WebSocket to customer
  → Partner delivers → status: DELIVERED → payment captured
```

> 🎯 **NFR addressed**: **Order consistency** — Saga pattern with compensating transactions. **Delivery tracking** — Kafka → WebSocket pipeline.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                    ┌────────────────────────────────────────┐
                    │  CUSTOMER APP / RESTAURANT APP          │
                    └──────────────────┬─────────────────────┘
                                       │
                               ┌───────▼────────┐
                               │   API Gateway  │
                               └───────┬────────┘
                                       │
     ┌─────────────────────────────────┼──────────────────────────────────┐
     │                                 │                                  │
┌────▼───────────┐    ┌────────────────▼──────┐           ┌──────────────▼──────┐
│ Search Service │    │   Order Service       │           │ Delivery Service    │
│ (Elasticsearch)│    │   (PostgreSQL ACID)   │           │ (Redis Geo + Kafka) │
└────────────────┘    └───────────┬───────────┘           └─────────────────────┘
                                  │
                          ┌───────▼────────┐
                          │  Kafka         │
                          │  order_events  │
                          └───────┬────────┘
                                  │
         ┌────────────────────────┼──────────────────────┐
         │                        │                      │
  Restaurant Notif          Dispatch Service       Customer Notif
  (push to app)        (find nearest partner)     (SMS/push updates)

  Real-time Tracking: partner location → Kafka → Redis Pub/Sub → WebSocket
  Menu Cache: Redis (250 MB, 1-hour TTL)
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Search Service** | Restaurant discovery (geo + text + rating) | Elasticsearch for combined relevance scoring |
| **Order Service** | Order lifecycle with ACID guarantees | PostgreSQL; Saga pattern for distributed workflow |
| **Delivery Service** | Partner dispatch + real-time tracking | Redis Geo (same as Uber); WebSocket for live map |
| **Kafka** | Event bus for order state changes | Decouples order service from notification/dispatch |
| **Menu Cache** | Redis-cached restaurant menus | 2.5 GB total; eliminates DB reads for 99% of menu views |

> 🎯 **NFR addressed**: **Order placement < 2s** — ACID + async Kafka for downstream. **Restaurant search < 200ms** — Elasticsearch + Redis cache. **Delivery tracking** — WebSocket with 5s updates. **Availability 99.99%** — each service independently scalable.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Order State Machine

```
PENDING → CONFIRMED → PREPARING → READY → PICKED_UP → OUT_FOR_DELIVERY → DELIVERED
At any point: → CANCELLED (can't cancel after PICKED_UP)

Each transition triggers downstream actions:
  CONFIRMED: Restaurant notified, payment authorized
  PREPARING: Dispatch Service finds delivery partner
  READY: Partner notified to head to restaurant; payment captured
  PICKED_UP: Customer gets "food is on the way!" notification
  DELIVERED: Rating prompt; driver payout initiated
```

---

### Deep Dive 2: Restaurant Search (Elasticsearch)

```
Elasticsearch query combining:
  - geo_distance filter (within 5km)
  - is_open filter
  - cuisine text match (relevance scoring)
  - Sort by: _score + rating + proximity

Cache: top city+cuisine combinations in Redis (5-min TTL)
```

---

### Deep Dive 3: Delivery Dispatch

```
Trigger: Order status → PREPARING
1. GEORADIUS partners:available:{city} {restaurant_lat} {restaurant_lng} 3km
2. Filter: partner not at max orders
3. Rank by ETA to restaurant (Google Maps API)
4. Send offer → 30s to accept → if rejected, try next
5. On accept: mark unavailable, assign to order
6. If no partner in 3km → expand to 6km
```

---

### Deep Dive 4: ETA Estimation

```
Total ETA = Preparation Time + Pickup Time + Delivery Time
  - Prep time: ML model based on restaurant queue, time of day, item count
  - Pickup: Google Maps (partner → restaurant)
  - Delivery: Google Maps (restaurant → customer)
  - Updated live via WebSocket every 2 minutes
```

---

### Deep Dive 5: Order Failure Handling (Saga Pattern)

```
Saga: Place order → Authorize payment → Notify restaurant
  If payment fails: cancel order (compensating transaction)
  If restaurant rejects: void payment, cancel order

Restaurant cancels (out of stock): void auth, refund, notify customer
Partner can't deliver: assign new partner, recalculate ETA
Customer claims non-delivery: GPS cross-check, default trust customer
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
- **CP for orders** — PostgreSQL ACID; never lose an order
- **AP for search** — slightly stale Elasticsearch index is acceptable
- **AP for tracking** — eventual consistency in partner location (5s lag fine)

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Restaurant search | Elasticsearch | PostGIS + full-text | ES handles combined geo + text relevance better |
| Order DB | PostgreSQL | Cassandra | Orders need ACID; Cassandra's eventual consistency unsuitable |
| Real-time tracking | Kafka + WebSocket | Polling | WebSocket < 1s lag; polling feels janky |
| Partner dispatch | Geo + ETA ranking | Pure proximity | ETA considers traffic conditions |
| Order failure | Saga Pattern | 2PC | Saga is modern, microservice-friendly |

---

### Summary Talk Track

1. "Food delivery has 3 distinct systems: **restaurant discovery**, **order management (ACID)**, and **delivery dispatch + tracking**."
2. "Core entities: **Restaurant**, **MenuItem**, **Order** (critical state machine), **DeliveryPartner** (real-time location)."
3. "Restaurant search: **Elasticsearch** for geo + text relevance — cached heavily."
4. "Order placement: **Saga pattern** — place → authorize payment → notify restaurant (each step has compensation)."
5. "Order state machine: PENDING → CONFIRMED → PREPARING → READY → PICKED_UP → DELIVERED."
6. "Dispatch: **Redis Geo + ETA ranking** — find nearest partner, send offer, retry if rejected."
7. "Tracking: **Kafka → Redis Pub/Sub → WebSocket** — customer sees partner every 5 seconds."

---

> **Previous**: [14 — Design Key-Value Store](./14-key-value-store.md)
> **Next**: [16 — Design Payment System](./16-payment-system.md)
