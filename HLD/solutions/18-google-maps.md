# 18. Design Google Maps

> **Difficulty**: Very Hard | **Asked At**: Google, Uber, Apple Maps, Lyft
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Search for places (restaurants, hospitals, addresses)?
- Turn-by-turn navigation (routing)?
- Real-time traffic?
- Map tile rendering (the visual map)?
- ETA calculation?
- Street View? (out of scope)
- Ride-hailing integration?

**Scale:**
- How many users per day?
- How many navigation requests per day?
- Size of the road network?

**Typical Interviewer Answer:**
- Search, routing with real-time traffic, ETA, map tile display
- 1 billion DAU (Google Maps actual scale)
- 20 million navigation requests per day
- Road network: 60 million road segments worldwide
- No Street View for this discussion

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Users can search for places (addresses, POIs like restaurants, hospitals)
2. Get directions from A to B (driving, transit, walking)
3. Step-by-step turn-by-turn navigation
4. ETA calculation factoring real-time traffic
5. View the map (rendered tiles at different zoom levels)
6. Traffic layer overlay (congestion indicators)

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Routing** | Route computed in < 2 seconds, even across continents |
| **ETA accuracy** | Within ± 2 minutes for typical journey |
| **Map tiles** | < 200ms to load map tiles |
| **Availability** | 99.99% |
| **Scale** | 1 billion DAU, 20M navigation sessions/day |

### Out of Scope
- Street View
- Indoor mapping
- Google Business profile management
- Real-time transit schedules (mention as data source)

---

## SECTION 3 — Capacity Estimation

### Navigation Sessions
- 20M navigation requests/day
- = 20M / 86,400 ≈ **~230 routing requests/sec** average
- Peak (rush hour 8–9am): **~1,000 routing requests/sec**

### Location Updates (From Users Providing Traffic Data)
- 1 billion DAU × 30% sharing location while driving = 300M active navigators
- Each sends location every 15 seconds
- = 300M / 15 = **~20 million location events/sec** — Kafka essential

### Map Tiles
- World map at zoom level 20: ~4.4 trillion tiles
- Typically cache zoom levels 0–15 (world to street level): ~100 billion tiles
- Each tile: ~10–50 KB PNG/WebP image
- Top tiles (popular cities): heavily CDN-cached

### Road Network Storage
- 60 million road segments
- Each segment: ~200 bytes (from_node, to_node, length, speed_limit, road_type)
- Total graph: 60M × 200B = **~12 GB** — fits in RAM on a single server!

---

## SECTION 4 — API Design

### 1. Get Directions
```
GET /api/v1/directions?
  origin=12.9716,77.5946&
  destination=12.9350,77.6140&
  mode=driving&
  departure_time=now&
  alternatives=2

Response:
{
  "routes": [
    {
      "route_id": "r1",
      "summary": "Via MG Road",
      "distance_meters": 8240,
      "duration_sec": 1680,            // with current traffic
      "duration_no_traffic_sec": 900,
      "legs": [
        {
          "start": { "lat": 12.97, "lng": 77.59 },
          "end": { "lat": 12.93, "lng": 77.61 },
          "steps": [
            { "instruction": "Head north on MG Road", "distance_m": 500, "duration_sec": 120 }
          ]
        }
      ],
      "polyline": "encoded_path_string"   // compressed lat/lng sequence
    }
  ]
}
```

### 2. Get Map Tiles
```
GET /api/v1/tiles/{zoom}/{x}/{y}
→ Returns PNG/WebP tile image
→ Served via CDN (99% cache hit for popular zoom/coordinates)
```

### 3. Search Places
```
GET /api/v1/places/search?q=Starbucks&lat=12.97&lng=77.59&radius=2000
Response: {
  "places": [
    {
      "place_id": "pl_abc",
      "name": "Starbucks - MG Road",
      "address": "42, MG Road, Bangalore",
      "location": { "lat": 12.975, "lng": 77.598 },
      "category": "cafe",
      "rating": 4.2,
      "distance_m": 450
    }
  ]
}
```

### 4. Report Traffic (from user's device)
```
POST /api/v1/telemetry
{
  "lat": 12.975, "lng": 77.598,
  "speed_kmh": 5,
  "heading": 45,
  "road_segment_id": "seg_xyz",
  "timestamp": 1722000000000
}
→ 200 OK (fire and forget)
```

