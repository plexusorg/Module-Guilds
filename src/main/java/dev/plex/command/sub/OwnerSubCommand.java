package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OwnerSubCommand extends GuildSubCommand
{
    public OwnerSubCommand(Guilds module)
    {
        super(module, command("owner")
                .description("Sets the guild owner")
                .usage("/guild <command> <player name>")
                .aliases("setowner,promote")
                .permission("plex.guilds.owner")
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
        java.util.UUID playerUuid = player.getUniqueId();
        module.getGuildHolder().guild(playerUuid).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(playerUuid))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            Member memberSender = guild.getMember(playerUuid);
            module.api().players().byName(first).whenComplete((result, failure) ->
            {
                if (failure != null)
                {
                    module.getLogger().error("Failed to look up player {}", first, failure);
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                if (result.isEmpty())
                {
                    player.sendMessage(messageComponent("playerNotFound"));
                    return;
                }
                PlexPlayerView plexPlayer = result.get();
                Member member = guild.getMember(plexPlayer.uuid());
                if (member == null)
                {
                    player.sendMessage(messageComponent("guildMemberNotFound"));
                    return;
                }
                module.getGuildMutationService().transferOwnership(guild, member, playerUuid, memberSender).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    player.sendMessage(messageComponent("guildOwnerSet", Placeholder.parsed("player", plexPlayer.name())));
                });
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
