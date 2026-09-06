package fs;

public class File extends Entry {
    private String content;

    public File(String name, Directory parent) {
        super(name, parent);
        this.content = "";
    }

    @Override public boolean isDirectory() { return false; }

    @Override
    public synchronized int getSize() { return content.length(); }

    public synchronized String getContent()            { return content; }
    public synchronized void appendContent(String c)   { this.content += c; this.lastUpdated = System.currentTimeMillis(); }
    public synchronized void setContent(String c)      { this.content = c;  this.lastUpdated = System.currentTimeMillis(); }
}
