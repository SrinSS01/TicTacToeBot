package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;

public class GuildEvent extends Event {
    public GuildEvent(Database db) {
        super(db);
    }

    protected static void upsertCommands(Guild guild) {
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
}
