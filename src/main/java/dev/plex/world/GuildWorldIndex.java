package dev.plex.world;

import dev.plex.Guilds;
import dev.plex.guild.Guild;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Appends one line to slime-worlds/index.log for each guild create, disband, restore, and purge.
 * The module never removes lines, so every copy of the worlds folder names the guild and owner of each world file.
 */
public final class GuildWorldIndex
{
    private static final Pattern WORLD_NAME = Pattern.compile("guild_[0-9a-f]{32}");

    private final Guilds module;

    public GuildWorldIndex(Guilds module)
    {
        this.module = module;
    }

    /** Writes the line and logs it. A failed write goes to the log and does not fail the caller. */
    public void record(String event, Guild guild, @Nullable UUID actor)
    {
        String line = line(event, guild, actor);
        module.getLogger().info("Guild world index: {}", line);
        try
        {
            append(List.of(line));
        }
        catch (IOException exception)
        {
            module.getLogger().error("Failed to write the guild world index", exception);
        }
    }

    /** Writes a KNOWN line for each guild whose world the index does not name yet. */
    public void seed(Collection<Guild> guilds)
    {
        try
        {
            synchronized (this)
            {
                Path file = file();
                Set<String> known = new HashSet<>();
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                {
                    for (String line : Files.readAllLines(file, StandardCharsets.UTF_8))
                    {
                        Matcher matcher = WORLD_NAME.matcher(line);
                        if (matcher.find())
                        {
                            known.add(matcher.group());
                        }
                    }
                }
                append(guilds.stream().filter(guild -> !known.contains(guild.getWorldName()))
                        .map(guild -> line("KNOWN", guild, null)).toList());
            }
        }
        catch (IOException exception)
        {
            module.getLogger().error("Failed to write the guild world index", exception);
        }
    }

    private synchronized void append(List<String> lines) throws IOException
    {
        if (lines.isEmpty())
        {
            return;
        }
        Path file = file();
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private Path file()
    {
        return module.getDataFolder().toPath().resolve("slime-worlds").resolve("index.log");
    }

    // Example: 2026-10-09T19:59:57Z DISBAND guild_a0b0... guild=a0b0d01a-... name="team raj" owner=Name:uuid actor=Name:uuid
    private static String line(String event, Guild guild, @Nullable UUID actor)
    {
        StringBuilder line = new StringBuilder()
                .append(Instant.now().truncatedTo(ChronoUnit.SECONDS)).append(' ')
                .append(event).append(' ')
                .append(guild.getWorldName())
                .append(" guild=").append(guild.getGuildUuid())
                .append(" name=\"").append(quote(guild.getName())).append('"')
                .append(" owner=").append(player(guild.getOwnerUuid()));
        if (!event.equals("KNOWN") && !event.equals("PURGE"))
        {
            line.append(" actor=").append(actor == null ? "console" : player(actor));
        }
        return line.toString();
    }

    private static String player(UUID uuid)
    {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name + ":" + uuid;
    }

    private static String quote(String name)
    {
        return name.replace("\\", "\\\\").replace("\"", "\\\"").replaceAll("\\p{Cntrl}", " ");
    }
}
