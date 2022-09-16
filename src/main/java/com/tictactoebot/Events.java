package com.tictactoebot;

import com.mongodb.client.model.Filters;
import com.tictactoebot.game.Game;
import com.tictactoebot.game.Player;
import com.tictactoebot.game.TicTacToe;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bson.Document;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Events extends ListenerAdapter {
    public static final Events INSTANCE = new Events();
    public static final Logger LOGGER = LoggerFactory.getLogger(Events.class);

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        LOGGER.info("%s is ready!".formatted(event.getJDA().getSelfUser().getName()));
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        LOGGER.info("%s is shutting down...".formatted(event.getJDA().getSelfUser().getName()));
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        Guild guild = event.getGuild();
        upsertCommands(guild);

        guild.getMembers().forEach( member -> {
            User user = member.getUser();
            insertNewUser(guild, user);
        });
    }

    private static void upsertCommands(Guild guild) {
        guild
            .upsertCommand("play", "Play a game of TicTacToe with a mentioned user")
            .addOption(OptionType.USER, "user", "The user to play with", true)
            .queue();
        guild
            .upsertCommand("leaderboard", "View the leaderboard").queue();
        guild
            .upsertCommand("stats", "View your current level or the mentioned user")
            .addOption(OptionType.USER, "user", "check level of this user", false)
            .queue();

        LOGGER.info("Registered commands for %s".formatted(guild.getName()));
    }

    @Override
    public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        insertNewUser(guild, user);
    }

    private static void insertNewUser(Guild guild, User user) {
        if (user.isBot()) return;
        Document result = Database.LEVELS.find(
                Filters.and(
                        Filters.eq("guildId", guild.getId()),
                        Filters.eq("userId", user.getId())
                )
        ).first();
        if (result == null) {
            Database.LEVELS.insertOne(new Document()
                    .append("guildId", guild.getId())
                    .append("userId", user.getId())
                    .append("tag", user.getAsTag())
                    .append("game-level", 0)
                    .append("game-xp", 0)
                    .append("game-xp-limit", 100)
                    .append("wins", 0)
                    .append("loses", 0)
                    .append("draws", 0)
            );
        }
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        Guild guild = event.getGuild();
        upsertCommands(guild);
        LOGGER.info("Joined guild: " + guild.getName());
        guild.getMembers().forEach( member -> {
            User user = member.getUser();
            insertNewUser(guild, user);
        });
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        List<Permission> permissions = List.of(
                Permission.USE_SLASH_COMMANDS,
                Permission.MESSAGE_EMBED_LINKS,
                Permission.MESSAGE_ATTACH_FILES,
                Permission.MESSAGE_WRITE
        );
        if (!Objects.requireNonNull(event.getGuild()).getSelfMember().hasPermission(
                permissions
        )) {
            StringBuilder builder = new StringBuilder();
            builder.append("```").append('\n');
            permissions.forEach(permission -> builder.append(permission.getName()).append('\n'));
            builder.append("```");
            event.reply("_I do not have one of the following permissions:_\n" + builder).setEphemeral(true).queue();
            return;
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        String commandName = event.getName();
        User user = event.getUser();
        switch (commandName) {
            case "play" -> {
                User mentionedUser = Objects.requireNonNull(event.getOption("user")).getAsUser();
                if (mentionedUser.equals(user)) {
                    event.reply("You can't play with yourself!").setEphemeral(true).queue();
                    return;
                }
                if (mentionedUser.isBot()) {
                    event.reply("You can't play with a bot!").setEphemeral(true).queue();
                    return;
                }
                // confirm if the mentioned user is ready to play
                event.deferReply().queue(
                    interactionHook ->
                        interactionHook.sendMessage("%s, will you accept a challenge of a game of TicTacToe from %s?".formatted(
                            mentionedUser.getAsMention(),
                            user.getAsMention()
                        )).addActionRow(
                                Button.success("ready", "Yes"),
                                Button.danger("not-ready", "No")
                        ).queue(message -> {
                            long messageID = message.getIdLong();
                            MatchData matchData = MatchData.create(user, mentionedUser);
                            matchData.setPendingEdit(message);
                            Database.MATCH_DATA_MAP.put(messageID, matchData);
                        }));
            }
            case "stats" -> {
                OptionMapping option = event.getOption("user");
                final var optionUser = new Object() {
                    User current = user;
                };
                if (option != null) {
                    optionUser.current = option.getAsUser();
                    if (optionUser.current.isBot()) {
                        event.reply("Bots don't participate in leveling").setEphemeral(true).queue();
                        return;
                    }
                }

                event.deferReply().queue(hook -> {
                    Document document = getUserInfo(optionUser.current.getId(), guild.getId());

                    int xp = document.getInteger("game-xp");
                    double xpLimit = document.getInteger("game-xp-limit");
                    int level = document.getInteger("game-level");
                    double ratio = xp / xpLimit;
                    int progressLength = (int) (ratio * 10);
                    int empty = 10 - progressLength;
                    String progressBar = "%s%s %d%%".formatted("\u2588".repeat(progressLength), "_".repeat(empty), (int) (ratio * 100));
                    String statsWindow =
                            "```js\n" +
                            "//  %s's stats  //\n\n".formatted(optionUser.current.getAsTag()) +
                            "game level: " + level + '\n' +
                            "game xp: %d/%d\n\nprogress bar\n".formatted(xp, (int) xpLimit) +
                            progressBar + "\n\n" +
                            "wins: " +  document.getInteger("wins") +   ", " +
                            "loses: " + document.getInteger("loses") +  ", " +
                            "draws: " + document.getInteger("draws") +  '\n' +
                            "```";
                    hook.sendMessage(statsWindow).queue();
                });
            }
            case "leaderboard" -> event.deferReply().queue(hook -> {
                    try (var result = Database.LEVELS.aggregate(List.of(
                            Filters.eq("$match", Filters.eq("guildId", guild.getId())),
                            Filters.eq("$group", Filters.and(
                                    Filters.eq("_id", "$_id"),
                                    Filters.eq("userId", Filters.eq("$first", "$userId")),
                                    Filters.eq("tag", Filters.eq("$first", "$tag")),
                                    Filters.eq("game-level", Filters.eq("$first", "$game-level")),
                                    Filters.eq("game-xp", Filters.eq("$first", "$game-xp")),
                                    Filters.eq("game-xp-limit", Filters.eq("$first", "$game-xp-limit"))
                            )),
                            Filters.eq("$sort", Filters.and(
                                    Filters.eq("game-level", -1),
                                    Filters.eq("game-xp", -1),
                                    Filters.eq("tag", 1)
                            ))
                    )).iterator()) {
                        final StringBuilder leaderboard = new StringBuilder();
                        AtomicInteger rank = new AtomicInteger(1);
                        leaderboard.append("```kt\nleaderboard\n\n");
                        String cell = "%6s";
                        leaderboard.append(cell.formatted("rank"))
                                .append(cell.formatted("level"))
                                .append("%13s".formatted("xp"))
                                .append(" name").append('\n');
                        result.forEachRemaining(document -> {
                            String tag = document.getString("tag");
                            int level = document.getInteger("game-level");
                            int xp = document.getInteger("game-xp");
                            int xpLimit = document.getInteger("game-xp-limit");
                            String xpStr = "(%d/%d)".formatted(xp, xpLimit);
                            leaderboard.append(cell.formatted(rank.getAndIncrement()))
                                    .append(cell.formatted(level))
                                    .append("%13s".formatted(xpStr))
                                    .append(" %s".formatted(tag)).append('\n');
                        });
                        leaderboard.append("\n```");
                        hook.sendMessage(leaderboard.toString()).queue();
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                });
        }
    }

    @Override
    public void onButtonClick(@NotNull ButtonClickEvent event) {
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

                event.deferEdit()
                        .setEmbeds(game.getEmbed())
                        .addFile(boardImage)
                        .setActionRows(game.getRows()).queue(hook -> hook.editOriginal("_ _").queue());
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
                        case WIN -> event.deferEdit().queue(hook -> {
                            User winner = game.getCurrentUser();
                            User loser = winner.getIdLong() == challenger.getIdLong() ? challengeAccepter : challenger;
                            hook.editOriginalComponents(List.of())
                                    .setEmbeds(embed)
                                    .retainFilesById(List.of())
                                    .addFile(boardImage).queue();

                            resetXPIfCrossesLimit(winner.getId(), guildId);
                            resetXPIfCrossesLimit(loser.getId(), guildId);

                            Database.LEVELS.updateOne(
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
                            );

                            Database.MATCH_DATA_MAP.remove(messageID);
                        });
                        case DRAW -> event.deferEdit().queue(hook -> {
                            hook.editOriginalComponents(board)
                                    .setEmbeds(embed)
                                    .retainFilesById(List.of())
                                    .addFile(boardImage)
                                    .queue();
                            resetXPIfCrossesLimit(challenger.getId(), guildId);
                            Database.LEVELS.updateOne(
                                    Filters.and(
                                            Filters.eq("userId", challenger.getId()),
                                            Filters.eq("guildId", guildId)
                                    ),
                                    Filters.eq("$inc", Filters.and(Filters.eq("draws", 1), Filters.eq("game-xp", 5)))
                            );

                            resetXPIfCrossesLimit(challengeAccepter.getId(), guildId);
                            Database.LEVELS.updateOne(
                                    Filters.and(
                                            Filters.eq("userId", challengeAccepter.getId()),
                                            Filters.eq("guildId", guildId)
                                    ),
                                    Filters.eq("$inc", Filters.and(Filters.eq("draws", 1), Filters.eq("game-xp", 5)))
                            );
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
                } catch (IOException ignore) {} }
            case "resign" -> {
                Game game = matchData.getGame();
                if (!game.contains(user.getIdLong())) {
                    event.deferEdit().queue();
                    return;
                }
                game.resign(user);
                MessageEmbed embed = game.getEmbed();
                game.disableButtons();
                event.editMessage("game ended").setEmbeds(embed).setActionRows(game.getRows()).queue();
                Database.MATCH_DATA_MAP.remove(messageID);
            }
        }
    }

    private void resetXPIfCrossesLimit(String id, String guildId) {
        Document info = getUserInfo(id, guildId);
        double xp = info.getInteger("game-xp");
        double limit = info.getInteger("game-xp-limit");
        int level = info.getInteger("game-level");
        if (xp >= limit) {
            level++;
            limit = level * 201;
            Database.LEVELS.updateOne(
                    Filters.and(
                            Filters.eq("userId", id),
                            Filters.eq("guildId", guildId)
                    ),
                    Filters.and(
                            Filters.eq("$set", Filters.eq("game-xp", xp % limit)),
                            Filters.eq("$set", Filters.eq("game-xp-limit", limit)),
                            Filters.eq("$set", Filters.eq("game-level", level))
                    )
            );
        }
    }

    private static Document getUserInfo(String id, String guildId) {
        return Objects.requireNonNull(Database.LEVELS.find(Filters.and(
                Filters.eq("userId", id),
                Filters.eq("guildId", guildId)
        )).first());
    }
}
