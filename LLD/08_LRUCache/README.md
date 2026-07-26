# 08. LRU Cache (Java LLD Solution)

This folder contains a complete, thread-safe, generic Java implementation of a Least Recently Used (LRU) Cache.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### LRUCache Generic Class & Internal Node
```java
public class LRUCache<K, V> {
    
    // Custom Doubly Linked List Node
    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value);
    }

    @Getter private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head; // Dummy head
    private final Node<K, V> tail; // Dummy tail
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public LRUCache(int capacity);

    public V get(K key); // O(1) Get & restructure list (MRU)
    public void put(K key, V value); // O(1) Put & Evict if full
    public int size();

    // Internal list manipulation helpers (not thread-safe on their own, called under lock):
    private void addNode(Node<K, V> node);
    private void removeNode(Node<K, V> node);
    private void moveToHead(Node<K, V> node);
    private void evict(); // Removes tail.prev node
}
```

---

## 2. Core Workflow & Usage

Here is how the cache is initialized and processed:

```java
LRUCache<String, String> cache = new LRUCache<>(3); // Capacity = 3

cache.put("1", "A");
cache.put("2", "B");
cache.put("3", "C");

cache.get("1"); // Retrieves "A", moves key 1 to head (MRU)

cache.put("4", "D"); // Cache full! Evicts key 2 (LRU), inserts 4 at head
```

---

## 3. Concurrency & Thread-Safety Details
- **ReadWriteLock Strategy**: Rather than using simple class synchronization, the cache uses `ReentrantReadWriteLock`.
- **Write Lock on Read Operations**: A critical interview point is explaining why `get(key)` must acquire a **Write Lock** (`lock.writeLock().lock()`). Since retrieving an item moves its node to the head of the list, a read operation modifies the doubly linked list pointers (`prev` and `next`). Therefore, concurrent readers could corrupt the pointers without write-exclusive locking.
