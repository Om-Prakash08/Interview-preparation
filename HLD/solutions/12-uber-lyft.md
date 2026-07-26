# 12. Design Uber / Lyft

> **Difficulty**: Hard | **Asked At**: Uber, Lyft, Google, Amazon, Meta
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

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

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Rider requests a ride (with pickup + destination)
2. System finds nearby available drivers
3. System matches rider to closest available driver
4. Both rider and driver see each other's real-time location
5. Driver navigates to rider, then to destination
6. Trip ends, payment processed
7. Ratings exchanged

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Match Latency** | Match rider with driver in < 5 seconds |
| **Location Update** | Drivers update location every 5 seconds |
| **Availability** | 99.99% — rider must be able to request ride |
| **Scalability** | 5M drivers × location update / 5 sec = 1M location writes/sec |
| **Accuracy** | ETA accurate within ±2 minutes |

### Out of Scope
- Multiple stops
- Uber Eats / food delivery
- UberPool (ride sharing between multiple riders)

---

## SECTION 3 — Capacity Estimation

### Location Updates
- 5 million active drivers
- Location update every 5 seconds
- = 5M / 5 = **1 million location writes/sec** (dominant load!)

### Ride Requests
- 10M rides/day = 10M / 86,400 ≈ **~115 ride requests/sec**

### Active Trips
- Average trip duration: 20 minutes
- Active trips at any time: 115/sec × 1200 sec = **~138,000 active trips simultaneously**

### Storage
- Location update: 100 bytes (driver_id, lat, lng, timestamp)
- 1M writes/sec × 100 bytes = **100 MB/sec** raw location data
- We only care about **current** location per driver, not history for matching
- Trip history: 10M/day × 1 KB = **10 GB/day**

---

## SECTION 4 — API Design

### 1. Rider Requests a Ride
```
POST /api/v1/rides/request
Authorization: Bearer <rider_token>
{
  "pickup_lat": 12.9716,
  "pickup_lng": 77.5946,
  "destination_lat": 12.9350,
  "destination_lng": 77.6140,
  "ride_type": "UberX"
}

Response 200:
{
  "ride_id": "ride_abc123",
  "status": "searching",
  "estimated_pickup_minutes": 4
}
```

### 2. Driver Updates Location (Periodic)
```
POST /api/v1/drivers/location
Authorization: Bearer <driver_token>
{
  "lat": 12.9716,
  "lng": 77.5946,
  "heading": 270,             // degrees
  "speed_kmh": 40
}
→ 200 OK (fire and forget, lightweight)
```

### 3. Get Ride Status (Polling or WebSocket)
```
GET /api/v1/rides/{ride_id}/status
Response: {
  "status": "driver_en_route",
  "driver": { "name": "Ravi K.", "rating": 4.8, "car": "Honda City - KA03 AB1234" },
  "driver_location": { "lat": 12.9730, "lng": 77.5960 },
  "eta_minutes": 3
}
```

### 4. Start / End Trip
```
POST /api/v1/rides/{ride_id}/start   // driver taps "Start trip"
POST /api/v1/rides/{ride_id}/end     // driver taps "End trip"
  → triggers fare calculation + payment
```

### 5. Driver Goes Online/Offline
```
PUT /api/v1/drivers/availability
{ "available": true | false }
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `drivers`
```
driver_id     BIGINT       PRIMARY KEY
name          VARCHAR(100)
phone         VARCHAR(20)
license_no    VARCHAR(50)
rating        FLOAT
is_available  BOOLEAN
current_lat   DOUBLE       -- kept in real-time (Redis, not Postgres)
current_lng   DOUBLE
vehicle_id    BIGINT
```

### Table 2: `rides`
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

### Location Store (Real-time, current position)
```
Redis Geospatial Index:
  Key: "drivers:available:{city}"
  GEOADD drivers:available:bangalore 77.5946 12.9716 driver_id_1
  GEOADD drivers:available:bangalore 77.6000 12.9700 driver_id_2
  ...

Query: find drivers within 5km of pickup:
  GEORADIUS drivers:available:bangalore 77.5946 12.9716 5 km COUNT 20 ASC
  → Returns 20 nearest driver IDs within 5km
