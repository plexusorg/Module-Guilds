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
    public WorldSubCommand()
    {
        super(command("world")
                .description("Teleports to your guild world")
                .usage("/guild <command>")
                .aliases("base")
                .permission("plex.guilds.world")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        if (!Guilds.get().isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            send(player, messageComponent("guildWorldLoading"));
            Guilds.get().getGuildWorldService().ensureWorld(guild).whenComplete((world, throwable) ->
            {
                if (throwable != null)
                {
                    Guilds.get().getLogger().error("Failed to load guild world", throwable);
                    send(player, messageComponent("guildWorldLoadFailed"));
                    return;
                }
                Guilds.get().scheduler().runEntity(player,
                        () -> player.teleportAsync(world.getSpawnLocation().toCenterLocation()));
            });
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

}
