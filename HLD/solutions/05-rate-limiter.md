# 5. Design a Rate Limiter

> **Difficulty**: Medium-Hard | **Asked At**: Google, Amazon, Cloudflare, Stripe, Uber
> **Time to Answer in Interview**: 35–40 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Should the rate limiter be per user, per IP, per API key, or all of these?
- Do we limit globally (across all servers) or per server instance?
- What should happen when limit is hit — hard reject (429) or soft throttle (queue)?
- Should clients be notified of remaining quota (rate limit headers)?
- Is this an in-process library or a standalone service?
- Do different APIs have different rate limits?

**Scale:**
- How many requests per second do we expect?
- How many unique users/IP addresses?

**Typical Interviewer Answer:**
- Global distributed rate limiter (not per-server)
- Per-user AND per-API-endpoint rules
- Hard reject with `HTTP 429 Too Many Requests` when limit exceeded
- Return `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers
- Multiple rules: e.g., "100 req/min per user" + "1000 req/day per user"

### 1.2 Functional Requirements (FR)
1. Limit number of requests a client can make within a time window
2. Support multiple rate limit rules (per user, per IP, per endpoint)
3. Return `429 Too Many Requests` when limit is exceeded
4. Return rate limit info in response headers
5. Support different limits for different API tiers (free vs paid)

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Accuracy** | Rate limits must be enforced correctly across all servers |
| **Latency** | Rate limit check must add < 5ms to every request |
| **Availability** | If rate limiter fails, fail open (allow traffic through) vs fail closed (block all) |
| **Scalability** | Must work correctly whether you have 1 or 1000 API servers |

### 1.4 Out of Scope
- DDoS protection (a different problem — handled at network layer)
- API key management
- Billing per API call

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌─────────────────┐       ┌──────────────────┐       ┌──────────────────┐
│  RateLimitRule   │       │  TokenBucket     │       │  RateLimitConfig │
│                  │       │  (per user/key)  │       │                  │
│ rule_id          │       │ key (user+endpt) │       │ tier (free/paid) │
│ endpoint         │──────►│ tokens_remaining │◄──────│ rules[]          │
│ limit            │       │ last_refill_time │       │ api_key          │
│ window_seconds   │       │ capacity         │       │                  │
│ tier             │       │ refill_rate      │       │                  │
└─────────────────┘       └──────────────────┘       └──────────────────┘
```

**Primary entities**: `RateLimitRule` (defines the limit per endpoint/tier), `TokenBucket` (runtime state per user), `RateLimitConfig` (tier → rules mapping).

### 2.2 Data Model (Redis)

**Token Bucket in Redis (Atomic with Lua script):**

```lua
-- lua script runs atomically in Redis (no race condition)
local key = KEYS[1]               -- "ratelimit:user:123"
local capacity = tonumber(ARGV[1]) -- 100
local refill_rate = tonumber(ARGV[2]) -- 10 tokens/sec
local now = tonumber(ARGV[3])      -- current timestamp

local data = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(data[1]) or capacity
local last_refill = tonumber(data[2]) or now

local elapsed = now - last_refill
local new_tokens = elapsed * refill_rate
tokens = math.min(capacity, tokens + new_tokens)

if tokens >= 1 then
  tokens = tokens - 1
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  redis.call('EXPIRE', key, 3600)
  return 1  -- allowed
else
  redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
  return 0  -- rejected
end
```

**Why Lua?** Redis executes Lua scripts atomically — no other command runs between the read and write. This prevents race conditions without distributed locks.

**Redis Key Schema:**
- Per user, per endpoint counter: key = `{user_id}:{endpoint}:{window}`
- Value = integer count + TTL
- 10 million active users × 10 endpoints × 8 bytes = **~800 MB** → fits in Redis easily

> 🎯 **NFR addressed**: **Accuracy** — Lua script ensures atomic check-and-update across all servers. **Latency** — Redis in-memory ops add < 1ms. **Scalability** — Redis handles 1M+ ops/sec, well above the ~20K peak request rate.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Rate Limiter as Middleware (not a user-facing API)

The rate limiter sits **between the API Gateway and backend services**. Every request passes through it.

