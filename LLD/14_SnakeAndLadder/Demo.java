package game;

import java.util.Arrays;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Snake & Ladder Game ===");

        // 1. Initialize Board
        Board board = new Board(100);

        // Add Snakes
        board.addSnake(new Snake(17, 7));
        board.addSnake(new Snake(62, 19));
        board.addSnake(new Snake(99, 5));
        board.addSnake(new Snake(54, 34));

        // Add Ladders
        board.addLadder(new Ladder(3, 38));
        board.addLadder(new Ladder(24, 60));
        board.addLadder(new Ladder(42, 84));
        board.addLadder(new Ladder(72, 91));

        // 2. Setup Players and Dice
        Player p1 = new Player("p1", "Alice");
        Player p2 = new Player("p2", "Bob");
        Player p3 = new Player("p3", "Charlie");

        Dice dice = new Dice(1); // 1 standard dice

        Game game = new Game(board, dice, Arrays.asList(p1, p2, p3));

        System.out.println("Board configured. Added 4 snakes and 4 ladders. Alice, Bob, and Charlie starting outside (0).");

        // 3. Play Game Loop
        System.out.println("\n--- Starting Game Loop ---");
        int turnCount = 0;
        while (!game.isGameOver() && turnCount < 1000) {
            game.playTurn();
            turnCount++;
        }

        System.out.printf("\nGame finished after %d turns.%n", turnCount);
        if (!game.getLeaderboard().isEmpty()) {
            System.out.printf("Winner: %s%n", game.getLeaderboard().get(0).getName());
        }
        System.out.println("\n=== Snake & Ladder Demo Finished successfully ===");
    }
}
