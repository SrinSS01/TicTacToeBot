package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.events.ReadyEvent;
import org.jetbrains.annotations.NotNull;

public class BotReadyEvent extends Event {
    public BotReadyEvent(Database db) {
        super(db);
    }

    public BotReadyEvent() {
        this(null);
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        LOGGER.info("%s is ready!".formatted(event.getJDA().getSelfUser().getName()));
    }
}
