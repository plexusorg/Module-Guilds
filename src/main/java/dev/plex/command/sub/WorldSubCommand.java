package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.world.GuildWorldType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WorldSubCommand extends GuildSubCommand
{
    private final Map<UUID, Confirmation> confirmations = new ConcurrentHashMap<>();

    public WorldSubCommand(Guilds module)
    {
        super(module, command("world")
                .description("Visit, generate, or reset your guild world")
                .usage("/guild <command> [generate <overworld|nether|end|superflat>|reset [confirm]]")
                .permission("plex.guilds.world")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        return guildOf(player) != null;
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        if (!module.isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (first == null)
        {
            teleportInGuildWorld(player, guild, world -> spawnLocation(guild, world));
            return null;
        }
        if (!first.equalsIgnoreCase("generate") && !first.equalsIgnoreCase("reset"))
        {
            return usage();
        }
        if (!guild.isOwner(player.getUniqueId()))
        {
            return messageComponent("guildNotOwner");
        }
        if (first.equalsIgnoreCase("generate"))
        {
            return generate(player, guild, remaining);
        }
        return reset(player, guild, remaining);
    }

    private Component generate(Player player, Guild guild, String remaining)
    {
        if (remaining == null)
        {
            return usage();
        }
        GuildWorldType type;
        try
        {
            type = GuildWorldType.parse(remaining);
        }
        catch (IllegalArgumentException exception)
        {
            return usage();
        }
        confirmations.remove(player.getUniqueId());
        player.sendMessage(messageComponent("guildWorldGenerating"));
        module.getGuildWorldService().generateWorld(guild, type, player.getUniqueId()).whenComplete((world, failure) ->
                player.sendMessage(failure == null ? messageComponent("guildWorldGenerated") : failureMessage(failure, "guildWorldAlreadyGenerated")));
        return null;
    }

    private Component reset(Player player, Guild guild, String remaining)
    {
        if (remaining != null && !remaining.equalsIgnoreCase("confirm"))
        {
            return usage();
        }
        long now = System.nanoTime();
        confirmations.entrySet().removeIf(entry -> now - entry.getValue().expiresAt() >= 0);
        if (remaining == null)
        {
            confirmations.put(player.getUniqueId(), new Confirmation(guild.getGuildUuid(), now + java.util.concurrent.TimeUnit.SECONDS.toNanos(60)));
            return messageComponent("guildWorldResetConfirm",
                    Placeholder.unparsed("guild", guild.getName()),
                    Placeholder.unparsed("world", guild.getWorldName()),
                    Placeholder.unparsed("command", "/guild world reset confirm"),
                    Placeholder.unparsed("days", Integer.toString(module.getConfig().getInt("guilds.worlds.backup-retention-days", 7))));
        }
        Confirmation confirmation = confirmations.remove(player.getUniqueId());
        if (confirmation == null || !confirmation.guildId().equals(guild.getGuildUuid()))
        {
            return messageComponent("guildWorldResetExpired");
        }
        player.sendMessage(messageComponent("guildWorldResetStarted", Placeholder.unparsed("guild", guild.getName())));
        module.getGuildWorldService().resetWorld(guild, player.getUniqueId()).whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to reset guild world {}", guild.getWorldName(), failure);
            }
            player.sendMessage(messageComponent(failure == null ? "guildWorldResetComplete" : "guildWorldResetFailed",
                    Placeholder.unparsed("guild", guild.getName())));
        });
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        Guild guild = sender instanceof Player player ? guildOf(player) : null;
        if (guild == null || !guild.isOwner(((Player) sender).getUniqueId()))
        {
            return List.of();
        }
        if (first == null)
        {
            return List.of("generate", "reset");
        }
        return switch (first.toLowerCase(java.util.Locale.ROOT))
        {
            case "generate" -> List.of("overworld", "nether", "end", "superflat");
            case "reset" -> List.of("confirm");
            default -> List.of();
        };
    }

    private record Confirmation(UUID guildId, long expiresAt)
    {
    }
}
