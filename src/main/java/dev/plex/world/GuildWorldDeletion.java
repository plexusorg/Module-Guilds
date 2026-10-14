package dev.plex.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Deletes the files of a guild world. It uses no ASP class, so finalization also runs when guild worlds are disabled. */
public final class GuildWorldDeletion
{
    private static final Pattern WORLD_NAME = Pattern.compile("guild_[0-9a-f]{32}");

    private GuildWorldDeletion()
    {
    }

    /** Deletes the world file, its reset journal, and every reset backup of the world. Missing files are not an error. */
    public static void delete(Path directory, String worldName) throws IOException
    {
        if (!WORLD_NAME.matcher(worldName).matches())
        {
            throw new IllegalArgumentException("Invalid guild world name: " + worldName);
        }
        Path backups = directory.resolve("backups");
        if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(backups))
        {
            throw new IOException("The guild world directories must not be symbolic links");
        }
        Files.deleteIfExists(directory.resolve(worldName + ".slime"));
        Files.deleteIfExists(directory.resolve(worldName + ".reset"));
        if (!Files.isDirectory(backups, LinkOption.NOFOLLOW_LINKS))
        {
            return;
        }
        try (var files = Files.list(backups))
        {
            for (Path file : files.toList())
            {
                String name = file.getFileName().toString();
                if (name.startsWith(worldName + "_") && name.endsWith(".slime"))
                {
                    Files.deleteIfExists(file);
                }
            }
        }
    }
}
