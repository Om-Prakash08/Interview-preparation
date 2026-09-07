# 12. Design Uber / Lyft

> **Difficulty**: Hard | **Asked At**: Uber, Lyft, Google, Amazon, Meta
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Rider requests ride, driver accepts — basic matching?
- Real-time location tracking of driver?
- Surge pricing?
- Support for multiple ride types (UberX, UberPool, Uber Black)?
- Payment processing?
- Driver ratings/reviews?
- Estimated arrival time (ETA)?

**Scale:**
- How many rides per day?
- How many active drivers at peak?
- Geographic spread?

**Typical Interviewer Answer:**
- Core: rider requests ride, match with nearby driver, real-time tracking, payment
- 10 million rides per day
- 5 million active drivers globally
- Driver location updates every 5 seconds
- ETA calculation required
- Surge pricing: mention as extension

### 1.2 Functional Requirements (FR)
1. Rider requests a ride (with pickup + destination)
2. System finds nearby available drivers
3. System matches rider to closest available driver
4. Both rider and driver see each other's real-time location
5. Driver navigates to rider, then to destination
6. Trip ends, payment processed
7. Ratings exchanged

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Match Latency** | Match rider with driver in < 5 seconds |
| **Location Update** | Drivers update location every 5 seconds |
| **Availability** | 99.99% — rider must be able to request ride |
| **Scalability** | 5M drivers × location update / 5 sec = 1M location writes/sec |
| **Accuracy** | ETA accurate within ±2 minutes |

### 1.4 Out of Scope
- Multiple stops
- Uber Eats / food delivery
- UberPool (ride sharing between multiple riders)

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   Driver     │     │    Ride      │     │   Rider      │
│              │     │              │     │              │
│ driver_id    │◄────│ ride_id      │────►│ rider_id     │
│ name         │     │ rider_id     │     │ name         │
│ is_available │     │ driver_id    │     │ phone        │
│ current_lat  │     │ status       │     │ payment_info │
│ current_lng  │     │ pickup_loc   │     │              │
│ vehicle_id   │     │ dest_loc     │     └──────────────┘
│ rating       │     │ fare         │
└──────────────┘     │ ride_type    │
                     └──────────────┘
```

**Primary entities**: `Driver` (location + availability), `Ride` (the trip lifecycle), `Rider` (the customer). The critical data is **driver location** — 1M writes/sec of geospatial updates.

### 2.2 Data Model / Schema

**Table 1: `rides`**
```
ride_id       BIGINT       PRIMARY KEY (Snowflake)
rider_id      BIGINT
driver_id     BIGINT       NULL (assigned after match)
status        ENUM('searching', 'driver_assigned', 'driver_en_route', 'in_progress', 'completed', 'cancelled')
pickup_lat    DOUBLE
pickup_lng    DOUBLE
dest_lat      DOUBLE
dest_lng      DOUBLE
ride_type     VARCHAR(20)
fare          DECIMAL      NULL (calculated at end)
requested_at  TIMESTAMP
started_at    TIMESTAMP
ended_at      TIMESTAMP
```
**DB Choice**: **PostgreSQL** (ACID, rides are financial records)

**Location Store (Real-time, current position):**
```
Redis Geospatial Index:
  Key: "drivers:available:{city}"
  GEOADD drivers:available:bangalore 77.5946 12.9716 driver_id_1

Query: find drivers within 5km of pickup:
  GEORADIUS drivers:available:bangalore 77.5946 12.9716 5 km COUNT 20 ASC
  → Returns 20 nearest driver IDs within 5km
```

**Location History (analytics + route tracking):**
```
Cassandra: driver_location_history
  driver_id, trip_id, timestamp, lat, lng
  PRIMARY KEY (trip_id, timestamp)
