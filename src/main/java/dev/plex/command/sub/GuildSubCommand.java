package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.CommandSpec;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.util.CustomLocation;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GuildSubCommand
{
    protected final Guilds module;
    private final CommandSpec commandSpec;

    protected GuildSubCommand(Guilds module, CommandSpec commandSpec)
    {
        this.module = module;
        this.commandSpec = commandSpec;
    }

    protected static CommandSpec.Builder command(String name)
    {
        return CommandSpec.builder(name);
    }

    public String getName()
    {
        return commandSpec.name();
    }

    public String getDescription()
    {
        return commandSpec.description();
    }

    public String getUsage()
    {
        return commandSpec.resolvedUsage();
    }

    public String getPermission()
    {
        return commandSpec.permission();
    }

    public RequiredCommandSource getRequiredSource()
    {
        return commandSpec.requiredSource();
    }

    public abstract Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                                @Nullable String first, @Nullable String remaining);

    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return List.of();
    }

    /** Returns true when the player can use this command in the current state. The player is null for the console. */
    public boolean isAvailable(@Nullable Player player)
    {
        return true;
    }

    /** Returns the help lines that this command shows to the player. */
    public List<HelpEntry> helpEntries(@Nullable Player player)
    {
        return List.of(new HelpEntry(getUsage(), getDescription(), "/guild " + getName() + " "));
    }

    protected Component messageComponent(String key, TagResolver... placeholders)
    {
        return module.messageComponent(key, placeholders);
    }

    protected Component usage()
    {
        return messageComponent("correctUsagePrefix")
                .append(LegacyComponentSerializer.legacyAmpersand().deserialize(getUsage()).colorIfAbsent(NamedTextColor.GRAY));
    }

    protected String arguments(@NotNull String first, @Nullable String remaining)
    {
        return remaining == null ? first : first + " " + remaining;
    }

    protected @Nullable Guild guildOf(@Nullable Player player)
    {
        return player == null ? null : module.getGuildHolder().guild(player.getUniqueId()).orElse(null);
    }

    protected CompletableFuture<UUID> resolvePlayer(String target)
    {
        try
        {
            return CompletableFuture.completedFuture(UUID.fromString(target));
        }
        catch (IllegalArgumentException exception)
        {
            Player online = Bukkit.getPlayerExact(target);
            if (online != null)
            {
                return CompletableFuture.completedFuture(online.getUniqueId());
            }
            return module.api().players().byName(target).thenApply(result -> result.map(view -> view.uuid()).orElse(null));
        }
    }

    /**
     * Translates a failed mutation into a message for the player.
     * The invalid key is used for an IllegalArgumentException; when it is null, the storage message is used.
     */
    protected Component failureMessage(Throwable failure, @Nullable String invalidKey, TagResolver... placeholders)
    {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null)
        {
            cause = cause.getCause();
        }
        if (cause instanceof SecurityException)
        {
            return messageComponent("guildNotAllowed");
        }
        if (cause instanceof IllegalArgumentException && invalidKey != null)
        {
            return messageComponent(invalidKey, placeholders);
        }
        if (cause instanceof IllegalStateException)
        {
            return messageComponent("guildUnavailable");
        }
        module.getLogger().error("A guild command failed", failure);
        return messageComponent("guildStorageFailed");
    }

    /** Returns the guild spawn, or the world spawn when the guild has none. */
    protected Location spawnLocation(Guild guild, World world)
    {
        CustomLocation spawn = guild.getSpawn();
        if (spawn == null || !world.getName().equals(spawn.getWorldName()))
        {
            return world.getSpawnLocation().toCenterLocation();
        }
        return new Location(world, spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch());
    }

    /** Loads the guild world, then teleports the player on the player's scheduler. The caller checks that guild worlds are enabled. */
    protected void teleportInGuildWorld(Player player, Guild guild, Function<World, Location> destination)
    {
        player.sendMessage(messageComponent("guildWorldLoading"));
        module.getGuildWorldService().ensureWorld(guild).whenComplete((world, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to load guild world {}", guild.getWorldName(), failure);
                player.sendMessage(messageComponent("guildWorldLoadFailed"));
                return;
            }
            ScheduledTask scheduled = player.getScheduler().run(module.plugin(), task ->
            {
                if (!module.getGuildWorldProtectionListener().canEnter(player.getUniqueId(), world))
                {
                    player.sendMessage(messageComponent("guildWorldNoAccess"));
                    return;
                }
                player.teleportAsync(destination.apply(world)).whenComplete((teleported, teleportFailure) ->
                {
                    if (teleportFailure != null)
                    {
                        module.getLogger().error("Failed to teleport a player to guild world {}", guild.getWorldName(), teleportFailure);
                    }
                    if (teleportFailure != null || !Boolean.TRUE.equals(teleported))
                    {
                        player.sendMessage(messageComponent("guildWorldVisitFailed"));
                    }
                });
            }, null);
            if (scheduled != null)
            {
                module.ownTask(scheduled);
            }
        });
    }

    /** Formats a duration in the largest whole unit: days, hours, or minutes. */
    protected static String formatDuration(Duration duration)
    {
        if (duration.toMinutes() % (24 * 60) == 0)
        {
            return duration.toDays() + "d";
        }
        if (duration.toMinutes() % 60 == 0)
        {
            return duration.toHours() + "h";
        }
        return duration.toMinutes() + "m";
    }

    public record HelpEntry(String usage, String description, String suggestion)
    {
    }
}
