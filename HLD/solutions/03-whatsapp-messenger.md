# 3. Design WhatsApp Messenger

> **Difficulty**: Hard | **Asked At**: Meta, Google, Microsoft, Apple
> **Time to Answer in Interview**: 40–45 minutes

---

## SECTION 1 — Clarifying Questions (Ask These FIRST in Interview)

**Functional Scope:**
- One-to-one messaging only, or also group chats?
- Do we need delivery receipts (sent ✓, delivered ✓✓, read ✓✓)?
- Do we support media (images, videos, audio) or text only?
- Online/offline presence (green dot)?
- Push notifications for offline users?
- Message encryption (end-to-end)?

**Scale:**
- How many DAU?
- How many messages per day?
- What is the typical message size?

**Typical Interviewer Answer (assume this unless told otherwise):**
- 2 billion DAU (WhatsApp's actual scale)
- 100 billion messages per day
- 1-to-1 and group chat (max 256 members)
- Text messages only for MVP; mention media as extension
- Delivery receipts: sent, delivered, read
- Push notifications for offline users

---

## SECTION 2 — Functional & Non-Functional Requirements

### Functional Requirements
1. One-to-one real-time messaging
2. Group chats (up to 256 members)
3. Message delivery receipts: Sent ✓ / Delivered ✓✓ / Read ✓✓ (blue)
4. Online presence indicator
5. Push notifications for offline users
6. Message history (retrieve past messages)

### Non-Functional Requirements
| Property | Target |
|---|---|
| **Message Latency** | < 100ms delivery for online users |
| **Availability** | 99.99% — messaging must work even during partial failures |
| **Consistency** | Messages must be delivered in order within a conversation |
| **Durability** | No message loss — at-least-once delivery guarantee |
| **Scalability** | 100 billion messages/day, 2 billion DAU |

### Out of Scope (for MVP)
- Payments (WhatsApp Pay)
- Status/Stories
- Calls (voice/video)

---

## SECTION 3 — Capacity Estimation

### Message Volume
- 100 billion messages/day
- = 100B / 86,400 ≈ **~1.15 million messages/sec**
- Peak (assume 3× average): ~3.5 million messages/sec

### Storage
- Average message size: 100 bytes (text + metadata)
- Per day: 100B × 100B = **10 TB/day**
- Per year: **~3.6 PB/year** (WhatsApp stores 30 days on server)
- 30-day retention: **~300 TB rolling window**

### Connections
- 2 billion DAU, assume 10% online at peak = **200 million concurrent connections**
- Each WebSocket connection: ~10 KB overhead
- 200M × 10KB = **~2 TB RAM just for connections** — requires thousands of servers

### Bandwidth
- 1.15M messages/sec × 100 bytes = **~115 MB/s** inbound

---

## SECTION 4 — API Design

WhatsApp uses **WebSocket** for real-time, and REST for non-real-time actions.

### 1. Establish WebSocket Connection
```
WS wss://chat.whatsapp.com/ws
Authorization: Bearer <token>

// Client connects on app open, stays connected while in foreground
// Heartbeat ping/pong every 30 seconds to keep alive
```

### 2. Send Message (over WebSocket)
```
// Client → Server (WebSocket message frame)
{
  "type": "message",
  "msg_id": "client-generated-uuid",
  "to": "user_id_456",           // or group_id
  "text": "Hey! How are you?",
  "timestamp": 1722000000000
}

// Server → Client (acknowledgement)
{
  "type": "ack",
  "msg_id": "client-generated-uuid",
  "status": "sent"               // server received it
}
```

### 3. Receive Message (Server → Client via WebSocket)
```
// Server pushes to recipient
{
  "type": "message",
  "msg_id": "server-msg-id-789",
  "from": "user_id_123",
  "text": "Hey! How are you?",
  "timestamp": 1722000000000
}

// Client acknowledges delivery
{
  "type": "ack",
  "msg_id": "server-msg-id-789",
  "status": "delivered"
}
```

### 4. REST Endpoints
```
GET  /api/v1/conversations                        // list all chats
GET  /api/v1/conversations/{conv_id}/messages     // fetch message history
POST /api/v1/groups                               // create group
POST /api/v1/groups/{group_id}/members            // add member
```

---

## SECTION 5 — Data Model & Database Choice

### Table 1: `messages`
```
message_id    BIGINT       PRIMARY KEY  (Snowflake)
conversation_id  BIGINT    NOT NULL
sender_id     BIGINT
text          TEXT
status        ENUM('sent', 'delivered', 'read')
created_at    TIMESTAMP
```
**DB Choice**: **Cassandra**
- Partition by `conversation_id`, cluster by `message_id DESC`
- Enables fast "give me last 50 messages in conversation X"
- Write-heavy (1.15M writes/sec) — Cassandra's LSM tree is write-optimized

### Table 2: `conversations`
```
conversation_id  BIGINT   PRIMARY KEY
type          ENUM('direct', 'group')
created_at    TIMESTAMP
last_message_preview  TEXT
```

### Table 3: `conversation_members`
```
conversation_id  BIGINT
user_id          BIGINT
joined_at        TIMESTAMP
PRIMARY KEY (conversation_id, user_id)
```

### Table 4: `users`
```
user_id       BIGINT       PRIMARY KEY
phone_number  VARCHAR(20)  UNIQUE
display_name  VARCHAR(100)
last_seen     TIMESTAMP
is_online     BOOLEAN
```
**DB Choice**: PostgreSQL (low write volume, relational)

### Message Status Tracking
- Use a separate **Redis Hash** per message for delivery status:
  ```
  msg:{message_id} → { user_id_1: "delivered", user_id_2: "read" }
  ```
- Expire after 30 days (TTL)

---

## SECTION 6 — High-Level Architecture

```
                    ┌─────────────────────────────────────────────────┐
                    │                    CLIENTS                       │
                    │            (iOS / Android / Web)                 │
                    └──────────────────┬──────────────────────────────┘
                                       │ WebSocket (persistent)
                               ┌───────▼────────┐
                               │  Load Balancer  │
                               │(Layer 4 / L7)  │
                               └───────┬────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          │                            │                            │
 ┌────────▼────────┐         ┌─────────▼───────────┐    ┌──────────▼──────┐
 │  Chat Server 1  │         │   Chat Server 2      │    │  Chat Server N  │
 │  (WebSocket)    │         │   (WebSocket)        │    │  (WebSocket)    │
 │  Holds 10K      │         │   Holds 10K          │    │  Holds 10K      │
 │  connections    │         │   connections        │    │  connections    │
 └────────┬────────┘         └─────────┬───────────┘    └──────────┬──────┘
          │                            │                            │
          └────────────────────────────┼────────────────────────────┘
                                       │
                              ┌────────▼────────┐
                              │  Message Queue  │
                              │   (Kafka)       │
                              └────────┬────────┘
                                       │
                   ┌───────────────────┼───────────────────┐
                   │                   │                   │
         ┌─────────▼──────┐   ┌────────▼───────┐  ┌───────▼──────────┐
         │ Message Store  │   │ Presence Service│  │ Notification Svc │
         │ (Cassandra)    │   │ (Redis pub/sub) │  │ (APNs / FCM)     │
         └────────────────┘   └────────────────┘  └──────────────────┘

         ┌──────────────────────────────────────────────┐
         │              Connection Registry             │
         │  (Redis): user_id → chat_server_id           │
         │  "User 123 is on Chat Server 7"              │
         └──────────────────────────────────────────────┘
```

### Write Path (Sending a Message):
```
Sender (Alice) → Chat Server A
  → Chat Server A assigns message_id, persists to Cassandra
  → Publishes to Kafka: { msg_id, to: Bob, ... }
  → Kafka consumed by routing logic:
       → Is Bob online? Check Connection Registry (Redis)
       → YES: Bob is on Chat Server C → push via WebSocket
       → NO: Bob is offline → Notification Service → APNs/FCM push notification
  → Delivery ack flows back: Bob's client → Chat Server C → Kafka → Chat Server A → Alice
```

### Read Path (Loading Chat History):
```
User opens conversation → REST GET /conversations/{id}/messages
  → Message Service queries Cassandra (partition key = conversation_id)
  → Returns last 50 messages, paginated by cursor
```

### Key Insight: Connection Registry
- With N chat servers, when Alice sends a message to Bob:
  - How does Alice's server know which server Bob is connected to?
  - **Solution**: **Redis Hash** maps `user_id → chat_server_id`
  - When Bob connects, his Chat Server registers: `SET user:{bob_id} server_id`
  - On disconnect: `DEL user:{bob_id}`
  - Alice's server looks up Bob's server, then routes message to it via internal gRPC call

---

## SECTION 7 — Deep Dives

### Deep Dive 1: Message Ordering

**Problem**: Messages must appear in the correct order. Network delays can cause out-of-order delivery.

**Solution: Sequence Numbers per Conversation**
- Each `conversation_id` has a monotonically increasing sequence counter (stored in Redis or Zookeeper)
- Every message gets a `seq_num` within its conversation
- Client renders messages ordered by `seq_num`, not arrival time
- If client sees gap (seq 5, then seq 7), it knows to fetch seq 6 from server

**Alternative**: Use Cassandra clustering key `message_id` (Snowflake = time-ordered). Works if clocks are synced (NTP). Cassandra guarantees order within a partition automatically.

---

### Deep Dive 2: Delivery Guarantees (At-Least-Once)

**Exactly-once delivery** is nearly impossible in distributed systems. WhatsApp guarantees **at-least-once** with **idempotency**:

1. Client generates a `client_msg_id` (UUID) before sending
2. If network drops after sending but before receiving ack, client retries with same `client_msg_id`
3. Server checks: "Have I seen this `client_msg_id` before?" → Yes: drop duplicate, resend ack → No: process and store
4. Dedup table: `processed_msgs` with TTL of 24 hours (Redis)

---

### Deep Dive 3: Offline Message Delivery

When recipient is offline:
1. Message is stored in Cassandra (durable)
2. Notification Service sends push notification via **APNs** (iOS) / **FCM** (Android)
3. When user comes back online:
   - Client reconnects WebSocket
   - Server checks: "Any messages delivered while you were offline?" → pulls undelivered messages from Cassandra
   - Sends them in order, updates delivery status

**Last-mile delivery** (from server to APNs/FCM) is fire-and-forget. WhatsApp trusts the device's push notification system.

---

### Deep Dive 4: Group Messages (Up to 256 Members)

**Naive approach**: Fan-out to all 256 members on write = 256 writes per message
- At 1.15M messages/sec: 1.15M × 256 = **294M writes/sec** — too high!

**Better approach (WhatsApp's actual approach)**:
1. Store 1 copy of group message in Cassandra
2. Maintain a per-user **message queue** (unread pointers) — a record of `(user_id, conv_id, last_read_seq)` 
3. Each member's Chat Server fetches the single message copy, not N copies
4. Delivery receipts are aggregated: all 256 members must receive before showing ✓✓ to sender

---

### Deep Dive 5: End-to-End Encryption (E2EE)

- WhatsApp uses the **Signal Protocol** for E2EE
- Server only sees encrypted ciphertext — cannot read messages
- Public keys exchanged during handshake, stored on server
- Private keys live on device only
- This means: server-side search and message backup require special handling (encrypted backups to Google Drive / iCloud)

For the interview, mention E2EE as a consideration, but say "implementation of the crypto layer is out of scope for this design session."

---

## SECTION 8 — Trade-offs & Alternatives

### CAP Theorem Position
**AP with eventual consistency for delivery receipts**
- Message delivery itself must be reliable (at-least-once)
- Delivery status (✓✓ vs ✓) can be slightly delayed — eventual consistency acceptable
- If two servers disagree on Bob's online status momentarily, that's fine

### Key Trade-offs Table

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Transport protocol | WebSocket | HTTP long polling | WebSocket is truly bidirectional, lower overhead; long polling is wasteful |
| Message store | Cassandra | MySQL | Cassandra's write throughput and horizontal scaling is unmatched for 1.15M msg/sec |
| Connection routing | Redis Connection Registry | Consistent hashing | Redis lookup is O(1); consistent hashing adds complexity with rebalancing |
| Group fan-out | Single copy + pointers | Multiple copies | Single copy saves 256× storage; pointers enable per-user read tracking |
| Delivery guarantee | At-least-once + dedup | Exactly-once | Exactly-once requires distributed transactions — too slow at this scale |

### What Would You Do Differently at Larger Scale?
- Dedicated **media server** with CDN for image/video delivery
- **Regional clusters** (US, EU, Asia) to avoid cross-continental latency
- **Message compression** (Zstandard) to reduce bandwidth by 60–70%
- **Binary protocol** (Protocol Buffers) instead of JSON for 3× smaller payloads

---

## Interview Flow Summary (Talk Track)

1. "The core challenge here is **real-time delivery at 1.15M messages/sec with 200M concurrent connections**"
2. "I'd use WebSocket for real-time messaging — persistent bidirectional connection"
3. "The key problem: with many chat servers, how do I route a message to the right server? **Connection Registry in Redis**"
4. "Messages are stored in Cassandra, partitioned by conversation_id"
5. "For offline users, I fall back to APNs/FCM push notifications"
6. "The hardest part is group messages — I'd use a single-copy-with-pointers approach to avoid fan-out explosion"
7. "Delivery guarantee: at-least-once with client-generated UUIDs for deduplication"

---

> **Previous**: [02 — Design Twitter / X Feed](./02-twitter-feed.md)
> **Next**: [04 — Design YouTube](./04-youtube.md)