---

## SECTION 5 — Data Model

### Road Network Graph (Core Data Structure)
```
nodes (intersections):
  node_id     BIGINT       PRIMARY KEY
  lat         DOUBLE
  lng         DOUBLE
  geohash     VARCHAR(12)

road_segments (edges):
  segment_id    BIGINT       PRIMARY KEY
  from_node_id  BIGINT       REFERENCES nodes
  to_node_id    BIGINT       REFERENCES nodes
  distance_m    INT
  speed_limit_kmh INT
  road_type     ENUM('motorway', 'primary', 'secondary', 'residential')
  is_one_way    BOOLEAN
  max_weight_t  FLOAT        NULL  (for trucks)
  toll          BOOLEAN

traffic_segments (real-time):
  segment_id    BIGINT       PRIMARY KEY (same as road_segments)
  current_speed_kmh INT      -- updated every 2 minutes from telemetry
  congestion    ENUM('free_flow', 'moderate', 'heavy', 'standstill')
  updated_at    TIMESTAMP
```
**DB for road network**: **In-memory (RAM)** on routing servers. 12 GB fits easily.
**DB for traffic**: **Redis** (fast updates, 60-second TTL per segment)

### Places / POI Data
```
places:
  place_id      VARCHAR(40)  PRIMARY KEY
  name          VARCHAR(200)
  address       TEXT
  lat           DOUBLE
  lng           DOUBLE
  category      VARCHAR(50)
  rating        FLOAT
  phone         VARCHAR(20)
  hours         JSONB
```
**DB**: **Elasticsearch** with geo_point field (same as food delivery search)

### Map Tile Storage
```
Tiles stored in S3:
  s3://google-maps-tiles/{zoom}/{x}/{y}.webp
  
  Naming: TMS (Tile Map Service) standard
  Served via CDN with very long TTL (tiles rarely change)
  When road changes (new highway built): invalidate specific tile range in CDN
```

---

## SECTION 6 — High-Level Architecture

```
USER DEVICE
    │
    ├─ GET /tiles/{z}/{x}/{y}  ────────────────► CDN (CloudFront/Akamai)
    │                                              │ Cache Hit (99%): serve tile
    │                                              │ Cache Miss (1%): S3 → render → cache
    │
    ├─ GET /directions ──────────────────────────► Routing Service
    │                                              │ Loads road graph in RAM
    │                                              │ Applies real-time traffic weights
    │                                              │ Runs routing algorithm
    │                                              ▼
    │                                         Return route
    │
    ├─ GET /places/search ───────────────────────► Place Search Service
    │                                              │ Elasticsearch geo query
    │                                              ▼
    │                                         Return POIs
    │
    └─ POST /telemetry ──────────────────────────► Telemetry Ingestion Service
                                                    │ (UDP / lightweight HTTP)
                                                    ▼
                                               Kafka (location stream)
                                                    │
                                          ┌─────────▼─────────────┐
                                          │  Traffic Aggregation  │
                                          │  Service (Flink)      │
                                          │  - Groups events by   │
                                          │    road segment       │
                                          │  - Computes avg speed │
                                          │    per segment        │
                                          │  - Every 2 minutes    │
                                          └─────────┬─────────────┘
                                                    │
                                          ┌─────────▼─────────────┐
                                          │  Traffic Redis Store  │
                                          │  segment_id → speed   │
                                          │  TTL: 60 seconds      │
                                          └─────────┬─────────────┘
                                                    │
                                          ┌─────────▼─────────────┐
                                          │  Routing Servers      │
                                          │  Load traffic from    │
                                          │  Redis every 2 min    │
                                          │  Apply to graph edges │
                                          └───────────────────────┘

MAP TILE RENDERING PIPELINE
═════════════════════════════════
  OpenStreetMap data + proprietary updates
       ↓
  Tile Rendering Workers (Mapnik / PostGIS)
       ↓ (generate tiles for each zoom level 0–20)
  S3 (tile storage)
       ↓
  CDN pre-warming (popular cities first)
       ↓
  Served to users
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Routing Algorithm — Getting from A to B

**The road network is a weighted directed graph:**
```
Nodes = intersections
Edges = road segments
Edge weight = travel_time = distance / current_speed
```

**Algorithms:**

**Dijkstra's Algorithm (baseline):**
```
Find shortest path from source to all nodes.
Time complexity: O((V + E) log V) with priority queue
For world graph: 60M nodes, 150M edges → too slow for 2-second target
```

**A* Search (heuristic-guided):**
```
Like Dijkstra but uses heuristic h(n) = straight-line distance to destination
Only explores nodes that seem "closer" to destination
Faster than Dijkstra for point-to-point routing
Still too slow for cross-continent routes
```

**Contraction Hierarchies (CH) — What Google/Uber actually use** ✅

```
Key insight: Most routing queries go through the same "important" nodes
(highways, motorways, city centers)

