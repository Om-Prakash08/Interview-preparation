# 15. File System (In-Memory) — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Create directories recursively (`mkdir /a/b/c`)
- Create and **write/append content** to files (`addContentToFile`)
- **Read** file content (`readContentFromFile`)
- **List** directory contents (`ls`)
- Get **recursive size** of a file or directory (`getSize`)
- Files auto-created if path doesn't exist during write

**Non-Functional Requirements:**
- **Thread-safe**: Concurrent reads/writes to the file system
- **Singleton** FileSystem — single root `/`
- Path resolution handles both files and directories uniformly

**Out of Scope:**
- File permissions / ACL
- Hard links / symbolic links
- Disk persistence (in-memory only)

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Entry` | Abstract Class | Common base: name, parent, timestamps; `getFullPath()` |
| `File` | Leaf (extends Entry) | Stores string content; read/write/append operations |
| `Directory` | Composite (extends Entry) | Contains list of child `Entry` objects; recursive size |
| `FileSystem` | Singleton | Path resolver + public API (mkdir, ls, read, write) |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Composite Pattern — Entry Hierarchy
```
Entry (abstract)
    ├── File (Leaf)
    │     └── content: String
    └── Directory (Composite)
          └── children: List<Entry>  ← can contain Files and Directories
```
**Why?** Both `File` and `Directory` share the same `Entry` interface. `getSize()` works recursively on `Directory` without caring whether children are files or subdirectories. This is the classic **Composite Pattern**.

### 🔷 Singleton + Facade — FileSystem
```
FileSystem (Singleton)
    ├── root: Directory ("/")
    ├── resolvePath(path) → traverses tree to find/create Entry
    └── Public API: mkdir, ls, addContentToFile, readContentFromFile, getSize
```
**Why Facade?** Clients use simple path strings like `/a/b/c/hello.txt` — all the tree-traversal complexity is hidden inside `FileSystem`.

### 🔷 Class Skeleton
```java
public abstract class Entry {
    protected final String name;
    protected Directory parent;
    protected final long created;
    protected long lastUpdated;

    public abstract boolean isDirectory();
    public abstract int getSize();

    public String getFullPath(); // Recursively: parent.getFullPath() + "/" + name
}

public class File extends Entry {
    private String content;

    @Override public boolean isDirectory() { return false; }
    @Synchronized public int getSize();           // content.length()
    @Synchronized public String getContent();
    @Synchronized public void appendContent(String c); // content += c; updates lastUpdated
    @Synchronized public void setContent(String c);
}

public class Directory extends Entry {
    private final List<Entry> children;

    @Override public boolean isDirectory() { return true; }
    @Synchronized public int getSize();             // sum of all children.getSize() recursively
    @Synchronized public List<Entry> getChildren(); // defensive copy
    @Synchronized public void addEntry(Entry e);
    @Synchronized public void removeEntry(Entry e);
}

public class FileSystem {
    private final Directory root; // root = new Directory("/", null)

    @Synchronized public static FileSystem getInstance();
    public Directory getRoot();

    @Synchronized private Entry resolvePath(String path, boolean createDirs);
    private Entry findChild(Directory parent, String name);

    @Synchronized public void mkdir(String path);
    @Synchronized public List<String> ls(String path);
    @Synchronized public void addContentToFile(String filePath, String content);
    @Synchronized public String readContentFromFile(String filePath);
    @Synchronized public int getSize(String path);
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup (Singleton Root)
```java
FileSystem fs = FileSystem.getInstance();
// root = Directory("/", null)
```

### Create Directory Structure
```java
fs.mkdir("/a/b/c");
// resolvePath splits: ["a", "b", "c"]
// Creates Directory "a" under root
// Creates Directory "b" under "a"
// Creates Directory "c" under "b"
```

### Write File (Auto-Creates Parent Dirs)
```java
fs.addContentToFile("/a/b/c/hello.txt", "Hello World");
// resolvePath("/a/b/c/hello.txt", createDirs=true)
// → traverses /a/b/c (already exists)
// → "hello.txt" not found → creates File("hello.txt", dirC)
// → file.appendContent("Hello World")
```

### Read, Append, List
```java
String content = fs.readContentFromFile("/a/b/c/hello.txt"); // "Hello World"

fs.addContentToFile("/a/b/c/hello.txt", " - Appended"); // "Hello World - Appended"

List<String> items = fs.ls("/a/b"); // ["c/"]  ← directories have trailing slash

int size = fs.getSize("/a"); // 24 (length of "Hello World - Appended")
// → Directory.getSize() recursively sums: /a → /b → /c → hello.txt → 24
```

### `getFullPath()` Recursion
```
hello.txt.getFullPath()
= parent.getFullPath() + "/" + "hello.txt"
= /a/b/c + "/" + "hello.txt"
= "/a/b/c/hello.txt"
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| File permissions | Add `Permission` field to `Entry`; check in `FileSystem` API |
| Symbolic links | Add `SymLink extends Entry` → resolves to target `Entry` |
| Binary file support | Change `File.content` from `String` to `byte[]` |
| Move / rename | Add `move(srcPath, destPath)` in `FileSystem` |
| File search | Add `search(String name)` → DFS on `Directory` tree |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `FileSystem.mkdir / ls` | `@Synchronized` | Atomic path resolution + child list modification |
| `FileSystem.addContentToFile` | `@Synchronized` | Prevent partial write / read race |
| `Directory.addEntry` | `@Synchronized` | Safe concurrent child insertion |
| `File.appendContent` | `@Synchronized` | Prevent dirty reads during concurrent file writes |
| `Directory.getSize()` | `@Synchronized` | Consistent recursive aggregate during concurrent child writes |
