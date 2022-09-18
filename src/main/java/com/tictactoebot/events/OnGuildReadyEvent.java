package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import org.jetbrains.annotations.NotNull;

public class OnGuildReadyEvent extends GuildEvent {
    public OnGuildReadyEvent(Database db) {
        super(db);
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        Guild guild = event.getGuild();
        upsertCommands(guild);

        guild.getMembers().forEach( member -> {
            User user = member.getUser();
            db.insertNewUser(guild, user);
        });
    }
}
