package dev.plex;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMember;
import dev.plex.guild.GuildRank;
import dev.plex.guild.GuildWarp;
import dev.plex.util.PlexLog;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class SQLManager
{
    private final Gson GSON = new Gson();
    private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final String SELECT_ALL_GUILDS = "select * from guild";
    private final String INSERT_GUILD = "insert into guild (uuid, name, owner_id) values (?, ?, ?)";
    private final String UPDATE_GUILD = "update guild set name = ?, display_name = ?, owner_id = ?, prefix = ?, prefix_enabled = ?, home = ?, motd = ?, privacy = ?, members = ?, moderators = ?, default_rank = ? where uuid = ?";
    private final String DELETE_GUILD_UUID = "delete from guild where uuid = ?";
    private final String DELETE_GUILD_OWNER = "delete from guild where owner_id = ?";
    private final String UPDATE_MEMBER = "update member set chat = ?, prefix = ? where player_uuid = ?";
    private final String[] CREATE_TABLES = new String[]
            {
                    """
                create table if not exists guild (
                    uuid varchar(46) primary key,
                    name text not null,
                    display_name varchar,
                    owner_id int not null,
                    created_at datetime not null default current_timestamp,
                    prefix varchar(3000),
                    prefix_enabled boolean default false,
                    home varchar,
                    motd varchar,
                    privacy not null default 'PUBLIC',
                    members varchar,
                    moderators varchar,
                    default_rank text
                );""",
                    """
                create table if not exists member (
                    id int auto_increment primary key,
                    player_uuid varchar(46) not null,
                    chat boolean default false,
                    prefix boolean default true
                );""",
                    """
                create table if not exists rank (
                    name text not null,
                    members varchar,
                    guild_uuid varchar(46) not null
                );""",
                    """
                create table if not exists warp (
                    name text not null,
                    location text not null,
                    guild_uuid varchar(46) not null
                );
                """
            };

    public void createTables()
    {
        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            Arrays.stream(CREATE_TABLES).forEach(table ->
            {
                try
                {
                    connection.prepareStatement(table).execute();
                }
                catch (SQLException ex)
                {
                    Guilds.logException(ex);
                }
            });
        }
        catch (SQLException ex)
        {
            Guilds.logException(ex);
        }
    }

    public CompletableFuture<GuildMember> insertMember(GuildMember member)
    {
        return CompletableFuture.supplyAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement("insert into member (player_uuid) values (?)");
                statement.setString(1, member.getUuid().toString());
                statement.execute();
                PreparedStatement getId = connection.prepareStatement("select id from member where player_uuid = ?");
                getId.setString(1, member.getUuid().toString());
                ResultSet set = getId.executeQuery();
                if (set.next())
                {
                    member.setId(set.getInt("id"));
                }
                else
                {
                    throw new RuntimeException("Missing member id for %s".formatted(member.getPlayer().getName()));
                }
                return member;
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
                return null;
            }
        });
    }

    public void insertGuild(Guild guild)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(INSERT_GUILD);
                statement.setString(1, guild.getUuid().toString());
                statement.setString(2, guild.getName());
                statement.setInt(3, guild.getOwner().getId());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void insertWarp(Guild guild, GuildWarp warp)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement("insert into warp values (?, ?, ?)");
                statement.setString(1, warp.getName());
                String location = GSON.toJson(warp.getLocation().serialize(), new TypeToken<Map<String, Object>>()
                {
                }.getType());
                statement.setString(2, location);
                statement.setString(3, guild.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void deleteWarp(Guild guild, GuildWarp warp)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement("delete from warp where name = ? and guild_uuid = ?");
                statement.setString(1, warp.getName());
                statement.setString(2, guild.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void insertRank(Guild guild, GuildRank rank)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement("insert into rank (name, guild_uuid) values (?, ?)");
                statement.setString(1, rank.getName());
                statement.setString(2, guild.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void deleteRank(Guild guild, GuildRank rank)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement("delete from rank where name = ? and guild_uuid = ?");
                statement.setString(1, rank.getName());
                statement.setString(2, guild.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void updateDefaultRank(Guild guild, GuildRank rank)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement("update guild set default_rank = ? where uuid = ?");
                statement.setString(1, rank.getName());
                statement.setString(2, guild.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void updateGuild(Guild guild)
    {
        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            PreparedStatement statement = connection.prepareStatement(UPDATE_GUILD);
            statement.setString(1, guild.getName());
            statement.setString(2, guild.getDisplayName());
            statement.setInt(3, guild.getOwner().getId());
            statement.setString(4, guild.getPrefix());
            statement.setBoolean(5, guild.isPrefixEnabled());

            if (guild.getHome() != null)
            {
                String home = GSON.toJson(guild.getHome().serialize(), new TypeToken<Map<String, Object>>()
                {
                }.getType());
                statement.setString(6, home);
            }

            statement.setString(7, guild.getMotd());
            statement.setString(8, guild.getPrivacy().name());

            String members = GSON.toJson(guild.getMemberIDs(), new TypeToken<List<Integer>>()
            {
            }.getType());
            statement.setString(9, members);

            String moderators = GSON.toJson(guild.getModeratorIDs(), new TypeToken<List<Integer>>()
            {
            }.getType());
            statement.setString(10, moderators);

            if (guild.getDefaultRank() != null)
            {
                statement.setString(11, guild.getDefaultRank().getName());
            }
            else
            {
                statement.setString(11, null);
            }

            statement.setString(12, guild.getUuid().toString());
            statement.executeUpdate();

            guild.getRanks().forEach(rank ->
            {
                try
                {
                    PreparedStatement rankStatement = connection.prepareStatement("update rank set members = ? where guild_uuid = ? and name = ?");
                    String rankMembers = GSON.toJson(rank.getMemberIDs(), new TypeToken<List<Integer>>()
                    {
                    }.getType());
                    rankStatement.setString(1, rankMembers);
                    rankStatement.setString(2, guild.getUuid().toString());
                    rankStatement.setString(3, rank.getName());
                    rankStatement.executeUpdate();
                }
                catch (SQLException ex)
                {
                    Guilds.logException(ex);
                }
            });
        }
        catch (SQLException ex)
        {
            Guilds.logException(ex);
        }
    }

    public void updateMember(GuildMember member)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(UPDATE_MEMBER);
                statement.setBoolean(1, member.isChat());
                statement.setBoolean(2, member.isPrefix());
                statement.setString(3, member.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void deleteGuild(Guild guild)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(DELETE_GUILD_UUID);
                statement.setString(1, guild.getUuid().toString());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public void deleteGuild(Player player)
    {
        CompletableFuture.runAsync(() ->
        {
            try (Connection connection = Plex.get().getSqlConnection().getCon())
            {
                PreparedStatement statement = connection.prepareStatement(DELETE_GUILD_OWNER);
                GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
                statement.setInt(1, member.getId());
                statement.execute();
            }
            catch (SQLException ex)
            {
                Guilds.logException(ex);
            }
        });
    }

    public List<Guild> getGuilds()
    {
        List<Guild> guilds = Lists.newArrayList();
        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            PreparedStatement guildStatement = connection.prepareStatement(SELECT_ALL_GUILDS);
            PreparedStatement rankStatement = connection.prepareStatement("select * from rank where guild_uuid = ?");
            ResultSet set = guildStatement.executeQuery();
            while (set.next())
            {
                Guild guild = new Guild(UUID.fromString(set.getString("uuid")), DATE_FORMAT.parse(set.getString("created_at")));
                guild.setName(set.getString("name"));
                guild.setDisplayName(set.getString("display_name"));
                guild.setOwner(Guilds.get().getMemberData().getMemberByID(set.getInt("owner_id")).orElseThrow());

                List<Integer> memberIds = GSON.fromJson(set.getString("members"), new TypeToken<List<Integer>>()
                {
                }.getType());
                if (memberIds != null)
                {
                    guild.setMembers(memberIds.stream().map(i -> Guilds.get().getMemberData().getMemberByID(i).orElseThrow()).toList());
                }

                List<Integer> moderatorIds = GSON.fromJson(set.getString("moderators"), new TypeToken<List<Integer>>()
                {
                }.getType());
                if (moderatorIds != null)
                {
                    guild.setModerators(moderatorIds.stream().map(i -> Guilds.get().getMemberData().getMemberByID(i).orElseThrow()).collect(Collectors.toList()));
                }

                List<GuildRank> ranks = Lists.newArrayList();
                rankStatement.setString(1, guild.getUuid().toString());
                ResultSet rankSet = rankStatement.executeQuery();
                while (rankSet.next())
                {
                    GuildRank rank = new GuildRank(rankSet.getString("name"));
                    if (rank.getName().equals(set.getString("default_rank")))
                    {
                        guild.setDefaultRank(rank);
                        break;
                    }
                    List<Integer> rankMemberIds = GSON.fromJson(rankSet.getString("members"), new TypeToken<List<Integer>>()
                    {
                    }.getType());
                    if (rankMemberIds != null)
                    {
                        rank.setMembers(rankMemberIds.stream().map(i -> Guilds.get().getMemberData().getMemberByID(i).orElseThrow()).collect(Collectors.toList()));
                    }
                    ranks.add(rank);
                }
                guild.setRanks(ranks);

                List<GuildWarp> warps = Lists.newArrayList();
                PreparedStatement warpStatement = connection.prepareStatement("select * from warp where guild_uuid = ?");
                warpStatement.setString(1, guild.getUuid().toString());
                ResultSet warpSet = warpStatement.executeQuery();
                while (warpSet.next())
                {
                    GuildWarp warp = new GuildWarp(warpSet.getString("name"),
                            Location.deserialize(GSON.fromJson(warpSet.getString("location"),
                                    new TypeToken<Map<String, Object>>()
                                    {
                                    }.getType())));
                    warps.add(warp);
                }
                guild.setWarps(warps);

                guild.setPrefix(set.getString("prefix"));
                guild.setPrefixEnabled(set.getBoolean("prefix_enabled"));
                guild.setMotd(set.getString("motd"));

                Map<String, Object> serializedLocation = GSON.fromJson(set.getString("home"), new TypeToken<Map<String, Object>>()
                {
                }.getType());
                if (serializedLocation != null)
                {
                    guild.setHome(Location.deserialize(serializedLocation));
                }
                guild.setPrivacy(Guild.Privacy.valueOf(set.getString("privacy")));
                guilds.add(guild);
            }
        }
        catch (SQLException | ParseException ex)
        {
            Guilds.logException(ex);
        }
        return guilds;
    }

    public List<GuildMember> getMembers()
    {
        List<GuildMember> members = Lists.newArrayList();
        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            PreparedStatement statement = connection.prepareStatement("select * from member");
            ResultSet set = statement.executeQuery();
            while (set.next())
            {
                GuildMember member = new GuildMember(UUID.fromString(set.getString("player_uuid")));
                member.setId(set.getInt("id"));
                member.setChat(set.getBoolean("chat"));
                member.setPrefix(set.getBoolean("prefix"));
                members.add(member);
            }
        }
        catch (SQLException ex)
        {
            Guilds.logException(ex);
        }
        return members;
    }
}
