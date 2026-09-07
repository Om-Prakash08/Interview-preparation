# 19. Design Web Crawler

> **Difficulty**: Hard | **Asked At**: Google, Bing, Amazon, LinkedIn
> **Time to Answer in Interview**: 35–40 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)
- What is the primary purpose of the crawler? (Search engine indexing)
- How many web pages do we need to crawl? (5 billion pages total)
- How fast must the crawler run? (3 billion pages per month)
- How do we handle page updates (freshness)?
- Do we need to respect `robots.txt` (politeness)?
- How do we handle duplicate URLs and duplicate content?

**Typical Interviewer Answer:** Crawl the public web for search indexing. 5 billion total pages, 3 billion recrawled monthly. Must respect `robots.txt` and domain rate limits. Deduplicate content and URLs.

### 1.2 Functional Requirements (FR)
1. **URL Discovery & Fetching**: Download web pages starting from seed URLs.
2. **HTML Parsing**: Extract text content and out-bound URLs for indexing.
3. **URL & Content Deduplication**: Avoid crawling duplicate URLs or storing identical content across different URLs.
4. **Politeness**: Respect `robots.txt` rules and limit requests per domain (e.g., max 1 req/sec).
5. **Freshness / Recrawling**: Periodically recrawl pages based on change frequency.

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Scale** | 5 Billion pages total; crawl 3 Billion pages/month (~1,160 pages/sec avg, peak 10,000 pages/sec) |
| **Robustness** | Handle spider traps, infinite loops, broken links, malformed HTML |
| **Politeness** | Max 1 request/sec per target domain |
| **Extensibility** | Support new file types (PDFs, images, JS rendering) |
| **Storage** | 250 TB raw page storage, 500 GB URL database |

### 1.4 Out of Scope
- Building the search engine inversion index (only responsible for crawling & raw storage)
- Spam / malware scanning

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐       ┌──────────────────────────┐       ┌──────────────────┐
│   URL Task       │       │   Crawled Page           │       │   Domain Config  │
│                  │       │                          │       │   (Politeness)   │
│  url_hash        │──────►│  url_hash                │       │                  │
│  canonical_url   │       │  content_hash (SHA-256) │       │  domain_name     │
│  priority        │       │  simhash_fingerprint     │       │  robots_txt      │
│  status          │       │  s3_html_key             │       │  last_crawl_time │
│  scheduled_at    │       │  crawled_at              │       └──────────────────┘
└──────────────────┘       └──────────────────────────┘
```

### 2.2 Data Model / Schema

**1. `url_frontier` (Cassandra - Scalable Write/Read)**
```sql
CREATE TABLE url_frontier (
  domain VARCHAR,
  priority INT,
  url_hash VARCHAR,
  canonical_url TEXT,
  status VARCHAR, -- 'pending', 'in_progress', 'done'
  scheduled_at TIMESTAMP,
  PRIMARY KEY ((domain), priority, url_hash)
);
```

**2. `crawled_pages_metadata` (Cassandra)**
```sql
CREATE TABLE crawled_pages (
  url_hash VARCHAR PRIMARY KEY,
  canonical_url TEXT,
  content_hash VARCHAR, -- SHA-256 for exact match
  simhash_fingerprint BIGINT, -- 64-bit SimHash for near-dup
  s3_key TEXT,
  crawled_at TIMESTAMP
);
```

**3. `politeness_cache` & `dns_cache` (Redis In-Memory)**
```
Key: domain:politeness:{domain_name} -> Value: last_request_timestamp (TTL 60s)
Key: dns:{domain_name}               -> Value: ip_address (TTL match DNS TTL)
Key: robots:{domain_name}            -> Value: disallowed_paths_json (TTL 24 hours)
```

> 🎯 **NFR addressed**: **Politeness** — Redis key tracking `last_request_timestamp` enforces domain rate limits. **Scale** — Cassandra partitioned by `domain` handles high throughput URL updates.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Fetcher Service Internal Interface
```python
class FetcherService:
    def fetch_page(self, url: str) -> FetchResult:
        # 1. Resolve DNS (check Redis DNS cache)
        # 2. Check robots.txt compliance
        # 3. HTTP GET request with User-Agent header
        # 4. Return HTML content & HTTP status
        pass
