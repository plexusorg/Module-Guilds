package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ResetWorldSubCommand extends GuildSubCommand
{
    public ResetWorldSubCommand(Guilds module)
    {
        super(module, command("resetworld")
                .description("Reset a guild world after confirmation")
                .usage("/guild <command> <guild UUID or name> [confirm]")
                .permission("plex.guilds.resetworld")
                .source(RequiredCommandSource.ANY)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender sender, @Nullable Player player,
                                       @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        if (!module.isGuildWorldsEnabled())
        {
            return messageComponent("guildWorldsUnavailable");
        }
        String target = arguments(first, remaining);
        Guild guild = findGuild(target);
        boolean confirmed = false;
        int lastSpace = target.lastIndexOf(' ');
        if (guild == null && lastSpace >= 0 && target.substring(lastSpace + 1).equalsIgnoreCase("confirm"))
        {
            guild = findGuild(target.substring(0, lastSpace));
            confirmed = true;
        }
        if (guild == null)
        {
            return messageComponent("guildResetTargetNotFound", Placeholder.unparsed("target", target));
        }
        if (!confirmed)
        {
            return messageComponent("guildWorldResetConfirm",
                    Placeholder.unparsed("guild", guild.getName()),
                    Placeholder.unparsed("world", guild.getWorldName()),
                    Placeholder.unparsed("command", "/guild resetworld " + guild.getGuildUuid() + " confirm"),
                    Placeholder.unparsed("days", Integer.toString(module.getConfig().getInt("guilds.worlds.backup-retention-days", 7))));
        }
        Guild selected = guild;
        sender.sendMessage(messageComponent("guildWorldResetStarted", Placeholder.unparsed("guild", selected.getName())));
        module.getGuildWorldService().resetWorld(selected).whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                module.getLogger().error("Failed to reset guild world {}", selected.getWorldName(), failure);
                sender.sendMessage(messageComponent("guildWorldResetFailed", Placeholder.unparsed("guild", selected.getName())));
                return;
            }
            sender.sendMessage(messageComponent("guildWorldResetComplete", Placeholder.unparsed("guild", selected.getName())));
        });
        return null;
    }

    private Guild findGuild(String target)
    {
        try
        {
            return module.getGuildHolder().guildById(UUID.fromString(target))
                    .orElseGet(() -> module.getGuildHolder().guildByName(target).orElse(null));
        }
        catch (IllegalArgumentException ignored)
        {
            return module.getGuildHolder().guildByName(target).orElse(null);
        }
    }
}
