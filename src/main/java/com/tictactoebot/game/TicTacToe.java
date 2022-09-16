package com.tictactoebot.game;

import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.util.Map;

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
    private final Map<Integer, WinCells> winCombinations;
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
        this.winCombinations = Map.of(
                ROW_0, WinCells.ROW_0,
                ROW_1, WinCells.ROW_1,
                ROW_2, WinCells.ROW_2,
                COLUMN_2, WinCells.COLUMN_2,
                COLUMN_1, WinCells.COLUMN_1,
                COLUMN_0, WinCells.COLUMN_0,
                DIAGONAL_RIGHT, WinCells.DIAGONAL_RIGHT,
                DIAGONAL_LEFT, WinCells.DIAGONAL_LEFT
        );
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public Pair<Move, WinCells> place(int pos, Player.Type player) {
        if (player != currentPlayer.get()) return Pair.of(Move.INVALID, null);
        int cell = 1 << pos;
        if (cell > LAST_INDEX || cell < 0 || ((x_board | o_board) & cell) == cell) return Pair.of(Move.INVALID, null);
        switch (currentPlayer.get()) {
            case CROSS -> {
                x_board |= cell;
                Pair<Boolean, WinCells> win = isWin(CROSS);
                if (win.getLeft()) return Pair.of(Move.WIN, win.getRight());
            }
            case NAUGHT -> {
                o_board |= cell;
                Pair<Boolean, WinCells> win = isWin(NAUGHT);
                if (win.getLeft()) return Pair.of(Move.WIN, win.getRight());
            }
        }
        if (isDraw()) return Pair.of(Move.DRAW, null);
        return Pair.of(Move.NONE, null);
    }

    private Pair<Boolean, WinCells> isWin(Player.Type type) {
        return switch (type) {
            case NAUGHT -> {
                for (var set : winCombinations.entrySet()) {
                    int winCombination = set.getKey();
                    if ((o_board & winCombination) == winCombination) yield Pair.of(true, set.getValue());
                }
                currentPlayer.set(CROSS);
                yield Pair.of(false, null);
            }
            case CROSS -> {
                for (var set : winCombinations.entrySet()) {
                    int winCombination = set.getKey();
                    if ((x_board & winCombination) == winCombination) yield Pair.of(true, set.getValue());
                }
                currentPlayer.set(NAUGHT);
                yield Pair.of(false, null);
            }
            default -> Pair.of(false, null);
        };
    }

    private boolean isDraw() {
        return (x_board | o_board) == DRAW;
    }

    public enum Move {
        WIN, DRAW, NONE, INVALID
    }

    /*
        +-----------+
        | 8 | 7 | 6 |
        |-----------|
        | 5 | 4 | 3 |
        |-----------|
        | 2 | 1 | 0 |
        +-----------+
    */
    public enum WinCells {
        ROW_0(0, 1, 2),
        ROW_1(3, 4, 5),
        ROW_2(6, 7, 8),
        COLUMN_2(6, 3, 0),
        COLUMN_1(7, 4, 1),
        COLUMN_0(8, 5, 2),
        DIAGONAL_RIGHT(6, 4, 2),
        DIAGONAL_LEFT(8, 4, 0);

        private final int[] cells;

        WinCells(int... cells) {
            this.cells = cells;
        }

        public int[] getCells() {
            return cells;
        }
    }
}