```

### 3.2 HTML Parser & Link Extractor Interface
```python
class ParserService:
    def parse(self, html_content: str, base_url: str) -> ParseResult:
        # 1. Extract text body & compute content_hash / SimHash
        # 2. Extract all <a href="..."> links
        # 3. Canonicalize / normalize extracted URLs
        pass
```

> 🎯 **NFR addressed**: **Robustness** — Parser Service handles malformed HTML using resilient parsing libraries (e.g., BeautifulSoup / HTML5 parser).

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation

- **Crawl Target**: 3B pages / month = 100M pages / day = **~1,160 pages/sec** average. Peak target = **10,000 pages/sec**.
- **Page Content Storage**: Avg compressed HTML = 50 KB. 5B pages × 50 KB = **250 TB S3 Storage**.
- **URL Frontier Database**: 5B URLs × 100 bytes = **500 GB database**.
- **Bandwidth**: 10,000 pages/sec × 50 KB = **500 MB/s download throughput**.

### 4.2 Data Flow Through System

```
Seed URLs → URL Scheduler → Priority Queue (URL Frontier)
  │
  ▼ (Dequeue next URL respecting politeness delay)
Crawler Worker Node
  ├─ 1. Check Redis DNS Cache (avoid slow DNS lookup)
  ├─ 2. Check Redis robots.txt Cache (skip disallowed paths)
  ├─ 3. Download HTML content (HTTP GET)
  │
  ▼
Parsing & Deduplication Pipeline
  ├─ 1. Calculate Content SHA-256 & SimHash -> Check DB for near-duplicates
  ├─ 2. Store Raw HTML in S3 Bucket
  ├─ 3. Extract outbound URLs from HTML
  │
  ▼
URL Deduplicator & Scheduler
  ├─ 1. Normalize URLs (canonical form: lowercase, strip query tracking params)
  ├─ 2. Check Bloom Filter (Have we seen this URL?)
  │      ├─ If YES: Skip (discard duplicate URL)
  │      └─ If NO: Insert into Bloom Filter & push to URL Frontier Queue
```

> 🎯 **NFR addressed**: **Storage & Scale** — Bloom Filter deduplicates 5B URLs in memory with only ~6 GB RAM required.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                              ┌───────────────────────────────────┐
                              │            URL Frontier           │
                              │  (Cassandra + Redis Priority Q)   │
                              └─────────────────┬─────────────────┘
                                                │ Dequeue URL
                                                ▼
 ┌───────────────┐            ┌───────────────────────────────────┐
 │ Redis Cache   │◄───────────┤          Crawler Workers          │
 │ - DNS Cache   │            │   - Fetch HTML via HTTP           │
 │ - Robots.txt  │            │   - Enforce domain rate limit     │
 └───────────────┘            └─────────────────┬─────────────────┘
                                                │ Parsed HTML
                                                ▼
 ┌───────────────┐            ┌───────────────────────────────────┐
 │ Storage Layer │◄───────────┤     Parsing & Dedup Pipeline      │
 │ - Raw HTML    │            │   - Extract links & text          │
 │   (S3)        │            │   - Content SHA-256 / SimHash     │
 └───────────────┘            └─────────────────┬─────────────────┘
                                                │ Extracted URLs
                                                ▼
                              ┌───────────────────────────────────┐
                              │          URL Deduplicator         │
                              │   - Canonicalize URL              │
                              │   - Check Redis Bloom Filter      │
                              └─────────────────┬─────────────────┘
                                                │ New Unique URLs
                                                ▼
                              ┌───────────────────────────────────┐
                              │            URL Scheduler          │
                              │   - Assign priority & recrawl     │
                              └───────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **URL Frontier** | Multi-queue system (Priority + Politeness) | Prevents domain overloading while prioritizing high-value URLs |
| **Redis Bloom Filter** | Fast URL deduplication | 6 GB RAM handles 5 Billion URLs with 1% false-positive rate |
| **S3 Storage** | Raw HTML page storage | Cheap, durable blob storage for 250 TB data |
| **SimHash Module** | Near-duplicate content detection | Identifies scraped or mirror content with Hamming distance $\le 3$ |

> 🎯 **NFR addressed**: **Politeness & Scale** — Decoupled workers fetch URLs asynchronously without blocking each other.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: URL Deduplication via Bloom Filter

**Problem**: Storing 5 Billion URLs in a hash set requires $5\text{B} \times 100\text{ bytes} = 500\text{ GB RAM}$, which is expensive.

**Solution: Redis Bloom Filter**
```
- Bit Array of Size M bits with K independent Hash Functions.
- To check URL existence:
    1. Pass URL through K hash functions -> get K bit indices.
    2. If ALL K bits are 1 -> URL is PROBABLY SEEN (1% false positive rate).
    3. If ANY bit is 0 -> URL is DEFINITELY NEW (0% false negative rate).

