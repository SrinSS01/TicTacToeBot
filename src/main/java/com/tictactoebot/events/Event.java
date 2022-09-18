package com.tictactoebot.events;

import com.tictactoebot.Database;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Event extends ListenerAdapter {
    protected final Database db;
    protected static final Logger LOGGER = LoggerFactory.getLogger(Event.class);

    public Event(Database db) {
        this.db = db;
    }
}
