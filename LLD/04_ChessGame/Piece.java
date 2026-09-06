package chess;

public abstract class Piece {
    private final Color color;
    private boolean killed = false;

    public Piece(Color color) { this.color = color; }

    public Color getColor()   { return color; }
    public boolean isKilled() { return killed; }
    public void setKilled(boolean killed) { this.killed = killed; }

    public abstract boolean canMove(Board board, Box start, Box end);
    public abstract String getSymbol();
}

class King extends Piece {
    public King(Color color) { super(color); }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) return false;
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());
        return (x + y > 0) && (x <= 1) && (y <= 1);
    }

    @Override public String getSymbol() { return getColor() == Color.WHITE ? "WK" : "BK"; }
}

class Knight extends Piece {
    public Knight(Color color) { super(color); }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) return false;
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());
        return x * y == 2;
    }

    @Override public String getSymbol() { return getColor() == Color.WHITE ? "WN" : "BN"; }
}

class Rook extends Piece {
    public Rook(Color color) { super(color); }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) return false;
        int xStart = start.getX(), yStart = start.getY(), xEnd = end.getX(), yEnd = end.getY();
        if (xStart != xEnd && yStart != yEnd) return false;
        if (xStart == xEnd) {
            int min = Math.min(yStart, yEnd), max = Math.max(yStart, yEnd);
            for (int i = min + 1; i < max; i++)
                if (board.getBox(xStart, i).getPiece() != null) return false;
        } else {
            int min = Math.min(xStart, xEnd), max = Math.max(xStart, xEnd);
            for (int i = min + 1; i < max; i++)
                if (board.getBox(i, yStart).getPiece() != null) return false;
        }
        return true;
    }

    @Override public String getSymbol() { return getColor() == Color.WHITE ? "WR" : "BR"; }
}

class Pawn extends Piece {
    public Pawn(Color color) { super(color); }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) return false;
        int xDiff = end.getX() - start.getX();
        int yDiff = Math.abs(end.getY() - start.getY());
        int forward = (getColor() == Color.WHITE) ? 1 : -1;
        int startRow = (getColor() == Color.WHITE) ? 1 : 6;
        if (yDiff == 0) {
            if (xDiff == forward) return end.getPiece() == null;
            if (start.getX() == startRow && xDiff == 2 * forward) {
                Box mid = board.getBox(start.getX() + forward, start.getY());
                return mid.getPiece() == null && end.getPiece() == null;
            }
        } else if (yDiff == 1 && xDiff == forward) {
            return end.getPiece() != null && end.getPiece().getColor() != this.getColor();
        }
        return false;
    }

    @Override public String getSymbol() { return getColor() == Color.WHITE ? "WP" : "BP"; }
}

class Bishop extends Piece {
    public Bishop(Color color) { super(color); }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) return false;
        int xDiff = Math.abs(start.getX() - end.getX()), yDiff = Math.abs(start.getY() - end.getY());
        if (xDiff != yDiff) return false;
        int xDir = (end.getX() > start.getX()) ? 1 : -1;
        int yDir = (end.getY() > start.getY()) ? 1 : -1;
        int x = start.getX() + xDir, y = start.getY() + yDir;
        while (x != end.getX() && y != end.getY()) {
            if (board.getBox(x, y).getPiece() != null) return false;
            x += xDir; y += yDir;
        }
        return true;
    }

    @Override public String getSymbol() { return getColor() == Color.WHITE ? "WB" : "BB"; }
}

class Queen extends Piece {
    public Queen(Color color) { super(color); }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        return new Rook(getColor()).canMove(board, start, end) || new Bishop(getColor()).canMove(board, start, end);
    }

    @Override public String getSymbol() { return getColor() == Color.WHITE ? "WQ" : "BQ"; }
}
