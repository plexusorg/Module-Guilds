package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
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
                .description("Teleports to your guild world")
                .usage("/guild <command>")
                .aliases("base")
                .permission("plex.guilds.world")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        if (!module.isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            player.sendMessage(messageComponent("guildWorldLoading"));
            module.getGuildWorldService().ensureWorld(guild).whenComplete((world, throwable) ->
            {
                if (throwable != null)
                {
                    module.getLogger().error("Failed to load guild world", throwable);
                    player.sendMessage(messageComponent("guildWorldLoadFailed"));
                    return;
                }
                module.scheduler().runEntity(player,
                        () -> player.teleportAsync(world.getSpawnLocation().toCenterLocation()));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }

}
