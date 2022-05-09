package dev.plex.data;

import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import dev.plex.Plex;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Member;
import dev.plex.util.CustomLocation;
import dev.plex.util.GuildUtil;
import dev.plex.util.PlexLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SQLGuildManager
{
    private static final Gson GSON = new Gson();

    private static final String SELECT_GUILD = "SELECT * FROM `guilds`";
    private static final String INSERT_GUILD = "INSERT INTO `guilds` (`guildUuid`, `name`, `owner`, `createdAt`, `members`, `moderators`, `prefix`, `motd`, `ranks`, `defaultRank`, `warps`, `home`, `tagEnabled`, `isPublic`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String DELETE_GUILD = "DELETE FROM `guilds` WHERE guildUuid=?";
    private static final String UPDATE_GUILD = "UPDATE `guilds` SET name=?, owner=?, members=?, moderators=?, prefix=?, motd=?, ranks=?, defaultRank=?, home=?, warps=?, tagEnabled=?, isPublic=? WHERE guildUuid=?";

    public CompletableFuture<Guild> insertGuild(Guild guild)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(INSERT_GUILD);
                statement.setString(1, guild.getGuildUuid().toString());
                statement.setString(2, guild.getName());
                statement.setString(3, GSON.toJson(guild.getOwner()));
                statement.setLong(4, guild.getCreatedAt().toInstant().toEpochMilli());
                statement.setString(5, GSON.toJson(guild.getMembers()));
                statement.setString(6, GSON.toJson(guild.getModerators().stream().map(UUID::toString).collect(Collectors.toList())));
                statement.setString(7, guild.getPrefix());
                statement.setString(8, guild.getMotd());
                statement.setString(9, GSON.toJson(guild.getRanks()));
                statement.setString(10, GSON.toJson(guild.getDefaultRank()));
                statement.setString(11, GSON.toJson(guild.getWarps()));
                statement.setString(12, GSON.toJson(guild.getHome()));
                statement.setBoolean(13, guild.isTagEnabled());
                statement.setBoolean(14, guild.isPublic());
                statement.execute();
                return guild;
            } catch (SQLException e)
            {
                GuildUtil.throwExceptionSync(e);
                return null;
            }
        });
    }

    public CompletableFuture<Guild> updateGuild(Guild guild)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(UPDATE_GUILD);
                statement.setString(1, guild.getName());
                statement.setString(2, GSON.toJson(guild.getOwner()));
                statement.setString(3, GSON.toJson(guild.getMembers()));
                statement.setString(4, GSON.toJson(guild.getModerators().stream().map(UUID::toString).collect(Collectors.toList())));
                statement.setString(5, guild.getPrefix());
                statement.setString(6, guild.getMotd());
                statement.setString(7, GSON.toJson(guild.getRanks()));
                statement.setString(8, GSON.toJson(guild.getDefaultRank()));
                statement.setString(9, GSON.toJson(guild.getHome()));
                statement.setString(10, GSON.toJson(guild.getWarps()));
                statement.setBoolean(11, guild.isTagEnabled());
                statement.setBoolean(12, guild.isPublic());
                statement.setString(13, guild.getGuildUuid().toString());
                statement.executeUpdate();
                return guild;
            } catch (SQLException e)
            {
                GuildUtil.throwExceptionSync(e);
                return null;
            }
        });
    }

    private List<Guild> getGuildsSync()
    {
        List<Guild> guilds = Lists.newArrayList();
        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            PreparedStatement statement = connection.prepareStatement(SELECT_GUILD);
            ResultSet set = statement.executeQuery();
            while (set.next())
            {
                Guild guild = new Guild(UUID.fromString(set.getString("guildUuid")),
                        set.getString("name"),
                        GSON.fromJson(set.getString("owner"), Member.class),
                        ZonedDateTime.ofInstant(Instant.ofEpochMilli(set.getLong("createdAt")), ZoneId.of(Plex.get().config.getString("server.timezone")).getRules().getOffset(Instant.now())));
                guild.getMembers().addAll(new Gson().fromJson(set.getString("members"), new TypeToken<List<Member>>()
                {
                }.getType()));
                guild.getModerators().addAll(new Gson().fromJson(set.getString("moderators"), new TypeToken<List<String>>()
                {
                }.getType()));
                guild.setPrefix(set.getString("prefix"));
                guild.setMotd(set.getString("motd"));
                guild.setHome(GSON.fromJson(set.getString("home"), CustomLocation.class));
                guild.setTagEnabled(set.getBoolean("tagEnabled"));
                Map<String, CustomLocation> warps = GSON.fromJson(set.getString("warps"), new TypeToken<Map<String, CustomLocation>>()
                {
                }.getType());
                PlexLog.debug("Loaded {0} warps for {1} guild", warps.size(), guild.getName());
                guild.getWarps().putAll(GSON.fromJson(set.getString("warps"), new TypeToken<Map<String, CustomLocation>>()
                {
                }.getType()));
                guild.setPublic(set.getBoolean("isPublic"));
                guilds.add(guild);
            }
        } catch (SQLException e)
        {
            GuildUtil.throwExceptionSync(e);
        }
        return guilds;
    }

    public CompletableFuture<List<Guild>> getGuilds()
    {
        return CompletableFuture.supplyAsync(this::getGuildsSync);
    }

}
