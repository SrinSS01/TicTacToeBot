package com.tictactoebot;

import com.mongodb.client.model.Filters;
import com.tictactoebot.game.Player;
import com.tictactoebot.game.TicTacToe;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings("unchecked")
public class Events extends ListenerAdapter {
    public static final Events INSTANCE = new Events();
    public static final Logger LOGGER = LoggerFactory.getLogger(Events.class);
    public static final Random RANDOM = new Random();

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

        guild.getMembers().forEach( member -> {
            User user = member.getUser();
            insertNewUser(guild, user);
        });
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
        guild.getMembers().forEach( member -> {
            User user = member.getUser();
            insertNewUser(guild, user);
        });
    }

    @Override
    public void onSlashCommand(@NotNull SlashCommandEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            return;
        }
        if (!guild.getSelfMember().hasPermission(Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.USE_SLASH_COMMANDS)) {
            event.reply("I do not have permission to send message here").setEphemeral(true).queue();
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
                if (Database.CURRENTLY_PLAYING.containsKey(user)) {
                    event.reply("You are already playing a game!").setEphemeral(true).queue();
                    return;
                }
                if (Database.CURRENTLY_PLAYING.containsKey(mentionedUser)) {
                    event.reply("This user is already playing a game!").setEphemeral(true).queue();
                    return;
                }
                // confirm if the mentioned user is ready to play
                event.reply("%s, will you accept a challenge of a game of TicTacToe from %s?".formatted(
                        mentionedUser.getAsMention(),
                        user.getAsMention()
                )).addActionRow(
                        Button.success("ready", "Yes"),
                        Button.danger("not-ready", "No")
                ).queue(interactionHook ->
                        interactionHook.editOriginalFormat("%s didn't accept the challenge!", mentionedUser.getAsMention()).setActionRow(
                        Button.success("ready", "Yes").asDisabled(),
                        Button.danger("not-ready", "No").asDisabled()
                ).queueAfter(5, TimeUnit.MINUTES));
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
                            "wins: " +  document.getInteger("wins") +   '\n' +
                            "loses: " + document.getInteger("loses") +  '\n' +
                            "draws: " + document.getInteger("draws") +  '\n' +
                            "game level: " + level + '\n' +
                            "game xp: %d/%d\n\nprogress bar\n".formatted(xp, (int) xpLimit) +
                            progressBar + '\n' +
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
                        leaderboard.append("```\nleaderboard\n\n");
                        result.forEachRemaining(document -> {
                            String tag = document.getString("tag");
                            int level = document.getInteger("game-level");
                            int xp = document.getInteger("game-xp");
                            int xpLimit = document.getInteger("game-xp-limit");
                            leaderboard.append("%3s".formatted(rank.getAndIncrement())).append(" %3s".formatted(level)).append(" (%d/%d)".formatted(xp, xpLimit)).append(" %s".formatted(tag)).append('\n');
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
        final String messageId = event.getMessageId();
        final List<User> mentionedUsers = event.getMessage().getMentionedUsers();
        final User challenger = mentionedUsers.get(1);
        final User challengeAccepter = mentionedUsers.get(0);
        final boolean isNonPlayer = user.equals(challenger) || !user.equals(challengeAccepter);
        switch (buttonId) {
            case "ready" -> {
                if (isNonPlayer) {
                    event.getInteraction().deferEdit().queue();
                    return;
                }
                // generate a random number between 0 and 10 using Random class
                // if the number is divisible by 2, then the user will play as Player.Type.CROSS else Player.Type.NAUGHT
                boolean random = isPlayAsNaught();
                setPlayers(/*player 1*/ challenger, /*player 2*/ challengeAccepter, random);
                TicTacToe game = new TicTacToe();
                Database.GAMES.put(challenger, game);
                Database.GAMES.put(challengeAccepter, game);
                List<ActionRow> board = new ArrayList<>(List.of(
                        ActionRow.of(Button.primary("8", " "), Button.primary("7", " "), Button.primary("6", " ")),
                        ActionRow.of(Button.primary("5", " "), Button.primary("4", " "), Button.primary("3", " ")),
                        ActionRow.of(Button.primary("2", " "), Button.primary("1", " "), Button.primary("0", " ")),
                        ActionRow.of(
                                Button.primary("null1", " ").asDisabled(),
                                Button.danger("resign", "stop"),
                                Button.primary("null2", " ").asDisabled()
                        )
                ));
                Database.BOARDS.put(messageId, board);
                event.editMessageFormat("%s is playing ` %s `, %s is playing ` %s `",
                        challenger.getAsMention(), Database.CURRENTLY_PLAYING.get(challenger).get(),
                        user.getAsMention(), Database.CURRENTLY_PLAYING.get(challengeAccepter).get()
                ).setActionRows(
                        board
                ).queue();
            }
            case "not-ready" -> {
                if (isNonPlayer) {
                    event.getInteraction().deferEdit().queue();
                    return;
                }
                event.editMessageFormat("%s declined the challenge!", user.getAsMention()).setActionRow(
                        Button.success("ready", "Yes").asDisabled(),
                        Button.danger("not-ready", "No").asDisabled()
                ).queue();
            }
            case "8", "7", "6", "5", "4", "3", "2", "1", "0" -> {
                if (!Database.GAMES.containsKey(user) || !mentionedUsers.contains(user)) {
                    event.deferEdit().queue();
                    return;
                }
                TicTacToe game = Database.GAMES.get(user);
                int pos = Integer.parseInt(buttonId);
                boolean result = game.place(pos, Database.CURRENTLY_PLAYING.get(user).get());
                ArrayList<ActionRow> board = (ArrayList<ActionRow>) Database.BOARDS.get(messageId);
                int row = 2 - pos / 3;
                ActionRow buttons = board.get(row);
                buttons.updateComponent(buttonId, Button.primary(buttonId, Database.CURRENTLY_PLAYING.get(user).get().toString()));
                board.set(row, buttons);
                if (result) {
                    final String guildId = Objects.requireNonNull(event.getGuild()).getId();
                    if (game.isWin()) {
                        disableBoard(board);
                        event.editComponents(board).queue(hook -> {
                            Player.Type type = game.getCurrentPlayer().get();
                            Player.Type challengerType = Database.CURRENTLY_PLAYING.get(challenger).get();
                            User loser = type == challengerType ? challenger : challengeAccepter;
                            User winner = type == challengerType ? challengeAccepter : challenger;
                            hook.sendMessageFormat("%s won! <a:celebrate:1018229309405671554>", winner.getAsMention()).queue();

                            resetXPIfGT5(winner.getId(), guildId);
                            resetXPIfGT5(loser.getId(), guildId);

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

                            mentionedUsers.forEach(it -> {
                                Database.CURRENTLY_PLAYING.remove(it);
                                Database.GAMES.remove(it);
                                Database.BOARDS.remove(messageId);
                            });
                        });
                    } else if (game.isDraw()) {
                        disableBoard(board);
                        event.editComponents(board).queue(hook -> {
                            hook.sendMessage("It was a draw <a:sweat:1018229316670197801>").queue();
                            mentionedUsers.forEach(it -> {
                                resetXPIfGT5(it.getId(), guildId);
                                Database.LEVELS.updateOne(
                                        Filters.and(
                                                Filters.eq("userId", it.getId()),
                                                Filters.eq("guildId", guildId)
                                        ),
                                        Filters.eq("$inc", Filters.and(Filters.eq("draws", 1), Filters.eq("game-xp", 5)))
                                );
                                Database.CURRENTLY_PLAYING.remove(it);
                                Database.GAMES.remove(it);
                                Database.BOARDS.remove(messageId);
                            });
                        });
                    } else event.editComponents(board).queue();
                } else event.deferEdit().queue();
            }
            case "resign" -> {
                mentionedUsers.forEach(it -> {
                    Database.CURRENTLY_PLAYING.remove(it);
                    Database.GAMES.remove(it);
                });

                if (!mentionedUsers.contains(user)) {
                    event.deferEdit().queue();
                    return;
                }
                List<ActionRow> board = ((ArrayList<ActionRow>) Database.BOARDS.get(messageId));
                disableBoard(board);
                event.editMessage("game ended").setActionRows(board).queue();
                Database.BOARDS.remove(messageId);
            }
        }
    }

    private void resetXPIfGT5(String id, String guildId) {
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

    private static void disableBoard(List<ActionRow> board) {
        board.replaceAll(row -> {
            row.forEach(button -> row.updateComponent(Objects.requireNonNull(button.getId()), ((Button) button).asDisabled()));
            return row;
        });
    }

    private void setPlayers(User user, User mentionedUser, boolean playAsX) {
        Player X = new Player(Player.Type.CROSS);
        Player O = new Player(Player.Type.NAUGHT);
        if (playAsX) {
            Database.CURRENTLY_PLAYING.put(user, X);
            Database.CURRENTLY_PLAYING.put(mentionedUser, O);
        } else {
            Database.CURRENTLY_PLAYING.put(user, O);
            Database.CURRENTLY_PLAYING.put(mentionedUser, X);
        }
    }

    private boolean isPlayAsNaught() {
        return RANDOM.nextInt(11) % 2 == 0;
    }
}
