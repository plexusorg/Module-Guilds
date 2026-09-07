package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.exception.PlayerNotFoundException;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

public class InviteSubCommand extends GuildSubCommand
{
    public InviteSubCommand(Guilds module)
    {
        super(module, command("invite")
                .description("Invites a player to the guild")
                .usage("/guild <command> <player name>")
                .aliases("inv")
                .permission("plex.guilds.invite")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            Player resolvedTarget;
            try
            {
                resolvedTarget = Bukkit.getPlayer(UUID.fromString(first));
            }
            catch (IllegalArgumentException ignored)
            {
                resolvedTarget = Bukkit.getPlayer(first);
            }
            if (resolvedTarget == null)
            {
                throw new PlayerNotFoundException();
            }
            Player target = resolvedTarget;
            if (target.getUniqueId().equals(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildCannotInviteSelf"));
                return;
            }
            if (guild.getMember(target.getUniqueId()) != null || module.getGuildHolder().guild(target.getUniqueId()).isPresent())
            {
                player.sendMessage(messageComponent("guildTargetAlreadyInGuild"));
                return;
            }
            String inviterName = player.getName();
            String targetName = target.getName();
            module.getGuildRepository().createInvite(guild.getGuildUuid(), player.getUniqueId(), target.getUniqueId(), Instant.now().plus(5, ChronoUnit.MINUTES)).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildInviteSent", Placeholder.parsed("player", targetName)));
                target.sendMessage(messageComponent("guildInviteReceived", Placeholder.parsed("player", inviterName), Placeholder.parsed("guild", guild.getName()),
                        Placeholder.styling("accept_invite", ClickEvent.runCommand("/guild accept " + guild.getName()))));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> suggestSubCommand(@NotNull CommandSender sender, @Nullable String first)
    {
        return first == null ? module.api().players().onlineNames() : List.of();
    }


}
