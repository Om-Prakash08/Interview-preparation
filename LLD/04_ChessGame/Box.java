package chess;

import lombok.Getter;
import lombok.Setter;

@Getter
public class Box {
    private final int x;
    private final int y;
    @Setter
    private Piece piece;

    public Box(int x, int y) {
        this.x = x;
        this.y = y;
        this.piece = null;
    }
}
