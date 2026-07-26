package fs;

import lombok.Synchronized;

public class File extends Entry {
    private String content;

    public File(String name, Directory parent) {
        super(name, parent);
        this.content = "";
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    @Synchronized
    public int getSize() {
        return content.length();
    }

    @Synchronized
    public String getContent() {
        return content;
    }

    @Synchronized
    public void appendContent(String content) {
        this.content += content;
        this.lastUpdated = System.currentTimeMillis();
    }

    @Synchronized
    public void setContent(String content) {
        this.content = content;
        this.lastUpdated = System.currentTimeMillis();
    }
}
