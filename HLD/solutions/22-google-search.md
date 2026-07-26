# 22. Design Google Search

> **Difficulty**: Very Hard | **Asked At**: Google, Bing, Elasticsearch, Amazon
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- Full web search or domain-specific (only one site)?
- Just text or also images, videos, news?
- Do we need spell correction, query suggestions?
- Ranking: by relevance only, or personalized?
- Real-time indexing (minutes) or batch (days)?

**Scale:**
- How many queries per day?
- How many web pages to index?

**Typical Interviewer Answer:**
- Full web text search
- 8.5 billion queries per day (Google's actual scale)
- 60 billion web pages in the index
- Ranking: relevance (not personalized for this design)
- Near-real-time indexing for freshness (news in minutes, regular pages in days)
- Spell correction and query autocomplete out of scope (covered in Q8)

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Given a query string, return top-10 most relevant web pages
2. Results include: title, URL, snippet (excerpt from page)
3. Search results must be fresh (news indexed within minutes)
4. Support basic query operators: phrase ("exact match"), exclusion (-word)
5. Ranking by relevance + authority (PageRank-like)

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Query Latency** | < 200ms from query to results |
| **Throughput** | 8.5B queries/day = ~100,000 queries/sec |
| **Index size** | 60 billion pages |
| **Freshness** | Breaking news indexed within 5 minutes |
| **Availability** | 99.999% — Google must never be down |

### Out of Scope
- Image/video/news-specific search (separate systems)
- Ad auction/monetization
- Knowledge graph (featured snippets)
- Query autocomplete (covered in Q8)

---

## SECTION 3 — Capacity Estimation

### Index Size
- 60 billion pages × avg 10 KB text content = **600 TB** of raw text
- Inverted index (compressed): ~1/3 of raw text = **~200 TB**
- Distributed across thousands of index servers

### Query Volume
- 100,000 queries/sec
- Each query touches multiple index shards → fan-out
- Latency target: 200ms (8 seconds budget across the pipeline)

### Crawling (input to indexing)
- We already designed this in Q19 (Web Crawler)
- 10,000 pages/sec → 864M pages/day → full refresh every ~2 months

---

## SECTION 4 — Core Data Structure: Inverted Index

This is the most important concept for this question.

### Forward Index vs Inverted Index

```
Forward Index (how a document store works):
  doc_id → { title, content, words: ["apple", "banana", "cherry"] }

Inverted Index (how search engines work):
  term → list of (doc_id, positions, frequency)

Example corpus:
  Doc 1: "The quick brown fox"
  Doc 2: "The fox jumped"
  Doc 3: "Quick brown rabbit"

Inverted Index:
  "the"    → [(1, [0], 1), (2, [0], 1)]       // term → [(doc_id, positions, freq)]
  "quick"  → [(1, [1], 1), (3, [0], 1)]
  "brown"  → [(1, [2], 1), (3, [1], 1)]
  "fox"    → [(1, [3], 1), (2, [1], 1)]
  "jumped" → [(2, [2], 1)]
  "rabbit" → [(3, [2], 1)]
```

**Posting List**: the list of (doc_id, ...) for each term.

```
Storage of posting list for "fox":
  Doc IDs: [1, 2] — stored as delta-encoded integers (difference from previous)
  Delta encoding: [1, 1] (doc 1, then delta +1 for doc 2) → smaller numbers → better compression
  Compressed with variable-byte encoding or PForDelta

  Positions stored separately (optional) for phrase matching:
    "quick brown": both "quick" and "brown" must have consecutive positions
```

---

## SECTION 5 — API Design

### 1. Search Query
```
GET /api/v1/search?q=machine+learning&start=0&num=10&lang=en

Response 200:
{
  "total_results": "About 4,180,000,000 results",
  "search_time_sec": 0.14,
  "results": [
    {
      "rank": 1,
      "url": "https://en.wikipedia.org/wiki/Machine_learning",
      "title": "Machine learning - Wikipedia",
      "snippet": "Machine learning (ML) is a field of inquiry devoted to understanding...",
      "last_indexed": "2025-07-25T08:00:00Z"
    }
  ]
}
```

### 2. Submit URL for Indexing (Webmaster tool)
```
POST /api/v1/index/submit
{ "url": "https://myblog.com/new-article" }
→ 202 Accepted (URL queued for priority crawling)
```

---

## SECTION 6 — High-Level Architecture

```
THE SEARCH PIPELINE (Two phases: offline indexing + online serving)

╔══════════════════════════════════════════════════════════════════════╗
║                    OFFLINE INDEXING PIPELINE                        ║
╚══════════════════════════════════════════════════════════════════════╝

  Web Crawler (Q19 design)
       │
       │ Raw HTML pages (10K pages/sec)
       ▼
  Document Processing Pipeline:
    ├─ HTML Parser: extract title, body text, meta tags, outlinks
    ├─ Content Extractor: remove boilerplate (nav, footer), keep main content
    ├─ Text Analyzer: tokenize, lowercase, remove stopwords, stem words
    │    "Running quickly" → ["run", "quick"]
    ├─ Spam/Duplicate Filter: remove scrapers, thin content
    └─ Feature Extractor: anchor text from inlinks, PageRank score, freshness
       │
       ▼
  Indexing Service:
    ├─ Assigns numeric doc_id to each URL
    ├─ Updates inverted index: for each term → append (doc_id, freq, positions)
    └─ Stores document metadata (title, URL, snippet)
       │
       ▼
  Distributed Index Store (1000s of shards across 1000s of servers)

╔══════════════════════════════════════════════════════════════════════╗
║                    ONLINE SERVING PIPELINE                          ║
╚══════════════════════════════════════════════════════════════════════╝

  User types query → DNS → Google Frontend Servers
       │
  ┌────▼──────────────────────────────────────────────────────────┐
  │                    Query Processing                            │
  │  1. Tokenize + normalize query: "Machine Learning" → ["machin", "learn"]
  │  2. Identify query type: navigational? informational? transactional?
  │  3. Expand query: synonyms, spelling correction
  └────┬──────────────────────────────────────────────────────────┘
       │
  ┌────▼──────────────────────────────────────────────────────────┐
  │                    Index Lookup (fan-out)                      │
  │  Query Coordinator sends term lookups to multiple index shards │
  │  in PARALLEL:                                                  │
  │    Shard 1: "machine" posting list → [doc1, doc4, doc9, ...]  │
  │    Shard 2: "learning" posting list → [doc2, doc4, doc7, ...] │
  │    Shard 3: "machine" posting list → [doc10, doc20, ...]      │
  │                    (different doc_id ranges per shard)        │
  └────┬──────────────────────────────────────────────────────────┘
       │ Fan-in: merge posting lists
  ┌────▼──────────────────────────────────────────────────────────┐
  │                    Scoring & Ranking                           │
  │  For each candidate doc_id:                                   │
  │    score = TF-IDF × PageRank × freshness × click_through_rate │
  │  Sort by score, take top-100                                  │
  └────┬──────────────────────────────────────────────────────────┘
       │
  ┌────▼──────────────────────────────────────────────────────────┐
  │                    Result Serving                              │
  │  Fetch doc metadata (title, URL, snippet) for top-10 results  │
  │  Highlight query terms in snippet                             │
  │  Return to user                                               │
  └────────────────────────────────────────────────────────────────┘

  ┌────────────────────────────────────────────────────────────────┐
  │                INFRASTRUCTURE                                  │
  │                                                                │
  │  Index Shards: 60B docs / N docs_per_shard = thousands shards │
  │  Each shard: in-memory posting lists + on-disk for large terms │
  │  Replication: 3 replicas per shard (read replicas handle load) │
  │  ZooKeeper: shard → server mapping                            │
  └────────────────────────────────────────────────────────────────┘
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Ranking — How Google Decides Order

**Multiple ranking signals combined:**

**1. TF-IDF (Term Frequency × Inverse Document Frequency)**
```
TF (term frequency): how often does "machine" appear in this doc?
  TF("machine", doc_5) = count("machine" in doc_5) / total words in doc_5

IDF (inverse doc frequency): how rare is "machine" across all docs?
  IDF("machine") = log(total_docs / docs_containing("machine"))
  → Common words ("the", "is") have low IDF → low weight
  → Rare words ("BERT", "transformer") have high IDF → high weight

TF-IDF = TF × IDF
→ High score: term appears often in THIS doc but rarely in OTHERS = very relevant
```

**2. BM25 (Better Practical Variant of TF-IDF)**
```
BM25 addresses TF saturation (400 occurrences ≠ 4× better than 100):
  BM25 = IDF × (TF × (k1 + 1)) / (TF + k1 × (1 - b + b × dl/avgdl))

  k1 = 1.2 (saturation parameter)
  b = 0.75 (length normalization)
  dl = document length, avgdl = average document length

→ Long documents don't unfairly dominate (length normalization)
→ BM25 is the standard in Elasticsearch and most production search engines
```

**3. PageRank (Link-based Authority)**
```
Core insight: a page linked to by many authoritative pages is itself authoritative.

Iterative formula:
  PR(page_A) = (1 - d) + d × Σ (PR(page_B) / outlinks(page_B))
  where B links to A, d = damping factor (0.85)

Interpretation:
  - Random web surfer: follows links with probability d, jumps to random page with (1-d)
  - PR = probability that random surfer lands on this page
  - Pages with high PR from authoritative sites (Wikipedia, .gov) rank higher

PageRank computed offline (batch): runs on the entire web graph
  Algorithm: MapReduce on 60B pages × their outlinks
  Converges in ~50 iterations → runs on Hadoop/Spark
```

**4. Combined Scoring (simplified Google formula)**
```
final_score = 
    α × BM25_text_score        // text relevance
  + β × PageRank_score         // link authority
  + γ × anchor_text_score      // text in links pointing to this page
  + δ × freshness_score        // how recently was page updated?
  + ε × click_through_rate     // do users click this result for this query?
  + ζ × local_personalization  // user's location, language, history (optional)

α, β, γ, δ, ε, ζ learned by ML (LambdaRank / LambdaMART)
→ Gradient boosted trees trained on human-rated query-doc pairs
```

---

### Deep Dive 2: Index Sharding Strategy

**60 billion documents → can't fit on one machine. How to shard?**

**Strategy 1: Document-based sharding (range or hash)**
```
Assign doc_id ranges to shards:
  Shard 0: doc_ids 0 to 1B
  Shard 1: doc_ids 1B to 2B
  ...
  Shard 59: doc_ids 59B to 60B

Query for "machine learning":
  → Query ALL 60 shards in parallel
  → Each shard returns its top-K results
  → Merge all results → global top-K

Pros: any shard can answer any query
Cons: every query fans out to ALL shards (expensive at scale)
```

**Strategy 2: Term-based sharding**
```
Each shard owns specific terms:
  Shard 0: terms "a" to "buzz"
  Shard 1: terms "cable" to "fox"
  ...

Query for "machine learning":
  → "machine" lives on Shard 15 → query Shard 15
  → "learning" lives on Shard 22 → query Shard 22
  → 2 shard queries (not 60) → much less fan-out

Cons: merge step more complex; hot terms (common words) → hot shards
Used by: Bing, Elasticsearch, Lucene
```

**Strategy 3: Hybrid (Google's approach)**
```
Combine document-based and term-based:
  → Documents sharded by quality tier (high-PageRank docs on fast SSDs)
  → Within a tier: term-based index for efficient lookup
  → High-quality tier queried for all queries
  → Low-quality tier queried only if high-quality tier has few results
```

---

### Deep Dive 3: Phrase Queries and Boolean Operators

**"machine learning" (phrase query — must appear as a phrase, not just both words):**
```
Find docs containing "machine" at position p
                  AND "learning" at position p+1

Process:
  1. Get posting list for "machine": [(doc1, [5,20,100]), (doc4, [12])]
  2. Get posting list for "learning": [(doc1, [6,21]), (doc4, [45])]
  3. Intersect doc_ids: doc1 appears in both
  4. Check positions: doc1 has "machine" at [5,20,100] and "learning" at [6,21]
     - Position 5 and 6: machine at 5, learning at 6 → consecutive! ✅
     → doc1 matches the phrase
```

**machine learning -free (exclude "free"):**
```
1. Get posting list for "machine": [doc1, doc4, doc7, ...]
2. Get posting list for "learning": [doc1, doc3, doc7, ...]
3. Intersect: [doc1, doc7, ...]
4. Get posting list for "free": [doc1, doc5, ...]
5. Result - excluded: [doc7, ...]  (doc1 removed because it contains "free")
```

---

### Deep Dive 4: Near-Real-Time Indexing (Freshness)

**Problem**: Breaking news (earthquake) should appear in search within minutes, not days.

```
Two-tier index:

TIER 1: Real-time Index (small, fast)
  - Contains last 24 hours of crawled pages
  - In-memory inverted index
  - Rebuilt every few minutes
  - Priority: RSS feeds, news sites, sitemaps with recent timestamps
  - 10s of millions of docs (tiny relative to full web)

TIER 2: Main Index (huge, stable)
  - Contains 60 billion pages
  - Updated slowly (days to weeks for most pages)
  - On-disk, optimized for throughput

Query combines both:
  → Query both tiers in parallel
  → Merge results, prioritize recent for news queries
  → If query contains time signals ("today", "latest", "breaking"):
     → Weight real-time tier heavily

Freshness signal in ranking:
  freshness_score = 1.0 / (days_since_indexed + 1)
  → Pages indexed today score 1.0
  → Pages indexed 30 days ago score 0.033
  → Weighted into final score (lower weight than BM25 for non-news queries)
```

---

### Deep Dive 5: Handling 100,000 Queries/sec with < 200ms Latency

```
Budget allocation (200ms total):
  Query processing: 5ms
  Index lookup (fan-out, parallel): 100ms
  Ranking & scoring: 50ms
  Result fetching + snippet generation: 30ms
  Network (DNS, TLS, etc.): 15ms

Key optimizations:

1. Caching:
   Most popular queries (top 1% = 50% of all queries)
   → Cache results in Redis (TTL: 1 hour for stable queries, 5 min for news)
   → Cache hit: ~1ms response (vs 200ms full processing)

2. Index in RAM:
   Hot index tier (most popular 1B docs) fully loaded into RAM across index servers
   Cold tier: on NVMe SSD with memory-mapped files

3. Early termination:
   For high-traffic queries: stop merging posting lists after top-10,000 docs
   (98% of the time, true top-10 are in first 10,000 candidates)

4. WAND (Weak AND) algorithm:
   Skip posting lists efficiently without full intersection
   → Only compute score for docs that could possibly be in top-K
   → 3-10× faster than full merge

5. Geographic distribution:
   Edge locations in every major city (Bangalore, Mumbai, Delhi, etc.)
   User query → nearest Google datacenter (< 20ms network RTT)
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP (Availability + Partition Tolerance)**
- Search with slightly stale index is better than no search at all
- Google has 99.999% uptime — they accept AP and handle consistency with careful distributed design
- Exception: real-time index tier is CP within its window (must show breaking news correctly)

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Index structure | Inverted index | B-Tree / full-text DB | Inverted index: O(1) term lookup; B-Tree needs full scan of table |
| Ranking | BM25 + PageRank + ML blend | Pure PageRank | BM25 alone doesn't use link authority; PageRank alone ignores query relevance |
| Sharding | Document-based | Term-based | Doc-based: simple, any shard can answer any query; term-based reduces fan-out but creates hot shards |
| Freshness | Two-tier (real-time + main) | Single tier with incremental updates | Two-tier gives < 5 min freshness for news; single tier struggles with incremental at scale |
| Caching | Query result caching (Redis) | No caching | Top 1% of queries = 50% of load; caching drastically reduces index server load |

---

## Interview Flow Summary (Talk Track)

1. "Google Search has two phases: **offline indexing** (crawl → process → inverted index) and **online serving** (query → fan-out → rank → return)"
2. "Core data structure: **Inverted Index** — term → posting list of (doc_id, positions, frequency)"
3. "Ranking: **BM25** (text relevance) × **PageRank** (link authority) × **freshness** — weighted by learned ML model"
4. "**PageRank**: iterative link-analysis algorithm — high PR from authoritative inlinks = trusted page"
5. "**Sharding**: document-based → each query fans out to all shards in parallel, merge top-K"
6. "**Latency**: 200ms budget — query result caching covers top-1% queries (50% of load)"
7. "**Freshness**: two-tier index — real-time tier (24h, in-memory, minutes-fresh) + main index (60B docs, days-old)"

---

> **Previous**: [21 — Design Recommendation System](./21-recommendation-system.md)
> **Next**: [23 — Design Live Streaming](./23-live-streaming.md)
