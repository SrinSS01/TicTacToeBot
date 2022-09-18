package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.events.ShutdownEvent;
import org.jetbrains.annotations.NotNull;

public class BotShutdownEvent extends Event {
    public BotShutdownEvent(Database db) {
        super(db);
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        LOGGER.info("%s is shutting down...".formatted(event.getJDA().getSelfUser().getName()));
        db.close();
    }
}