### 3.2 Response Headers (always return these)
```
HTTP/1.1 200 OK
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 63
X-RateLimit-Reset: 1722000060    // Unix timestamp when window resets

-- When rejected:
HTTP/1.1 429 Too Many Requests
Retry-After: 30
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1722000060
```

### 3.3 Rules Config API (internal)
```
GET  /api/v1/rate-limit/rules                     // list all rules
POST /api/v1/rate-limit/rules                     // create/update rule
  Body: { "endpoint": "/api/v1/tweets", "tier": "free", "limit": 100, "window_seconds": 60 }
```

> 🎯 **NFR addressed**: **Availability** — rate limit headers help clients self-regulate, reducing unnecessary retries. **Accuracy** — `Retry-After` header prevents thundering herd on window reset.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Request Volume:**
- Assume 500 million API requests/day
- = 500M / 86,400 ≈ **~5,800 requests/sec** average
- Peak: ~20,000 requests/sec

**Rate Limiter Overhead:**
- Every request → 1 Redis read + 1 Redis write (to check and update counter)
- Redis can handle **1 million ops/sec** → more than enough
- Rate limit check: ~1ms (Redis round trip) → acceptable latency overhead

### 4.2 Data Flow Through System

**Every Request Flow:**
```
Client → API Gateway → Rate Limiter Middleware
  → Extract user_id/IP from request
  → Lookup applicable rules from config (cached in memory)
  → Execute Lua script on Redis (atomic token bucket check)
  → ALLOW? → Forward to backend API servers → Return 2xx + rate limit headers
  → REJECT? → Return HTTP 429 + Retry-After header (never reaches backend)
```

> 🎯 **NFR addressed**: **Latency** — single Redis round-trip (~1ms) is well within the 5ms budget. **Scalability** — Redis handles all servers' rate-limit checks centrally.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                    ┌──────────────────────────────────────┐
                    │           API CLIENTS                │
                    └──────────────────┬───────────────────┘
                                       │ HTTP Request
                               ┌───────▼────────┐
                               │  API Gateway   │
                               │  (or Nginx)    │
                               └───────┬────────┘
                                       │ (every request passes through)
                               ┌───────▼──────────────┐
                               │   Rate Limiter       │
                               │   Middleware         │
                               │                      │
                               │  1. Extract user_id  │
                               │     or IP            │
                               │  2. Check rules      │
                               │  3. Call Redis       │
                               │  4. Allow or reject  │
                               └───────┬──────────────┘
                                       │
               ┌───────────────────────┼──────────────────────────┐
               │                       │                          │
        ALLOW (2xx)           REJECT (429)                  Redis Cluster
               │                       │                          │
      ┌────────▼───────┐    ┌──────────▼────────┐   ┌────────────▼──────────┐
      │  Backend API   │    │ Response:          │   │  Stores rate limit    │
      │  Servers       │    │ HTTP 429           │   │  counters per user    │
      └────────────────┘    │ Retry-After: 30s   │   │  Token bucket state   │
                            │ X-RateLimit-Limit  │   │  (in-memory, fast)    │
                            │ X-RateLimit-Reset  │   └───────────────────────┘
                            └───────────────────┘

                    ┌───────────────────────────────────┐
                    │       Rules Config Service        │
                    │  (defines limits per tier/API)    │
                    │  Stored in DB, cached in memory   │
                    └───────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **API Gateway** | Entry point for all requests; hosts rate limiter middleware | Centralized enforcement before requests reach backends |
| **Rate Limiter Middleware** | Executes rate limit check on every request | Inline middleware for minimal latency; no extra network hop |
| **Redis Cluster** | Stores token bucket state per user/endpoint | In-memory for < 1ms ops; centralized for global accuracy |
| **Rules Config Service** | Stores and serves rate limit rules per tier/endpoint | Cached in-memory at gateway; updated on config change |
| **Backend API Servers** | Process allowed requests | Never see rejected traffic — rate limiter shields them |

> 🎯 **NFR addressed**: **Accuracy** — centralized Redis ensures all API servers share one counter. **Latency < 5ms** — Redis Lua script executes in < 1ms. **Availability** — Redis Cluster with replicas for HA. **Scalability** — Redis handles 1M+ ops/sec.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Algorithm Comparison (MOST IMPORTANT for this question)

This is the heart of the interview. Know all 4 algorithms:

