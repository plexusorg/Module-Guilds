package dev.plex.data;

import dev.plex.Plex;
import dev.plex.storage.StorageType;

import java.sql.Connection;
import java.sql.SQLException;

public class SQLManager
{
    public static void makeTables()
    {
        if (Plex.get().getStorageType() == StorageType.MONGODB)
        {
            return;
        }

        try (Connection connection = Plex.get().getSqlConnection().getCon())
        {
            connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS `guilds` (" +
                            "`name` VARCHAR(2000) NOT NULL, " +
                            "`owner` VARCHAR(46) NOT NULL, " +
                            "`createdAt` BIGINT NOT NULL, " +
                            "`prefix` VARCHAR(2000), " +
                            "`motd` VARCHAR(3000), " +
                            "`home` VARCHAR(1000)," +
                            "`members` LONGTEXT, " +
                            "`moderators` LONGTEXT, " +
                            "`tagEnabled` BOOLEAN" +
                            ");"
            ).execute();
        } catch (SQLException e)
        {
            e.printStackTrace();
        }
    }

}
