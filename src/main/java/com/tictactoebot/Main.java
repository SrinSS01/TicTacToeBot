package com.tictactoebot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws LoginException {
        String token = args.length != 0? args[0] : System.getenv("BOT_TOKEN");
        if (token == null) {
            throw new IllegalArgumentException("Either the bot token not been provided in command line argument or environment variable BOT_TOKEN is not set!");
        }
        JDA bot = JDABuilder.createDefault(
                token,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.GUILD_MESSAGE_REACTIONS,
                    GatewayIntent.GUILD_VOICE_STATES,
                    GatewayIntent.GUILD_EMOJIS,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_PRESENCES
        )
        .setMemberCachePolicy(MemberCachePolicy.ALL)
        .enableCache(CacheFlag.CLIENT_STATUS)
        .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOTE)
        .addEventListeners(Events.INSTANCE)
        .setStatus(OnlineStatus.ONLINE)
        .setActivity(Activity.playing("TicTacToe"))
        .build();

        Thread stopThread = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            String command = null;
            while (!"stop".equals(command)) {
                command = sc.next();
            }
            bot.shutdown();
        }, "stop thread");
        stopThread.start();
    }
}
