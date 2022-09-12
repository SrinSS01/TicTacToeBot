package com.tictactoebot.game;

public class Player {
    private Type type;

    public Player(Type type) {
        this.type = type;
    }

    public void set(Type type) {
        this.type = type;
    }

    public Type get() {
        return type;
    }

    public enum Type {
        NAUGHT('O'), CROSS('X'), NONE('\0');
        private final char type;
        Type(char type) { this.type = type; }

        @Override
        public String toString() {
            return String.valueOf(type);
        }
    }
}