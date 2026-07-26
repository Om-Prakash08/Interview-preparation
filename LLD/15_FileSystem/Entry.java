package fs;

import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class Entry {
    protected final String name;
    @Setter
    protected Directory parent;
    protected final long created;
    protected long lastUpdated;

    public Entry(String name, Directory parent) {
        this.name = name;
        this.parent = parent;
        this.created = System.currentTimeMillis();
        this.lastUpdated = created;
    }

    public abstract boolean isDirectory();
    public abstract int getSize();

    public String getFullPath() {
        if (parent == null) {
            return "/";
        }
        if (parent.parent == null) {
            return "/" + name;
        }
        return parent.getFullPath() + "/" + name;
    }
}