#### Algorithm 1: Fixed Window Counter (Simple but flawed)
```
Rule: Max 100 requests per minute per user

Counter key: user:{user_id}:minute:{current_minute}

On each request:
  count = INCR user:123:minute:720   // 720 = minute of day
  SET EXPIRE 60 seconds
  if count > 100: reject (429)
  else: allow
```

**Problem — Boundary Burst:**
```
Minute 1 (12:00:00 – 12:00:59):  100 requests at 12:00:59  → allowed
Minute 2 (12:01:00 – 12:01:59):  100 requests at 12:01:01  → allowed
Result: 200 requests in 2 seconds! (around the minute boundary)
```
**Verdict**: Simple to implement, but the boundary burst can be 2× the intended limit. ❌ for strict limiting.

#### Algorithm 2: Sliding Window Log (Accurate but memory heavy)
```
Store a sorted set of request timestamps for each user.

On each request:
  1. Remove all timestamps older than (now - 1 minute)
  2. Count remaining timestamps
  3. If count >= 100: reject (429)
  4. Else: add current timestamp to the set, allow

Redis: ZSET user:123:requests
  ZREMRANGEBYSCORE 0 (now - 60000)  // remove old entries
  count = ZCARD
  if count >= 100: reject
  ZADD now
```

**Advantage**: Perfectly accurate — no boundary burst issue.
**Problem**: Stores every single request timestamp. 100 req/min × 10M users = **1 billion timestamps in Redis** → too much memory.
**Verdict**: Accurate but doesn't scale well for high traffic. ⚠️

#### Algorithm 3: Sliding Window Counter (Best balance) ✅
```
Combines fixed window simplicity with sliding window accuracy.

Approximate the sliding window using two adjacent fixed windows:

current_window_count = exact count in current window
previous_window_count = exact count in previous window

weight = (time remaining in current window) / window_size
effective_count = previous_window_count × weight + current_window_count

if effective_count > limit: reject (429)
```

**Example:**
```
Limit: 100 req/min
Previous minute count: 80 requests
Current minute count: 30 requests (40 seconds into current minute)
Weight = (60 - 40) / 60 = 0.33

Effective count = 80 × 0.33 + 30 = 26.4 + 30 = 56.4 → ALLOW
```

**Verdict**: Very accurate (within 0.003% error), memory-efficient (only 2 counters per window), highly recommended. ✅

#### Algorithm 4: Token Bucket ✅ (Most common in production)
```
Metaphor: A bucket holds N tokens. Tokens are added at rate R per second.
Each request consumes 1 token. If bucket is empty: reject.

Parameters:
  bucket_capacity = 100   (max burst)
  refill_rate     = 10    (tokens per second, = 600/min)

State stored per user in Redis:
  tokens_remaining: float
  last_refill_time: timestamp

On each request:
  elapsed = now - last_refill_time
  new_tokens = elapsed × refill_rate
  tokens = min(capacity, tokens_remaining + new_tokens)
  last_refill_time = now

  if tokens >= 1:
    tokens -= 1
    allow request
  else:
    reject (429)
```

**Advantage**: Allows **controlled bursting** — if user was quiet for a minute, they can burst 100 requests instantly. Good for API clients that batch requests.
**Used by**: Stripe, AWS, Shopify APIs.

#### Algorithm 5: Leaky Bucket (Smooth output rate)
```
Requests go into a FIFO queue. Queue drains at a fixed rate.

On each request:
  if queue.size() < capacity: enqueue request
  else: reject (429)
  
Background thread: dequeue and process at fixed rate (e.g., 10 req/sec)
```

