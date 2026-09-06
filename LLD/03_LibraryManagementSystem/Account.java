package library;

public abstract class Account {
    public enum AccountStatus { ACTIVE, CLOSED, BLACKLISTED }

    private final String id;
    private final String password;
    private final String name;
    private AccountStatus status;

    protected Account(String id, String password, String name) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.status = AccountStatus.ACTIVE;
    }

    public String getId()       { return id; }
    public String getPassword() { return password; }
    public String getName()     { return name; }

    public synchronized AccountStatus getStatus()              { return status; }
    public synchronized void setStatus(AccountStatus status)   { this.status = status; }
}

class Member extends Account {
    private int borrowedBooksCount;

    public Member(String id, String password, String name) {
        super(id, password, name);
        this.borrowedBooksCount = 0;
    }

    public synchronized int getBorrowedBooksCount()      { return borrowedBooksCount; }
    public synchronized void incrementBorrowedBooksCount() { this.borrowedBooksCount++; }
    public synchronized void decrementBorrowedBooksCount() {
        if (borrowedBooksCount > 0) this.borrowedBooksCount--;
    }
}

class Librarian extends Account {
    public Librarian(String id, String password, String name) {
        super(id, password, name);
    }
}
