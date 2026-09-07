# 18. Design Google Maps

> **Difficulty**: Very Hard | **Asked At**: Google, Uber, Apple Maps, Lyft
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Search for places (restaurants, hospitals, addresses)?
- Turn-by-turn navigation (routing) and ETA calculation?
- Real-time traffic integration?
- Map tile rendering (visual map display)?
- Street View / indoor mapping?
- Scale: how many DAU and navigation sessions?

**Typical Interviewer Answer:** Core scope is place search, routing with real-time traffic, ETA calculation, and map tile display. 1 Billion DAU, 20M navigation sessions/day. No Street View.

### 1.2 Functional Requirements (FR)
1. Users can search for places / POIs by name or category.
2. Get directions from location A to location B (driving, walking, transit).
3. Step-by-step turn-by-turn navigation.
4. Real-time ETA estimation accounting for traffic delays.
5. Render map tiles at different zoom levels (0–20).
6. Traffic layer overlay showing congestion levels.

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Routing Latency** | Route computed in < 2 seconds globally |
| **ETA Accuracy** | Within ± 2 minutes for typical urban journeys |
| **Tile Load Time** | < 200ms to fetch map tiles from CDN |
| **Availability** | 99.99% for map rendering and routing |
| **Scale** | 1B DAU, 20M navigation sessions/day, 20M location updates/sec |

### 1.4 Out of Scope
- Street View / 3D imagery rendering
- Indoor navigation / airport mapping
- Google Business Profile management

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐       ┌──────────────────────────┐       ┌──────────────────┐
│   Node           │       │  Road Segment (Edge)     │       │   Traffic Data   │
│   (Intersection) │       │                          │       │   (Real-time)    │
│                  │       │  segment_id              │       │                  │
│  node_id         │◄─────►│  from_node_id            │◄─────►│  segment_id      │
│  lat, lng        │       │  to_node_id              │       │  current_speed   │
│  geohash         │       │  distance_m              │       │  congestion_lvl  │
└──────────────────┘       │  speed_limit             │       └──────────────────┘
                           └──────────────────────────┘
┌──────────────────┐       ┌──────────────────────────┐
│   Place / POI    │       │  Map Tile                │
│                  │       │                          │
│  place_id        │       │  zoom, x, y              │
│  name, category  │       │  s3_key                  │
│  lat, lng        │       └──────────────────────────┘
└──────────────────┘
```

### 2.2 Data Model / Schema

**1. `nodes` & `road_segments` (Graph Data - In-Memory RAM)**
```sql
-- Intersections
nodes (node_id BIGINT PK, lat DOUBLE, lng DOUBLE, geohash VARCHAR(12));

