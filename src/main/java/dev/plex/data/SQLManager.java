package dev.plex.data;

import dev.plex.Guilds;
import dev.plex.util.GuildUtil;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SQLManager
{
    public static void makeTables()
    {
        try
        {
            Guilds.get().api().storage().withConnection(connection ->
            {
                try (PreparedStatement statement = connection.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS `guilds` (" +
                                "`guildUuid` VARCHAR(46) NOT NULL, " +
                                "`name` VARCHAR(2000) NOT NULL, " +
                                "`owner` LONGTEXT NOT NULL, " +
                                "`createdAt` BIGINT NOT NULL, " +
                                "`prefix` VARCHAR(2000), " +
                                "`motd` VARCHAR(3000), " +
                                "`home` VARCHAR(1000)," +
                                "`members` LONGTEXT, " +
                                "`moderators` LONGTEXT, " +
                                "`ranks` LONGTEXT, " +
                                "`defaultRank` LONGTEXT, " +
                                "`warps` LONGTEXT, " +
                                "`tagEnabled` BOOLEAN, " +
                                "`isPublic` BOOLEAN, " +
                                "PRIMARY KEY (`guildUuid`)" +
                                ");"))
                {
                    statement.execute();
                }
                return null;
            });
        }
        catch (SQLException e)
        {
            GuildUtil.throwExceptionSync(e);
        }
    }
}
