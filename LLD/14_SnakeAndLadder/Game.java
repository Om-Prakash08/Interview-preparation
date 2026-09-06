package game;

import java.util.*;

public class Game {
    private final Board board;
    private final Dice dice;
    private final Queue<Player> players;
    private final List<Player> leaderboard;
    private boolean isGameOver;

    public List<Player> getLeaderboard() { return leaderboard; }

    public Game(Board board, Dice dice, List<Player> playerList) {
        this.board = board;
        this.dice = dice;
        this.players = new LinkedList<>(playerList);
        this.leaderboard = new ArrayList<>();
        this.isGameOver = false;
    }

    public synchronized void playTurn() {
        if (isGameOver) return;

        Player currentPlayer = players.poll();
        int diceValue = dice.roll();
        int currentPos = currentPlayer.getPosition();
        int finalPos = currentPos + diceValue;

        System.out.printf("[Roll] %s rolled a %d (Position: %d -> %d)%n",
                currentPlayer.getName(), diceValue, currentPos, finalPos);

        if (finalPos > board.getSize()) {
            System.out.printf("  [Out of bounds] Move exceeds cell %d. %s stays at %d.%n",
                    board.getSize(), currentPlayer.getName(), currentPos);
            players.offer(currentPlayer);
        } else {
            int actualPos = board.getNextPosition(finalPos);
            currentPlayer.setPosition(actualPos);
            if (actualPos == board.getSize()) {
                System.out.printf("[Game Over] %s reached cell %d and WON!%n", currentPlayer.getName(), board.getSize());
                leaderboard.add(currentPlayer);
                isGameOver = true;
            } else {
                players.offer(currentPlayer);
            }
        }
    }

    public synchronized boolean isGameOver() { return isGameOver; }
}
