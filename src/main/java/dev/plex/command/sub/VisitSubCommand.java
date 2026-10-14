package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VisitSubCommand extends GuildSubCommand
{
    public VisitSubCommand(Guilds module)
    {
        super(module, command("visit")
                .description("Visit a guild world where you are a guest")
                .usage("/guild <command> <guild>")
                .permission("plex.guilds.world")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        return player != null && (player.hasPermission("plex.guilds.world.bypass") || guildOf(player) == null || !guestGuilds(player.getUniqueId()).isEmpty());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining, GuildContext context)
    {
        assert player != null;
        if (first == null && !context.actor().staffOverride())
        {
            return usage();
        }
        if (!module.isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        Guild guild = context.actor().staffOverride() ? context.guild() : findGuild(arguments(first, remaining));
        if (guild == null || !context.actor().staffOverride() && !guild.canEnterWorld(player.getUniqueId()) && !player.hasPermission("plex.guilds.world.bypass"))
        {
            return messageComponent("guildWorldNoAccess");
        }
        teleportInGuildWorld(player, guild, world -> spawnLocation(guild, world), context.actor());
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null && sender instanceof Player player
                ? (player.hasPermission("plex.guilds.world.bypass") ? module.getGuildHolder().guilds() : guestGuilds(player.getUniqueId())).stream().map(Guild::getName).toList()
                : List.of();
    }

    private List<Guild> guestGuilds(UUID playerId)
    {
        return module.getGuildHolder().guilds().stream()
                .filter(guild -> guild.getActiveGuest(playerId) != null)
                .toList();
    }

    private Guild findGuild(String target)
    {
        try
        {
            return module.getGuildHolder().guildById(UUID.fromString(target)).orElse(null);
        }
        catch (IllegalArgumentException exception)
        {
            return module.getGuildHolder().guildByName(target).orElse(null);
        }
    }
}
