package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

public class GuildJoinEvent extends GuildEvent {
    public GuildJoinEvent(Database db) {
        super(db);
    }

    @Override
    public void onGuildJoin(@NotNull net.dv8tion.jda.api.events.guild.GuildJoinEvent event) {
        Guild guild = event.getGuild();
        upsertCommands(guild);
        LOGGER.info("Joined guild: " + guild.getName());
        guild.getMembers().forEach( member -> {
            User user = member.getUser();
            db.insertNewUser(guild, user);
        });
    }
}
