package chess;

public class Board {
    private final Box[][] boxes;

    public Board() {
        this.boxes = new Box[8][8];
        this.resetBoard();
    }

    public Box getBox(int x, int y) {
        if (x < 0 || x > 7 || y < 0 || y > 7) {
            throw new IllegalArgumentException("Index out of bounds");
        }
        return boxes[x][y];
    }

    public final void resetBoard() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                boxes[i][j] = new Box(i, j);
            }
        }

        // Place White pieces on row 0 and 1
        boxes[0][0].setPiece(new Rook(Color.WHITE));
        boxes[0][1].setPiece(new Knight(Color.WHITE));
        boxes[0][2].setPiece(new Bishop(Color.WHITE));
        boxes[0][3].setPiece(new Queen(Color.WHITE));
        boxes[0][4].setPiece(new King(Color.WHITE));
        boxes[0][5].setPiece(new Bishop(Color.WHITE));
        boxes[0][6].setPiece(new Knight(Color.WHITE));
        boxes[0][7].setPiece(new Rook(Color.WHITE));

        for (int i = 0; i < 8; i++) {
            boxes[1][i].setPiece(new Pawn(Color.WHITE));
        }

        // Place Black pieces on row 7 and 6
        boxes[7][0].setPiece(new Rook(Color.BLACK));
        boxes[7][1].setPiece(new Knight(Color.BLACK));
        boxes[7][2].setPiece(new Bishop(Color.BLACK));
        boxes[7][3].setPiece(new Queen(Color.BLACK));
        boxes[7][4].setPiece(new King(Color.BLACK));
        boxes[7][5].setPiece(new Bishop(Color.BLACK));
        boxes[7][6].setPiece(new Knight(Color.BLACK));
        boxes[7][7].setPiece(new Rook(Color.BLACK));

        for (int i = 0; i < 8; i++) {
            boxes[6][i].setPiece(new Pawn(Color.BLACK));
        }
    }
}
