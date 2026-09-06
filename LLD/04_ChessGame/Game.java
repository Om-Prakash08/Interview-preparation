package chess;

import java.util.ArrayList;
import java.util.List;

public class Game {
    public enum GameStatus { ACTIVE, WHITE_WIN, BLACK_WIN, FORFEIT, STALEMATE, RESIGNED }

    private final Board board;
    private final Player[] players;
    private Player currentTurn;
    private GameStatus status;
    private final List<Move> movesPlayed;

    public Board getBoard()           { return board; }
    public Player[] getPlayers()      { return players; }
    public Player getCurrentTurn()    { return currentTurn; }
    public GameStatus getStatus()     { return status; }
    public List<Move> getMovesPlayed(){ return movesPlayed; }

    public Game(Player p1, Player p2) {
        this.board = new Board();
        this.players = new Player[]{p1, p2};
        this.currentTurn = p1.getColor() == Color.WHITE ? p1 : p2;
        this.status = GameStatus.ACTIVE;
        this.movesPlayed = new ArrayList<>();
    }

    public synchronized boolean playerMove(Player player, int startX, int startY, int endX, int endY) {
        if (this.status != GameStatus.ACTIVE) {
            System.out.println("Game is not active!");
            return false;
        }
        if (player != currentTurn) {
            System.out.printf("It is not %s's turn! Current turn: %s%n", player.getName(), currentTurn.getName());
            return false;
        }

        Box startBox = board.getBox(startX, startY);
        Box endBox = board.getBox(endX, endY);
        Piece sourcePiece = startBox.getPiece();

        if (sourcePiece == null) { System.out.println("No piece at start position!"); return false; }
        if (sourcePiece.getColor() != player.getColor()) { System.out.println("Cannot move opponent's piece!"); return false; }
        if (!sourcePiece.canMove(board, startBox, endBox)) {
            System.out.printf("Invalid move for %s from (%d,%d) to (%d,%d)%n",
                    sourcePiece.getClass().getSimpleName(), startX, startY, endX, endY);
            return false;
        }

        Move move = new Move(player, startBox, endBox);
        movesPlayed.add(move);

        Piece destinationPiece = endBox.getPiece();
        if (destinationPiece != null) {
            destinationPiece.setKilled(true);
            System.out.printf("[Capture] %s's %s captured %s's %s at (%d,%d)%n",
                    player.getName(), sourcePiece.getClass().getSimpleName(),
                    destinationPiece.getColor() == Color.WHITE ? "White" : "Black",
                    destinationPiece.getClass().getSimpleName(), endX, endY);
            if (destinationPiece instanceof King) {
                this.status = (player.getColor() == Color.WHITE) ? GameStatus.WHITE_WIN : GameStatus.BLACK_WIN;
                System.out.printf("[Checkmate] %s wins the game by capturing the King!%n", player.getName());
            }
        } else {
            System.out.printf("[Move] %s moved %s from (%d,%d) to (%d,%d)%n",
                    player.getName(), sourcePiece.getClass().getSimpleName(), startX, startY, endX, endY);
        }

        endBox.setPiece(sourcePiece);
        startBox.setPiece(null);

        if (this.status == GameStatus.ACTIVE) {
            this.currentTurn = (this.players[0] == currentTurn) ? this.players[1] : this.players[0];
        }
        return true;
    }

    public void printBoardState() {
        System.out.println("\n  -------------------------");
        for (int i = 7; i >= 0; i--) {
            System.out.print(i + " |");
            for (int j = 0; j < 8; j++) {
                Piece piece = board.getBox(i, j).getPiece();
                System.out.print(piece == null ? " . " : " " + piece.getSymbol());
                System.out.print("|");
            }
            System.out.println();
            System.out.println("  -------------------------");
        }
        System.out.println("    0   1   2   3   4   5   6   7\n");
    }
}