Preprocessing (offline, run once):
  1. Rank all nodes by "importance" (how often they're on shortest paths)
  2. Add shortcut edges: if A→B→C is always the fastest path, add edge A→C
  3. Remove less important nodes (they become implicit)
  4. Result: a hierarchy of nodes from "local roads" to "motorways"

Runtime query (extremely fast):
  1. From source: traverse UP the hierarchy (local roads → highways)
  2. From destination: traverse DOWN the hierarchy (highways → local roads)
  3. They meet somewhere in the middle at a "high importance" node
  4. Combine paths: O(1) to O(log N) hops typically

Result: 60M node graph → route computed in milliseconds!
Real-world: Google routes from Mumbai to Delhi in < 100ms using CH
```

**OSRM (Open Source Routing Machine)** uses CH and can route across Europe in 0.5ms.

---

### Deep Dive 2: Real-Time Traffic Integration

```
Data sources for real-time traffic:
  1. Anonymized speed data from Google Maps app users
  2. Waze (acquired 2013) user reports
  3. Municipal traffic sensor APIs
  4. Historical patterns (ML: "Monday 8am on MG Road is usually 10 km/h")

Pipeline:
  User locations → Kafka (20M events/sec)
       ↓
  Flink stream processing:
    - Match GPS point to nearest road segment (map matching)
    - Filter noise (users walking, indoors)
    - Aggregate speeds per segment per 2-minute window
    - Detect sudden slowdowns (accident) vs gradual buildup (rush hour)
       ↓
  Redis: segment_id → { avg_speed, confidence, updated_at }
       ↓
  Routing servers load traffic every 2 minutes
  Blend with historical patterns (if traffic data sparse)

Traffic-aware edge weight:
  travel_time = segment_distance / min(current_speed, speed_limit)
  
For routing: multiply baseline weight by traffic multiplier:
  free_flow: 1.0×
  moderate: 1.5×
  heavy: 2.5×
  standstill: 10× (virtually block the road)
```

---

### Deep Dive 3: Map Tile Rendering

**How does a map become tiles?**

```
Source data:
  - OpenStreetMap: roads, buildings, parks, water bodies (open dataset)
  - Google proprietary: business data, satellite imagery, Street View
  - Stored in PostGIS (PostgreSQL with geospatial extensions)

Tile rendering (offline, pre-generated):
  For each zoom level (0–20) and each tile coordinate (x, y):
    1. PostGIS spatial query: get all features within tile's bounding box
    2. Style them (road color, width, label, building shade)
    3. Render to PNG/WebP image
    4. Store in S3: s3://tiles/{zoom}/{x}/{y}.webp

  Zoom 0: 1 tile (whole world, ~512×512px)
  Zoom 10: 1M tiles (country level)
  Zoom 15: 1B tiles (neighborhood level)
  Zoom 20: 1T tiles (building level) — only pre-generate for cities

Updates:
  New road opened → only regenerate affected tiles
  Tile bounding box calculation → regenerate ~50 tiles at all zoom levels
  CDN invalidation: purge {zoom}/{x}/{y} for changed tiles
```

**Vector Tiles (modern approach):**
```
Instead of pre-rendered PNGs, send vector data:
  { roads: [{ geometry, road_type, name }], buildings: [...] }
Client renders using WebGL (MapboxGL, Google Maps JS SDK)

Advantages:
  - Smaller payloads (20% of PNG size)
  - Client can rotate/tilt map without server (3D view)
  - Style changes without re-downloading tiles
  - Much smaller CDN storage

This is why modern Google Maps and Uber use vector tiles
```

---

### Deep Dive 4: Place Search (Geocoding + POI Search)

**Forward Geocoding:** "MG Road Bangalore" → lat/lng
**Reverse Geocoding:** lat/lng → "42 MG Road, Bangalore 560001"
**POI Search:** "restaurants near me" → list of places

**Architecture:**
```
Geocoding:
  - Offline: build an index mapping address tokens to lat/lng
  - Input: "MG Road Bangalore" → tokenize → ["MG", "Road", "Bangalore"]
  - Lookup: Elasticsearch fuzzy match on address text + geo proximity boost
  - Return best match

Elasticsearch index for places:
  {
    name: "Starbucks", category: "cafe",
    address: "42 MG Road", 
    location: { "lat": 12.975, "lon": 77.598 }  // geo_point
  }

Search query: "coffee near Bangalore" within 5km:
  text match: "coffee" ← hits name/category
  geo filter: within 5km of (12.97, 77.59)
  scoring: combined text relevance + distance + rating
```

---

### Deep Dive 5: ETA Estimation

ETA is more complex than routing duration — it must account for:

```
ETA = Route duration (with current traffic)
    + Expected traffic delay (will traffic worsen during the trip?)
    + Stop light wait time (signal cycle analysis)
    + Turn delay (left turns at highway take longer than right turns)
    + Driver behaviour factor (aggressive vs conservative)

ML-based ETA:
  Input features:
    - Current route distance + intermediate segment speeds
    - Historical travel time for same route (day + hour of week)
    - Current traffic index (worse than historical?)
    - Weather (rain → 20% slower)
    - Events nearby (stadium event ending → traffic spike)
    - Start time (rush hour departure vs non-peak)

  Google's DeepMind team trained a GNN (Graph Neural Network) on:
    - Billions of historical trips
    - Road graph structure
    - Real-time traffic
    → Reduced ETA prediction error by 40% vs pure algorithmic approach

  Output: P50 ETA (median), P90 ETA (90th percentile — "usually within this time")
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP for routing and map tiles:**
- Slightly stale traffic data (5-min old): acceptable
- Better to show navigation with 10% inaccurate traffic than error out
- If routing server is down: show cached routes, flag as potentially outdated

**CP for nothing specific in this system** — this is fundamentally an AP system.

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Routing algorithm | Contraction Hierarchies | Pure Dijkstra / A* | CH gives millisecond routing for world graph; Dijkstra is too slow (seconds) |
| Map tiles | Pre-rendered (offline) + CDN | On-the-fly rendering | Pre-rendered: consistent quality, CDN-cacheable; on-the-fly: always fresh but slow and expensive |
| Tile format | Vector tiles (WebGL) | Raster PNG | Vector is smaller, client-rendered, supports 3D/tilt; raster is simpler but larger and not 3D |
| Traffic data | Crowdsourced (user phones) | Road sensors | Sensors are expensive and sparse; crowdsourcing covers every road with active users |
| ETA | ML model (GNN) | Pure routing formula | GNN reduces error by 40%; formula doesn't account for behavioral patterns |

### What Would You Do Differently at Larger Scale?
- **Predictive routing**: route that's fastest in 20 minutes (traffic will build up on highway X)
- **Multi-modal routing**: driving + transit + last-mile walking combined
- **Offline maps**: download region tiles + road graph for offline navigation
- **Indoor mapping**: airport terminals, malls (different graph, GPS doesn't work)

---

## Interview Flow Summary (Talk Track)

1. "Google Maps has 3 systems: **map rendering (tiles)**, **routing (algorithm)**, and **real-time traffic (pipeline)**"
2. "Map tiles: pre-rendered offline → S3 → CDN (99% cache hit). Modern: vector tiles + WebGL for 3D"
3. "Routing: **Contraction Hierarchies** — offline preprocess builds shortcuts, runtime routes in milliseconds"
4. "Traffic: 20M location events/sec from user phones → Kafka → Flink aggregates avg speed per segment every 2 min → Redis"
5. "Traffic applied to CH graph edge weights: free_flow = 1×, standstill = 10× multiplier"
6. "ETA: not just routing duration — **ML model (GNN)** trained on billions of historical trips for 40% better accuracy"
7. "Place search: **Elasticsearch** with geo_point — combined text + proximity ranking"

---

> **Previous**: [17 — Design Netflix Streaming](./17-netflix-streaming.md)
> **Next**: [19 — Design Web Crawler](./19-web-crawler.md)
