package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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
                .description("Visit a guild that gives you access")
                .usage("/guild <command> <guild name or UUID>")
                .permission("plex.guilds.world")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        if (first == null)
        {
            return usage();
        }
        if (!module.isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        Guild guild = findGuild(arguments(first, remaining));
        if (guild == null || !guild.canEnterWorld(player.getUniqueId()))
        {
            return messageComponent("guildWorldNoAccess");
        }
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
                player.teleportAsync(world.getSpawnLocation().toCenterLocation()).whenComplete((teleported, teleportFailure) ->
                {
                    if (teleportFailure != null)
                    {
                        module.getLogger().error("Failed to teleport a guest to guild world {}", guild.getWorldName(), teleportFailure);
                    }
                    if (teleportFailure != null || !Boolean.TRUE.equals(teleported))
                    {
                        player.sendMessage(messageComponent("guildWorldVisitFailed"));
                    }
                });
            }, () -> player.sendMessage(messageComponent("guildWorldVisitFailed")));
            if (scheduled == null)
            {
                player.sendMessage(messageComponent("guildWorldVisitFailed"));
            }
            else
            {
                module.ownTask(scheduled);
            }
        });
        return null;
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
