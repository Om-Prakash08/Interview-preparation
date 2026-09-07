# 22. Design Google Search

> **Difficulty**: Very Hard | **Asked At**: Google, Bing, Elasticsearch, Amazon
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- Full web search or domain-specific search?
- Text search only or multi-modal (images, video, news)?
- Ranking criteria: relevance, page authority (PageRank), or personalization?
- Freshness expectations for indexing new content vs breaking news?
- Scale: expected daily search queries and web pages to index?

**Typical Interviewer Answer:** Full web text search. 8.5 Billion queries per day (100,000 queries/sec peak). Index of 60 Billion web pages. Ranking based on text relevance + PageRank authority. Latency target < 200ms.

### 1.2 Functional Requirements (FR)
1. Given a text query, return the top-10 most relevant web page results.
2. Each result item includes title, URL, and a contextual text snippet.
3. Support query operators: exact phrase matching (`"..."`) and exclusion (`-word`).
4. Near-real-time indexing for breaking news (within minutes) and batch indexing for normal web pages.
5. Rank documents based on term relevance (BM25) and link authority (PageRank).

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Query Latency** | $< 200\text{ms}$ p99 search response time |
| **Throughput** | 8.5 Billion queries/day (~100,000 queries/sec peak) |
| **Index Size** | 60 Billion documents (~200 TB compressed inverted index) |
| **Freshness** | Breaking news indexed in $< 5\text{ minutes}$ |
| **Availability** | 99.999% global availability |

### 1.4 Out of Scope
- Image / video / shopping tab vertical search
- Query autocomplete (covered in separate design)
- Knowledge graph entity cards

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────────────┐       ┌──────────────────────────┐
│   Document Metadata      │       │   Posting List Entry     │
│                          │       │                          │
│  doc_id (64-bit int)     │       │  doc_id                  │
│  url, title, snippet     │◄─────►│  term_frequency (TF)     │
│  pagerank_score          │       │  positions [int array]   │
└──────────────────────────┘       └────────────▲─────────────┘
                                                │
                                   ┌────────────┴─────────────┐
                                   │   Inverted Index Term    │
                                   │                          │
                                   │  term (e.g. "google")    │
                                   │  idf_score               │
                                   └──────────────────────────┘
```

### 2.2 Data Model / Schema

**1. Inverted Index Structure (In-Memory Posting Lists)**
```
Term: "learning"
Posting List: [
  { doc_id: 101, tf: 5, positions: [12, 45, 88] },
  { doc_id: 405, tf: 2, positions: [3, 109] }
]
-- Doc IDs stored using delta encoding [101, 304] to maximize compression efficiency.
```

**2. Document Metadata Store (Bigtable / RocksDB Key-Value)**
```
Key: doc_id:101
Value: {
  "url": "https://en.wikipedia.org/wiki/Machine_learning",
  "title": "Machine learning - Wikipedia",
  "snippet": "Machine learning is a field of study...",
  "pagerank": 8.92
}
```

> 🎯 **NFR addressed**: **Query Latency < 200ms** — Inverted Index postings stored in memory or fast NVMe SSD with delta encoding for minimal RAM footprint.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Web Search Query Interface
```
GET /api/v1/search?q=machine+learning&start=0&num=10&lang=en
Response 200 OK:
{
  "total_results": "About 4,180,000,000 results",
  "search_time_sec": 0.12,
  "results": [
    {
      "rank": 1,
      "url": "https://en.wikipedia.org/wiki/Machine_learning",
      "title": "Machine learning - Wikipedia",
      "snippet": "Machine learning is a field of inquiry devoted to understanding...",
      "last_indexed": "2026-09-06T12:00:00Z"
    }
  ]
}
```

### 3.2 Real-time Index Ingestion API
```
POST /api/v1/index/submit
{ "url": "https://news.com/breaking-event", "priority": "HIGH" }
```

> 🎯 **NFR addressed**: **Throughput** — Query API is lightweight, with front-end edge caches handling top 1% query duplicates instantly.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Index Storage**: 60 Billion pages × 10 KB raw text = 600 TB raw text. Inverted index (~30% of text size) = **~200 TB compressed inverted index**.
- **Query RPS**: 8.5B queries/day = **~100,000 queries/sec**.
- **Fan-Out Load**: 100K RPS fan-out across 1,000 index shards = **100 Million sub-queries/sec** across the index fleet.

### 4.2 Data Flow Through System

```
ONLINE QUERY SERVING PIPELINE (< 200ms)
  User Query -> API Gateway -> Query Processor
    ├─ 1. Tokenize, lowercase, stem terms ("running" -> "run")
    ├─ 2. Expand synonyms & handle spelling correction
    │
    ▼
  Index Coordinator Node (Fan-Out)
    ├─ Broadcast query terms in parallel to Document Shard Fleet
    ├─ Each shard evaluates posting lists: BM25 score = TF × IDF
    ├─ Shards return their Top-K candidate doc_ids
    │
    ▼
  Global Merger & Ranker
    ├─ Merge candidate doc_ids from all shards
    ├─ Compute Final Score = BM25_score × PageRank_score × Freshness_score
    ├─ Sort candidates and pick Top 10
    │
    ▼
  Snippet Generator & Document Metadata Lookup
    ├─ Fetch title, URL, and dynamic contextual snippet from Metadata Store
    └─ Return JSON response to User