Memory Calculation:
  - 10 bits per URL for 1% false positive rate.
  - 5 Billion URLs × 10 bits = ~50 Billion bits = ~6.25 GB RAM.
  - Fits easily inside a single Redis instance!
```

---

### Deep Dive 2: Politeness & Priority Architecture (URL Frontier)

```
                       ┌────────────────────────┐
                       │  Incoming Extracted    │
                       │        URLs            │
                       └───────────┬────────────┘
                                   │
                                   ▼
                       ┌────────────────────────┐
                       │    Priority Queues     │
                       │ (High / Medium / Low)  │
                       └───────────┬────────────┘
                                   │ Queue Selector (Pick High 60%, Med 30%, Low 10%)
                                   ▼
                       ┌────────────────────────┐
                       │   Queue Router / Map   │
                       │ (Hash Domain -> FIFO)  │
                       └───────────┬────────────┘
                                   │
               ┌───────────────────┼───────────────────┐
               ▼                   ▼                   ▼
        ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
        │ Queue for   │     │ Queue for   │     │ Queue for   │
        │ domainA.com │     │ domainB.com │     │ domainC.com │
        └──────┬──────┘     └──────┬──────┘     └──────┬──────┘
               │                   │                   │
               └───────────────────┼───────────────────┘
                                   │ Enforce 1 sec delay per queue
                                   ▼
                            Worker Fetchers
```

---

### Deep Dive 3: Spider Traps & Infinite Loop Mitigation

**Spider Traps**: Dynamically generated infinite URLs (e.g., `site.com/calendar/2026/09/07/next/next...`).

**Mitigation Strategies:**
1. **Max Path Depth & Length**: Limit URL path depth to $\le 10$ slashes and max length to 2,048 characters.
2. **Domain Crawl Cap**: Max 10,000 pages per domain per crawl cycle.
3. **URL Pattern Detection**: Flag paths containing repeating directory names (e.g., `/a/b/a/b/`).
4. **Adaptive Change Frequency**: Recrawl high-change news sites every 6h, static pages monthly.

---

### Trade-offs & Alternatives

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| **URL Dedup** | Bloom Filter | Cassandra DB Lookup | Bloom filter requires ~6 GB RAM vs expensive disk IO queries for 5B URLs |
| **Near-Dup** | SimHash | SHA-256 Hash | SHA-256 misses near-duplicates (mirrors, minor whitespace/footer changes) |
| **Crawl Strategy** | Priority Queue | Pure BFS | Pure BFS wastes bandwidth on forum spam over Wikipedia |

---

### Summary Talk Track

1. "A Web Crawler must scale to **5 Billion pages** while maintaining strict **politeness** and **deduplication**."
2. "We decouple the **URL Frontier** into Priority Queues and Domain Politeness Queues, ensuring max 1 req/sec per domain."
3. "URL deduplication uses a **Redis Bloom Filter** needing only **6.25 GB RAM** for 5 Billion URLs with a 1% false positive rate."
4. "Content deduplication uses **SimHash 64-bit fingerprints** to filter near-duplicate mirror pages."

---

> **Previous**: [18 — Design Google Maps](./18-google-maps.md)
> **Next**: [20 — Design Kafka / Distributed Message Queue](./20-kafka-message-queue.md)