```

Redis Geo: O(N+log(M)) for GEORADIUS — very fast

### Location History (for analytics + route tracking)
```
Cassandra: driver_location_history
  driver_id     BIGINT
  trip_id       BIGINT
  timestamp     TIMESTAMP
  lat           DOUBLE
  lng           DOUBLE
  PRIMARY KEY (trip_id, timestamp)   // query entire trip's path
```

---

## SECTION 6 — High-Level Architecture

```
RIDER APP                             DRIVER APP
   │                                      │
   │ POST /rides/request                  │ POST /drivers/location (every 5s)
   │                                      │
   └──────────────────┬───────────────────┘
                      │
              ┌───────▼────────┐
              │  API Gateway   │
              │  (Auth, RL)    │
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
     │    │    Real-time lat/lng         │
     │    │    GEORADIUS queries         │
     │    └─────────────────────────────┘
     │
     │    ┌────────────────────────────────┐
     │    │  PostgreSQL                   │
     │    │  Rides, Drivers, Payments     │
     │    └────────────────────────────────┘
     │
     │    ┌─────────────────────────────────────────────┐
     │    │  WebSocket / SSE Server                     │
     │    │  Pushes real-time updates to rider and      │
     │    │  driver apps (driver location, status)      │
     │    └─────────────────────────────────────────────┘
     │
     │    ┌─────────────────────────────────────────────┐
     │    │  ETA Service                                │
     │    │  Uses road network graph + current traffic  │
     │    │  (integration with Google Maps / Mapbox API)│
     │    └─────────────────────────────────────────────┘
     │
     │    ┌─────────────────────────────────────────────┐
     │    │  Payment Service                            │
     │    │  Calculates fare, charges rider via Stripe  │
     │    └─────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Driver Location Update at 1M writes/sec

**The Scaling Challenge:**
- 5M drivers × 1 update/5s = 1M writes/sec
- You cannot write to PostgreSQL at 1M writes/sec

**Solution: Location Update Pipeline**
```
Driver App → Location Service (lightweight HTTP/UDP endpoint)
                    ↓
              Kafka topic: driver_locations
                    ↓
         Two consumers in parallel:

Consumer A (hot path):
  → Update Redis GEOADD (current position only)
  → O(log N) per update
  → 1M GEOADD/sec — Redis Cluster handles this

Consumer B (cold path):
  → Write location history to Cassandra (for trip reconstruction)
  → Batched writes (not 1M/sec per key — batch by trip)
```

**Why Kafka in between?**
- Decouples the ingest (1M/sec) from storage (Redis + Cassandra)
- If Redis is briefly slow: Kafka buffers the backlog
- Enables replay of location data for debugging

---

### Deep Dive 2: Matching Algorithm

**Given**: Rider at (lat, lng), ride_type = "UberX"

**Step 1: Find nearby available drivers**
```
GEORADIUS drivers:available:bangalore {rider_lat} {rider_lng} 5 km 
         COUNT 50 ASC WITHCOORD  → returns 50 nearest drivers with positions
```

**Step 2: Filter by ride type**
```
For each candidate driver: check if their vehicle matches "UberX"
  → Lookup from in-memory cache (driver_id → vehicle_type)
```

**Step 3: Rank candidates**
```
Score = (0.6 × distance) + (0.2 × driver_rating) + (0.2 × acceptance_rate)
Pick top 1 (or top 3 for redundancy)
```

**Step 4: Send ride offer to driver**
```
Push notification to Driver App: "New ride request nearby"
Driver has 15 seconds to accept

If accepted → match made
If rejected / no response → try next driver in list
```

**Step 5: Lock assignment**
```
-- Atomic: only one ride can be assigned to a driver at a time
UPDATE drivers SET is_available = false, current_ride_id = {ride_id}
WHERE driver_id = {driver_id} AND is_available = true   -- optimistic check
RETURNING driver_id   -- returns nothing if already taken by another request
```

---

### Deep Dive 3: Real-Time Location Streaming to Rider App

After match, rider needs to see driver moving in real-time:

**Option A: Polling (simple)**
```
Rider app polls GET /rides/{ride_id}/driver_location every 3 seconds
Simple, works anywhere, but adds latency and load
```

**Option B: WebSocket (real-time)** ✅
```
Rider app opens WebSocket to location streaming server
Driver app sends location update → Location Service → Redis Pub/Sub
Redis Pub/Sub publishes to channel "location:{ride_id}"
Location streaming server (subscribed to Redis) → pushes to rider's WebSocket
Latency: < 500ms end-to-end (driver movement visible on rider's map)
```

