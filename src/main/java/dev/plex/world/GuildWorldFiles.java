package dev.plex.world;

import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Owns atomic world snapshots, reset journals, and reset backup retention. */
final class GuildWorldFiles implements SlimeLoader
{
    private static final Pattern WORLD_NAME = Pattern.compile("guild_[0-9a-f]{32}");
    private static final Pattern BACKUP_NAME = Pattern.compile("guild_[0-9a-f]{32}_[0-9]+_[0-9a-f-]{36}\\.slime");
    private final Path directory;
    private final Path backups;

    GuildWorldFiles(Path directory) throws IOException
    {
        Files.createDirectories(directory);
        this.directory = directory.toRealPath();
        this.backups = this.directory.resolve("backups");
        Files.createDirectories(backups);
        if (Files.isSymbolicLink(backups))
        {
            throw new IOException("The guild backup directory must not be a symbolic link");
        }
    }

    @Override
    public byte[] readWorld(String worldName) throws IOException, UnknownWorldException
    {
        if (!worldExists(worldName))
        {
            throw new UnknownWorldException(worldName);
        }
        return Files.readAllBytes(path(worldName, ".slime"));
    }

    @Override
    public boolean worldExists(String worldName)
    {
        return Files.isRegularFile(path(worldName, ".slime"), LinkOption.NOFOLLOW_LINKS);
    }

    @Override
    public List<String> listWorlds() throws IOException
    {
        try (var files = Files.list(directory))
        {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".slime"))
                    .map(name -> name.substring(0, name.length() - 6))
                    .filter(name -> WORLD_NAME.matcher(name).matches()).toList();
        }
    }

    @Override
    public void saveWorld(String worldName, byte[] bytes) throws IOException
    {
        Path target = path(worldName, ".slime");
        Path temporary = Files.createTempFile(directory, worldName, ".tmp");
        try
        {
            Files.write(temporary, bytes);
            force(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void deleteWorld(String worldName) throws IOException, UnknownWorldException
    {
        if (!Files.deleteIfExists(path(worldName, ".slime")))
        {
            throw new UnknownWorldException(worldName);
        }
    }

    Set<String> pendingResets() throws IOException
    {
        try (var files = Files.list(directory))
        {
            Set<String> worlds = new HashSet<>();
            for (Path file : files.toList())
            {
                String name = file.getFileName().toString();
                if (name.endsWith(".reset"))
                {
                    String world = name.substring(0, name.length() - 6);
                    if (WORLD_NAME.matcher(world).matches())
                    {
                        worlds.add(world);
                    }
                }
            }
            return worlds;
        }
    }

    void prepareReset(String worldName) throws IOException
    {
        Path journal = path(worldName, ".reset");
        if (Files.exists(journal, LinkOption.NOFOLLOW_LINKS))
        {
            String backup = readJournal(journal);
            if (!backup.equals("none") && !Files.isRegularFile(backups.resolve(backup), LinkOption.NOFOLLOW_LINKS))
            {
                throw new IOException("The pending reset backup is missing: " + backup);
            }
            return;
        }
        String backup = "none";
        if (worldExists(worldName))
        {
            backup = worldName + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID() + ".slime";
            Path temporary = Files.createTempFile(backups, worldName, ".tmp");
            try
            {
                Files.copy(path(worldName, ".slime"), temporary, StandardCopyOption.REPLACE_EXISTING);
                force(temporary);
                Files.move(temporary, backups.resolve(backup), StandardCopyOption.ATOMIC_MOVE);
            }
            finally
            {
                Files.deleteIfExists(temporary);
            }
        }
        // Keep the old file until its backup and this recovery marker both exist.
        Files.writeString(journal, backup, StandardOpenOption.CREATE_NEW);
        force(journal);
    }

    void completeReset(String worldName) throws IOException
    {
        Files.delete(path(worldName, ".reset"));
    }

    void cancelReset(String worldName) throws IOException
    {
        Files.deleteIfExists(path(worldName, ".reset"));
    }

    void expireBackups(Duration retention) throws IOException
    {
        Set<String> protectedBackups = new HashSet<>();
        for (String world : pendingResets())
        {
            protectedBackups.add(readJournal(path(world, ".reset")));
        }
        Instant cutoff = Instant.now().minus(retention);
        try (var files = Files.list(backups))
        {
            for (Path file : files.toList())
            {
                String name = file.getFileName().toString();
                if (BACKUP_NAME.matcher(name).matches() && !protectedBackups.contains(name)
                        && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))
                {
                    Files.delete(file);
                }
            }
        }
    }

    private String readJournal(Path journal) throws IOException
    {
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS) || Files.size(journal) > 200)
        {
            throw new IOException("Invalid guild reset journal: " + journal);
        }
        String backup = Files.readString(journal);
        if (!backup.equals("none") && !BACKUP_NAME.matcher(backup).matches())
        {
            throw new IOException("Invalid guild reset backup name in " + journal);
        }
        return backup;
    }

    private Path path(String worldName, String suffix)
    {
        if (!WORLD_NAME.matcher(worldName).matches())
        {
            throw new IllegalArgumentException("Invalid guild world name: " + worldName);
        }
        return directory.resolve(worldName + suffix);
    }

    private void force(Path file) throws IOException
    {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE))
        {
            channel.force(true);
        }
    }
}