-- Directed edges
road_segments (
  segment_id BIGINT PK, from_node_id BIGINT, to_node_id BIGINT,
  distance_m INT, speed_limit_kmh INT, road_type ENUM('motorway','primary','secondary','residential'),
  is_one_way BOOLEAN
);
```
*Note*: Whole graph is loaded into **Routing Server RAM (~12 GB total)**.

**2. `traffic_segments` (Redis Key-Value)**
```
Key: traffic:{segment_id}
Value: { "current_speed_kmh": 15, "congestion": "heavy", "updated_at": 1722000000 }
TTL: 60 seconds
```

**3. `places` (Elasticsearch with geo_point)**
```json
{
  "place_id": "pl_101",
  "name": "Starbucks Coffee",
  "category": "cafe",
  "location": { "lat": 12.975, "lon": 77.598 },
  "rating": 4.5
}
```

> 🎯 **NFR addressed**: **Routing Latency < 2s** — Graph in RAM avoids disk IO. **Tile Load < 200ms** — Tiles stored as flat files in S3 and cached via CDN.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Get Directions (Routing)
```
GET /api/v1/directions?origin=12.9716,77.5946&destination=12.9350,77.6140&mode=driving&departure_time=now
Response 200 OK:
{
  "routes": [
    {
      "route_id": "r_101",
      "distance_meters": 8240,
      "duration_sec": 1200,            // traffic adjusted
      "duration_no_traffic_sec": 900,
      "polyline": "encoded_string...",
      "steps": [ { "instruction": "Turn left on MG Road", "distance_m": 500 } ]
    }
  ]
}
```

### 3.2 Get Map Tile
```
GET /api/v1/tiles/{zoom}/{x}/{y}
→ Returns PNG / WebP image or Vector Tile PBF protocol buffer (served via CDN)
```

### 3.3 Place Search
```
GET /api/v1/places/search?q=coffee&lat=12.9716&lng=77.5946&radius_m=2000
Response 200 OK:
{ "places": [ { "place_id": "pl_101", "name": "Starbucks", "distance_m": 450 } ] }
```

### 3.4 Telemetry (Location updates from active users)
```
POST /api/v1/telemetry
{ "lat": 12.975, "lng": 77.598, "speed_kmh": 12, "heading": 90, "timestamp": 1722000000 }
```

> 🎯 **NFR addressed**: **Scale** — Telemetry API is lightweight fire-and-forget; GET tiles served 99% by CDN.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Navigation Sessions**: 20M requests/day = **~230 req/sec** avg, peak **~1,000 req/sec**.
- **Telemetry Ingestion**: 300M driving users send GPS every 15s = **~20 Million events/sec** → Kafka ingestion required.
- **Road Network Graph**: 60M road segments × 200B = **12 GB RAM** (fits easily in RAM of single routing cluster node).
- **Map Tile Storage**: ~100 Billion tiles (Zoom 0–15) × 30 KB = **~3 PB in S3**.

### 4.2 Data Flow Through System

**Navigation & Real-time Route Calculation Flow:**
```
User App → GET /directions → API Gateway → Routing Service
  1. Routing Service retrieves user location & destination coordinates.
  2. Map matching converts lat/lng to nearest Graph Nodes (Source, Target).
  3. Routing Engine runs Contraction Hierarchies (CH) algorithm on in-memory graph.
  4. Segment edge weights are updated dynamically using real-time speed from Redis Traffic Store.
  5. Route polyline and turn instructions returned to user device in < 100ms.
```

**Traffic Aggregation Pipeline:**
```
User GPS → Telemetry Ingestion → Kafka (20M events/sec) → Flink Aggregator 
  → Map matching to segment_id → Compute avg speed per segment every 2 min 
  → Store in Redis Traffic Cache → Routing Servers refresh weights every 2 min.
```

> 🎯 **NFR addressed**: **ETA Accuracy** — 2-minute Flink window ensures real-time traffic condition updates.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                                 ┌─────────────────────────────────┐
                                 │       CDN (CloudFront/Akamai)   │
                                 │   Cache Map Tiles (Zoom 0-15)   │
                                 └────────────────┬────────────────┘
                                                  │ Cache miss
USER DEVICE                                       ▼
  │  GET /tiles ───────────────────────────► S3 Tile Bucket
  │
  ├─ GET /directions ──────────────────────► API Gateway
  │                                               │
  │                                      ┌────────▼────────────────┐
  │                                      │  Routing Service        │
  │                                      │  - In-Memory Road Graph │
  │                                      │  - Contraction Hierarchy│
  │                                      └────────┬────────┬───────┘
  │                                               │        │
  │                                      ┌────────▼──┐ ┌───▼───────┐
  │                                      │  Redis    │ │  Geo-     │
  │                                      │ Traffic   │ │  Coder    │
  │                                      └───────────┘ └───────────┘
  │
  ├─ GET /places/search ───────────────────► Place Search Service
  │                                               │
  │                                               ▼
  │                                         Elasticsearch
  │
  └─ POST /telemetry ──────────────────────► Telemetry Ingestion (UDP/HTTP)
                                                  │
                                                  ▼
                                            Kafka Stream
                                                  │
                                                  ▼
                                            Apache Flink
                                            (Avg Speed / 2 min)
                                                  │
                                                  ▼
                                            Redis Traffic Store
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **CDN** | Serves static map tiles | 99% hit rate; < 50ms latency globally |
| **Routing Service** | Computes shortest path & ETA | Contraction Hierarchies in RAM; < 100ms execution |
| **Redis Traffic Store** | Stores live segment speeds | Low latency read access for graph weight updates |
| **Kafka & Flink** | Processes 20M GPS events/sec | Scalable stream processing and sliding-window aggregation |
| **Elasticsearch** | POI Place Search | Geo-distance filtering + fuzzy text matching |

> 🎯 **NFR addressed**: **Availability 99.99%** — Statistically decoupled reading (tiles on CDN, routing in RAM) from telemetry ingestion.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Graph Routing Algorithm — Contraction Hierarchies (CH)

**Problem**: Dijkstra's algorithm ($O((V+E) \log V)$) on 60M nodes takes 5+ seconds. A* search is better but still slow across long distances.

**Solution: Contraction Hierarchies (CH)**
```
Phase 1: Preprocessing (Offline, run once or daily)
  1. Rank nodes by importance (highways > main roads > local streets).
  2. "Contract" low-importance nodes: add "shortcut" edges if shortest path between 
     uncontracted neighbors passed through the contracted node.
  3. Result: Graph augmented with shortcut edges connecting major highways directly.

