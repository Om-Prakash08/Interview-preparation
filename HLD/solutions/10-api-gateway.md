# 10. Design an API Gateway

> **Difficulty**: Medium | **Asked At**: Amazon, Google, Uber, Netflix, Stripe
> **Time to Answer in Interview**: 35–40 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Does the gateway need to handle authentication and authorization?
- Do we need rate limiting per client/API key?
- Should it do request routing and load balancing to backend services?
- Do we need SSL termination?
- Request/response transformation (e.g., translate REST → gRPC)?
- API versioning support?
- Do we need analytics/logging of all API calls?

**Scale:**
- How many requests per second does this gateway need to handle?
- How many backend microservices?
- Expected latency budget for gateway overhead?

**Typical Interviewer Answer:**
- Yes to all core features: auth, rate limiting, routing, SSL termination, logging
- 1 million requests/second
- 500+ backend microservices
- Gateway adds < 10ms overhead to each request

### 1.2 Functional Requirements (FR)
1. **Request Routing**: Route incoming request to the correct backend microservice
2. **Authentication & Authorization**: Validate JWT tokens / API keys
3. **Rate Limiting**: Per client, per endpoint rate limits
4. **SSL Termination**: Decrypt HTTPS at gateway; use HTTP internally
5. **Load Balancing**: Distribute requests across multiple instances of a service
6. **Request/Response Logging**: Log every request for observability
7. **Circuit Breaker**: Stop routing to unhealthy backend services
8. **API Versioning**: Route `/v1/users` and `/v2/users` to different service versions
9. **Request Transformation**: Modify headers, add correlation IDs, strip internal fields from responses

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Throughput** | 1 million requests/sec |
| **Gateway Latency** | < 10ms overhead per request |
| **Availability** | 99.999% (5 nines) — gateway is the entry point for all traffic |
| **Scalability** | Horizontally scalable, stateless |
| **Security** | Prevent injection, validate all inputs at edge |

### 1.4 Out of Scope
- Service mesh (Istio) — that handles east-west traffic; gateway handles north-south (external traffic)
- GraphQL federation
- API monetization / billing per call

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  Route           │      │  BackendService  │      │  ServiceInstance │
│                  │      │                  │      │                  │
│  route_id        │──────│  service_name    │──────│  host            │
│  path_prefix     │      │  health_check    │      │  port            │
│  methods[]       │      │  lb_algorithm    │      │  is_healthy      │
│  backend_service │      │  circuit_breaker │      │  last_checked    │
│  auth_required   │      │    _threshold    │      │                  │
│  rate_limit_rpm  │      └──────────────────┘      └──────────────────┘
│  version         │
└──────────────────┘      ┌──────────────────┐
                          │  RequestLog      │
                          │                  │
                          │  request_id      │
                          │  timestamp       │
                          │  client_ip       │
                          │  path, method    │
                          │  response_code   │
                          │  latency_ms      │
                          └──────────────────┘
```

**Primary entities**: `Route` (maps path → backend service), `BackendService` (service metadata + health config), `ServiceInstance` (individual running instance), `RequestLog` (observability record).

### 2.2 Data Model / Schema

**Config Store (Route Rules) — PostgreSQL:**
```
routes table:
  route_id         UUID      PRIMARY KEY
  path_prefix      VARCHAR   UNIQUE
  backend_service  VARCHAR
  methods          TEXT[]
  auth_required    BOOLEAN
  rate_limit_rpm   INT
  version          VARCHAR   (v1, v2)

services table:
  service_name     VARCHAR   PRIMARY KEY
  health_check_url TEXT
  lb_algorithm     ENUM('round_robin', 'least_conn', 'ip_hash')
  circuit_breaker_threshold INT

instances table:
  service_name     VARCHAR
  host             VARCHAR
  port             INT
  is_healthy       BOOLEAN
  last_checked     TIMESTAMP
  PRIMARY KEY (service_name, host, port)
