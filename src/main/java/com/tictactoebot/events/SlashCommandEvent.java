package com.tictactoebot.events;

import com.tictactoebot.Database;
import com.tictactoebot.MatchData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.Button;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class SlashCommandEvent extends Event {
    public SlashCommandEvent(Database db) {
        super(db);
    }

    @Override
    public void onSlashCommand(@NotNull net.dv8tion.jda.api.events.interaction.SlashCommandEvent event) {
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
                    Database.UserInfo info = db.getUserInfo(optionUser.current.getId(), guild.getId());

                    int xp = info.xp();
                    double xpLimit = info.xp_limit();
                    int level = info.level();
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
                                    "wins: " +  info.wins() +   ", " +
                                    "loses: " + info.loses() +  ", " +
                                    "draws: " + info.draws() +  '\n' +
                                    "```";
                    hook.sendMessage(statsWindow).queue();
                });
            }
            case "leaderboard" -> event.deferReply().queue(hook -> {
                final StringBuilder leaderboard = new StringBuilder();
                AtomicInteger rank = new AtomicInteger(1);
                leaderboard.append("```kt\nleaderboard\n\n");
                String cell = "%6s";
                leaderboard.append(cell.formatted("rank"))
                        .append(cell.formatted("level"))
                        .append("%13s".formatted("xp"))
                        .append(" name").append('\n');
                db.forEach(guild.getId(), ((tag, xp, xp_limit, level) -> {
                    String xpStr = "(%d/%d)".formatted(xp, xp_limit);
                    leaderboard.append(cell.formatted(rank.getAndIncrement()))
                            .append(cell.formatted(level))
                            .append("%13s".formatted(xpStr))
                            .append(" %s".formatted(tag)).append('\n');
                }));
                leaderboard.append("\n```");
                hook.sendMessage(leaderboard.toString()).queue();
                /*try (var result = Database.LEVELS.aggregate(List.of(
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
                }*/
            });
        }
    }
}