```

> 🎯 **NFR addressed**: **Freshness** — Separate Real-time index tier ingests breaking news into memory in < 5 minutes without touching main disk index shards.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                                  ┌───────────────────────────┐
                                  │      User Browser / App   │
                                  └─────────────┬─────────────┘
                                                │ GET /search
                                                ▼
                                  ┌───────────────────────────┐
                                  │    Query Processor Node   │
                                  │  (Tokenize, Stem, Expand) │
                                  └─────────────┬─────────────┘
                                                │
                                                ▼
                                  ┌───────────────────────────┐
                                  │    Search Coordinator     │
                                  └──────┬─────────────┬──────┘
                                         │ Parallel    │ Parallel
                                         ▼ Fan-out     ▼ Fan-out
┌───────────────────────────┐   ┌───────────────────────────┐   ┌───────────────────────────┐
│ Real-Time Index Tier      │   │ Main Index Shard 1        │   │ Main Index Shard N        │
│ (In-Memory, News < 5 min) │   │ (Doc IDs 0 - 1B)          │   │ (Doc IDs 59B - 60B)       │
└─────────────┬─────────────┘   └─────────────┬─────────────┘   └─────────────┬─────────────┘
              │                               │                               │
              └───────────────────────┬───────┴───────────────────────────────┘
                                      │ Top-K Candidates
                                      ▼
                        ┌───────────────────────────┐
                        │   Scoring & Ranking Engine│
                        │   BM25 + PageRank + ML    │
                        └─────────────┬─────────────┘
                                      │ Top 10 Doc IDs
                                      ▼
                        ┌───────────────────────────┐
                        │   Metadata & Snippet Store│
                        │   (Bigtable / RocksDB)    │
                        └───────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Query Processor** | Normalizes query text | Stemming and spell check improve recall for search terms |
| **Search Coordinator** | Handles query fan-out & merge | Coordinates parallel calls across 1,000 index shards |
| **Inverted Index Shards**| Serves term postings | In-memory posting lists enable sub-millisecond term intersection |
| **PageRank Engine** | Offline graph authority calculator | MapReduce job computes global link authority scores across web graph |
| **Real-Time Index Tier**| Indexing breaking news | Handles high-write news stream without locking static main shards |

> 🎯 **NFR addressed**: **Availability 99.999%** — Shard replication with 3 read-replicas per shard prevents single-node hardware failure from taking down search.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Ranking Formula (BM25 + PageRank Integration)

$$\text{Final Score}(q, d) = \sum_{t \in q} \text{BM25}(t, d) \times \log(\text{PageRank}(d) + 1) \times \text{Freshness}(d)$$

1. **BM25 (Best Matching 25 - Text Relevance)**:
   $$\text{BM25}(t, d) = \text{IDF}(t) \cdot \frac{\text{TF}(t, d) \cdot (k_1 + 1)}{\text{TF}(t, d) + k_1 \cdot \left(1 - b + b \cdot \frac{|d|}{\text{avgdl}}\right)}$$
   - Prevents term frequency saturation (having 1,000 occurrences of a word doesn't make a page 1,000x more relevant).

2. **PageRank (Global Link Authority)**:
   $$\text{PR}(A) = (1 - d) + d \sum_{B \in \text{Inlinks}(A)} \frac{\text{PR}(B)}{\text{Outlinks}(B)}$$
   - Calculated offline via Spark/MapReduce jobs over 60 Billion pages.

---

### Deep Dive 2: Index Sharding Strategy (Document-Based Sharding)

**Why Document-Based Sharding (Partitioning by Doc ID):**
```
Document-Based Sharding:
  - Shard 1 contains ALL terms for Doc IDs 0 to 1 Billion.
  - Shard 2 contains ALL terms for Doc IDs 1 to 2 Billion.

Query Execution:
  - Search Coordinator sends query "machine learning" to ALL Shards in parallel.
  - Each shard computes top candidates for its doc range.
  - Coordinator merges local top candidates -> global top 10.
  - Advantage: Adding new documents is trivial; no single term hotspot blocks a single machine.
```

---

### Deep Dive 3: Performance & Latency Optimization (< 200ms SLA)

1. **Result Caching (Redis)**: Top 1% of queries account for ~50% of traffic. Cache full SERP results in Redis with 1-hour TTL.
2. **WAND (Weak AND) Algorithm**: Skip evaluating posting list documents whose upper-bound score cannot exceed the current $k$-th best candidate score. Yields 5x throughput boost.
3. **Delta & Variable-Byte Encoding**: Compress posting list integer IDs ($[100, 102, 105] \rightarrow \Delta [100, 2, 3]$) to save 80% RAM.

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **Index Layout** | Inverted Index | Relational / B-Tree DB | Inverted index gives $O(1)$ term lookup vs full table scans |
| **Sharding** | Document-Based | Term-Based | Term-based creates hot shards for common words ("the", "weather") |
| **Architecture** | Two-Tier (Real-time + Main) | Single-Tier Index | Real-time tier allows indexing news in minutes without expensive main index rebuilds |

---

### Summary Talk Track

1. "Google Search uses an **Inverted Index Architecture** divided into an offline indexing pipeline and online query serving."
2. "To achieve **< 200ms latency** across **60 Billion documents**, we use document-based sharding and the **WAND algorithm** to prune weak candidates."
3. "Ranking blends **BM25 text relevance** with offline **PageRank link authority**."
4. "Freshness is solved via a **Two-Tier Index**: an in-memory Real-Time index for breaking news (< 5 min) alongside the main static index."

---

> **Previous**: [21 — Design Recommendation System](./21-recommendation-system.md)
> **Next**: [23 — Design Live Streaming](./23-live-streaming.md)