```

**All config loaded into memory on startup, refreshed every 30 seconds.**

**Request Log (Observability) — ClickHouse / Elasticsearch:**
```
request_id, timestamp, client_ip, method, path, backend_service,
response_code, latency_ms, user_id
```
Written async via Kafka (never on critical path).

> 🎯 **NFR addressed**: **Gateway Latency < 10ms** — config loaded in-memory, never queried per-request. **Availability** — gateway continues with cached config even if Config DB goes down. **Security** — route rules enforce auth requirements at the edge.

---

## Step 3 — API or Interface (~5 min)

The gateway itself doesn't have an end-user API — it **proxies** all APIs. But it has an internal **Management API** for configuration:

### 3.1 Register a Route
```
POST /admin/routes
{
  "path_prefix": "/api/v1/users",
  "backend_service": "user-service",
  "methods": ["GET", "POST", "PUT"],
  "auth_required": true,
  "rate_limit": { "requests": 100, "window": "1m" }
}
Response: { "route_id": "route_abc", "created_at": "..." }
```

### 3.2 Register a Backend Service
```
POST /admin/services
{
  "service_name": "user-service",
  "instances": [
    { "host": "user-svc-1.internal", "port": 8080 },
    { "host": "user-svc-2.internal", "port": 8080 }
  ],
  "health_check_path": "/health",
  "circuit_breaker": { "threshold": 50, "timeout_sec": 30 }
}
```

### 3.3 Get Gateway Metrics
```
GET /admin/metrics
Response: {
  "rps": 984231,
  "p50_latency_ms": 4.2,
  "p99_latency_ms": 18.5,
  "error_rate": 0.001,
  "active_connections": 284000
}
```

> 🎯 **NFR addressed**: **Throughput** — management API is separate from traffic path; no impact on request processing. **Scalability** — route changes propagate to all gateway instances via config refresh.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Traffic:**
- 1 million requests/sec peak
- Average request/response size: 2 KB
- Gateway bandwidth: 1M × 2 KB = **2 GB/s** (significant — need high-bandwidth NICs)

**Gateway Instances:**
- Single gateway instance: ~50,000 req/sec (Nginx/Envoy can do this)
- Needed: 1M / 50K = **20 gateway instances** minimum
- With 2× headroom for failures: **40 instances**

**Auth Validation:**
- JWT validation: ~0.1ms (cryptographic signature check, CPU-bound)
- 1M req/sec × 0.1ms = 100 CPU-seconds/sec → needs high-core gateway nodes

### 4.2 Data Flow Through System — Request Pipeline

```
EVERY REQUEST passes through these stages IN ORDER:

