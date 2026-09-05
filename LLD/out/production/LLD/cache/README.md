# 08. LRU Cache — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Fixed-capacity key-value cache
- `get(key)` → returns value if present, else `null`; marks the item as **Most Recently Used (MRU)**
- `put(key, value)` → inserts or updates; if capacity full, **evict the Least Recently Used (LRU)** item first
- Both operations must be **O(1)** time complexity

**Non-Functional Requirements:**
- **Thread-safe**: Concurrent reads and writes from multiple threads
- **Generic**: Support any key-value types `<K, V>`

**Out of Scope:**
- TTL / expiry-based eviction
- Distributed cache (Redis-style)
- Persistence to disk

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Node<K,V>` | Inner Class | Doubly linked list node; holds key, value, prev, next pointers |
| `LRUCache<K,V>` | Class | Main cache; manages HashMap + Doubly Linked List |

**Data Structures Used:**
- `HashMap<K, Node<K,V>>` → O(1) lookup by key
- **Doubly Linked List (DLL)** → O(1) move-to-head + evict-from-tail
- Dummy `head` and `tail` nodes → eliminate null checks in pointer manipulation

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Why HashMap + Doubly Linked List?

```
HashMap alone → O(1) lookup, but can't track order of access
LinkedList alone → O(n) search
Combined → O(1) both lookup AND order tracking
```

### 🔷 Cache Structure (Visual)
```
HEAD ↔ [Most Recently Used] ↔ ... ↔ [Least Recently Used] ↔ TAIL
         ↑ new inserts go here                         ↑ evictions happen here
```

### 🔷 Class Skeleton
```java
public class LRUCache<K, V> {

    // Internal doubly linked list node
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
    }

    private final int capacity;
    private final Map<K, Node<K, V>> map;  // HashMap for O(1) lookup
    private final Node<K, V> head;         // Dummy head (MRU side)
    private final Node<K, V> tail;         // Dummy tail (LRU side)
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public V get(K key);          // O(1): lookup + moveToHead
    public void put(K key, V value); // O(1): insert/update + evict if full
    public int size();

    // Private helpers (called under lock):
    private void addNode(Node<K, V> node);   // Always adds after dummy head
    private void removeNode(Node<K, V> node); // Unlinks node from list
    private void moveToHead(Node<K, V> node); // removeNode + addNode
    private void evict();                     // Removes tail.prev (LRU)
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Cache Operations
```java
LRUCache<String, String> cache = new LRUCache<>(3);

cache.put("1", "A"); // HEAD ↔ [1:A] ↔ TAIL
cache.put("2", "B"); // HEAD ↔ [2:B] ↔ [1:A] ↔ TAIL
cache.put("3", "C"); // HEAD ↔ [3:C] ↔ [2:B] ↔ [1:A] ↔ TAIL

cache.get("1");
// → Found in map → moveToHead("1")
// HEAD ↔ [1:A] ↔ [3:C] ↔ [2:B] ↔ TAIL

cache.put("4", "D"); // Cache full! Evict LRU = "2" (tail.prev)
// HEAD ↔ [4:D] ↔ [1:A] ↔ [3:C] ↔ TAIL
```

### `get()` Internal Steps
```
1. lock.writeLock().lock()   ← ⚠️ WRITE lock, not READ lock (see below)
2. node = map.get(key)
3. if node == null → return null
4. moveToHead(node)          ← modifies DLL pointers
5. return node.value
6. lock.writeLock().unlock()
```

### `put()` Internal Steps
```
1. lock.writeLock().lock()
2. if key exists → update value, moveToHead
3. else → create new Node, addNode (after head), put in map
4. if map.size() > capacity → evict() (remove tail.prev, remove from map)
5. lock.writeLock().unlock()
```

### ⚠️ Key Interview Point — Why `get()` needs a WRITE Lock?
> `get()` calls `moveToHead()` which **modifies the doubly linked list pointers** (`prev`, `next`). If two threads call `get()` concurrently with a READ lock, they'd both modify pointers simultaneously → **pointer corruption**. Therefore `get()` must use a **Write Lock** despite being semantically a read.

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| TTL-based expiry | Store `insertTime` in `Node`, check on `get()` |
| LFU Cache (Least Frequently Used) | Replace DLL with frequency-bucket structure |
| Max memory size (not count) | Track `currentBytes`; evict until `currentBytes < maxBytes` |
| Cache statistics | Add `hitCount`, `missCount` counters |
| Distributed cache | Shard by `key.hashCode() % shardCount` across multiple `LRUCache` instances |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `get(key)` | `WriteLock` | Modifies DLL pointers — concurrent reads would corrupt list |
| `put(key, value)` | `WriteLock` | Modifies map + DLL atomically |
| `evict()` | Called inside `WriteLock` | Remove from map + DLL must be atomic |

> **Pro Tip:** Using `ReentrantReadWriteLock` instead of simple `synchronized` is an intentional design choice — it allows multiple readers in the future if we ever separate structural modifications from reads.
