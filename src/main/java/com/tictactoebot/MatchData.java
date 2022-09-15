package com.tictactoebot;

import com.tictactoebot.game.Game;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.tictactoebot.game.Player.Type.CROSS;
import static com.tictactoebot.game.Player.Type.NAUGHT;

public class MatchData {
    private Game game = null;
    private final User challenger, challengeAccepter;
    public static final Random RANDOM = new Random();
    private ScheduledFuture<?> pendingEdit;

    public MatchData(User challenger, User challengeAccepter) {
        this.challenger = challenger;
        this.challengeAccepter = challengeAccepter;
    }

    public static MatchData create(User challenger, User challengeAccepter) {
        return new MatchData(challenger, challengeAccepter);
    }

    public Game acknowledge() {
        boolean cross = isCross();
        cancelEdit();
        return game = Game.start(
                Pair.of(challenger, cross? CROSS: NAUGHT),
                Pair.of(challengeAccepter, cross? NAUGHT: CROSS)
        );
    }

    private boolean isCross() {
        return RANDOM.nextInt(11) % 2 == 0;
    }

    public Game getGame() {
        return game;
    }

    public User getChallenger() {
        return challenger;
    }

    /**
     * generate a random number between 0 and 10 using Random class
     * if the number is divisible by 2, then the user will play as Player.Type.CROSS else Player.Type.NAUGHT
     */
    public User getChallengeAccepter() {
        return challengeAccepter;
    }

    public void setPendingEdit(Message message) {
        this.pendingEdit = message.editMessageFormat("%s didn't accept the challenge!", challengeAccepter.getAsMention()).setActionRows(
                List.of()
        ).queueAfter(30, TimeUnit.SECONDS);
    }

    private void cancelEdit() {
        pendingEdit.cancel(true);
    }
}
