# 7. Design Notification System

> **Difficulty**: Medium | **Asked At**: Meta, Uber, LinkedIn, Amazon, Google
> **Time to Answer in Interview**: 35–40 minutes

---

## Step 1 — Requirements (~5 min)

### 1.1 Clarifying Questions (Ask These FIRST)

**Functional Scope:**
- What types of notifications? Push (mobile), Email, SMS, In-app?
- Is delivery guaranteed or best-effort?
- Should we support scheduled notifications (send at a specific time)?
- Do we need personalization or priority levels (critical vs promotional)?
- What are the rate limits? (e.g., don't spam a user more than N times/hour)

**Scale:**
- How many notifications per day?
- How many users?
- Expected peak volume?

**Typical Interviewer Answer:**
- All 4 types: Push (iOS + Android), Email, SMS, In-app
- 1 billion notifications per day
- 100 million DAU
- Scheduling: yes (up to 30 days in future)
- Priority: high (OTP, alerts) vs low (promotions)
- At-least-once delivery

### 1.2 Functional Requirements (FR)
1. Send push notifications (iOS APNs, Android FCM)
2. Send email notifications (via SendGrid / SES)
3. Send SMS notifications (via Twilio)
4. Send in-app notifications (bell icon)
5. User preferences — opt out of specific notification types
6. Scheduled notifications (send at a future time)
7. Notification templates with dynamic variables (`Hello {name}, your order {order_id} is ready`)

### 1.3 Non-Functional Requirements (NFR)
| Property | Target |
|---|---|
| **Throughput** | Handle 1 billion notifications/day = ~11,500/sec |
| **Latency** | High-priority (OTP, alerts): < 5 sec; Low-priority: best effort |
| **Reliability** | At-least-once delivery for high-priority notifications |
| **Scalability** | Horizontally scalable across all channels |
| **Deduplication** | Don't send duplicate notifications if retry triggers |

### 1.4 Out of Scope
- WhatsApp / Telegram bot notifications
- Analytics and open-rate tracking (mention as extension)
- A/B testing notification content

---

## Step 2 — Core Entities (~3 min)

### 2.1 Entity Identification

```
┌──────────────────┐     ┌──────────────────┐     ┌──────────────────┐
│ NotificationJob  │     │ NotifDelivery    │     │  UserDevice      │
│                  │     │ (per-recipient)  │     │                  │
│ job_id           │────►│ delivery_id      │     │ user_id          │
│ type (push/email │     │ job_id           │     │ device_id        │
│   /sms/inapp)    │     │ user_id          │     │ platform         │
│ priority         │     │ channel          │     │ push_token       │
│ template_id      │     │ status           │     │ is_active        │
│ template_vars    │     │ idempotency_key  │     └──────────────────┘
│ scheduled_at     │     └──────────────────┘
│ status           │                              ┌──────────────────┐
└──────────────────┘                              │ UserPreference   │
                                                  │                  │
                                                  │ user_id          │
                                                  │ push_enabled     │
                                                  │ email_enabled    │
                                                  │ quiet_hours      │
                                                  └──────────────────┘
```

**Primary entities**: `NotificationJob` (the send request), `NotificationDelivery` (per-recipient delivery tracking), `UserDevice` (push tokens), `UserPreference` (opt-in/out settings).

### 2.2 Data Model / Schema

**Table 1: `notification_jobs`**
```
job_id           BIGINT       PRIMARY KEY (Snowflake)
type             ENUM('push', 'email', 'sms', 'inapp')
priority         ENUM('high', 'low')
template_id      VARCHAR(100)
template_vars    JSONB
status           ENUM('queued', 'processing', 'delivered', 'failed')
scheduled_at     TIMESTAMP
created_at       TIMESTAMP
retry_count      INT          DEFAULT 0
```
**DB Choice**: PostgreSQL (good for job tracking, scheduling queries)

**Table 2: `notification_deliveries` (per-recipient delivery record)**
```
delivery_id      BIGINT       PRIMARY KEY
job_id           BIGINT
user_id          BIGINT
channel          ENUM('push', 'email', 'sms', 'inapp')
status           ENUM('pending', 'delivered', 'failed', 'unsubscribed')
delivered_at     TIMESTAMP
failure_reason   TEXT         NULL
idempotency_key  VARCHAR(64)  UNIQUE   -- prevents duplicates on retry
```
**DB Choice**: Cassandra (high write volume, partition by `user_id` for per-user queries)

**Table 3: `user_devices` (for push notifications)**
```
user_id          BIGINT
device_id        VARCHAR(200)
platform         ENUM('ios', 'android', 'web')
push_token       TEXT         -- APNs token or FCM registration token
is_active        BOOLEAN
last_seen        TIMESTAMP
PRIMARY KEY (user_id, device_id)
```

**Table 4: `user_preferences`**
```
user_id              BIGINT   PRIMARY KEY
push_enabled         BOOLEAN  DEFAULT true
email_enabled        BOOLEAN  DEFAULT true
sms_enabled          BOOLEAN  DEFAULT true
marketing_enabled    BOOLEAN  DEFAULT true
quiet_hours_start    TIME     NULL  (e.g., 22:00 — don't disturb after 10pm)
quiet_hours_end      TIME     NULL
```

**Table 5: `inapp_notifications`**
```
notification_id  BIGINT       PRIMARY KEY
user_id          BIGINT
text             TEXT
action_url       TEXT
is_read          BOOLEAN      DEFAULT false
created_at       TIMESTAMP
```
**DB Choice**: Cassandra (partition by `user_id`, cluster by `created_at DESC`)

> 🎯 **NFR addressed**: **Deduplication** — `idempotency_key` UNIQUE constraint on deliveries prevents double-sends. **Reliability** — Cassandra durability for delivery records. **Scalability** — Cassandra for high-volume delivery writes.

---

## Step 3 — API or Interface (~5 min)

### 3.1 Send Notification (Internal API — called by other services)
```
POST /api/v1/notifications/send
Authorization: Internal-Service-Key

{
  "type": "push",                 // push | email | sms | inapp
  "priority": "high",             // high | low
  "recipients": ["user_id_1", "user_id_2"],
  "template_id": "order_shipped",
  "template_variables": {
    "order_id": "ORD-12345",
    "delivery_date": "July 28"
  },
  "scheduled_at": null           // null = send immediately, or ISO8601 timestamp
}

Response 202 Accepted:
{
  "notification_job_id": "job_abc123",
  "status": "queued"
}
```

### 3.2 Get Notification Status
```
GET /api/v1/notifications/{notification_job_id}/status
Response: { "status": "delivered" | "failed" | "pending", "delivered_at": "..." }
```

### 3.3 User Preference Update
```
PUT /api/v1/users/{user_id}/notification-preferences
{
  "push_enabled": true,
  "email_enabled": false,
  "sms_enabled": true,
  "marketing_emails": false
}
```

### 3.4 Fetch In-App Notifications (User-facing)
```
GET /api/v1/users/{user_id}/notifications?limit=20&cursor=...
Response: {
  "notifications": [
    { "id": "n1", "text": "Your order shipped!", "read": false, "created_at": "..." }
  ],
  "unread_count": 5
}
```

> 🎯 **NFR addressed**: **Throughput** — 202 Accepted (async) pattern prevents blocking under high load. **Latency** — high-priority notifications bypass scheduling, sent immediately.

---

## Step 4 — Data Flow (~3 min)

### 4.1 Capacity Estimation (Back-of-Envelope)

**Volume:**
- 1 billion notifications/day
- = 1B / 86,400 ≈ **~11,500 notifications/sec** average
- Peak (morning rush, flash sales): ~50,000/sec

**Breakdown by channel (approximate):**
- Push notifications: 70% = 700M/day
- Email: 20% = 200M/day
- SMS: 5% = 50M/day (expensive! $0.01/SMS = $500K/day at full volume)
- In-app: 5% = 50M/day

**Storage (notification logs):**
- Keep logs for 30 days for debugging and idempotency
- 1B records/day × 30 days × 200 bytes = **~6 TB** of notification logs

### 4.2 Data Flow Through System

**Immediate Notification Flow:**
```
Producer Service (Order/Payment/Auth)
  → POST /notifications/send
  → Notification API Service:
    1. Validate request
    2. Check user preferences (opted out? quiet hours?)
    3. Render template with variables
    4. Enqueue to Kafka (topic by channel: push/email/sms/inapp)
  → Channel Worker consumes from Kafka:
    - Push Worker → APNs (iOS) / FCM (Android)
    - Email Worker → SendGrid / Amazon SES
    - SMS Worker → Twilio
    - In-app Worker → Write to Cassandra
  → Update delivery status
```

**Scheduled Notification Flow:**
```
Notification API stores job with scheduled_at in DB
  → Scheduler Service polls DB every minute: WHERE scheduled_at <= now()
  → Enqueues due notifications into Kafka
  → Same channel worker pipeline as above
```

> 🎯 **NFR addressed**: **Throughput** — Kafka decouples producers from channel workers; scales consumers independently. **Reliability** — Kafka retains messages if workers are down; at-least-once delivery.

---

## Step 5 — High-level Design (~10 min)

### 5.1 Architecture Diagram

```
     PRODUCER SERVICES (any internal service that wants to notify users)
     ────────────────────────────────────────────────────────────────────
     [Order Service]  [Payment Service]  [Marketing Service]  [Auth Service]
            │                │                  │                   │
            └────────────────┼──────────────────┘───────────────────┘
                             │
                    POST /notifications/send
                             │
                    ┌────────▼────────────┐
                    │  Notification API   │
                    │  Service            │
                    │  - Validate request │
                    │  - Check user prefs │
                    │  - Render template  │
                    │  - Enqueue to Kafka │
                    └────────┬────────────┘
                             │
                    ┌────────▼────────────┐
                    │  Kafka              │
                    │                     │
                    │  Topics:            │
                    │  notifications.push │
                    │  notifications.email│
                    │  notifications.sms  │
                    │  notifications.inapp│
                    └────────┬────────────┘
                             │
         ┌───────────────────┼──────────────────────────────┐
         │                   │                              │
┌────────▼──────┐   ┌────────▼──────┐             ┌────────▼────────┐
│ Push Worker   │   │ Email Worker  │             │ SMS Worker      │
│               │   │               │             │                 │
│ Reads user    │   │ Renders HTML  │             │ Formats SMS     │
│ device tokens │   │ email via     │             │ via Twilio API  │
│ → APNs (iOS)  │   │ SendGrid/SES  │             │                 │
│ → FCM (Andrd) │   │               │             └─────────────────┘
└────────┬──────┘   └────────┬──────┘
         │                   │
    ┌────▼────┐         ┌────▼─────────┐
    │  APNs   │         │  SendGrid /  │
    │  FCM    │         │  Amazon SES  │
    └─────────┘         └──────────────┘

┌──────────────────────────────────────────────────────┐
│              Scheduler Service                       │
│  Polls DB every minute for scheduled_at <= now()    │
│  Enqueues due notifications into Kafka               │
└──────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────┐
│           User Preference Filter (in API Svc)        │
│  Before enqueuing: check if user has opted out       │
│  Check quiet hours: if now is in quiet window,       │
│  delay notification to after quiet_hours_end         │
└──────────────────────────────────────────────────────┘
```

### 5.2 Component Walkthrough

| Component | Role | Why This Choice |
|---|---|---|
| **Notification API Service** | Validates, renders templates, checks preferences, enqueues | Single entry point; all filtering done before queue to avoid wasted work |
| **Kafka** | Channel-specific topic queues | Durable, replayable; separate topics for priority isolation |
| **Push/Email/SMS Workers** | Channel-specific delivery via third-party APIs | Independently scalable per channel; isolated failure domains |
| **Scheduler Service** | Polls for due scheduled notifications | Simple polling approach; minute-level granularity is sufficient |
| **User Preference Filter** | Checks opt-outs, quiet hours before enqueuing | Prevents sending to opted-out users (legal compliance + user experience) |

> 🎯 **NFR addressed**: **Throughput 11.5K/sec** — Kafka + consumer group scaling. **Latency < 5s for OTP** — separate high-priority topic with large consumer group. **Deduplication** — idempotency_key checked before delivery. **Scalability** — each channel worker pool scales independently.

---

## Step 6 — Deep Dives (~15 min)

### Deep Dive 1: Push Notifications — APNs vs FCM

**iOS (Apple Push Notification service — APNs):**
```
Your Server → APNs (Apple's servers) → iOS Device
- Requires: device push token + Apple Developer certificate
- APNs is persistent, always-on connection from device to Apple
- If device offline: APNs stores 1 notification per app (overwrites old)
- Payload limit: 4 KB
```

**Android (Firebase Cloud Messaging — FCM):**
```
Your Server → FCM (Google's servers) → Android Device
- Requires: FCM registration token (changes when app reinstalled)
- If device offline: FCM queues messages for up to 4 weeks
- Payload limit: 4 KB
```

**Invalid Tokens:**
- APNs / FCM return error codes when token is stale (user uninstalled app)
- Push Worker must handle these errors: mark token as inactive in `user_devices`
- Don't keep sending to dead tokens (wastes quota, causes rate limiting by APNs/FCM)

---

### Deep Dive 2: Handling Failures & Retries

**Problem**: APNs/FCM is a third-party service. It will fail sometimes.

**Solution: Exponential Backoff with Retry Limit**
```
Attempt 1: immediate
Attempt 2: 1 minute later
Attempt 3: 5 minutes later
Attempt 4: 30 minutes later
Attempt 5: give up, mark as failed

For high-priority (OTP, security alerts): max 5 retries
For low-priority (promotions): max 2 retries, then drop
```

**Idempotency**: Each delivery attempt has a unique `idempotency_key` (`job_id:user_id:channel:attempt`). On retry, the worker can safely retry without worrying about double-sending — the key prevents duplicate delivery records.

---

### Deep Dive 3: Rate Limiting Per User

**Problem**: Don't spam users. Marketing team might trigger 100 notifications for same user.

**Rules:**
```
Max 5 push notifications per user per hour
Max 10 emails per user per day
Max 3 SMS per user per day
Max 2 marketing emails per user per week
```

**Implementation:**
- Before enqueuing, check Redis counters:
  - `notif:push:{user_id}:{hour}` → if ≥ 5: queue for next hour or drop
  - Counter has TTL matching the window
- For high-priority (OTP, account alerts): bypass rate limiting entirely

---

### Deep Dive 4: Fan-out for Mass Notifications

**Problem**: Marketing team sends a promotional notification to all 100 million users.

**Naive approach**: Query all user IDs, create 100M notification records → too slow.

**Solution:**
1. Marketing team creates a **segment** (e.g., "all users in India who haven't ordered in 30 days")
2. Notification API creates 1 job with a **segment_id** (not individual user IDs)
3. Fan-out Worker expands the segment → streams user IDs in batches of 10,000
4. Each batch → Kafka → Push Workers
5. Process 100M notifications over 30–60 minutes (acceptable for marketing)

**Priority queues in Kafka**: Use separate Kafka topics with separate consumer groups:
- `notifications.high` → large consumer group (fast processing)
- `notifications.low` → small consumer group (slower, bulk processing)

This ensures OTPs never wait behind marketing blasts.

---

### Deep Dive 5: Template Rendering

```
Template: "Hello {{name}}, your order {{order_id}} shipped! Track at {{tracking_url}}"

Variables:  { "name": "Alice", "order_id": "123", "tracking_url": "https://..." }

Rendered: "Hello Alice, your order 123 shipped! Track at https://..."
```

- Templates stored in DB, cached in Redis
- Rendering done in API Service before enqueuing (so workers get ready-to-send payload)
- Support Markdown for email templates (render to HTML)
- Localization: template per locale (`order_shipped_en`, `order_shipped_hi`, `order_shipped_de`)

---

### Trade-offs & Alternatives

**CAP Theorem Position:**
**AP with at-least-once delivery**:
- It's acceptable to deliver a notification twice (idempotency key prevents true duplicates)
- It's NOT acceptable to fail to deliver an OTP
- Kafka provides durability — messages survive worker crashes

**Key Trade-offs Table:**

| Decision | Choice | Alternative | Reasoning |
|---|---|---|---|
| Queue | Kafka | RabbitMQ | Kafka retains messages (replayable), RabbitMQ deletes on ack — Kafka better for audit/retry |
| Priority separation | Separate Kafka topics | Single topic with priority field | Separate topics guarantee high-priority messages are processed independently |
| Fan-out timing | Async segment expansion | Synchronous per-user query | Synchronous fan-out blocks API response; async allows gradual rollout |
| Email provider | SendGrid/SES | Self-hosted SMTP | Deliverability of self-hosted SMTP is very hard to maintain (spam filters) |
| Retry mechanism | Exponential backoff | Fixed interval | Exponential backoff reduces load on failing external services |

**What Would You Do Differently at Larger Scale?**
- **Delivery tracking**: webhook from APNs/FCM confirming device actually received notification (vs. just accepted by APNs)
- **A/B testing**: send variant A to 50% of users, variant B to other 50%, track click rates
- **Notification center**: history of all past notifications per user (already covered by `inapp_notifications` table)
- **Suppression lists**: global lists of hard-bounced emails — never send again

---

### Summary Talk Track

1. "A notification system is fundamentally an **async fan-out pipeline** from event source to delivery channel."
2. "Core entities: **NotificationJob** (request), **Delivery** (per-recipient tracking), **UserDevice** (push tokens), **UserPreference** (opt-outs)."
3. "Architecture: Notification API → Kafka (by channel) → Channel Workers → APNs/FCM/SendGrid/Twilio."
4. "The 3 key challenges: **priority separation** (OTP vs marketing), **retry with backoff**, and **rate limiting per user**."
5. "For push: device tokens stored per user, pushed to APNs (iOS) or FCM (Android)."
6. "For mass sends: segment expansion in fan-out worker, not at API time."
7. "Idempotency key prevents duplicate delivery on retry."
8. "Quiet hours and user preferences are checked BEFORE enqueuing — avoids wasted work."

---

> **Previous**: [06 — Design Google Drive / Dropbox](./06-google-drive.md)
> **Next**: [08 — Design Search Autocomplete / Typeahead](./08-search-autocomplete.md)