STAGE 1: SSL Termination → Decrypt TLS, internal HTTP/2
STAGE 2: Request Parsing → Extract method/path/headers, add X-Request-ID
STAGE 3: Authentication → Validate JWT signature, extract user_id/permissions
STAGE 4: Rate Limiting → Check Redis counter, reject 429 if over limit
STAGE 5: Routing → Match path against in-memory prefix trie → target service
STAGE 6: Load Balancing → Select instance (round-robin or least connections)
STAGE 7: Response Processing → Strip internal headers, set CORS, compress
STAGE 8: Logging (Async) → Publish to Kafka → ClickHouse
```

> 🎯 **NFR addressed**: **Gateway Latency < 10ms** — each stage is sub-ms; 8 stages total well under budget. **Security** — auth + validation at edge before requests reach backends.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                  EXTERNAL CLIENTS
                  (Web, Mobile, Partners, B2B)
                          │
                          │ HTTPS (TLS 1.3)
                          │
              ┌───────────▼────────────────┐
              │      DNS Load Balancer     │
              │   (Route53 / GeoDNS)       │
              │   Routes to nearest region │
              └───────────┬────────────────┘
                          │
              ┌───────────▼────────────────┐
              │   L4 Load Balancer (AWS    │
              │   NLB / HAProxy)           │
              │   TCP-level distribution   │
              └───────────┬────────────────┘
                          │
     ┌────────────────────┼─────────────────────────┐
     │                    │                         │
┌────▼────────┐    ┌───────▼─────────┐     ┌────────▼────────┐
│  Gateway 1  │    │   Gateway 2     │     │   Gateway N     │
│  (Stateless)│    │   (Stateless)   │     │   (Stateless)   │
│  Pipeline:  │    │  Same pipeline  │     │  Same pipeline  │
│ 1. SSL Term │    │                 │     │                 │
│ 2. Auth     │    │                 │     │                 │
│ 3. Rate Lmt │    │                 │     │                 │
│ 4. Route    │    │                 │     │                 │
│ 5. LB       │    │                 │     │                 │
│ 6. Log      │    │                 │     │                 │
└──────┬──────┘    └────────┬────────┘     └────────┬────────┘
       │                    │                        │
       └────────────────────┼────────────────────────┘
                            │ HTTP/gRPC (internal)
          ┌─────────────────┼──────────────────────────────────┐
          │                 │                                  │
 ┌────────▼──────┐  ┌────────▼──────┐                ┌────────▼──────┐
 │ User Service  │  │ Order Service │  ...           │ Payment Svc   │
 │ (3 instances) │  │ (5 instances) │                │ (2 instances) │
 └───────────────┘  └───────────────┘                └───────────────┘

     Shared Infrastructure (not on critical path):
     ┌───────────────┐  ┌────────────────┐  ┌──────────────────┐
     │ Config DB     │  │ Redis Cluster  │  │ Kafka → Logging  │
     │ (Postgres)    │  │ (Rate limits,  │  │ (ClickHouse)     │
     │ Route rules   │  │  Auth cache)   │  │                  │
     └───────────────┘  └────────────────┘  └──────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **DNS Load Balancer** | Geo-routes clients to nearest region | Reduces latency for global users |
| **L4 Load Balancer** | TCP-level distribution across gateway instances | No HTTP parsing overhead; pure network distribution |
| **Gateway Instances** | Stateless request pipeline (auth, RL, route, LB, log) | Identical instances; add/remove for horizontal scaling |
| **Config DB (Postgres)** | Stores route rules and service registry | Loaded in-memory; DB only for persistence and admin API |
| **Redis Cluster** | Rate limit counters + JWT validation cache | Shared state across gateway instances for global accuracy |
| **Kafka → ClickHouse** | Async request logging and analytics | Never on critical path; Kafka buffers spikes |

> 🎯 **NFR addressed**: **Availability 99.999%** — stateless gateways behind LB; any instance can serve any request. **Throughput 1M rps** — 40 instances × 50K rps each. **Gateway Latency < 10ms** — in-memory config, cached JWT, async logging. **Scalability** — add gateway instances linearly.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: JWT Authentication & Caching

**JWT Validation:**
```
JWT = header.payload.signature (Base64URL encoded)

Validation:
  1. Decode header → get algorithm (RS256)
  2. Verify signature using gateway's in-memory public key
  3. Check exp claim (expiry): if expired → 401
  4. Extract sub (user_id) and scope (permissions) from payload
```

**Key rotation problem**: 
- Auth service rotates JWT signing keys periodically
- Gateway must pick up new public keys without downtime
- Solution: **JWKS endpoint** (JSON Web Key Set)
  - Auth Service exposes `GET /.well-known/jwks.json` → list of current public keys
  - Gateway refreshes JWKS every 60 seconds
  - Supports multiple keys simultaneously (transition period)

**Caching JWT validation:**
- After validating a token, cache `{token_hash → user_id, permissions}` in Redis with TTL = min(60s, token_expiry)
- Same token validated once per minute (not once per request)
- Reduces cryptographic CPU load by ~100×

---

### Deep Dive 2: Circuit Breaker

**Problem**: If Order Service is down or slow, gateway keeps routing requests there → those requests pile up → gateway's thread pool exhausts → entire gateway slows.

**Circuit Breaker Pattern (3 states):**
```
CLOSED (normal):
  - Route all requests to service
  - Monitor error rate + latency
  - If error rate > 50% in 10 seconds → trip to OPEN

OPEN (service is sick):
  - Immediately reject all requests to this service with 503
  - Don't waste time waiting for unhealthy service
  - After 30 seconds → try HALF-OPEN

HALF-OPEN (testing recovery):
  - Allow 10% of requests through as a probe
  - If those succeed → move back to CLOSED
  - If those fail → back to OPEN

