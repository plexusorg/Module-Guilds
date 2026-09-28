package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.exception.PlayerNotFoundException;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InviteSubCommand extends GuildSubCommand
{
    public InviteSubCommand(Guilds module)
    {
        super(module, command("invite")
                .description("Invite an online player to your guild")
                .usage("/guild <command> <player>")
                .permission("plex.guilds.invite")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        Guild guild = guildOf(player);
        return guild != null && guild.canManage(player.getUniqueId());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null || remaining != null)
        {
            return usage();
        }
        assert player != null;
        Guild guild = guildOf(player);
        if (guild == null)
        {
            return messageComponent("guildNotFound");
        }
        if (!guild.canManage(player.getUniqueId()))
        {
            return messageComponent("guildNotManager");
        }
        Player target = findOnline(first);
        if (target == null)
        {
            return messageComponent("guildPlayerNotFound", Placeholder.unparsed("player", first));
        }
        if (target.getUniqueId().equals(player.getUniqueId()))
        {
            return messageComponent("guildCannotInviteSelf");
        }
        String inviterName = player.getName();
        String targetName = target.getName();
        module.getGuildMutationService().createInvite(guild, player.getUniqueId(), target.getUniqueId()).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(failureMessage(throwable, "guildTargetAlreadyInGuild"));
                return;
            }
            player.sendMessage(messageComponent("guildInviteSent", Placeholder.unparsed("player", targetName)));
            target.sendMessage(messageComponent("guildInviteReceived",
                    Placeholder.unparsed("player", inviterName),
                    Placeholder.unparsed("guild", guild.getName()),
                    Placeholder.styling("accept_invite", ClickEvent.runCommand("/guild accept " + guild.getName()))));
        });
        return null;
    }

    private Player findOnline(String target)
    {
        try
        {
            return PlexCommand.resolveOnlinePlayer(target);
        }
        catch (PlayerNotFoundException ignored)
        {
            return null;
        }
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? module.api().players().onlineNames() : List.of();
    }
}
