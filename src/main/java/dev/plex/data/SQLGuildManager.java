package dev.plex.data;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import dev.plex.Plex;
import dev.plex.guild.Guild;
import dev.plex.util.CustomLocation;
import dev.plex.util.GuildUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SQLGuildManager
{
    private static final Gson GSON = new Gson();

    private static final String SELECT_GUILD = "SELECT * FROM `guilds`";
    private static final String SELECT_GUILD_OWNER = "SELECT * FROM `guilds` WHERE owner=?";
    private static final String SELECT_GUILD_MEMBER_MYSQL = "SELECT * FROM `guilds` WHERE json_search(members, ?, ?) IS NOT NULL LIMIT 1";
    private static final String INSERT_GUILD = "INSERT INTO `guilds` (`name`, `owner`, `createdAt`, `members`, `moderators`, `prefix`, `motd`, `home`, `tagEnabled`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_GUILD = "DELETE FROM `guilds` WHERE owner=?";

    public CompletableFuture<Guild> insertGuild(Guild guild)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(INSERT_GUILD);
                statement.setString(1, guild.getName());
                statement.setString(2, guild.getOwner().toString());
                statement.setLong(3, guild.getCreatedAt().toInstant().toEpochMilli());
                statement.setString(4, GSON.toJson(guild.getMembers().stream().map(UUID::toString).collect(Collectors.toList())));
                statement.setString(5, GSON.toJson(guild.getModerators().stream().map(UUID::toString).collect(Collectors.toList())));
                statement.setString(6, guild.getPrefix());
                statement.setString(7, guild.getMotd());
                statement.setString(8, GSON.toJson(guild.getHome()));
                statement.setBoolean(9, guild.isTagEnabled());
                statement.execute();
                return guild;
            } catch (SQLException e)
            {
                GuildUtil.throwExceptionSync(e);
                return null;
            }
        });
    }

    public CompletableFuture<List<Guild>> getGuilds()
    {
        return CompletableFuture.supplyAsync(() ->
        {
            List<Guild> guilds = Lists.newArrayList();
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(SELECT_GUILD);
                ResultSet set = statement.executeQuery();
                while (set.next())
                {
                    Guild guild = new Guild(
                            set.getString("name"),
                            UUID.fromString(set.getString("owner")),
                            ZonedDateTime.ofInstant(Instant.ofEpochMilli(set.getLong("createdAt")), ZoneId.of(Plex.get().config.getString("server.timezone")).getRules().getOffset(Instant.now())));
                    guild.getMembers().addAll(new Gson().fromJson(set.getString("members"), new TypeToken<List<String>>(){}.getType()));
                    guild.getModerators().addAll(new Gson().fromJson(set.getString("moderators"), new TypeToken<List<String>>(){}.getType()));
                    guild.setPrefix(set.getString("prefix"));
                    guild.setMotd(set.getString("motd"));
                    guild.setHome(GSON.fromJson(set.getString("home"), CustomLocation.class));
                    guild.setTagEnabled(set.getBoolean("tagEnabled"));
                    guilds.add(guild);
                }
            } catch (SQLException e)
            {
                GuildUtil.throwExceptionSync(e);
            }
            return guilds;
        });
    }

}
