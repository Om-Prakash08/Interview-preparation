package atm;

public class CashDispenser {
    private int hundredDollarBills;
    private int fiftyDollarBills;
    private int twentyDollarBills;

    public CashDispenser(int hundreds, int fifties, int twenties) {
        this.hundredDollarBills = hundreds;
        this.fiftyDollarBills = fifties;
        this.twentyDollarBills = twenties;
    }

    public synchronized double getTotalAvailableCash() {
        return (hundredDollarBills * 100) + (fiftyDollarBills * 50) + (twentyDollarBills * 20);
    }

    public synchronized boolean canDispenseAmount(int amount) {
        if (amount % 10 != 0 || amount > getTotalAvailableCash()) {
            return false;
        }

        int tempHundreds = hundredDollarBills;
        int tempFifties = fiftyDollarBills;
        int tempTwenties = twentyDollarBills;

        int rem = amount;

        int reqHundreds = rem / 100;
        int useHundreds = Math.min(reqHundreds, tempHundreds);
        rem -= useHundreds * 100;

        int reqFifties = rem / 50;
        int useFifties = Math.min(reqFifties, tempFifties);
        rem -= useFifties * 50;

        int reqTwenties = rem / 20;
        int useTwenties = Math.min(reqTwenties, tempTwenties);
        rem -= useTwenties * 20;

        return rem == 0;
    }

    public synchronized boolean dispense(int amount) {
        if (!canDispenseAmount(amount)) {
            return false;
        }

        int rem = amount;

        int reqHundreds = rem / 100;
        int useHundreds = Math.min(reqHundreds, hundredDollarBills);
        hundredDollarBills -= useHundreds;
        rem -= useHundreds * 100;

        int reqFifties = rem / 50;
        int useFifties = Math.min(reqFifties, fiftyDollarBills);
        fiftyDollarBills -= useFifties;
        rem -= useFifties * 50;

        int reqTwenties = rem / 20;
        int useTwenties = Math.min(reqTwenties, twentyDollarBills);
        twentyDollarBills -= useTwenties;
        rem -= useTwenties * 20;

        System.out.printf("[CashDispenser] Dispensed: %d x $100, %d x $50, %d x $20. Remaining ATM cash: $%.2f%n",
                useHundreds, useFifties, useTwenties, getTotalAvailableCash());
        return true;
    }
}
