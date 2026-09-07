# 21. Design Recommendation System

> **Difficulty**: Hard | **Asked At**: Netflix, Amazon, Spotify, YouTube, LinkedIn
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- What type of items are being recommended? (Movies / Videos like Netflix)
- What feedback signals are available? (Explicit ratings + implicit watch time/clicks)
- How do we handle cold start for new users or new items?
- What are the serving latency requirements? (< 100ms)
- Do we need real-time recommendations or precomputed batch recommendations?
- Scale: How many users and catalog items?

**Typical Interviewer Answer:** Movie recommendations for 200 Million active users, 50,000 catalog items. Support explicit ratings (1-5 stars) and implicit feedback (watch completion %, clicks, skips). Serve recommendations in < 100ms. Handle cold-start gracefully.

### 1.2 Functional Requirements (FR)
1. **Personalized Top-K Recommendations**: Generate personalized top 20 movie recommendations for homepage rows.
2. **Multi-Signal Ingestion**: Ingest explicit user ratings and implicit user interactions (clicks, watch time, skips).
3. **Cold Start Strategy**: Recommend relevant content for new users with zero history and newly added catalog titles.
4. **Diversity & Business Rules**: Enforce content diversity (avoid recommending 10 movies of identical sub-genres) and apply filters (remove already-watched titles).

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Serving Latency** | $< 100\text{ms}$ end-to-end (cached precomputed recs $< 10\text{ms}$) |
| **Freshness** | Recommendations update within 1 hour of significant user watch activity |
| **Scale** | 200M DAU, 5 homepage loads/day = 1B rec requests/day (~11,500 requests/sec avg) |
| **Availability** | 99.99% homepage recommendation availability |

### 1.4 Out of Scope
- Ad bidding / real-time dynamic pricing
- Trending / Global Top 10 lists (handled by separate aggregation system)

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────────────┐       ┌──────────────────────────┐       ┌──────────────────────────┐
│   User Embedding Vector  │       │  Item Embedding Vector   │       │  User Feedback Event     │
│                          │       │                          │       │                          │
│  user_id                 │       │  item_id                 │       │  user_id, item_id        │
│  vector [128 floats]     │       │  vector [128 floats]     │       │  event_type (watch/click)│
│  updated_at              │       │  updated_at              │       │  weight (0.0 to 1.0)     │
└────────────┬─────────────┘       └────────────┬─────────────┘       └──────────────────────────┘
             │                                  │
             └────────────────┬─────────────────┘
                              │ Dot Product Vector Match
                              ▼
               ┌──────────────────────────────┐
               │ Candidate Recommendations    │
               │ user_id -> [item_ids, scores]│
               └──────────────────────────────┘
```

### 2.2 Data Model / Schema

**1. `user_recommendations_cache` (Redis - Precomputed Top-K)**
```
Key: recs:user:{user_id}
Value: JSON String [ {"item_id": "m_101", "score": 0.95}, {"item_id": "m_202", "score": 0.91} ]
TTL: 3600 seconds (1 hour)
```

**2. `user_vectors` & `item_vectors` (Milvus / FAISS Vector DB or Feature Store)**
```
User Embedding Collection: { user_id: INT, vector: FLOAT[128], last_active: TIMESTAMP }
Item Embedding Collection: { item_id: INT, vector: FLOAT[128], genre: VARCHAR, release_year: INT }
```

**3. `user_events_stream` (Kafka Event Schema)**
```json
{
  "user_id": "u_999",
  "item_id": "m_555",
  "event_type": "watch_complete",
  "implicit_weight": 1.0,
  "timestamp": 1722000000
}
```

> 🎯 **NFR addressed**: **Serving Latency < 10ms** — Precomputed recommendations stored in Redis cache serve 90% of requests instantly.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Get Personalized Recommendations
```
GET /api/v1/recommendations/{user_id}?category=homepage&limit=20
Response 200 OK:
{
  "user_id": "u_999",
  "recommendations": [
    {
      "item_id": "m_101",
      "title": "Interstellar",
      "score": 0.96,
      "reason": "Because you watched Inception"
    }
  ],
  "ttl_sec": 3600
}
```

### 3.2 Ingest User Interaction Event
```
POST /api/v1/events
{
  "user_id": "u_999",
  "item_id": "m_101",
  "event_type": "watch_partial",
  "watch_percentage": 0.75,
  "timestamp": 1722000000
}
Response: 202 Accepted
```

> 🎯 **NFR addressed**: **Scale & Latency** — Interaction ingestion is an asynchronous fire-and-forget API pushing directly to Kafka.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Serving RPS**: 200M DAU × 5 requests/day = 1 Billion requests/day = **~11,500 RPS avg**.
- **Vector DB Size**: 200M users × 128 floats × 4 bytes = **~100 GB RAM** for user vectors. 50K items × 128 floats × 4 bytes = **~25 MB RAM** (tiny catalog vector footprint).
- **Training Event Throughput**: 200M users × 10 interaction events/day = 2 Billion events/day = **~23,000 events/sec** → Kafka ingestion.

### 4.2 Data Flow Through System

```
TRAINING & FEATURE PIPELINE (Offline / Asynchronous)
  User Interaction Events -> Kafka -> Flink Feature Store -> Spark Model Trainer
    ├─ Matrix Factorization (ALS) / Two-Tower Neural Network
    └─ Outputs updated User Embeddings & Item Embeddings -> Vector DB / Feature Store

