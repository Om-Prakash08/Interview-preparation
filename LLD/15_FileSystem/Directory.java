package fs;

import lombok.Synchronized;
import java.util.ArrayList;
import java.util.List;

public class Directory extends Entry {
    private final List<Entry> children;

    public Directory(String name, Directory parent) {
        super(name, parent);
        this.children = new ArrayList<>();
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    @Synchronized
    public int getSize() {
        int total = 0;
        for (Entry e : children) {
            total += e.getSize();
        }
        return total;
    }

    @Synchronized
    public List<Entry> getChildren() {
        return new ArrayList<>(children);
    }

    @Synchronized
    public void addEntry(Entry entry) {
        children.add(entry);
        entry.setParent(this);
        this.lastUpdated = System.currentTimeMillis();
    }

    @Synchronized
    public void removeEntry(Entry entry) {
        children.remove(entry);
        this.lastUpdated = System.currentTimeMillis();
    }
}
