package cache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache<K, V> {

    private static class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) { this.key = key; this.value = value; }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> map;
    private final Node<K, V> head;
    private final Node<K, V> tail;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public int getCapacity() { return capacity; }

    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public V get(K key) {
        lock.writeLock().lock();
        try {
            Node<K, V> node = map.get(key);
            if (node == null) return null;
            moveToHead(node);
            return node.value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            Node<K, V> node = map.get(key);
            if (node != null) {
                node.value = value;
                moveToHead(node);
            } else {
                if (map.size() >= capacity) evict();
                Node<K, V> newNode = new Node<>(key, value);
                addNode(newNode);
                map.put(key, newNode);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try { return map.size(); }
        finally { lock.readLock().unlock(); }
    }

    private void addNode(Node<K, V> node) {
        node.next = head.next;
        node.prev = head;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node) { removeNode(node); addNode(node); }

    private void evict() {
        Node<K, V> lru = tail.prev;
        if (lru != head) {
            removeNode(lru);
            map.remove(lru.key);
            System.out.printf("[Eviction] Cache full. Evicted key: %s%n", lru.key);
        }
    }
}