SERVING PIPELINE (Real-Time Retrieval < 100ms)
  Client Homepage -> GET /recommendations/{user_id}
    │
    ├─ 1. Check Redis Cache (`recs:user:{user_id}`)
    │      ├─ Cache HIT (90%): Return Top-20 immediately (< 10ms)
    │      └─ Cache MISS: Fallback to Two-Stage Recommendation Engine
    │
    ▼ Two-Stage Engine (Fallback / Real-Time Computation)
  Stage 1: Candidate Retrieval (ANN Search)
    ├─ Fetch User Embedding vector from Feature Store
    └─ Perform Approximate Nearest Neighbor (ANN) search via FAISS against 50,000 items -> Retrieve Top 500 candidates (~5ms)
    │
  Stage 2: Heavy Re-Ranking & Filtering
    ├─ Apply ML Ranking model on 500 candidates (incorporate real-time context: device, time)
    ├─ Filter out already-watched titles (Redis Bloom filter per user)
    ├─ Apply Maximal Marginal Relevance (MMR) for genre diversity (~50ms)
    └─ Write Top-20 result to Redis Cache & return to user
```

> 🎯 **NFR addressed**: **Serving Latency < 100ms** — Two-stage retrieval cuts catalog scoring effort from 50,000 items to 500 candidates in ~5ms.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                                  ┌───────────────────────────┐
                                  │      Client Web / App     │
                                  └─────────────┬─────────────┘
                                                │ GET /recommendations
                                                ▼
                                  ┌───────────────────────────┐
                                  │   Recommendation Service  │
                                  └──────┬─────────────┬──────┘
                                         │             │
                    ┌────────────────────┘             └────────────────────┐
                    │ Cache Hit (<10ms)                                     │ Cache Miss (<100ms)
                    ▼                                                       ▼
      ┌───────────────────────────┐                           ┌───────────────────────────┐
      │   Redis Precomputed Cache │                           │   Two-Stage Engine        │
      │   recs:user:{user_id}     │                           │   (FAISS ANN + ML Ranker) │
      └───────────────────────────┘                           └─────────────┬─────────────┘
                                                                            │ Read embeddings
                                                                            ▼
┌───────────────────────────┐                           ┌───────────────────────────┐
│   User Interaction Events │                           │   Feature Store / Vector  │
│   (Clicks, Watch Time)    │                           │   (User & Item Embeddings)│
└─────────────┬─────────────┘                           └─────────────▲─────────────┘
              │                                                       │
              ▼                                                       │ Write embeddings
┌───────────────────────────┐                           ┌─────────────┴─────────────┐
│   Kafka Stream & Flink    ├──────────────────────────►│   Spark Offline Trainer   │
└───────────────────────────┘                           │   (Nightly Two-Tower Model│
                                                        └───────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Redis Cache** | Precomputed recommendations store | Sub-10ms response for 90% of user request traffic |
| **FAISS ANN Index** | Candidate Retrieval (Stage 1) | Retrieves top 500 candidates from 50K catalog in ~5ms |
| **ML Ranking Model** | Candidate Re-Ranking (Stage 2) | Applies cross-features, context, and diversity penalty |
| **Two-Tower Neural Net**| Embedding Generation | Generates dual 128-dim vectors (User Tower & Item Tower) |
| **User History Filter** | Watched content exclusion | Redis Bloom filter prevents recommending already-watched titles |

> 🎯 **NFR addressed**: **Freshness & Availability** — Background hourly batch workers refresh Redis cache so active users rarely hit cache misses.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Deep Learning Two-Tower Model Architecture

```
USER TOWER                                      ITEM TOWER
User ID, Demographics, Recent Watched          Item ID, Genre, Cast, Metadata
          │                                               │
          ▼ (Deep Neural Nets)                            ▼ (Deep Neural Nets)
