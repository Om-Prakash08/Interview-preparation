package shop;

public class Product {
    private final String id;
    private final String name;
    private final double price;
    private int stock;

    public Product(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    public String getId()   { return id; }
    public String getName() { return name; }
    public double getPrice(){ return price; }

    public synchronized int getStock()                  { return stock; }
    public synchronized boolean decrementStock(int qty) { if (qty <= stock) { stock -= qty; return true; } return false; }
    public synchronized void incrementStock(int qty)    { stock += qty; }
}
