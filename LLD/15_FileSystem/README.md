# 15. File System (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of an In-Memory File System.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Composite Pattern (Abstract Entry Base)
```java
@Getter
public abstract class Entry {
    protected final String name;
    @Setter
    protected Directory parent;
    protected final long created;
    protected long lastUpdated;

    public Entry(String name, Directory parent);

    public abstract boolean isDirectory();
    public abstract int getSize();

    public String getFullPath(); // Recursively builds absolute path from root
}
```

### Leaf Node: File
```java
public class File extends Entry {
    private String content;

    public File(String name, Directory parent);

    @Override public boolean isDirectory(); // Returns false

    @Synchronized public int getSize();    // Returns content.length()
    @Synchronized public String getContent();
    @Synchronized public void appendContent(String content); // Updates lastUpdated
    @Synchronized public void setContent(String content);   // Overwrites content
}
```

### Composite Node: Directory
```java
public class Directory extends Entry {
    private final List<Entry> children;

    public Directory(String name, Directory parent);

    @Override public boolean isDirectory(); // Returns true

    @Synchronized public int getSize();              // Recursive sum of all children sizes
    @Synchronized public List<Entry> getChildren();  // Returns a defensive copy
    @Synchronized public void addEntry(Entry entry); // Adds file or subdirectory
    @Synchronized public void removeEntry(Entry entry);
}
```

### FileSystem (Facade / Singleton)
```java
public class FileSystem {
    private final Directory root;

    @Synchronized public static FileSystem getInstance();
    public Directory getRoot();

    // Path Resolution
    @Synchronized private Entry resolvePath(String path, boolean createDirs);
    private Entry findChild(Directory parent, String name);

    // Public API
    @Synchronized public void mkdir(String path);         // Creates directories recursively
    @Synchronized public List<String> ls(String path);   // Returns sorted list of children
    @Synchronized public void addContentToFile(String filePath, String content);
    @Synchronized public String readContentFromFile(String filePath);
    @Synchronized public int getSize(String path);        // Recursive directory/file size
}
```

---

## 2. Core Workflow & Usage

Here is how the directory tree is built and files are read and written:

```java
FileSystem fs = FileSystem.getInstance();

// 1. Create directory structure (recursive mkdirs)
fs.mkdir("/a/b/c");

// 2. Write file (auto-creates parent dirs if needed)
fs.addContentToFile("/a/b/c/hello.txt", "Hello World");

// 3. Read content
String content = fs.readContentFromFile("/a/b/c/hello.txt"); // "Hello World"

// 4. Append more content
fs.addContentToFile("/a/b/c/hello.txt", " - Appended Text");

// 5. List directory contents
List<String> items = fs.ls("/a/b"); // ["c/"]

// 6. Get recursive size
int size = fs.getSize("/a"); // Returns 29 (size of all nested files)
```

---

## 3. Concurrency & Thread-Safety Details
- **Recursive Locking**: The `FileSystem` API methods (`mkdir`, `ls`, `addContentToFile`, `readContentFromFile`, `getSize`) are all synchronized (`@Synchronized`) on the singleton instance.
- **Directory Mutation Safety**: `Directory.addEntry` and `Directory.removeEntry` are synchronized, protecting the `children` list from concurrent list modification exceptions during parallel file creation.
- **File Content Safety**: All read/write operations on a `File` object go through synchronized methods, preventing dirty reads when one thread writes content while another reads it.
- **Composite Sizing Thread-Safety**: The recursive `getSize()` on `Directory` is synchronized, ensuring a consistent aggregate across nested nodes when child file writes happen concurrently.