User Embedding Vector u [128-dim]              Item Embedding Vector v [128-dim]
          │                                               │
          └───────────────────────┬───────────────────────┘
                                  │ Dot Product (u · v)
                                  ▼
                        Predicted Affinity Score
```
- **Serving Trick**: Item embeddings ($v$) are pre-computed offline since the catalog (50K) changes infrequently. Only user embedding ($u$) is queried dynamically.

---

### Deep Dive 2: Candidate Retrieval via Approximate Nearest Neighbor (ANN)

**Problem**: Computing vector dot product ($u \cdot v$) against 50,000 items per user request at 11,500 RPS requires 74 Billion operations/sec.

**Solution: FAISS Inverted File Index (IVF)**
```
1. Clustering: Partition 50,000 item vectors into 256 cluster centroids via K-Means offline.
2. Indexing: Assign each item vector to its nearest centroid.
3. Querying:
   - Calculate distance from User Vector to 256 centroids.
   - Select ONLY the top 8 closest clusters.
   - Search items within those 8 clusters (~1,500 items instead of 50,000).
   - Result: 30x speedup with 98% recall accuracy!
```

---

### Deep Dive 3: Cold Start Mitigation Strategies

```
Problem: New user has 0 watch history -> Vector DB lookup returns zero embedding.

Multi-Tiered Fallback Strategy:
1. New User (Zero History):
   - Tier 1: Survey Onboarding (User picks 3 favorite genres -> Seed initial user embedding).
   - Tier 2: Demographic CF (Recommend top titles watched by users in same location/age group).
   - Tier 3: Popularity Fallback ("Top 10 Movies in your Country").

2. New Item (Newly added movie with 0 watches):
   - Content-Based Transfer: Pass movie metadata (genre, cast, description) through Item Tower to generate immediate Item Embedding $v$.
   - Exploration Slot ($\epsilon$-Greedy): Show new item to 5% of users in position 3 to gather interaction data rapidly.
```

---

### Deep Dive 4: Genre Diversity via Maximal Marginal Relevance (MMR)

**Problem**: Raw dot-product scoring recommends 20 Sci-Fi space movies if user watched *Interstellar*.

**Solution: MMR Re-Ranking Algorithm**
$$MR = \arg\max_{i \in R} \left[ \lambda \cdot \text{Sim}_1(\text{User}, i) - (1 - \lambda) \cdot \max_{j \in S} \text{Sim}_2(i, j) \right]$$
- $\text{Sim}_1$: Relevance score to user.
- $\text{Sim}_2$: Similarity score between candidate item $i$ and already selected recommendation $j$.
- $\lambda = 0.7$: Balances high relevance ($70\%$) with genre diversity ($30\%$).

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **Candidate Search**| FAISS ANN Index | Brute-force Dot Product | FAISS gives 30x speedup with negligible (2%) accuracy loss |
| **Serving Model** | Two-Stage (Retrieve + Rank) | Single-stage Deep Net | Single heavy model on 50K catalog violates < 100ms latency SLA |
| **Architecture** | Precomputed Redis Cache | 100% Real-Time Scoring | Precomputing homepage recs reduces cluster compute cost by 90% |

---

### Summary Talk Track

1. "We design a scalable recommendation system using a **Two-Stage Architecture**: **Candidate Retrieval** followed by **Heavy Re-Ranking**."
2. "Stage 1 uses a **Two-Tower Neural Network** with **FAISS ANN Indexing** to reduce 50,000 items to 500 candidates in **~5ms**."
3. "Stage 2 applies **Maximal Marginal Relevance (MMR)** to prevent genre monotony and filters watched titles via **Redis Bloom Filters**."
4. "To meet sub-10ms SLAs at 11,500 RPS, we **precompute homepage recommendations in Redis**, covering 90% of requests with cache hits."

---

> **Previous**: [20 — Design Kafka](./20-kafka-message-queue.md)
> **Next**: [22 — Design Google Search](./22-google-search.md)