**Advantage**: Smooths out bursty traffic — always processes at constant rate.
**Problem**: Adds latency (requests wait in queue). Not suitable for APIs where latency matters.
**Used for**: Outbound rate limiting to third-party APIs (don't overwhelm external services).

---

### Deep Dive 2: Distributed Rate Limiting (The Hard Part)

**Problem**: If you have 10 API servers, each counting independently, a user can make 10× the limit!

```
Limit: 100 req/min per user
Server 1 sees: 90 requests → allows
Server 2 sees: 90 requests → allows
User actually made 180 requests!
```

**Solutions:**

**Option A: Centralized Redis (recommended)**
- All servers check the same Redis cluster
- Atomic Lua script → globally consistent count
- Downside: Redis is on the critical path (adds ~1ms per request)

**Option B: Sticky Sessions (simpler)**
- Load balancer routes user → always same server (by consistent hashing on user_id)
- Each server maintains local count
- Problem: server failures, load imbalance

**Option C: Gossip Protocol (eventual consistency)**
- Servers periodically share counts with each other (gossip)
- Eventually consistent — may temporarily allow slightly over limit
- Good for soft limits where slight over-counting is acceptable

**Recommended**: Centralized Redis with Lua atomicity for strict limits.

---

### Deep Dive 3: Race Condition Prevention

**Problem**: Two requests arrive simultaneously:
```
Server A: reads count = 99 → ok → will write 100
Server B: reads count = 99 → ok → will write 100
Both allowed! Actual count = 100 + 1 request over limit
```

**Solution**: Use Redis **atomic operations**:
- `INCR` (atomic increment) + check return value
- Or Lua script (entire check-and-update as single atomic operation)
- Or Redis `MULTI/EXEC` (transaction) — but watch out for CAS failures under contention

---

### Deep Dive 4: Handling Redis Failure (Fail Open vs Fail Closed)

If Redis goes down:
- **Fail Open**: Let all requests through (no rate limiting). Risk: abuse. But at least your API keeps working.
- **Fail Closed**: Reject all requests. Risk: complete outage when Redis is briefly unavailable.

**Recommended**: **Fail Open** with alerting. Rate limiting is a protection mechanism — a brief window of unprotected access is less catastrophic than a total API outage.

Also: use **Redis Sentinel** or **Redis Cluster** for HA to minimize Redis downtime.

---

### Deep Dive 5: Multi-level Rate Limiting

In production, combine multiple rules:
```
Rule 1: 100 requests/minute per user
Rule 2: 5000 requests/day per user
Rule 3: 10 requests/second per IP (DDoS protection)
Rule 4: 1000 requests/hour per API key (business tier)

For each request: check ALL rules. Reject if ANY rule is violated.
```

Each rule = separate Redis key with appropriate TTL.

---

### Trade-offs & Alternatives

**Which Algorithm to Recommend in Interview:**

| Algorithm | Pros | Cons | Use When |
|---|---|---|---|
| Fixed Window | Simple, fast | Boundary burst (2×) | Low-stakes limits |
| Sliding Window Log | Perfectly accurate | High memory usage | Low traffic, strict accuracy |
| **Sliding Window Counter** | Accurate, memory efficient | Approximate (not exact) | **General purpose (recommend this)** |
| **Token Bucket** | Allows burst, intuitive | Slightly more complex | **API rate limiting (recommend this)** |
| Leaky Bucket | Smooth output | Adds latency | Outbound request throttling |

**CAP Theorem Position:**
- **CP** for strict rate limiting (correct count > availability)
- **AP** for soft limits (approximate enforcement acceptable)

**Key Trade-offs:**

| Decision | Choice | Reasoning |
|---|---|---|
| Central vs local | Centralized Redis | Local counting fails in distributed setup |
| Redis failure | Fail open | API availability > strict rate limiting |
| Algorithm | Token Bucket or Sliding Window Counter | Best balance of accuracy and burst handling |
| Atomicity | Lua scripts | Prevents race conditions without distributed locks |

---

### Summary Talk Track

1. "A rate limiter has two parts: **the algorithm** and **the distributed enforcement**."
2. "Core entities: **RateLimitRule** (config), **TokenBucket** (runtime state per user in Redis)."
3. "For algorithm: I'd use **Token Bucket** — allows controlled bursts, easy to tune."
4. "For distribution: **centralized Redis** so all API servers share the same counter."
5. "Atomicity is critical — I'd use a **Lua script** in Redis to prevent race conditions."
6. "On Redis failure: **fail open** — brief unprotected window is better than full outage."
7. "Headers: always return `X-RateLimit-Remaining` and `Retry-After` — good API design."
8. "Multi-tier: support different limits per user tier (free vs paid) via a rules config service."

---

> **Previous**: [04 — Design YouTube](./04-youtube.md)
> **Next**: [06 — Design Google Drive / Dropbox](./06-google-drive.md)
