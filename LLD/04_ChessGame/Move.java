package chess;

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

    public Player getPlayer()      { return player; }
    public Box getStart()          { return start; }
    public Box getEnd()            { return end; }
    public Piece getPieceMoved()   { return pieceMoved; }
    public Piece getPieceKilled()  { return pieceKilled; }
}