Phase 2: Query Time (Online, execution < 10ms)
  1. Bidirectional Dijkstra search.
  2. Forward search from Source ONLY moves UP the hierarchy (local -> highway).
  3. Backward search from Target ONLY moves UP the hierarchy (local -> highway).
  4. Both searches meet at a top-tier highway node.
  5. Unpack shortcuts to produce full turn-by-turn route.
```

---

### Deep Dive 2: Map Tile Management — Raster vs Vector Tiles

```
Option A: Raster Tiles (PNG/WebP)
  - Server renders 256x256 pixel images for every (zoom, x, y).
  - Zoom 20 requires ~4.4 Trillion tiles (storage bottleneck).
  - Client cannot rotate or tilt smooth 3D view.

Option B: Vector Tiles (Mapbox / Google Maps modern approach) ✅
  - Server sends raw geometric vectors (polygons, lines, labels) in Protobuf format.
  - Client GPU renders tile styling natively using WebGL/Metal.
  - Payload size is 80% smaller than PNG.
  - Dynamic rotation, night-mode styling, and 3D buildings handled client-side without re-downloading.
```

---

### Deep Dive 3: ETA Estimation using Machine Learning

```
ETA Calculation Formula:
  ETA = Route Distance / Current Segment Speeds + Junction Delays + Signal Cycles + ML Residual

Graph Neural Networks (GNN):
  - Google DeepMind trained GNNs on road network super-segments.
  - Features: historical traffic patterns (time of day, day of week), weather, spatial neighborhood traffic.
  - Result: Reduced ETA prediction errors by 40% compared to pure static segment speed summation.
```

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **Routing Algo** | Contraction Hierarchies | Dijkstra / A* | CH enables < 10ms cross-country queries via pre-calculated shortcuts |
| **Map Display** | Vector Tiles | Pre-rendered PNG | Vector tiles save 80% bandwidth and support client-side rotation/3D |
| **Traffic DB** | Redis | PostgreSQL | Redis handles high-frequency (2-min TTL) overwrite of 60M segment speeds |

---

### Summary Talk Track

1. "Google Maps requires solving three primary problems: **tile rendering**, **graph routing**, and **real-time traffic processing**."
2. "For routing across 60M road segments, standard Dijkstra is too slow. We use **Contraction Hierarchies (CH)** to preprocess shortcut edges between major highways, reducing query latency to **< 10ms**."
3. "Real-time traffic ingests **20M GPS events/sec** via Kafka and Apache Flink, updating segment speeds in **Redis** every 2 minutes."
4. "Map tiles are served using **Vector Tiles** cached at the CDN level for **< 200ms** latency."

---

> **Previous**: [17 — Design Netflix Streaming](./17-netflix-streaming.md)
> **Next**: [19 — Design Web Crawler](./19-web-crawler.md)
