# 6. Design Google Drive / Dropbox

> **Difficulty**: Hard | **Asked At**: Google, Dropbox, Amazon, Microsoft
> **Time to Answer in Interview**: 40–45 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- Upload and download files — both?
- Do we support sync across multiple devices?
- Is real-time collaboration (like Google Docs) needed?
- File versioning — keep previous versions?
- File sharing with other users?
- What's the max file size?

**Scale:**
- How many DAU?
- How much total storage?
- What is the read-to-write ratio?

**Typical Interviewer Answer:**
- 50 million DAU, 500 million registered users
- Each user gets 15 GB free, premium users up to 2 TB
- Total storage: 500M × 15 GB = ~7.5 exabytes (petabyte scale)
- Support desktop sync client (not just web UI)
- File versioning: keep last 30 versions
- File sharing: read-only or edit access
- No real-time collaborative editing (that's Google Docs — different problem)

### 1.2 Functional Requirements (FR)
1. Upload files of any type (up to 5 GB per file)
2. Download files
3. Sync files across multiple devices automatically
4. File versioning — restore any of the last 30 versions
5. Share files/folders with other users (view or edit)
6. Folder hierarchy support

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Durability** | 99.999999999% (11 nines) — files must NEVER be lost |
| **Availability** | 99.99% for upload/download |
| **Sync latency** | Changes on Device A appear on Device B within 30 seconds |
| **Scalability** | Petabytes to exabytes of storage |
| **Bandwidth efficiency** | Only upload/download changed parts of files (delta sync) |

### 1.4 Out of Scope
- Real-time collaborative editing
- Photo/video auto-enhancement
- Optical character recognition (OCR)

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│   File       │        │ FileVersion  │        │   Folder     │
│              │        │              │        │              │
│ file_id      │───────►│ file_id      │        │ folder_id    │
│ owner_id     │        │ version_id   │        │ owner_id     │
│ file_name    │        │ s3_key       │        │ parent_id    │
│ folder_id    │        │ checksum     │        │ folder_name  │
│ checksum     │        │ size_bytes   │        └──────────────┘
│ current_ver  │        │ created_at   │
└──────────────┘        └──────────────┘        ┌──────────────┐
                                                │  FileShare   │
┌──────────────┐                                │              │
│  FileChunk   │                                │ file_id      │
│              │                                │ shared_with  │
│ file_id      │                                │ permission   │
│ version_id   │                                │ expires_at   │
│ chunk_index  │                                └──────────────┘
│ chunk_hash   │
│ s3_chunk_key │
└──────────────┘
```

**Primary entities**: `File` (metadata), `FileVersion` (immutable version history), `FileChunk` (content-addressable parts for delta sync), `Folder` (hierarchy), `FileShare` (access control).

### 2.2 Data Model / Schema

**Table 1: `files` (metadata)**
```
file_id          BIGINT       PRIMARY KEY
owner_user_id    BIGINT
file_name        VARCHAR(255)
folder_id        BIGINT       NULL (null = root)
size_bytes       BIGINT
checksum_sha256  VARCHAR(64)  -- for deduplication and integrity
current_version  INT          DEFAULT 1
is_deleted       BOOLEAN      DEFAULT false
created_at       TIMESTAMP
updated_at       TIMESTAMP
```
**DB Choice**: **PostgreSQL** (relational, supports folder hierarchy queries, moderate scale)
- Shard by `owner_user_id` when needed

**Table 2: `file_versions`**
```
file_id          BIGINT
version_id       INT
size_bytes       BIGINT
s3_key           TEXT         -- actual file location in S3
checksum_sha256  VARCHAR(64)
created_at       TIMESTAMP
PRIMARY KEY (file_id, version_id DESC)
```

**Table 3: `file_chunks` (for delta sync)**
```
file_id          BIGINT
version_id       INT
chunk_index      INT
chunk_hash       VARCHAR(64)  -- SHA256 of chunk content
s3_chunk_key     TEXT
PRIMARY KEY (file_id, version_id, chunk_index)
```

**Table 4: `folders`**
```
folder_id        BIGINT       PRIMARY KEY
owner_user_id    BIGINT
parent_folder_id BIGINT       NULL (null = root)
folder_name      VARCHAR(255)
```

**Table 5: `file_shares`**
```
share_id         BIGINT       PRIMARY KEY
file_id          BIGINT
shared_with      BIGINT       -- user_id or group_id
permission       ENUM('view', 'edit')
expires_at       TIMESTAMP    NULL
```

**Blob Storage:**
- Actual file content → **Amazon S3** (or Google Cloud Storage)
- 11 nines durability, cross-region replication
- Use S3 **Intelligent-Tiering**: hot files in S3 Standard, cold files automatically moved to S3 Glacier (80% cheaper)

> 🎯 **NFR addressed**: **Durability 11 nines** — S3 for all file content with cross-region replication. **Bandwidth efficiency** — chunk-level storage enables delta sync (only transfer changed chunks). **Scalability** — PostgreSQL sharded by user_id + S3 for unlimited blob storage.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Upload File (Chunked + Resumable)
```
// Step 1: Initiate upload session
POST /api/v1/files/upload/init
{
  "file_name": "presentation.pdf",
  "file_size": 104857600,      // 100 MB
  "folder_id": "folder_abc",
  "checksum_sha256": "d2a84f..."
}
Response: { "upload_id": "up_xyz", "chunk_size": 5242880 }

// Step 2: Upload chunks
PUT /api/v1/files/upload/{upload_id}/chunk/{chunk_index}
Content-Range: bytes 0-5242879/104857600
[binary data]
Response: { "chunk_index": 0, "received": true }

// Step 3: Complete upload
POST /api/v1/files/upload/{upload_id}/complete
Response: { "file_id": "file_123", "version_id": "v1" }
```

### 3.2 Download File
```
GET /api/v1/files/{file_id}?version_id=v1
→ 302 Redirect to CDN/S3 pre-signed URL (valid for 15 minutes)
```

### 3.3 List Files in Folder
```
GET /api/v1/folders/{folder_id}/contents
Response: { "items": [ { file_object }, { folder_object } ] }
```

### 3.4 Share File
```
POST /api/v1/files/{file_id}/share
{
  "recipient_email": "bob@example.com",
  "permission": "view"   // or "edit"
}
Response: { "share_link": "https://drive.google.com/s/xyz" }
```

### 3.5 Get File Versions
```
GET /api/v1/files/{file_id}/versions
Response: { "versions": [{ "version_id": "v3", "created_at": "...", "size": 102400 }] }
```

> 🎯 **NFR addressed**: **Availability** — resumable uploads survive network failures. **Bandwidth efficiency** — chunked upload enables delta sync. **Durability** — checksum verification on upload ensures integrity.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Users & Storage:**
- 500 million registered users
- Average user uses 10 GB: 500M × 10 GB = **5 petabytes total storage**
- Daily active: 50 million users upload/modify on average

**Upload Volume:**
- 50M users × 2 files modified/day × 500 KB average = **50 TB/day uploaded**
- = 50 TB / 86,400 ≈ **~580 MB/s upload bandwidth**

**Download Volume:**
- Reads > Writes (10:1 ratio)
- = **~5.8 GB/s download bandwidth**

**Metadata:**
- 500M users × avg 1000 files = **500 billion file metadata records**
- Each record: ~200 bytes → **~100 TB of metadata** (needs a scalable DB)

**API QPS:**
- 50M DAU × 20 operations/day / 86,400 ≈ **~11,500 API calls/sec**

### 4.2 Data Flow Through System

**Upload + Sync Flow:**
```
Desktop Client detects file change (OS file watcher)
  → Compute diff (re-chunk, compare chunk hashes to previous version)
  → Upload ONLY changed chunks → Upload Service → S3
  → Update metadata DB (new version, new chunk references)
  → Publish FileChangedEvent to Kafka
  → Sync Service notifies all other devices (long polling / SSE)
  → Devices pull changed chunks from S3 via CDN
```

**Download Flow:**
```
Client requests file → API → 302 Redirect to pre-signed CDN/S3 URL
  → CDN serves from edge cache (hit) or S3 origin (miss)
```

> 🎯 **NFR addressed**: **Sync latency < 30s** — Kafka + long polling/SSE for instant device notifications. **Bandwidth efficiency** — only changed chunks uploaded/downloaded via delta sync.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
                    ┌──────────────────────────────────────────────────┐
                    │         CLIENTS (Desktop Sync / Web / Mobile)   │
                    └──────────────────┬───────────────────────────────┘
                                       │
                               ┌───────▼────────┐
                               │  API Gateway   │
                               │  + Auth        │
                               └───────┬────────┘
                                       │
          ┌────────────────────────────┼─────────────────────────────┐
          │                            │                             │
 ┌────────▼────────┐         ┌─────────▼────────┐        ┌──────────▼──────┐
 │  Upload Service │         │  Download Service │        │  Metadata Svc  │
 │  (chunked       │         │  (generates       │        │  (files, folders│
 │   resumable)    │         │   pre-signed URLs)│        │   versions,     │
 └────────┬────────┘         └─────────┬─────────┘        │   sharing)     │
          │                            │                  └──────────┬──────┘
          │                            │                             │
 ┌────────▼─────────┐        ┌─────────▼──────────┐       ┌─────────▼──────┐
 │  S3 (Blob Store) │        │  CDN (CloudFront)  │       │  PostgreSQL    │
 │  Raw file chunks │        │  Caches popular    │       │  (Metadata DB) │
 │  All versions    │        │  downloads at edge │       │  Sharded by    │
 └──────────────────┘        └────────────────────┘       │  user_id       │
                                                          └────────────────┘

 ┌──────────────────────────────────────────────────────────────────────────┐
 │                         SYNC PIPELINE                                    │
 │                                                                          │
 │  Desktop Client                Message Queue        All User Devices      │
 │  ─────────────                 ─────────────        ─────────────────     │
 │  File changed                  Kafka:               Long-poll or SSE      │
 │      ↓            →→→→→→→→→→→  FileChangedEvent  →→→  to detect changes   │
 │  Compute diff                                       and pull new chunks  │
 │  Upload delta chunks                                                      │
 └──────────────────────────────────────────────────────────────────────────┘

 ┌────────────────────────────────────────┐
 │      Deduplication Store              │
 │  chunk_hash → s3_chunk_key            │
 │  (Redis or separate lookup table)     │
 │  If hash exists: skip upload,         │
 │  just reference same S3 object        │
 └────────────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Upload Service** | Handles chunked resumable uploads to S3 | Stateless; supports delta sync by accepting individual chunks |
| **Download Service** | Generates pre-signed S3/CDN URLs | Short-lived URLs for security; CDN offloads bandwidth |
| **Metadata Service** | CRUD for files, folders, versions, shares | PostgreSQL for relational file hierarchy |
| **S3** | Stores all file chunks and versions | 11 nines durability; cost-effective at petabyte scale |
| **CDN** | Edge caches popular downloads | Reduces S3 egress costs; lower download latency globally |
| **Kafka** | Event bus for sync notifications | Durable; enables real-time sync across devices |
| **Dedup Store** | Content-addressable chunk → S3 key mapping | Avoids storing duplicate content across users |

> 🎯 **NFR addressed**: **Durability** — S3 + cross-region replication. **Availability 99.99%** — CDN for reads, multiple upload service instances. **Sync latency < 30s** — Kafka → SSE push to all devices. **Scalability** — S3 for unlimited storage, PostgreSQL sharded by user_id.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Chunking & Delta Sync (The Core Innovation)

This is the most important concept for Google Drive / Dropbox.

**Problem**: User edits 1 paragraph in a 100 MB Word document. Should we re-upload 100 MB?

**Solution: Content-Defined Chunking**
1. Split file into variable-size chunks (4–8 MB each) using content-defined algorithms (Rabin fingerprinting)
2. Each chunk is identified by its SHA-256 hash
3. On file change: re-chunk the file → compare new chunk hashes to previous version's chunk hashes
4. Only upload chunks with **new or changed hashes** — unchanged chunks are reused

```
Version 1: [chunk_A] [chunk_B] [chunk_C] [chunk_D]
                      ^edit here^
Version 2: [chunk_A] [chunk_B'] [chunk_C] [chunk_D]

Upload: only chunk_B' (the changed chunk)
Reuse from S3: chunk_A, chunk_C, chunk_D (same hashes → same S3 objects)
```

**Benefit**: If you edit a 1 GB file in one small section → only upload 4–8 MB (the changed chunk). Massively reduces bandwidth.

---

### Deep Dive 2: Deduplication (Cross-User)

**Problem**: 1 million users upload the same popular PDF. Store it 1 million times?

**Solution: Content-Addressable Storage**
- Each chunk is stored once, identified by its content hash
- Dedup table: `{chunk_hash} → {s3_key}`
- Before uploading a chunk: check if `chunk_hash` already exists in dedup table
- If yes: just create a reference, skip the upload
- Saves enormous storage for popular files (think: the same movie uploaded 10,000 times)

**Privacy consideration**: Cross-user dedup must be done carefully. A user should never know their file matches another user's file. The dedup happens at the storage layer, invisibly.

---

### Deep Dive 3: Sync Protocol (Multi-Device)

**Problem**: Alice edits a file on her laptop. Her phone and tablet should auto-update.

**Solution:**
1. Alice's laptop client detects file change (OS file system watcher: inotify on Linux, FSEvents on Mac, ReadDirectoryChangesW on Windows)
2. Client computes diff, uploads changed chunks → Upload Service
3. Upload Service writes to S3, updates metadata DB
4. Upload Service publishes `FileChangedEvent { file_id, user_id, version_id }` to **Kafka**
5. **Sync Service** consumes event
6. All of Alice's other connected devices are listening via **long polling** or **Server-Sent Events (SSE)**
7. Device receives notification → pulls changed chunks from S3 via CDN
8. Applies delta to local file

**Conflict resolution** (two devices edit simultaneously):
- Last-write-wins (simpler): whichever version arrives last at server wins
- Both versions kept as separate conflict copies (Dropbox approach): user sees "conflict copy" and resolves manually
- **Operational Transform** (Google Docs approach): complex, for real-time collaboration

---

### Deep Dive 4: Versioning

- Every file save creates a new version entry in `file_versions` table
- Chunks are immutable and content-addressed (never overwritten in S3)
- Version restore = just point `current_version` to an older version
- After 30 versions: oldest version is soft-deleted from metadata; chunk data stays in S3 until no version references it → then garbage collected

**Garbage Collection:**
- Background job runs daily
- Finds chunk hashes with zero version references
- Deletes from S3 (or moves to Glacier for cost savings)

---

### Deep Dive 5: Storage Cost Optimization

```
Hot data  → S3 Standard        (fast, expensive: ~$0.023/GB/month)
Warm data → S3-IA              (accessed occasionally: ~$0.0125/GB/month)
Cold data → S3 Glacier         (rarely accessed: ~$0.004/GB/month)

S3 Intelligent-Tiering:
  Moves objects between tiers automatically based on access patterns.
  Files not accessed in 30 days → IA tier (45% cheaper)
  Files not accessed in 90 days → Glacier (83% cheaper)
```

For 5 petabytes: savings can be **tens of millions of dollars/year**.

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
**CP (Consistency + Partition Tolerance)** for file operations:
- File system semantics require consistency — if you upload a file, you must be able to download the same file immediately
- Slight availability trade-off is acceptable (show upload error rather than silently losing data)

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| File storage | S3 | HDFS / GFS | S3 is managed, 11 nines durability, scales to exabytes without ops work |
| Chunking | Content-defined (Rabin) | Fixed-size chunks | Content-defined chunks survive inserts/deletions better; fixed chunks shift all subsequent chunks on insertion |
| Conflict resolution | Conflict copies | Last-write-wins | Conflict copies preserve user data; LWW risks silent data loss |
| Metadata DB | PostgreSQL | Cassandra | Files/folders are relational by nature; Cassandra doesn't support hierarchical queries well |
| Sync notification | Long polling / SSE | WebSocket | Long polling/SSE is simpler for one-way server→client pushes; WebSocket is overkill here |

**What Would You Do Differently at Larger Scale?**
- **Global distribution**: replicate user's files to the nearest regional S3 bucket for faster access
- **Preview generation**: async pipeline to generate PDF thumbnails, image previews
- **Virus scanning**: every upload goes through ClamAV / VirusTotal before being made accessible
- **Admin tools**: data lineage, GDPR right-to-erasure workflows

---

### Summary Talk Track

1. "Google Drive's core challenge is **efficient sync across devices** without re-uploading entire files."
2. "Core entities: **File**, **FileVersion**, **FileChunk** — chunks are the unit of storage and transfer."
3. "The key insight: **content-defined chunking + delta sync** — only transfer changed chunks."
4. "Deduplication: chunks are content-addressed (SHA-256 hash). Same content → same S3 object, never stored twice."
5. "File metadata in PostgreSQL, actual file chunks in S3 with 11 nines durability."
6. "Sync pipeline: file change → Kafka event → all devices notified via long polling → pull changed chunks."
7. "Versioning: every save = new version. Restore = pointer update. GC handles orphaned chunks."
8. "CAP choice: **CP** — consistency is essential for a file system."

---

> **Previous**: [05 — Design a Rate Limiter](./05-rate-limiter.md)
> **Next**: [07 — Design Notification System](./07-notification-system.md)
