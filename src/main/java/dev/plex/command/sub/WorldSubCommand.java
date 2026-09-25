package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WorldSubCommand extends GuildSubCommand
{
    public WorldSubCommand(Guilds module)
    {
        super(module, command("world")
                .description("Go to your guild world")
                .usage("/guild <command>")
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
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
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
        teleportInGuildWorld(player, guild, world -> spawnLocation(guild, world));
        return null;
    }
}
