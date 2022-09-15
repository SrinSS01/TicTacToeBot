package com.tictactoebot.game;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Game {
    private final TicTacToe ticTacToe;
    private final List<ActionRow> rows;
    private final List<Pair<Integer, Integer>> coordinates;
    private static final int BOARD_SIZE = 750;
    private static final int CELL_MIDDLE = (BOARD_SIZE/* - LINES_WIDTH*/) / 6;
    private final long challenger;
    private final long challengeAccepter;
    private final EmbedBuilder embedBuilder = new EmbedBuilder();
    private final Map<User, Player.Type> userPlayerMap;
    private final Map<Player.Type, User> playerUserMap;
    private final Player currentPlayer;
    private final Description description;
    private final BufferedImage boardImage = new BufferedImage(750, 750, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D graphics = boardImage.createGraphics();
    private static final BufferedImage back;
    private static final BufferedImage cross;
    private static final BufferedImage naught;


    static {
        try {
            cross = ImageIO.read(Objects.requireNonNull(Game.class.getResource("cross.png")));
            back = ImageIO.read(Objects.requireNonNull(Game.class.getResource("back.png")));
            naught = ImageIO.read(Objects.requireNonNull(Game.class.getResource("circle.png")));
        } catch (IOException | NullPointerException e) {
            throw new RuntimeException(e);
        }
    }

    private Game(Pair<User, Player.Type> challenger, Pair<User, Player.Type> challengeAccepter) {
        userPlayerMap = Map.of(challenger.getLeft(), challenger.getRight(), challengeAccepter.getLeft(), challengeAccepter.getRight());
        playerUserMap = Map.of(challenger.getRight(), challenger.getLeft(), challengeAccepter.getRight(), challengeAccepter.getLeft());
        this.challenger = challenger.getLeft().getIdLong();
        this.challengeAccepter = challengeAccepter.getLeft().getIdLong();
        ticTacToe = new TicTacToe();
        currentPlayer = ticTacToe.getCurrentPlayer();
        rows = new ArrayList<>(List.of(
                ActionRow.of(Button.primary("8", " "), Button.primary("7", " "), Button.primary("6", " ")),
                ActionRow.of(Button.primary("5", " "), Button.primary("4", " "), Button.primary("3", " ")),
                ActionRow.of(Button.primary("2", " "), Button.primary("1", " "), Button.primary("0", " ")),
                ActionRow.of(
                        Button.primary("null1", " ").asDisabled(),
                        Button.danger("resign", "stop"),
                        Button.primary("null2", " ").asDisabled()
                )
        ));
        coordinates = List.of(
                Pair.of(CELL_MIDDLE, CELL_MIDDLE), Pair.of((CELL_MIDDLE * 3), CELL_MIDDLE), Pair.of((CELL_MIDDLE * 5) , CELL_MIDDLE),
                Pair.of(CELL_MIDDLE, (CELL_MIDDLE * 3)), Pair.of((CELL_MIDDLE * 3), (CELL_MIDDLE * 3)), Pair.of((CELL_MIDDLE * 5), (CELL_MIDDLE * 3)),
                Pair.of(CELL_MIDDLE, (CELL_MIDDLE * 5)), Pair.of((CELL_MIDDLE * 3), (CELL_MIDDLE * 5)), Pair.of((CELL_MIDDLE * 5), (CELL_MIDDLE * 5))
        );
        Player.Type currentPlayerType = getCurrentPlayerType();
        description = Description.create(challenger.getLeft(), challengeAccepter.getLeft());
        embedBuilder.setDescription(description.setTurn(playerUserMap.get(currentPlayerType), currentPlayerType).toString());
        graphics.drawImage(back, 0, 0, 750, 750, null);
    }
    public static Game start(Pair<User, Player.Type> challenger, Pair<User, Player.Type> challengeAccepter) {
        return new Game(challenger, challengeAccepter);
    }
    public TicTacToe.Move select(String cell, Player.Type type) {
        int pos = Integer.parseInt(cell);
        TicTacToe.Move move = ticTacToe.place(pos, type);
        if (move == TicTacToe.Move.INVALID) return TicTacToe.Move.INVALID;
        draw(pos, type);
        int row = 2 - pos / 3;
        ActionRow buttons = rows.get(row);
        Button newButton = Button.primary(cell, type.toString());
        buttons.updateComponent(cell, newButton.asDisabled());
        rows.set(row, buttons);
        switch (move) {
            case WIN -> {
                embedBuilder.setDescription(description.setWinner(getCurrentUser()).toString());
                disableButtons();
            }
            case DRAW -> {
                embedBuilder.setDescription(description.setDraw().toString());
                disableButtons();
            }
            default -> {
                Player.Type currentPlayerType = getCurrentPlayerType();
                embedBuilder.setDescription(description.setTurn(playerUserMap.get(currentPlayerType), currentPlayerType).toString());
            }
        }
        return move;
    }

    private void draw(int pos, Player.Type type) {
        Pair<Integer, Integer> xy = coordinates.get(8 - pos);
        graphics.drawImage(type == Player.Type.CROSS? cross: naught, xy.getLeft() - 50, xy.getRight() - 50, 100, 100, null);
    }

    public boolean contains(long playerID) {
        return playerID == challenger || playerID == challengeAccepter;
    }

    public void disableButtons() {
        rows.replaceAll(row -> {
            row.forEach(button -> {
                String buttonId = button.getId();
                row.updateComponent(Objects.requireNonNull(buttonId), ((Button) button).asDisabled());
            });
            return row;
        });
    }

    // getters
    public Player.Type getCurrentPlayerType() {
        return currentPlayer.get();
    }

    public User getCurrentUser() {
        return playerUserMap.get(getCurrentPlayerType());
    }

    public Player.Type getPlayerType(User user) {
        return userPlayerMap.get(user);
    }

    public List<ActionRow> getRows() {
        return rows;
    }

    public MessageEmbed getEmbed() {
        return embedBuilder.build();
    }

    public File getBoardImage() throws IOException {
        String fileName = "board" + System.currentTimeMillis() + ".png";
        File file = new File(fileName);
        file.deleteOnExit();
        embedBuilder.setImage("attachment://" + fileName);
        ImageIO.write(boardImage, "png", file);
        return file;
    }

    public void resign(User user) {
        embedBuilder.setDescription(description.resign(user).toString());
    }

    static class Description {
        final String header;
        String gameState;

        Description(User challenger, User challengeAccepter) {
            header = "_press the button where you want to play next_\n\n%s vs %s\n\n".formatted(challenger.getAsMention(), challengeAccepter.getAsMention());
        }

        static Description create(User challenger, User challengeAccepter) {
            return new Description(challenger, challengeAccepter);
        }

        Description setTurn(User turn, Player.Type type) {
            gameState = "_%s's turn_\n**playing as** `%s`".formatted(turn.getAsMention(), type.toString());
            return this;
        }

        Description setWinner(User winner) {
            gameState = "%s is the winner".formatted(winner.getAsMention());
            return this;
        }

        Description setDraw() {
            gameState = "It was a Draw";
            return this;
        }

        Description resign(User user) {
            gameState = user.getAsMention() + " _resigned_";
            return this;
        }

        @Override
        public String toString() {
            return header + gameState;
        }
    }
}
