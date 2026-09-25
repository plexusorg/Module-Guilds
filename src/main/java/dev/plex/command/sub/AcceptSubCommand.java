package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.storage.entity.GuildInviteEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AcceptSubCommand extends GuildSubCommand
{
    public AcceptSubCommand(Guilds module)
    {
        super(module, command("accept")
                .description("Accept a guild invite")
                .usage("/guild <command> <guild>")
                .permission("plex.guilds.accept")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        return player != null && guildOf(player) == null;
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        if (guildOf(player) != null)
        {
            return messageComponent("alreadyInGuild");
        }
        UUID playerUuid = player.getUniqueId();
        module.getGuildRepository().invitesFor(playerUuid).whenComplete((invites, throwable) ->
        {
            if (throwable != null)
            {
                module.getLogger().error("Failed to load invites for {}", playerUuid, throwable);
                player.sendMessage(messageComponent("guildStorageFailed"));
                return;
            }
            GuildInviteEntity invite = findInvite(invites, arguments(first, remaining));
            if (invite == null)
            {
                player.sendMessage(messageComponent("guildNotValidInvite"));
                return;
            }
            Guild guild = module.getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid())).orElse(null);
            if (invite.getExpiresAt() < Instant.now().toEpochMilli() || guild == null)
            {
                module.getGuildRepository().deleteInvite(UUID.fromString(invite.getGuildUuid()), playerUuid);
                player.sendMessage(messageComponent("guildInviteExpired"));
                return;
            }
            join(player, guild);
        });
        return null;
    }

    private GuildInviteEntity findInvite(List<GuildInviteEntity> invites, String guildName)
    {
        return invites.stream()
                .filter(invite -> module.getGuildHolder().guildById(UUID.fromString(invite.getGuildUuid()))
                        .map(guild -> guild.getName().equalsIgnoreCase(guildName))
                        .orElse(false))
                .findFirst()
                .orElse(null);
    }

    private void join(Player player, Guild guild)
    {
        // The player may have joined or created a guild while the invites loaded.
        if (guildOf(player) != null)
        {
            player.sendMessage(messageComponent("alreadyInGuild"));
            return;
        }
        String playerName = player.getName();
        module.getGuildMutationService().addMember(guild, player.getUniqueId(), true).whenComplete((unused, throwable) ->
        {
            if (throwable != null)
            {
                player.sendMessage(failureMessage(throwable, null));
                return;
            }
            module.broadcastToGuild(guild, messageComponent("guildMemberJoined", Placeholder.unparsed("player", playerName)));
        });
    }
}
