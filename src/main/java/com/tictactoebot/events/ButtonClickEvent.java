package com.tictactoebot.events;

import com.tictactoebot.Database;
import com.tictactoebot.MatchData;
import com.tictactoebot.game.Game;
import com.tictactoebot.game.Player;
import com.tictactoebot.game.TicTacToe;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class ButtonClickEvent extends Event {
    public ButtonClickEvent(Database db) {
        super(db);
    }

    @Override
    public void onButtonClick(@NotNull net.dv8tion.jda.api.events.interaction.ButtonClickEvent event) {
        String buttonId = event.getComponentId();
        final User user = event.getUser();
        final long messageID = event.getMessage().getIdLong();

        final boolean isPlayer = Database.MATCH_DATA_MAP.containsKey(messageID);

        if (!isPlayer) {
            event.getInteraction().deferEdit().queue();
            return;
        }
        final MatchData matchData = Database.MATCH_DATA_MAP.get(messageID);
        final User challenger = matchData.getChallenger();
        final User challengeAccepter = matchData.getChallengeAccepter();
        switch (buttonId) {
            case "ready" -> { try {
                if (!user.equals(challengeAccepter)) {
                    event.deferEdit().queue();
                    return;
                }

                Game game = matchData.acknowledge();
                File boardImage = game.getBoardImage();

                event.editMessage("game started")
                        .setEmbeds(game.getEmbed())
                        .addFile(boardImage)
                        .setActionRows(game.getRows()).queue();
            } catch (Exception e) { LOGGER.error(e.getMessage(), e); } }
            case "not-ready" -> {
                if (!user.equals(challengeAccepter)) {
                    event.deferEdit().queue();
                    return;
                }
                event.editMessageFormat("%s declined the challenge!", user.getAsMention()).setActionRows(List.of()).queue();
                Database.MATCH_DATA_MAP.remove(messageID);
            }
            case "8", "7", "6", "5", "4", "3", "2", "1", "0" -> { try {
                long userID = user.getIdLong();
                Game game = matchData.getGame();

                if (!game.contains(userID)) {
                    event.deferEdit().queue();
                    return;
                }
                Player.Type playerType = game.getPlayerType(user);
                TicTacToe.Move result = game.select(buttonId, playerType);
                final String guildId = Objects.requireNonNull(event.getGuild()).getId();
                List<ActionRow> board = game.getRows();
                File boardImage = game.getBoardImage();
                MessageEmbed embed = game.getEmbed();
                switch (result) {
                    case WIN -> event.editMessage("game ended").queue(hook -> {
                        User winner = game.getCurrentUser();
                        User loser = winner.getIdLong() == challenger.getIdLong() ? challengeAccepter : challenger;
                        hook.editOriginalComponents(List.of())
                                .setEmbeds(embed)
                                .retainFilesById(List.of())
                                .addFile(boardImage).queue();

                        Database.UserInfo winnerInfo = db.getUserInfo(winner.getId(), guildId);
                        Database.UserInfo loserInfo = db.getUserInfo(loser.getId(), guildId);
                        db.resetXPIfCrossesLimit(winnerInfo);
                        db.resetXPIfCrossesLimit(loserInfo);
                        db.update(winnerInfo, loserInfo);
                        /*Database.LEVELS.updateOne(
                                Filters.and(
                                        Filters.eq("userId", winner.getId()),
                                        Filters.eq("guildId", guildId)
                                ),
                                Filters.eq("$inc", Filters.and(Filters.eq("wins", 1), Filters.eq("game-xp", 10)))
                        );
                        Database.LEVELS.updateOne(
                                Filters.and(
                                        Filters.eq("userId", loser.getId()),
                                        Filters.eq("guildId", guildId)
                                ),
                                Filters.eq("$inc", Filters.and(Filters.eq("loses", 1), Filters.eq("game-xp", 1)))
                        );*/

                        Database.MATCH_DATA_MAP.remove(messageID);
                    });
                    case DRAW -> event.editMessage("game ended").queue(hook -> {
                        hook.editOriginalComponents(List.of())
                                .setEmbeds(embed)
                                .retainFilesById(List.of())
                                .addFile(boardImage)
                                .queue();
                        Database.UserInfo challengerInfo = db.getUserInfo(challenger.getId(), guildId);
                        Database.UserInfo challengeAccepterInfo = db.getUserInfo(challengeAccepter.getId(), guildId);
                        db.resetXPIfCrossesLimit(challengerInfo);
                        db.resetXPIfCrossesLimit(challengeAccepterInfo);
                        db.updateDraw(challengerInfo, challengeAccepterInfo);
                        /*resetXPIfCrossesLimit(challenger.getId(), guildId);
                        Database.LEVELS.updateOne(
                                Filters.and(
                                        Filters.eq("userId", challenger.getId()),
                                        Filters.eq("guildId", guildId)
                                ),
                                Filters.eq("$inc", Filters.and(Filters.eq("draws", 1), Filters.eq("game-xp", 5)))
                        );

                        db.resetXPIfCrossesLimit(challengeAccepter.getId(), guildId);
                        Database.LEVELS.updateOne(
                                Filters.and(
                                        Filters.eq("userId", challengeAccepter.getId()),
                                        Filters.eq("guildId", guildId)
                                ),
                                Filters.eq("$inc", Filters.and(Filters.eq("draws", 1), Filters.eq("game-xp", 5)))
                        );*/
                        Database.MATCH_DATA_MAP.remove(messageID);
                    });
                    case NONE ->
                            event.deferEdit()
                                    .queue(hook ->
                                            hook.editOriginalComponents(board)
                                                    .setEmbeds(embed)
                                                    .retainFilesById(List.of())
                                                    .addFile(boardImage).queue());
                    case INVALID -> event.deferEdit().queue();
                }
            } catch (IOException e) { LOGGER.error(e.getMessage(), e); } }
            case "resign" -> {
                Game game = matchData.getGame();
                if (!game.contains(user.getIdLong())) {
                    event.deferEdit().queue();
                    return;
                }
                game.resign(user);
                MessageEmbed embed = game.getEmbed();
                game.disableButtons();
                event.deferEdit().queue(hook -> hook.editOriginal("game ended").setActionRows(List.of()).setEmbeds(embed).queue());
                Database.MATCH_DATA_MAP.remove(messageID);
            }
        }
    }
}