```

> 🎯 **NFR addressed**: **Match Latency < 5s** — Redis GEORADIUS is O(N+log(M)), sub-ms for nearby driver lookup. **Scalability 1M writes/sec** — Redis Geo handles high-throughput location updates. **Availability** — PostgreSQL for durable ride records; Redis for ephemeral location data.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Rider Requests a Ride
```
POST /api/v1/rides/request
Authorization: Bearer <rider_token>
{
  "pickup_lat": 12.9716, "pickup_lng": 77.5946,
  "destination_lat": 12.9350, "destination_lng": 77.6140,
  "ride_type": "UberX"
}
Response 200: { "ride_id": "ride_abc123", "status": "searching", "estimated_pickup_minutes": 4 }
```

### 3.2 Driver Updates Location (Periodic)
```
POST /api/v1/drivers/location
Authorization: Bearer <driver_token>
{ "lat": 12.9716, "lng": 77.5946, "heading": 270, "speed_kmh": 40 }
→ 200 OK (fire and forget, lightweight)
```

### 3.3 Get Ride Status (Polling or WebSocket)
```
GET /api/v1/rides/{ride_id}/status
Response: {
  "status": "driver_en_route",
  "driver": { "name": "Ravi K.", "rating": 4.8, "car": "Honda City - KA03 AB1234" },
  "driver_location": { "lat": 12.9730, "lng": 77.5960 },
  "eta_minutes": 3
}
```

### 3.4 Start / End Trip + Driver Availability
```
POST /api/v1/rides/{ride_id}/start
POST /api/v1/rides/{ride_id}/end     → triggers fare calculation + payment
PUT  /api/v1/drivers/availability    { "available": true | false }
```

> 🎯 **NFR addressed**: **Location Update** — lightweight fire-and-forget POST every 5 seconds. **Match Latency** — WebSocket for real-time ride status updates to rider.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Location Updates:**
- 5 million active drivers × update every 5 seconds = **1 million location writes/sec**

**Ride Requests:**
- 10M rides/day = **~115 ride requests/sec**

**Active Trips:**
- Average trip: 20 min → ~138,000 active trips simultaneously

**Storage:**
- Location update: 100 bytes → 1M/sec × 100B = **100 MB/sec** raw location data
- We only care about **current** location per driver (not history) for matching
- Trip history: 10M/day × 1 KB = **10 GB/day**

### 4.2 Data Flow Through System

**Ride Request Flow:**
```
Rider taps "Request Ride"
  → Ride Service creates ride record (status=searching)
  → Matching Service:
    1. GEORADIUS query → find 50 nearest available drivers within 5km
    2. Filter by ride_type (vehicle match)
    3. Rank by distance + rating + acceptance_rate
    4. Send ride offer to top driver via push notification
    5. Driver has 15s to accept
    6. If accepted → match made, status=driver_assigned
    7. If rejected → try next driver
```

**Location Update Flow:**
```
Driver App → Location Service (lightweight endpoint)
  → Kafka: driver_locations topic
  → Consumer A (hot path): Redis GEOADD (current position)
  → Consumer B (cold path): Cassandra (trip history, batched)
```

> 🎯 **NFR addressed**: **Scalability** — Kafka buffers 1M/sec location writes; Redis and Cassandra consume independently. **Match Latency** — GEORADIUS sub-ms lookup + rank + push = < 5s total.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
RIDER APP                             DRIVER APP
   │                                      │
   │ POST /rides/request                  │ POST /drivers/location (every 5s)
   │                                      │
   └──────────────────┬───────────────────┘
                      │
              ┌───────▼────────┐
              │  API Gateway   │
              └───────┬────────┘
                      │
     ┌────────────────┼──────────────────────────┐
     │                │                          │
┌────▼────────┐ ┌─────▼──────────┐    ┌──────────▼──────────┐
│ Ride Service│ │Location Service│    │  Matching Service   │
│ (manages    │ │(receives driver│    │  (finds nearby      │
│ ride state  │ │ location pings)│    │  available drivers) │
└────┬────────┘ └─────┬──────────┘    └──────────┬──────────┘
     │               │                           │
     │    ┌──────────▼──────────────────┐        │
     │    │    Redis Geo Index          │◄───────┘
     │    │    "drivers:available:{city}"│
     │    │    GEORADIUS queries         │
     │    └─────────────────────────────┘
     │
     │    ┌────────────────────────────────┐
     │    │  PostgreSQL (Rides, Payments)  │
     │    └────────────────────────────────┘
     │
     │    ┌─────────────────────────────────────────────┐
     │    │  WebSocket / SSE Server                     │
     │    │  Pushes real-time updates to rider/driver   │
     │    └─────────────────────────────────────────────┘
     │
     │    ┌─────────────────────────────────────────────┐
     │    │  ETA Service (Google Maps / Mapbox API)     │
     │    └─────────────────────────────────────────────┘
     │
     │    ┌─────────────────────────────────────────────┐
     │    │  Payment Service (Stripe)                   │
     │    └─────────────────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Location Service** | Ingests 1M driver pings/sec via Kafka | Lightweight; decoupled from storage via Kafka |
| **Redis Geo Index** | Real-time driver positions for GEORADIUS matching | Sub-ms proximity queries; handles 1M GEOADD/sec |
| **Matching Service** | Finds and ranks nearby drivers, sends ride offers | Proximity + scoring; atomic driver assignment to prevent double-matching |
| **Ride Service** | Manages ride lifecycle (searching → completed) | State machine orchestration; PostgreSQL for durability |
| **WebSocket Server** | Real-time location streaming to rider app | < 500ms latency for live driver tracking on map |
| **ETA Service** | Road network + traffic for arrival estimate | External API (Google Maps); cached for common routes |
| **Payment Service** | Fare calculation + charge at trip end | Stripe integration; idempotent to prevent double charges |

> 🎯 **NFR addressed**: **Scalability 1M writes/sec** — Kafka + Redis pipeline. **Match Latency < 5s** — GEORADIUS + push notification. **Availability 99.99%** — stateless services, Redis replicas, PostgreSQL HA. **Accuracy** — ETA service with real-time traffic data.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Driver Location Update at 1M writes/sec

**The Scaling Challenge:** 5M drivers × 1 update/5s = 1M writes/sec. PostgreSQL can't handle this.

**Solution: Location Update Pipeline**
```
Driver App → Location Service (lightweight HTTP/UDP endpoint)
                    ↓
              Kafka topic: driver_locations
                    ↓