**Connection handling:**
- Location streaming servers are stateful (hold WebSocket connections)
- Which server holds the rider's connection? → Stored in Redis: `ws_conn:{rider_id} → server_id`
- When driver location update arrives: look up rider's server_id → route message there

---

### Deep Dive 4: Geospatial Indexing — Beyond Redis Geo

**Problem with Redis GEORADIUS**: Linear scan at scale. Redis Geo uses **geohash** internally.

**Geohash**: A string encoding of lat/lng into a hierarchical grid
```
Geohash precision:
  length 1: 5000km × 5000km cell
  length 6: 1.2km × 0.6km cell  (ideal for Uber matching)
  length 7: 153m × 153m cell

Driver at (12.9716, 77.5946):
  geohash = "tdr1wu2"

Nearby drivers = all drivers whose geohash starts with "tdr1w"
  (same 6-char prefix = same ~1km cell)
```

**Uber's actual approach: H3 Hexagonal Grid (open-sourced)**
```
Earth divided into hexagonal cells at 16 resolutions
Resolution 9: ~0.1 km² per cell (ideal for matching)

Benefits over rectangular grid:
  - Hexagons are equidistant from all 6 neighbors (no diagonal distortion)
  - Consistent cell area across globe (unlike geohash near poles)
  - Efficient hierarchical queries

Driver location → H3 cell index
All drivers in same H3 cell → O(1) lookup from HashMap
```

---

### Deep Dive 5: Surge Pricing

**Problem**: More riders than drivers in an area → match rate drops → rider experience suffers.

**Solution: Dynamic Price Multiplier**
```
supply = available_drivers in H3 cell
demand = ride_requests in last 5 minutes in H3 cell

ratio = demand / supply

if ratio < 1.0: surge = 1.0x (normal)
if ratio < 1.5: surge = 1.2x
if ratio < 2.0: surge = 1.5x
if ratio >= 2.0: surge = 2.0x (max cap)
```

**Effect:**
- Higher prices attract more drivers to surge area
- Some riders choose not to ride (demand reduction)
- Self-balancing supply-demand mechanism

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP for location tracking** (eventual consistency — driver location 5 seconds stale is fine)
**CP for ride assignment** (never assign same driver to two riders — strong consistency for matching)

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Location store | Redis Geo / H3 hashmap | PostgreSQL PostGIS | PostGIS can't handle 1M writes/sec; Redis is in-memory, sub-ms lookups |
| Location updates | Kafka buffered pipeline | Direct Redis write | Kafka absorbs 1M/sec spikes; direct Redis write risks overloading Redis |
| Live tracking | Redis Pub/Sub + WebSocket | Client polling | WebSocket gives < 500ms latency; polling adds 3-5s lag on every update |
| Matching | Proximity + score ranking | Pure proximity | Score ranking improves UX (high-rated drivers preferred); pure proximity can assign a bad driver |
| Surge pricing | Demand/supply ratio | Fixed rules | Ratio-based is self-regulating; fixed rules need manual tuning |

### What Would You Do Differently at Larger Scale?
- **Multi-city isolation**: separate Redis clusters per city (Bangalore, Mumbai, Delhi) to avoid cross-city noise in geo queries
- **Driver dispatch ML model**: replace score formula with an ML model trained on acceptance rates, ETAs, and completion rates
- **Pre-positioning**: predict where demand will spike (stadium event ending) → notify nearby drivers to move there

---

## Interview Flow Summary (Talk Track)

1. "Uber has two dominant problems: **1M driver location writes/sec** and **fast, correct ride matching**"
2. "Location: driver pings every 5s → Kafka → Redis Geo / H3 hashmap (sub-ms lookup)"
3. "Matching: GEORADIUS query → filter by vehicle type → rank by distance + rating → send offer → driver accepts"
4. "Live tracking: Redis Pub/Sub + WebSocket — rider sees driver moving in < 500ms"
5. "Assignment uses an atomic DB update with availability check — prevents one driver from being double-assigned"
6. "Surge pricing: demand/supply ratio per H3 cell — self-balancing mechanism"
7. "CAP split: AP for location (stale ok), CP for ride assignment (no double assignment)"

---

> **Previous**: [11 — Design BookMyShow](./11-bookmyshow.md)
> **Next**: [13 — Design Instagram](./13-instagram.md)
