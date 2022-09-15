package com.tictactoebot.game;

import static com.tictactoebot.game.Player.Type.CROSS;
import static com.tictactoebot.game.Player.Type.NAUGHT;

public class TicTacToe {
    private static final int LAST_INDEX = 1 << 8;
    private static final int COLUMN_0 = 4 | (4 << 3) | (4 << 3 << 3);
    private static final int COLUMN_1 = 2 | (2 << 3) | (2 << 3 << 3);
    private static final int COLUMN_2 = 1 | (1 << 3) | (1 << 3 << 3);
    private static final int ROW_0 = 7;
    private static final int ROW_1 = 7 << 3;
    private static final int ROW_2 = 7 << 3 << 3;
    private static final int DIAGONAL_RIGHT = 84;
    private static final int DIAGONAL_LEFT = 273;

    private static final int DRAW = ROW_0 | ROW_1 | ROW_2;
    private int x_board;
    private int o_board;
    private final Player currentPlayer;
    private final int[] winCombinations;
    /*
        +-----------+
        | 8 | 7 | 6 |
        |-----------|
        | 5 | 4 | 3 |
        |-----------|
        | 2 | 1 | 0 |
        +-----------+
    */
    public TicTacToe() {
        this.x_board = 0;
        this.o_board = 0;
        this.currentPlayer = new Player(CROSS);
        this.winCombinations = new int[]{
                ROW_0, ROW_1, ROW_2,                    // rows
                COLUMN_2, COLUMN_1, COLUMN_0,           // columns
                DIAGONAL_RIGHT, DIAGONAL_LEFT           // diagonals
        };
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    /*public boolean place(int pos, Player.Type player) {
        if (player != currentPlayer.get()) return false;
        int cell = 1 << pos;
        if (cell > LAST_INDEX || cell < 0 || ((x_board | o_board) & cell) == cell) return false;
        switch (currentPlayer.get()) {
            case CROSS -> x_board |= cell;
            case NAUGHT -> o_board |= cell;
        }
        return true;
    }

    public boolean isWin() {
        return switch (currentPlayer.get()) {
            case NAUGHT -> {
                for (int winCombination : winCombinations) {
                    if ((o_board & winCombination) == winCombination) yield true;
                }
                currentPlayer.set(CROSS);
                yield false;
            }
            case CROSS -> {
                for (int winCombination : winCombinations) {
                    if ((x_board & winCombination) == winCombination) yield true;
                }
                currentPlayer.set(NAUGHT);
                yield false;
            }
            default -> false;
        };
    }*/
    public Move place(int pos, Player.Type player) {
        if (player != currentPlayer.get()) return Move.INVALID;
        int cell = 1 << pos;
        if (cell > LAST_INDEX || cell < 0 || ((x_board | o_board) & cell) == cell) return Move.INVALID;
        switch (currentPlayer.get()) {
            case CROSS -> {
                x_board |= cell;
                if (isWin(CROSS)) return Move.WIN;
            }
            case NAUGHT -> {
                o_board |= cell;
                if (isWin(NAUGHT)) return Move.WIN;
            }
        }
        if (isDraw()) return Move.DRAW;
        return Move.NONE;
    }

    private boolean isWin(Player.Type type) {
        return switch (type) {
            case NAUGHT -> {
                for (int winCombination : winCombinations) {
                    if ((o_board & winCombination) == winCombination) yield true;
                }
                currentPlayer.set(CROSS);
                yield false;
            }
            case CROSS -> {
                for (int winCombination : winCombinations) {
                    if ((x_board & winCombination) == winCombination) yield true;
                }
                currentPlayer.set(NAUGHT);
                yield false;
            }
            default -> false;
        };
    }

    private boolean isDraw() {
        return (x_board | o_board) == DRAW;
    }

    public enum Move {
        WIN, DRAW, NONE, INVALID
    }
}
