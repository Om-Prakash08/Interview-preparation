package chess;

import lombok.Getter;

@Getter
public class Move {
    private final Player player;
    private final Box start;
    private final Box end;
    private final Piece pieceMoved;
    private final Piece pieceKilled;

    public Move(Player player, Box start, Box end) {
        this.player = player;
        this.start = start;
        this.end = end;
        this.pieceMoved = start.getPiece();
        this.pieceKilled = end.getPiece();
    }
}
