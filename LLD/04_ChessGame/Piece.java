package chess;

import lombok.Getter;
import lombok.Setter;

@Getter
public abstract class Piece {
    private final Color color;
    @Setter
    private boolean killed = false;

    public Piece(Color color) {
        this.color = color;
    }

    public abstract boolean canMove(Board board, Box start, Box end);
    public abstract String getSymbol();
}


class King extends Piece {
    public King(Color color) {
        super(color);
    }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) {
            return false; // Can't capture own piece
        }
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());
        return (x + y > 0) && (x <= 1) && (y <= 1);
    }

    @Override
    public String getSymbol() {
        return getColor() == Color.WHITE ? "WK" : "BK";
    }
}

class Knight extends Piece {
    public Knight(Color color) {
        super(color);
    }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) {
            return false;
        }
        int x = Math.abs(start.getX() - end.getX());
        int y = Math.abs(start.getY() - end.getY());
        return x * y == 2;
    }

    @Override
    public String getSymbol() {
        return getColor() == Color.WHITE ? "WN" : "BN";
    }
}

class Rook extends Piece {
    public Rook(Color color) {
        super(color);
    }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) {
            return false;
        }
        int xStart = start.getX();
        int yStart = start.getY();
        int xEnd = end.getX();
        int yEnd = end.getY();

        if (xStart != xEnd && yStart != yEnd) {
            return false; // Rooks move only in straight lines
        }

        // Check if there are any pieces blocking the path
        if (xStart == xEnd) {
            int min = Math.min(yStart, yEnd);
            int max = Math.max(yStart, yEnd);
            for (int i = min + 1; i < max; i++) {
                if (board.getBox(xStart, i).getPiece() != null) {
                    return false; // Blocked
                }
            }
        } else {
            int min = Math.min(xStart, xEnd);
            int max = Math.max(xStart, xEnd);
            for (int i = min + 1; i < max; i++) {
                if (board.getBox(i, yStart).getPiece() != null) {
                    return false; // Blocked
                }
            }
        }
        return true;
    }

    @Override
    public String getSymbol() {
        return getColor() == Color.WHITE ? "WR" : "BR";
    }
}

class Pawn extends Piece {
    public Pawn(Color color) {
        super(color);
    }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) {
            return false;
        }
        int xDiff = end.getX() - start.getX();
        int yDiff = Math.abs(end.getY() - start.getY());

        int forwardDirection = (getColor() == Color.WHITE) ? 1 : -1;
        int startRow = (getColor() == Color.WHITE) ? 1 : 6;

        // Straight movement
        if (yDiff == 0) {
            if (xDiff == forwardDirection) {
                return end.getPiece() == null; // Must be empty
            }
            if (start.getX() == startRow && xDiff == 2 * forwardDirection) {
                // Check intermediate cell and end cell
                Box midBox = board.getBox(start.getX() + forwardDirection, start.getY());
                return midBox.getPiece() == null && end.getPiece() == null;
            }
        } else if (yDiff == 1 && xDiff == forwardDirection) {
            // Diagonal capture
            return end.getPiece() != null && end.getPiece().getColor() != this.getColor();
        }
        return false;
    }

    @Override
    public String getSymbol() {
        return getColor() == Color.WHITE ? "WP" : "BP";
    }
}

class Bishop extends Piece {
    public Bishop(Color color) {
        super(color);
    }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        if (end.getPiece() != null && end.getPiece().getColor() == this.getColor()) {
            return false;
        }
        int xDiff = Math.abs(start.getX() - end.getX());
        int yDiff = Math.abs(start.getY() - end.getY());

        if (xDiff != yDiff) {
            return false; // Must move diagonally
        }

        // Check path blockages
        int xDirection = (end.getX() > start.getX()) ? 1 : -1;
        int yDirection = (end.getY() > start.getY()) ? 1 : -1;

        int x = start.getX() + xDirection;
        int y = start.getY() + yDirection;
        while (x != end.getX() && y != end.getY()) {
            if (board.getBox(x, y).getPiece() != null) {
                return false; // Blocked
            }
            x += xDirection;
            y += yDirection;
        }
        return true;
    }

    @Override
    public String getSymbol() {
        return getColor() == Color.WHITE ? "WB" : "BB";
    }
}

class Queen extends Piece {
    public Queen(Color color) {
        super(color);
    }

    @Override
    public boolean canMove(Board board, Box start, Box end) {
        // Queen can move like a Rook or a Bishop
        Rook rookTemp = new Rook(getColor());
        Bishop bishopTemp = new Bishop(getColor());
        return rookTemp.canMove(board, start, end) || bishopTemp.canMove(board, start, end);
    }

    @Override
    public String getSymbol() {
        return getColor() == Color.WHITE ? "WQ" : "BQ";
    }
}
