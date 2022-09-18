package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;

public class GuildMemberJoinEvent extends GuildEvent {
    public GuildMemberJoinEvent(Database db) {
        super(db);
    }

    @Override
    public void onGuildMemberJoin(@NotNull net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent event) {
        Guild guild = event.getGuild();
        User user = event.getUser();
        db.insertNewUser(guild, user);
    }
}
