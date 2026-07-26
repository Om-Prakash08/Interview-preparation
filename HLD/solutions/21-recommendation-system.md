# 21. Design Recommendation System

> **Difficulty**: Hard | **Asked At**: Netflix, Amazon, Spotify, YouTube, LinkedIn
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- What are we recommending? Products? Movies? Songs? Articles? Friends?
- What signals do we use? Explicit (ratings) vs implicit (clicks, time spent)?
- Cold start problem: new users with no history?
- Real-time recommendations (homepage) or batch (email digest)?
- Diversity requirement? (don't recommend same genre 10 times in a row)

**Scale:**
- How many users?
- How many items to recommend from?
- How often are recommendations refreshed?

**Typical Interviewer Answer:**
- Movie/video recommendations (like Netflix)
- Both explicit (ratings) + implicit (watch time, clicks) signals
- 200 million users, 50,000 titles
- Homepage recommendations refreshed every hour
- New user cold start must be handled

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. For a given user, generate a personalized list of recommended items (top-K)
2. Recommendations consider both explicit (ratings) and implicit feedback (watch time)
3. Handle cold start: new users with no history get reasonable recommendations
4. Recommendations reflect recent activity (last watched movie influences next recommendation)
5. Diversity: don't recommend only one type of content

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Freshness** | Recommendations updated within 1 hour of user activity |
| **Latency** | Serve recommendations in < 100ms |
| **Scale** | 200M users × recommendations refresh per hour = 200M rec computations/hour |
| **Coverage** | All users get recommendations (including cold-start users) |

### Out of Scope
- Real-time bid optimization (ads)
- Trending recommendations (different system)
- A/B testing framework (mention as extension)

---

## SECTION 3 — Capacity Estimation

### Recommendation Generation
- 200M users × 1 regen/hour = **~56,000 regen jobs/sec** (if spread evenly)
- Each regen: score 50,000 titles per user — needs optimization (candidate retrieval)

### Serving
- 200M DAU × 5 homepage loads/day = 1 billion serving requests/day
- = **~11,500 serving requests/sec**
- Precomputed recommendations → served from Redis in < 1ms

### Training Data
- 200M users × 10 events/day = 2 billion implicit feedback events/day
- = **~23,000 events/sec** → Kafka → training pipeline

### Storage
- User-item matrix: 200M users × 50K items × sparse = only ~1% filled
- Stored as (user_id, item_id, rating_or_weight) tuples: ~100B entries × 20 bytes = **~2 TB**
- User embedding vectors: 200M users × 128 dim × 4 bytes = **~100 GB**
- Item embedding vectors: 50K items × 128 dim × 4 bytes = **~25 MB** (tiny)

---

## SECTION 4 — API Design

### 1. Get Recommendations for User
```
GET /api/v1/recommendations/{user_id}?context=homepage&limit=20
Response: {
  "user_id": "u123",
  "recommendations": [
    {
      "item_id": "movie_456",
      "title": "Interstellar",
      "score": 0.95,
      "reason": "Because you watched Inception"
    }
  ],
  "generated_at": "2025-07-26T10:00:00Z",
  "ttl_sec": 3600
}
```

### 2. Record User Event (Feedback Signal)
```
POST /api/v1/events
{
  "user_id": "u123",
  "item_id": "movie_456",
  "event_type": "watch_complete",   // or "watch_partial", "click", "rating", "skip"
  "value": 1.0,                     // for rating: 1-5, for watch: fraction completed
  "timestamp": 1722000000000
}
→ 202 Accepted (fire and forget)
```

### 3. Rate an Item (Explicit Feedback)
```
POST /api/v1/ratings
{
  "user_id": "u123",
  "item_id": "movie_456",
  "rating": 4.5
}
→ 200 OK
```

---

## SECTION 5 — Core Recommendation Algorithms

### Algorithm 1: Collaborative Filtering (CF)

**Core idea**: "Users like you also liked this"

**User-Based CF:**
```
1. Find users similar to target user (based on rating history)
2. Recommend items those similar users liked that target hasn't seen

Similarity metric: Cosine similarity between rating vectors
  sim(u1, u2) = (ratings_u1 · ratings_u2) / (|ratings_u1| × |ratings_u2|)

Problem: 200M users × 200M users similarity matrix → too large!
```

**Item-Based CF (more scalable):**
```
1. Find items similar to items user already likes
2. Recommend those similar items

Item similarity computed offline (item space: 50K << user space: 200M)
  sim(item_A, item_B) = cosine similarity of their user rating vectors

At query time:
  → Look up items user has rated
  → For each rated item: retrieve top-K similar items
  → Aggregate scores, return top-N
```

### Algorithm 2: Matrix Factorization (MF) — The Gold Standard ✅

```
Factorize the User-Item Rating Matrix into two low-rank matrices:
  R ≈ P × Q^T

  P: User matrix (200M users × 128 latent factors)
  Q: Item matrix (50K items × 128 latent factors)

Each user u has a latent vector P[u] (128 dimensions)
Each item i has a latent vector Q[i] (128 dimensions)

Predicted rating: P[u] · Q[i]^T (dot product)

Training: minimize RMSE on known ratings:
  min Σ (R[u,i] - P[u] · Q[i])^2 + regularization

Algorithm: ALS (Alternating Least Squares) or SGD (Stochastic Gradient Descent)
  - Fix Q, solve for P (closed form)
  - Fix P, solve for Q (closed form)
  - Alternate until convergence

After training:
  For user u: top recommendations = items with highest P[u] · Q[i] score
  But 50K dot products per user is still fast (matrix multiply)
```

### Algorithm 3: Deep Learning (Two-Tower Model) — Netflix/YouTube's actual approach ✅

```
Two separate neural networks:
  User Tower: user_id, user features → 128-dim embedding
  Item Tower: item_id, item features → 128-dim embedding

Training: maximize dot product for (user, item) pairs they interacted with
          minimize for (user, random_item) pairs (negative sampling)

At serving time:
  User embedding: 1 forward pass through User Tower (fast)
  Item embeddings: pre-computed for all 50K items (updated nightly)
  Retrieval: Approximate Nearest Neighbor (ANN) search
    → Given user embedding, find top-100 items with highest cosine similarity
    → Sub-millisecond using FAISS (Facebook AI Similarity Search)
```

---

## SECTION 6 — High-Level Architecture

```
DATA COLLECTION & TRAINING PIPELINE
════════════════════════════════════════════════════════════════════

 User events (clicks, watches, ratings, skips)
       │
       ▼
 Kafka (user_events topic)
       │
 ┌─────▼───────────────────────────────────────────────────────────┐
 │              Feature Engineering Service                        │
 │  - Compute implicit weights:                                    │
 │    watch_complete = 1.0, watch_partial (50%) = 0.5             │
 │    click = 0.2, skip = -0.1                                     │
 │  - Add item features: genre, year, director, cast               │
 │  - Add user features: age_group, location, device               │
 └─────┬───────────────────────────────────────────────────────────┘
       │
       ▼
 Feature Store (Redis + S3)
   - User features: real-time (Redis, TTL 1 hour)
   - Item features: batch (S3, updated daily)
       │
 ┌─────▼───────────────────────────────────────────────────────────┐
 │            Training Pipeline (runs nightly + weekly)            │
 │                                                                 │
 │  Nightly (fast):                                                │
 │    Update user embeddings based on last 24h of events          │
 │    Keep item embeddings fixed (only user side updated)         │
 │                                                                 │
 │  Weekly (full):                                                 │
 │    Full model retrain on last 30 days of data                  │
 │    Update both user and item embeddings                        │
 │    Framework: PyTorch + Spark for distributed training         │
 └─────┬───────────────────────────────────────────────────────────┘
       │
       ▼
 Model Store (S3) → Serving Cluster (loaded into memory)

════════════════════════════════════════════════════════════════════

SERVING PIPELINE (Real-time, < 100ms)
════════════════════════════════════════════════════════════════════

 User opens Netflix homepage
       │
       ▼ GET /recommendations/{user_id}
       │
 ┌─────▼──────────────────────────────────────────────────────────┐
 │  Recommendation Service                                         │
 │                                                                 │
 │  1. Check Redis cache:                                          │
 │     recs:{user_id} → precomputed list (cached for 1 hour)      │
 │     Cache HIT (90% of requests): return immediately            │
 │     Cache MISS: generate fresh recommendations                  │
 │                                                                 │
 │  2. Two-stage retrieval + ranking:                              │
 │     STAGE 1 (Candidate Retrieval):                              │
 │       - ANN search: find top-500 items by embedding similarity  │
 │       - (using FAISS index on item embeddings)                  │
 │       - Takes ~5ms                                              │
 │                                                                 │
 │     STAGE 2 (Re-Ranking):                                       │
 │       - Run full ranking model on 500 candidates               │
 │       - Add diversity penalty (not too many same-genre items)   │
 │       - Add business rules: promote new releases, filter watched│
 │       - Takes ~50ms                                             │
 │                                                                 │
 │  3. Cache result in Redis (TTL: 1 hour)                        │
 │  4. Return top-20 recommendations                              │
 └─────────────────────────────────────────────────────────────────┘

════════════════════════════════════════════════════════════════════

 BACKGROUND PRECOMPUTATION (every hour, batch)
 ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 For all active users (logged in last 7 days):
   Run Stage 1 + Stage 2 in batch (Spark on EMR)
   Store in Redis: recs:{user_id} (TTL 70 min)
 
 Covers 95% of homepage loads from cache.
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Cold Start Problem

**New user (no watch history):**
```
Strategy 1: Popularity-based (safe default)
  → Show top-rated and most-watched content in user's region
  → "Most popular in India this week"
  → Simple, works for all new users

Strategy 2: Onboarding survey
  → Ask user: "Which genres do you like?" (action, comedy, drama)
  → Map selected genres to seed recommendations
  → Much better than pure popularity

Strategy 3: Demographic CF
  → Use user's age, location, language, device
  → Find users with similar demographics → use their preferences
  → Better than popularity, no survey needed

Strategy 4: Content-based (item features only, no user history)
  → After first watch: use features of that item to find similar items
  → Genre, director, cast, plot keywords
  → Immediately personalizes after 1 interaction
```

**New item (just added):**
```
Cold start for items:
  → No user interactions yet → can't appear in CF

Solutions:
  1. Content-based: use item features (genre, cast) to make similar to existing items
  2. Item embeddings from metadata: movie description → NLP → embedding
  3. Manual curation: editors promote new releases to a featured list
  4. Epsilon-greedy exploration: randomly show new items to 5% of users, learn quickly
```

---

### Deep Dive 2: Candidate Retrieval at Scale (ANN Search)

**Problem**: Given user embedding (128 dim), find top-500 most similar items from 50K.

**Naive approach**: compute dot product with all 50K items
- 50K × 128 = 6.4 million multiplications
- For 11,500 requests/sec: 74 billion multiplications/sec — too slow

**Solution: Approximate Nearest Neighbor (ANN)**

**FAISS (Facebook AI Similarity Search):**
```
Offline index building:
  1. All item embeddings (50K × 128) loaded into FAISS
  2. FAISS builds IVF (Inverted File Index):
     - Cluster items into 256 centroids (k-means)
     - Each item assigned to nearest centroid
     - Query: only search within top-8 closest clusters (not all 256)
     → Only ~50K/256 × 8 = ~1,560 items to compare (vs 50K)
     → 30× speedup with ~2% accuracy loss

Online query:
  1. User embedding → find 8 nearest cluster centroids
  2. Search items within those clusters (~1,560 items)
  3. Return top-500 by dot product
  4. Time: ~1ms (vs 50ms for brute force)
```

---

### Deep Dive 3: Real-Time vs Batch Recommendations

```
BATCH (nightly recompute):
  Pros: scalable (Spark), cheapest
  Cons: stale — doesn't reflect last 1 hour of behavior
  Use for: base recommendations, weekly digest emails

NEAR-REAL-TIME (hourly refresh):
  Current architecture: precompute every hour using latest features
  Pros: reasonably fresh, manageable load
  Cons: 1-hour lag
  Use for: homepage recommendations (current design)

REAL-TIME (session-based):
  User watches a movie right now → what to recommend immediately?
  Solution: Session-aware recommendations
    - Take base recommendations (batch)
    - Re-rank using current session actions (what user clicked/watched today)
    - Session context: Redis store of last 10 actions (TTL: 1 hour)
    - Re-ranking: fast (no retraining) — just boost items similar to session actions

Hybrid (Netflix's actual approach):
  Base recommendations: batch (weekly)
  User context updates: near-real-time (hourly embedding update)
  Session boost: real-time (re-rank in < 50ms using session actions)
```

---

### Deep Dive 4: Diversity in Recommendations

**Problem**: Matrix factorization might recommend 10 sci-fi movies to a sci-fi fan — boring, no exploration.

**Diversity techniques:**

**Maximal Marginal Relevance (MMR):**
```
After scoring 500 candidates:
  - Penalize candidates too similar to already-selected items
  - score_adjusted = λ × relevance - (1-λ) × max_similarity_to_selected

  λ = 0.8: mostly relevance (Netflix default)
  λ = 0.5: 50-50 relevance/diversity (exploration mode)

Result: no 2 consecutive recommendations from same genre
```

**Slot-based templates:**
```
Homepage slots:
  Row 1: "Top picks for you" (pure relevance)
  Row 2: "Because you watched [X]" (item-based CF, similar to X)
  Row 3: "New releases" (trending + business rule)
  Row 4: "Family movies" (demographic-based, if family account)

Different algorithm per row → natural diversity
```

---

### Deep Dive 5: Measuring Recommendation Quality

```
Offline metrics (during training):
  RMSE (Root Mean Square Error): prediction accuracy on held-out ratings
  Recall@K: fraction of future-liked items in top-K recommendations
  NDCG@K: Normalized Discounted Cumulative Gain (quality of ranking order)

Online metrics (A/B test in production):
  Click-Through Rate (CTR): fraction of recs that get clicked
  Watch Time per session: did recs lead to longer engagement?
  Conversion: did rec lead to subscription renewal?

The key insight (Netflix's research):
  RMSE improved 10% on rating prediction → no measurable change in online watch time
  Direct online metrics (CTR, watch time) matter more than offline metrics
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP (Availability + Partition Tolerance)**
- Slightly stale recommendations (1-hour old) are perfectly acceptable
- Better to serve cached recommendations than fail the homepage
- Recommendation quality degrades gracefully — never hard fails

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Algorithm | Two-Tower DL model | Matrix Factorization (ALS) | Two-Tower handles complex features (text, images, session); ALS is simpler and still very effective |
| Candidate retrieval | FAISS ANN | Brute-force dot product | FAISS: 30× faster with 2% accuracy loss; brute force infeasible at 11,500 rps |
| Freshness | Hourly batch refresh | Real-time user embedding | Real-time is complex; hourly is a practical balance for homepage |
| Cold start | Demographic CF + survey | Popularity only | Demographic gives better personalization; popularity is a fallback |
| Diversity | MMR re-ranking | Pure score ranking | Pure score gives repetitive genre recs; MMR improves engagement and retention |

---

## Interview Flow Summary (Talk Track)

1. "A recommendation system has three phases: **data collection → model training → serving**"
2. "Core algorithm: **Two-Tower model** — user embedding + item embedding; score = dot product"
3. "Serving is a **two-stage pipeline**: candidate retrieval (FAISS ANN, ~500 candidates) + ranking (full model, top-20)"
4. "**Precomputed in batch** (hourly for active users) → cached in Redis → homepage loads in < 5ms"
5. "**Cold start**: new users get popularity-based + demographic CF; new items get content-based features"
6. "**Diversity**: Maximal Marginal Relevance (MMR) re-ranks to avoid genre monotony"
7. "**Measure online**: CTR and watch time matter more than offline RMSE"

---

> **Previous**: [20 — Design Kafka](./20-kafka-message-queue.md)
> **Next**: [22 — Design Google Search](./22-google-search.md)
