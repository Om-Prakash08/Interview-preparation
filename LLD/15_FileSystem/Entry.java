package fs;

public abstract class Entry {
    protected final String name;
    protected Directory parent;
    protected final long created;
    protected long lastUpdated;

    public Entry(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
        this.created = System.currentTimeMillis();
        this.lastUpdated = created;
    }

    public String getName()      { return name; }
    public Directory getParent() { return parent; }
    public long getCreated()     { return created; }
    public long getLastUpdated() { return lastUpdated; }
    public void setParent(Directory parent) { this.parent = parent; }

    public abstract boolean isDirectory();
    public abstract int getSize();

    public String getFullPath() {
        if (parent == null) return "/";
        if (parent.parent == null) return "/" + name;
        return parent.getFullPath() + "/" + name;
    }
}
