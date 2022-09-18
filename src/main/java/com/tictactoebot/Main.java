package com.tictactoebot;

import com.tictactoebot.events.*;
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
        String bot_token_env = System.getenv("BOT_TOKEN");
        String token;
        int index = 0;
        int expectedLength = 5;
        if (bot_token_env != null) {
            token = bot_token_env;
            expectedLength = 4;
        } else token = args[index++];

        if (args.length != expectedLength) {
            System.out.println("Usage: java -jar TicTacToeBot.jar <bot_token> <database_user_name> <database_password> <database_host> <database_name>");
            return;
        }

        String dbUser = args[index++];
        String dbPassword = args[index++];
        String dbHost = args[index++];
        String dbName = args[index];

        Database db = new Database(dbUser, dbPassword, dbHost, dbName);

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
        .addEventListeners(
                new BotReadyEvent(),
                new BotShutdownEvent(db),
                new GuildJoinEvent(db),
                new GuildMemberJoinEvent(db),
                new OnGuildReadyEvent(db),
                new SlashCommandEvent(db),
                new ButtonClickEvent(db)
        )
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
