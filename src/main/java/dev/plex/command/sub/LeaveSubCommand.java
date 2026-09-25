package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LeaveSubCommand extends GuildSubCommand
{
    private static final String CONFIRM = "confirm";

    public LeaveSubCommand(Guilds module)
    {
        super(module, command("leave")
                .description("Leave your guild")
                .usage("/guild <command>")
                .permission("plex.guilds.leave")
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
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        UUID playerUuid = player.getUniqueId();
        String playerName = player.getName();
        if (!guild.isOwner(playerUuid))
        {
            module.getGuildMutationService().removeMember(guild, playerUuid, playerUuid).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(failureMessage(throwable, null));
                    return;
                }
                player.sendMessage(messageComponent("guildLeft"));
                module.broadcastToGuild(guild, messageComponent("guildMemberLeft", Placeholder.unparsed("player", playerName)));
            });
            return null;
        }
        if (!CONFIRM.equalsIgnoreCase(first) || remaining != null)
        {
            return messageComponent("guildLeaveOwnerWarning", Placeholder.unparsed("command", "/guild leave confirm"))
                    .clickEvent(ClickEvent.suggestCommand("/guild leave confirm"));
        }
        module.getGuildMutationService().deleteGuild(guild, playerUuid).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(failureMessage(throwable, null));
                return;
            }
            // The guild object keeps its member list after removal, so the old members still get this message.
            module.broadcastToGuild(guild, messageComponent("guildDisbanded",
                    Placeholder.unparsed("player", playerName), Placeholder.unparsed("guild", guild.getName())));
        });
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        Guild guild = sender instanceof Player player ? guildOf(player) : null;
        return first == null && guild != null && guild.isOwner(((Player) sender).getUniqueId()) ? List.of(CONFIRM) : List.of();
    }
}
