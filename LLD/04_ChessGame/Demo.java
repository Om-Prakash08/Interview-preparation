package chess;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Chess Game ===");

        Player p1 = new Player("Alice", Color.WHITE);
        Player p2 = new Player("Bob", Color.BLACK);

        Game game = new Game(p1, p2);
        System.out.println("Chess Game started. Alice is White, Bob is Black.");
        game.printBoardState();

        // 1. Valid Pawn move (White)
        // White Pawn from (1, 3) to (3, 3)
        System.out.println("--- Move 1: Alice moves Pawn to d4 ---");
        game.playerMove(p1, 1, 3, 3, 3);
        game.printBoardState();

        // 2. Valid Knight move (Black)
        // Black Knight from (7, 1) to (5, 2)
        System.out.println("--- Move 2: Bob moves Knight to c6 ---");
        game.playerMove(p2, 7, 1, 5, 2);
        game.printBoardState();

        // 3. Attempt Invalid Move (White)
        // White Rook from (0, 0) to (2, 0) - Invalid because it is blocked by Pawn at (1,0)
        System.out.println("--- Move 3: Alice attempts invalid Rook move (blocked by Pawn) ---");
        boolean success = game.playerMove(p1, 0, 0, 2, 0);
        if (!success) {
            System.out.println("Validation successfully blocked the invalid Rook move!");
        }

        // 4. Valid Knight move (White)
        // White Knight from (0, 1) to (2, 2)
        System.out.println("\n--- Move 4: Alice moves Knight to c3 ---");
        game.playerMove(p1, 0, 1, 2, 2);
        game.printBoardState();

        // 5. Black Pawn move
        System.out.println("--- Move 5: Bob moves Pawn to e5 ---");
        game.playerMove(p2, 6, 4, 4, 4);
        game.printBoardState();

        // 6. Capture (White Pawn at (3, 3) captures Black Pawn at (4, 4))
        System.out.println("--- Move 6: Alice's Pawn captures Bob's Pawn at (4,4) ---");
        game.playerMove(p1, 3, 3, 4, 4);
        game.printBoardState();

        System.out.println("=== Demo Finished successfully ===");
    }
}
