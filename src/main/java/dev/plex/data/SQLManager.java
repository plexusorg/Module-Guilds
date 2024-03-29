package dev.plex.data;

import dev.plex.Plex;

import java.sql.Connection;
import java.sql.SQLException;

public class SQLManager
{
    public static void makeTables()
    {
        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            connection.prepareStatement(
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
                            ");"
            ).execute();
        }
        catch (SQLException e)
        {
            e.printStackTrace();
        }
    }
}