State stored in memory per gateway instance (eventually consistent across fleet — acceptable)
```

---

### Deep Dive 3: Service Discovery

**Problem**: Backend service instances are dynamic — they scale up/down, move between hosts. Gateway can't have static config for each instance.

**Solution: Service Registry (Consul or Kubernetes DNS)**
```
When user-service instance starts:
  → Registers itself: { "service": "user-service", "host": "10.0.1.5", "port": 8080 }
  → Sends heartbeats every 5 seconds

When gateway needs to route to user-service:
  → Queries Consul: "give me all healthy instances of user-service"
  → Gets: ["10.0.1.5:8080", "10.0.1.7:8080", "10.0.2.1:8080"]
  → Load balances among them

When user-service instance dies:
  → Heartbeat stops → Consul marks unhealthy after 3 missed beats
  → Gateway's next service lookup excludes the dead instance
```

Gateway caches service registry lookups for 5 seconds (avoids hitting Consul on every request).

---

### Deep Dive 4: Observability (3 Pillars)

**1. Metrics (Prometheus + Grafana)**
```
rps_per_service[service_name]
latency_p50/p95/p99[service_name]
error_rate[service_name]
rate_limit_rejections[client_id]
circuit_breaker_state[service_name]
```

**2. Distributed Tracing (Jaeger / Zipkin)**
```
X-Request-ID (correlation ID) propagated through all hops:
  Gateway → User Service → Order Service → Payment Service

Each service records its own span (start_time, end_time, errors)
Jaeger collects spans → reconstructs full request trace
```

**3. Logging (Kafka → ClickHouse)**
```
Every request logged (async, never on critical path)
Query logs: "show all 5xx errors from user-service in last 1 hour"
Alerting: PagerDuty triggered if error_rate > 1% for 5 minutes
```

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
**AP (Availability + Partition Tolerance)**
- Gateway must stay available even if Config DB goes down (uses in-memory cached config)
- Rate limit state can be slightly inconsistent across gateway instances (brief over-limiting acceptable)
- Circuit breaker state is per-instance (not globally synchronized) — acceptable tradeoff for speed

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Gateway tech | Envoy / custom | Nginx | Envoy is built for microservices (gRPC, health checks, circuit breaker built in) |
| Auth | JWT validation at gateway | Session lookup per request | JWT is stateless — no DB call needed; session lookup adds latency |
| Rate limiting | Centralized Redis | Per-gateway local counter | Local counter allows burst above limit across fleet; Redis ensures global accuracy |
| Config loading | In-memory cache (30s TTL) | Per-request DB lookup | Per-request DB lookup = 1M DB queries/sec — would destroy Config DB |
| Circuit breaker | Per-instance state | Global distributed state | Global state requires distributed lock (adds latency); per-instance is fast and good enough |

**What Would You Do Differently at Larger Scale?**
- **Service Mesh** (Istio/Envoy) for east-west (service-to-service) traffic, separate from gateway
- **GraphQL Federation** layer on top of gateway for flexible querying
- **Edge computing**: run gateway logic at CDN edge (Cloudflare Workers) for < 5ms global latency
- **API key management**: self-serve developer portal for external API consumers

---

### Summary Talk Track

1. "An API Gateway is the **single entry point** for all external traffic — it does auth, rate limiting, routing, and observability."
2. "Core entities: **Route** (path→service mapping), **BackendService** (instances + health), **RequestLog** (observability)."
3. "Every request goes through a **pipeline**: SSL term → Auth → Rate limit → Route → Load balance → Log."
4. "Auth: **JWT validated at gateway** (stateless, no DB call). Public keys cached from JWKS endpoint."
5. "Rate limiting: **Redis** shared across all gateway instances for global accuracy."
6. "Routing: **in-memory prefix trie** updated every 30s from Config DB — never query DB on critical path."
7. "Circuit breaker prevents **cascading failures**: if a backend is sick, reject requests fast instead of letting them pile up."
8. "Gateway is **stateless** — every instance is identical, horizontally scalable."

---

> **Previous**: [09 — Design Distributed Cache](./09-distributed-cache.md)
> **Next**: [11 — Design BookMyShow / Ticketmaster](./11-bookmyshow.md)
