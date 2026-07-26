package fs;

import lombok.Synchronized;
import java.util.*;

public class FileSystem {
    private static FileSystem instance;
    private final Directory root;

    private FileSystem() {
        this.root = new Directory("root", null);
    }

    @Synchronized
    public static FileSystem getInstance() {
        if (instance == null) {
            instance = new FileSystem();
        }
        return instance;
    }

    public Directory getRoot() {
        return root;
    }

    @Synchronized
    private Entry resolvePath(String path, boolean createDirs) {
        if (path == null || path.isEmpty()) return root;
        if (path.equals("/")) return root;

        String[] parts = path.split("/");
        Directory current = root;

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            Entry next = findChild(current, part);
            if (next == null) {
                if (createDirs || i < parts.length - 1) { 
                    Directory newDir = new Directory(part, current);
                    current.addEntry(newDir);
                    current = newDir;
                } else {
                    return null; 
                }
            } else if (next.isDirectory()) {
                current = (Directory) next;
            } else {
                if (i == parts.length - 1) {
                    return next; 
                } else {
                    throw new IllegalArgumentException("Invalid path: File found along traversal path.");
                }
            }
        }
        return current;
    }

    private Entry findChild(Directory parent, String name) {
        for (Entry child : parent.getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    @Synchronized
    public void mkdir(String path) {
        resolvePath(path, true);
        System.out.printf("[FS] Created directory: %s%n", path);
    }

    @Synchronized
    public List<String> ls(String path) {
        Entry entry = resolvePath(path, false);
        List<String> list = new ArrayList<>();
        if (entry == null) {
            System.out.printf("[FS] Path %s not found.%n", path);
            return list;
        }

        if (entry.isDirectory()) {
            Directory dir = (Directory) entry;
            for (Entry child : dir.getChildren()) {
                list.add(child.getName() + (child.isDirectory() ? "/" : ""));
            }
        } else {
            list.add(entry.getName());
        }
        Collections.sort(list);
        return list;
    }

    @Synchronized
    public void addContentToFile(String filePath, String content) {
        if (!filePath.startsWith("/")) {
            throw new IllegalArgumentException("File path must start with /");
        }

        int lastSlashIndex = filePath.lastIndexOf("/");
        String dirPath = filePath.substring(0, lastSlashIndex);
        String fileName = filePath.substring(lastSlashIndex + 1);

        if (dirPath.isEmpty()) dirPath = "/";

        Entry dirEntry = resolvePath(dirPath, true);
        if (!(dirEntry instanceof Directory)) {
            throw new IllegalArgumentException("Parent path is not a directory");
        }
        Directory parentDir = (Directory) dirEntry;

        Entry fileEntry = findChild(parentDir, fileName);
        File file;
        if (fileEntry == null) {
            file = new File(fileName, parentDir);
            parentDir.addEntry(file);
        } else if (fileEntry instanceof File) {
            file = (File) fileEntry;
        } else {
            throw new IllegalArgumentException("Directory exists with same name: " + fileName);
        }

        file.appendContent(content);
        System.out.printf("[FS] Appended content to file %s (New Size: %d bytes)%n", filePath, file.getSize());
    }

    @Synchronized
    public String readContentFromFile(String filePath) {
        Entry entry = resolvePath(filePath, false);
        if (entry instanceof File) {
            return ((File) entry).getContent();
        }
        System.out.printf("[FS] File %s not found or is a directory.%n", filePath);
        return null;
    }

    @Synchronized
    public int getSize(String path) {
        Entry entry = resolvePath(path, false);
        return (entry != null) ? entry.getSize() : 0;
    }
}