Consumer A (hot path):
  → Update Redis GEOADD (current position only)
  → 1M GEOADD/sec — Redis Cluster handles this

Consumer B (cold path):
  → Write location history to Cassandra (for trip reconstruction)
  → Batched writes (not 1M/sec per key)
```

**Why Kafka in between?**
- Decouples the ingest from storage
- If Redis is briefly slow: Kafka buffers the backlog
- Enables replay of location data for debugging

---

### Deep Dive 2: Matching Algorithm

```
Step 1: Find nearby available drivers
  GEORADIUS drivers:available:bangalore {lat} {lng} 5 km COUNT 50 ASC

Step 2: Filter by ride type
  Check driver_id → vehicle_type (in-memory cache)

Step 3: Rank candidates
  Score = (0.6 × distance) + (0.2 × driver_rating) + (0.2 × acceptance_rate)

Step 4: Send ride offer to top driver (15 seconds to accept)
  If rejected / timeout → try next driver in list

Step 5: Atomic lock assignment
  UPDATE drivers SET is_available = false WHERE driver_id = {id} AND is_available = true
  RETURNING driver_id   -- returns nothing if already taken
```

---

### Deep Dive 3: Real-Time Location Streaming to Rider

After match, rider needs to see driver moving in real-time:

**WebSocket approach ✅:**
```
Rider app opens WebSocket to location streaming server
Driver location update → Redis Pub/Sub channel "location:{ride_id}"
Streaming server subscribes → pushes to rider's WebSocket
Latency: < 500ms end-to-end
```

**Connection routing:**
- `ws_conn:{rider_id} → server_id` stored in Redis
- When driver location arrives → look up rider's server → route message there

---

### Deep Dive 4: Geospatial Indexing — Beyond Redis Geo

**Geohash:** String encoding of lat/lng into hierarchical grid
- Length 6: ~1.2km × 0.6km cell (ideal for matching)
- Nearby drivers = all drivers whose geohash shares same prefix

**Uber's H3 Hexagonal Grid (open-sourced):**
- Earth divided into hexagonal cells at 16 resolutions
- Resolution 9: ~0.1 km² per cell
- Benefits: equidistant from all 6 neighbors (no diagonal distortion)
- Driver location → H3 cell index → O(1) HashMap lookup

---

### Deep Dive 5: Surge Pricing

```
supply = available_drivers in H3 cell
demand = ride_requests in last 5 minutes in H3 cell

ratio = demand / supply
if ratio < 1.0: surge = 1.0x (normal)
if ratio >= 2.0: surge = 2.0x (max cap)

Effect: Higher prices attract more drivers + reduce demand → self-balancing
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
- **AP for location tracking** — driver location 5 seconds stale is fine
- **CP for ride assignment** — never assign same driver to two riders

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Location store | Redis Geo / H3 hashmap | PostgreSQL PostGIS | PostGIS can't handle 1M writes/sec; Redis is in-memory, sub-ms |
| Location updates | Kafka buffered pipeline | Direct Redis write | Kafka absorbs spikes; direct write risks overloading Redis |
| Live tracking | Redis Pub/Sub + WebSocket | Client polling | WebSocket < 500ms latency; polling adds 3-5s lag |
| Matching | Proximity + score ranking | Pure proximity | Score ranking improves UX (high-rated drivers preferred) |
| Surge pricing | Demand/supply ratio | Fixed rules | Ratio-based is self-regulating |

---

### Summary Talk Track

1. "Uber has two dominant problems: **1M driver location writes/sec** and **fast, correct ride matching**."
2. "Core entities: **Driver** (location), **Ride** (lifecycle), **Rider** (customer)."
3. "Location: driver pings every 5s → Kafka → Redis Geo / H3 hashmap (sub-ms lookup)."
4. "Matching: GEORADIUS → filter by vehicle → rank by distance + rating → send offer → accept."
5. "Live tracking: Redis Pub/Sub + WebSocket — rider sees driver in < 500ms."
6. "Assignment uses atomic DB update — prevents double-assignment."
7. "CAP split: **AP for location** (stale ok), **CP for ride assignment** (no double assignment)."

---

> **Previous**: [11 — Design BookMyShow](./11-bookmyshow.md)
> **Next**: [13 — Design Instagram](./13-instagram.md)
