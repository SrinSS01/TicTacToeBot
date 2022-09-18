package com.tictactoebot;

/*import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;*/

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.mariadb.jdbc.MariaDbPoolDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class Database implements AutoCloseable {
    private final MariaDbPoolDataSource pool;

    public static final Map<Long, MatchData> MATCH_DATA_MAP = new HashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(Database.class);

    public Database(String user, String password, String host, String name) {
        String connectionStr = "jdbc:mariadb://" + host + "/" + name + "?user=" + user + "&password=" + password + "&maxPoolSize=20";
        try {
            pool = new MariaDbPoolDataSource(connectionStr);
            try (Connection connection = pool.getConnection()) {
                try (Statement statement = connection.createStatement()) {
                    ResultSet res = statement.executeQuery("select TABLE_NAME from information_schema.TABLES where TABLE_NAME='levels'");
                    if (res.next()) return;
                    statement.execute("""
                    create table levels(
                        user_id varchar(18),
                        guild_id varchar(18),
                        draws int default 0,
                        wins int default 0,
                        loses int default 0,
                        level int default 0,
                        xp int default 0,
                        xp_limit int default 100,
                        tag varchar(20),
                    
                        constraint id primary key (user_id, guild_id)
                    );
                """);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }


    public void insertNewUser(Guild guild, User user) {
        if (user.isBot()) return;
        String guildId = guild.getId();
        String userId = user.getId();
        try (Connection connection = pool.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                ResultSet res = statement.executeQuery("select tag from levels where user_id='" + userId + "' and guild_id='" + guildId + '\'');
                if (res.next()) {
                    return;
                }
                statement.execute("insert into levels(user_id, guild_id, tag) values ('" + userId + "', '" + guildId + "', '" + user.getAsTag() + "')");
            }
        } catch (SQLException exception) {
            LOGGER.error(exception.getSQLState(), exception);
        }
        /*Document result = Database.LEVELS.find(
                Filters.and(
                        Filters.eq("guildId", guildId),
                        Filters.eq("userId", userId)
                )
        ).first();
        if (result == null) {
            Database.LEVELS.insertOne(new Document()
                    .append("guildId", guildId)
                    .append("userId", userId)
                    .append("tag", user.getAsTag())
                    .append("game-level", 0)
                    .append("game-xp", 0)
                    .append("game-xp-limit", 100)
                    .append("wins", 0)
                    .append("loses", 0)
                    .append("draws", 0)
            );
        }*/
    }

    public UserInfo getUserInfo(String id, String guildId) {
        UserInfo info = UserInfo.NULL;
        try (Connection connection = pool.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                ResultSet res = statement.executeQuery("select * from levels where user_id='" + id + "' and guild_id='" + guildId + '\'');
                if (res.next()) {
                    info = new UserInfo(
                        res.getString("user_id"),
                        res.getString("guild_id"),
                        res.getInt("draws"),
                        res.getInt("wins"),
                        res.getInt("loses"),
                        res.getInt("level"),
                        res.getInt("xp"),
                        res.getInt("xp_limit"),
                        res.getString("tag")
                    );
                }
            }
        } catch (SQLException exception) {
            LOGGER.error(exception.getSQLState(), exception);
        }
        return info;
    }

    public void resetXPIfCrossesLimit(UserInfo info) {
        int xp = info.xp;
        int limit = info.xp_limit;
        int level = info.level;
        if (xp >= limit) {
            level++;
            limit = level * 201;

            info.xp(xp % limit);
            info.xp_limit(limit);
            info.level(level);

            /*Database.LEVELS.updateOne(
                    Filters.and(
                            Filters.eq("userId", id),
                            Filters.eq("guildId", guildId)
                    ),
                    Filters.and(
                            Filters.eq("$set", Filters.eq("game-xp", xp % limit)),
                            Filters.eq("$set", Filters.eq("game-xp-limit", limit)),
                            Filters.eq("$set", Filters.eq("game-level", level))
                    )
            );*/
        }
    }

    public void update(UserInfo winner, UserInfo loser) {
        try (Connection connection = pool.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("update levels set wins=wins+1, xp=" + (winner.xp + 10) + ", xp_limit=" + winner.xp_limit + ", level=" + winner.level + " where guild_id='" + winner.guild_id + "' and user_id='" + winner.user_id + '\'');
                statement.executeUpdate("update levels set loses=loses+1,  xp=" + (loser.xp + 1) + ", xp_limit=" + loser.xp_limit + ", level=" + loser.level + " where guild_id='" + loser.guild_id + "' and user_id='" + loser.user_id + '\'');
            }
        } catch (SQLException e) {
            LOGGER.error(e.getSQLState(), e);
        }
    }

    public void updateDraw(UserInfo winner, UserInfo loser) {
        try (Connection connection = pool.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("update levels set draws=draws+1, xp=" + (winner.xp + 5) + ", xp_limit=" + winner.xp_limit + ", level=" + winner.level + " where guild_id='" + winner.guild_id + "' and user_id='" + winner.user_id + '\'');
                statement.executeUpdate("update levels set draws=draws+1,  xp=" + (loser.xp + 5) + ", xp_limit=" + loser.xp_limit + ", level=" + loser.level + " where guild_id='" + loser.guild_id + "' and user_id='" + loser.user_id + '\'');
            }
        } catch (SQLException e) {
            LOGGER.error(e.getSQLState(), e);
        }
    }

    public void forEach(String guildID, ILeaderboardInfo info) {
        try (Connection connection = pool.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                ResultSet res = statement.executeQuery("select tag, xp, xp_limit, level from levels where guild_id='" + guildID + "' order by level DESC, xp DESC, tag");
                while (res.next()) {
                    info.info(
                            res.getString("tag"),
                            res.getInt("xp"),
                            res.getInt("xp_limit"),
                            res.getInt("level")
                    );
                }
            }
        } catch (SQLException e) {
            LOGGER.error(e.getSQLState(), e);
        }
    }

    @Override
    public void close() {
        pool.close();
    }

    @SuppressWarnings("unused")
    public static class UserInfo {
        public static final UserInfo NULL = null;
        String user_id;
        String guild_id;
        int draws;
        int wins;
        int loses;
        int level;
        int xp;
        int xp_limit;
        String tag;

        public UserInfo(String user_id, String guild_id, int draws, int wins, int loses, int level, int xp, int xp_limit, String tag) {
            this.user_id = user_id;
            this.guild_id = guild_id;
            this.draws = draws;
            this.wins = wins;
            this.loses = loses;
            this.level = level;
            this.xp = xp;
            this.xp_limit = xp_limit;
            this.tag = tag;
        }

        public String user_id() {
            return user_id;
        }

        public void user_id(String user_id) {
            this.user_id = user_id;
        }

        public String guild_id() {
            return guild_id;
        }

        public void guild_id(String guild_id) {
            this.guild_id = guild_id;
        }

        public int draws() {
            return draws;
        }

        public void draws(int draws) {
            this.draws = draws;
        }

        public int wins() {
            return wins;
        }

        public void wins(int wins) {
            this.wins = wins;
        }

        public int loses() {
            return loses;
        }

        public void loses(int loses) {
            this.loses = loses;
        }

        public int level() {
            return level;
        }

        public void level(int level) {
            this.level = level;
        }

        public int xp() {
            return xp;
        }

        public void xp(int xp) {
            this.xp = xp;
        }

        public int xp_limit() {
            return xp_limit;
        }

        public void xp_limit(int xp_limit) {
            this.xp_limit = xp_limit;
        }

        public String tag() {
            return tag;
        }

        public void tag(String tag) {
            this.tag = tag;
        }
    }

    @FunctionalInterface
    public interface ILeaderboardInfo {
        void info(String tag, int xp, int xp_limit, int level);
    }
}
