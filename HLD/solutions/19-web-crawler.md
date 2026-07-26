# 19. Design Web Crawler

> **Difficulty**: Hard | **Asked At**: Google, Bing, Amazon, LinkedIn
> **Time to Answer in Interview**: 35–40 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- What is it crawling for? Search indexing? Data mining? Link checking?
- Only HTML pages or also PDFs, images, JS-heavy SPAs?
- How deep should we crawl? (depth limit?)
- Do we need to recrawl pages periodically (freshness)?
- How to handle robots.txt (politeness)?
- Handle duplicate content?
- Handle traps (infinite loops of dynamically generated URLs)?

**Scale:**
- How many pages to crawl?
- How fast must we crawl?
- How fresh should the index be?

**Typical Interviewer Answer:**
- Crawl the entire public web for search indexing
- 5 billion web pages total
- Recrawl popular pages every few days, less popular pages every few weeks
- Must respect robots.txt
- Handle JavaScript-rendered pages (mention Headless Chrome)
- 3 billion pages crawled per month

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. Discover and download web pages starting from seed URLs
2. Parse HTML: extract text content + links to new URLs
3. Store page content for indexing
4. Re-crawl pages periodically based on change frequency
5. Respect robots.txt (politeness) — don't overload websites
6. Deduplicate URLs (don't crawl same page twice)
7. Deduplicate content (same content under multiple URLs)

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Scale** | 5 billion pages; crawl 3 billion/month |
| **Throughput** | 1,000–10,000 pages/sec |
| **Politeness** | Limit requests per domain (1 req/sec max per domain) |
| **Freshness** | Popular pages recrawled every 3 days |
| **Distributed** | Horizontally scalable across 1000s of crawlers |

### Out of Scope
- Building the search index (just the crawling part)
- Spam detection / malware scanning
- Language detection

---

## SECTION 3 — Capacity Estimation

### Pages
- 5 billion pages total
- Recrawl: 3 billion/month = 100M pages/day = **~1,160 pages/sec** average
- Peak throughput target: **10,000 pages/sec**

### Storage
- Average page (HTML compressed): 50 KB
- 5B pages × 50 KB = **250 TB** of page content
- URL database: 5B × 100 bytes = **500 GB**
- DNS cache: 10M domains × 50 bytes = **500 MB** (tiny)

### Bandwidth
- 10,000 pages/sec × 50 KB = **500 MB/s download bandwidth**
- With 1000 crawler nodes: 500 KB/s per node (very manageable)

---

## SECTION 4 — Key Algorithm: BFS vs Priority Queue

### Basic BFS (Breadth-First Search)
```
Queue: [seed_url_1, seed_url_2, ...]
While queue not empty:
  url = dequeue()
  page = fetch(url)
  links = parse(page)
  for link in links:
    if link not seen: enqueue(link)
```
**Problem**: BFS treats all pages equally — low-priority forum spam queued alongside Wikipedia.

### Priority Queue Based (Production approach) ✅
```
Priority determined by:
  - PageRank / domain authority (Google.com > random blog)
  - Freshness need (news sites need more frequent recrawl)
  - Discovery time (new URLs from high-priority pages get high priority)
  - Change frequency (page that changes daily > page unchanged for years)

Priority queues:
  HIGH: major news sites, government, Wikipedia
  MEDIUM: popular blogs, e-commerce
  LOW: forums, personal pages
  VERY_LOW: already crawled, low change frequency

Crawler picks from HIGH queue 60%, MEDIUM 30%, LOW 10%
```

---

## SECTION 5 — Data Model

### Table 1: `url_frontier` (The URL queue)
```
url_hash        VARCHAR(64)  PRIMARY KEY   -- SHA-256 of canonical URL
url             TEXT         NOT NULL
priority        INT          DEFAULT 50     -- 0 (low) to 100 (high)
status          ENUM('pending', 'in_progress', 'done', 'failed')
scheduled_at    TIMESTAMP    -- when to next crawl
last_crawled_at TIMESTAMP
crawl_count     INT          DEFAULT 0
domain          VARCHAR(200)
```
**DB**: **Cassandra** (partition by domain for politeness rate limiting; high write volume)

### Table 2: `crawled_pages`
```
url_hash        VARCHAR(64)  PRIMARY KEY
url             TEXT
content_hash    VARCHAR(64)  -- SHA-256 of page content (dedup)
raw_html        TEXT         -- or pointer to S3 key
title           TEXT
status_code     INT
crawled_at      TIMESTAMP
content_length  INT
outbound_links  INT
```
**Storage**: HTML content → **S3** (too large for DB)
**DB**: Cassandra for metadata (url_hash → S3 key, crawled_at)

### Table 3: `dns_cache`
```
domain          VARCHAR(200) PRIMARY KEY
ip_address      VARCHAR(50)
ttl_expires     TIMESTAMP
```
**Storage**: **Redis** with TTL matching DNS TTL (~5 minutes)

### Table 4: `robots_cache`
```
domain          VARCHAR(200) PRIMARY KEY
robots_txt      TEXT
disallowed_paths TEXT[]
crawl_delay_sec INT
expires_at      TIMESTAMP    -- recheck robots.txt daily
```
**Storage**: **Redis** with 24-hour TTL

---

## SECTION 6 — High-Level Architecture

```
SEED URLS
(Alexa top 1M, hand-picked authoritative sources)
     │
     ▼
┌──────────────────────────────────────────────────────────────────┐
│                     URL FRONTIER                                 │
│  Priority Queue (Cassandra + Redis)                              │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ HIGH:    [ news.google.com, wikipedia.org, ... ]            ││
│  │ MEDIUM:  [ stackoverflow.com, medium.com, ... ]             ││
│  │ LOW:     [ random-blog.com, ... ]                           ││
│  └─────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────┬───────────────────────────┘
                                       │ URL assigned to crawler
            ┌────────────────┬─────────▼──────────┬────────────────┐
            │                │                    │                │
     ┌──────▼────┐    ┌──────▼────┐       ┌──────▼────┐    ┌──────▼────┐
     │ Crawler 1 │    │ Crawler 2 │       │ Crawler 3 │    │ Crawler N │
     │           │    │           │  ...  │           │    │           │
     │ 1. Check  │    │ Same flow │       │ Same flow │    │ Same flow │
     │    robots │    │           │       │           │    │           │
     │ 2. DNS    │    │           │       │           │    │           │
     │ 3. Fetch  │    │           │       │           │    │           │
     │ 4. Parse  │    │           │       │           │    │           │
     │ 5. Store  │    │           │       │           │    │           │
     │ 6. Enqueue│    │           │       │           │    │           │
     │    links  │    │           │       │           │    │           │
     └──────┬────┘    └───────────┘       └───────────┘    └───────────┘
            │
            │ New URLs → URL Scheduler
            ▼
┌──────────────────────────────────────────────────────────────────┐
│                      URL SCHEDULER                               │
│  1. Normalize URL (canonical form)                               │
│  2. Bloom filter check (seen before?) → if yes: skip            │
│  3. Politeness check (domain request rate) → if too fast: delay │
│  4. Priority assignment                                          │
│  5. Insert into URL Frontier                                     │
└──────────────────────────────────────────────────────────────────┘
            │
            │ Parsed content
            ▼
┌──────────────────────────────────────────────────────────────────┐
│                    STORAGE LAYER                                 │
│  Raw HTML → S3                                                   │
│  Metadata → Cassandra                                            │
│  → Content extractor pipeline → Search Indexer                   │
└──────────────────────────────────────────────────────────────────┘

POLITENESS LAYER:
  Redis rate limiter per domain: max 1 req/sec
  robots.txt cached per domain: 24 hours
  Crawl-delay directive respected
```

---

## SECTION 7 — Deep Dives

### Deep Dive 1: URL Deduplication (Bloom Filter)

**Problem**: 5 billion URLs to track. Is a URL already crawled?

**Naive**: Store all 5B URLs in a hash set
- 5B × 100 bytes = 500 GB in memory — too expensive

**Solution: Bloom Filter**
```
A Bloom Filter answers "Have I seen this URL?" with:
  - 100% accuracy for "No, definitely not seen"
  - ~1% false positive rate for "Yes, seen" (may wrongly skip rare new URLs)

Memory: ~10 bits per URL
  5B URLs × 10 bits = ~6 GB — fits comfortably in RAM!

How it works:
  - k hash functions, each maps URL to a position in a bit array
  - On URL seen: set k bits to 1
  - On URL check: if ANY bit is 0 → never seen (100% certain)
                   if ALL bits are 1 → probably seen (1% false positive)

Trade-off: 1% of new URLs mistakenly skipped → acceptable
           0% of seen URLs re-crawled unnecessarily (false negatives impossible)
```

**Implementation in distributed crawler:**
- Bloom filter stored in **Redis** (shared across all crawlers)
- `SETBIT bloom_filter {hash_position} 1`
- `GETBIT bloom_filter {hash_position}` (for all k positions)
- Size: 6 GB Redis key per filter instance

---

### Deep Dive 2: Content Deduplication (Near-Duplicate Detection)

**Problem**: Many pages have identical or nearly identical content.
- Mobile vs desktop version of same page
- Paginated results (page 1 and page 2 look almost identical)
- Scrapers copying original content

**Exact duplicate**: SHA-256 of content
- `content_hash` in crawled_pages table
- If hash already exists: skip storing, just record URL → existing content

**Near-duplicate (SimHash / MinHash):**
```
SimHash: represents a document as a 64-bit fingerprint
  1. Tokenize document: ["the", "quick", "brown", "fox", ...]
  2. Hash each token to 64-bit vector
  3. Sum vectors (weighted by frequency)
  4. Sign of each bit → 64-bit fingerprint

Near-duplicate detection:
  Two SimHashes are "similar" if they differ in ≤ 3 bits
  (Hamming distance ≤ 3 → ~95% similar content)

Storage:
  All seen SimHashes in a sorted index
  New page fingerprint → binary search for fingerprints within Hamming distance 3
  If found: near-duplicate, skip
```

---

### Deep Dive 3: Handling Spider Traps

**Problem**: Malicious or poorly designed websites generate infinite URLs:
```
example.com/page1
example.com/page1/next
example.com/page1/next/next
example.com/page1/next/next/next
... (infinite loop)
```

**Solutions:**
1. **Max URL length**: reject URLs > 2048 characters
2. **Max crawl depth per domain**: never go deeper than 10 levels from seed
3. **URL normalization**: strip irrelevant parameters (`?session_id=...`, `?utm_source=...`)
4. **Domain crawl cap**: max 10,000 pages per domain per crawl cycle
5. **URL pattern detection**: if URL path has repeating segment → skip

```python
def is_spider_trap(url, domain_crawl_count):
    # Too long
    if len(url) > 2048: return True
    # Too deep
    if url.count('/') > 15: return True
    # Too many from this domain
    if domain_crawl_count > 10000: return True
    # Repeating pattern
    path = urllib.parse.urlparse(url).path
    segments = path.split('/')
    if len(segments) != len(set(segments)):  # duplicate segments in path
        return True
    return False
```

---

### Deep Dive 4: Politeness & robots.txt

**Why politeness matters:**
- Aggressive crawling can crash small websites
- Legal requirement in many jurisdictions
- Banning by IP if too aggressive (crawler blocked)

**robots.txt (Robots Exclusion Standard):**
```
User-agent: Googlebot
Disallow: /admin/
Disallow: /private/
Crawl-delay: 1   # wait 1 second between requests

User-agent: *
Disallow: /     # disallow all other crawlers
```

**Crawler implementation:**
```
Before crawling any URL from domain X:
  1. Fetch and cache robots.txt: GET http://X/robots.txt
  2. Parse: which paths are disallowed for our user-agent?
  3. Cache result in Redis with 24-hour TTL
  4. For each URL: check against disallowed patterns before crawling
  5. Honor Crawl-delay: use Redis rate limiter per domain (1 req/sec)

Additionally:
  - Set User-Agent header: "Googlebot/2.1 (+http://www.google.com/bot.html)"
  - Cache robots.txt per domain to avoid fetching on every request
  - Never crawl disallowed paths even if discovered via links
```

---

### Deep Dive 5: Recrawl Strategy (Freshness)

**Problem**: Web changes constantly. How often to recrawl?

**Approach: Adaptive Recrawl Scheduling**
```
For each page, estimate change frequency:
  - Compute fingerprint of current content
  - Compare with previous crawl's fingerprint
  - If different: page changes frequently → higher recrawl priority
  - If same: page is stable → lower recrawl priority

Change frequency estimation:
  high_change:   news articles, stock prices → recrawl every 6 hours
  medium_change: blog posts → recrawl every 3 days
  low_change:    Wikipedia pages → recrawl every 2 weeks
  static:        old forum posts → recrawl monthly

URL Frontier scheduling:
  scheduled_at = last_crawled_at + change_frequency_estimate
  Crawler picks URLs where scheduled_at <= now()
```

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP (Availability + Partition Tolerance)**
- If URL frontier is briefly inconsistent: a URL might be crawled twice → acceptable (content deduplicated)
- Better to have crawlers always working than to wait for perfect distributed coordination
- Bloom filter has 1% false positive: acceptable tradeoff for memory savings

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| URL deduplication | Bloom Filter | Full URL hash set in DB | Bloom filter: 6 GB for 5B URLs; hash set: 500 GB — Bloom is 80× more memory efficient |
| Content dedup | SimHash (near-dup) | Exact SHA-256 only | Exact misses near-duplicates; SimHash catches scrapers/mirrors |
| URL queue | Priority queue (multi-tier) | Simple BFS queue | Priority ensures high-value content crawled first; BFS treats spam equally with Wikipedia |
| Crawler coordination | Distributed (Kafka/Cassandra) | Centralized master | Centralized master is a bottleneck at 10K pages/sec; distributed scales linearly |
| DNS caching | Per-crawler Redis cache | System DNS (OS) | OS DNS TTL is strict (5 min); crawlers benefit from longer caching to reduce DNS lookups |

---

## Interview Flow Summary (Talk Track)

1. "A web crawler has 5 key components: **URL Frontier, Fetcher, Parser, Deduplicator, Scheduler**"
2. "URL Frontier is a **priority queue** — not BFS — high-value pages (Wikipedia) crawled before spam blogs"
3. "URL deduplication: **Bloom Filter** in Redis — 6 GB tracks 5 billion URLs with 1% false positive rate"
4. "Content deduplication: **SHA-256** for exact dups, **SimHash** for near-duplicates (scrapers/mirrors)"
5. "Politeness: **robots.txt** cached per domain, **Redis rate limiter** (max 1 req/sec per domain)"
6. "Spider traps: URL length limit, depth limit, domain crawl cap, repeating path segment detection"
7. "Recrawl scheduling: adaptive frequency based on observed change rate — news sites every 6h, static pages monthly"

---

> **Previous**: [18 — Design Google Maps](./18-google-maps.md)
> **Next**: [20 — Design Kafka / Distributed Message Queue](./20-kafka-message-queue.md)
