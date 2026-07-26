package library;

import lombok.Getter;
import lombok.Synchronized;

public abstract class Account {
    public enum AccountStatus {
        ACTIVE,
        CLOSED,
        BLACKLISTED
    }

    @Getter
    private final String id;
    @Getter
    private final String password;
    @Getter
    private final String name;
    private AccountStatus status;

    protected Account(String id, String password, String name) {
        this.id = id;
        this.password = password;
        this.name = name;
        this.status = AccountStatus.ACTIVE;
    }

    @Synchronized
    public AccountStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}

class Member extends Account {
    private int borrowedBooksCount;

    public Member(String id, String password, String name) {
        super(id, password, name);
        this.borrowedBooksCount = 0;
    }

    @Synchronized
    public int getBorrowedBooksCount() {
        return borrowedBooksCount;
    }

    @Synchronized
    public void incrementBorrowedBooksCount() {
        this.borrowedBooksCount++;
    }

    @Synchronized
    public void decrementBorrowedBooksCount() {
        if (borrowedBooksCount > 0) {
            this.borrowedBooksCount--;
        }
    }
}

class Librarian extends Account {
    public Librarian(String id, String password, String name) {
        super(id, password, name);
    }
}
