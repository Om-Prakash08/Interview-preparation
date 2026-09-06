package chess;

public class Box {
    private final int x;
    private final int y;
    private Piece piece;

    public Box(int x, int y) {
        this.x = x;
        this.y = y;
        this.piece = null;
    }

    public int getX()       { return x; }
    public int getY()       { return y; }
    public Piece getPiece() { return piece; }
    public void setPiece(Piece piece) { this.piece = piece; }
}
