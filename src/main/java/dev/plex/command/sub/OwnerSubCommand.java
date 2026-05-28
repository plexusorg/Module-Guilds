package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OwnerSubCommand extends SimplePlexCommand
{
    public OwnerSubCommand()
    {
        super(command("owner")
                .description("Sets the guild owner")
                .usage("/guild <command> <player name>")
                .aliases("setowner,promote")
                .permission("plex.guilds.owner")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        if (args.length == 0)
        {
            return usage();
        }
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            Member memberSender = guild.getMember(player.getUniqueId());
            PlexPlayerView plexPlayer = api().players().byName(args[0]).orElse(null);
            if (plexPlayer == null)
            {
                send(player, messageComponent("playerNotFound"));
                return;
            }
            Member member = guild.getMember(plexPlayer.uuid());
            if (member == null)
            {
                send(player, messageComponent("guildMemberNotFound"));
                return;
            }
            Guilds.get().getGuildRepository().transferOwner(guild.getGuildUuid(), member.getUuid(), player.getUniqueId()).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    send(player, messageComponent("guildStorageFailed"));
                    return;
                }
                guild.setOwnerUuid(member.getUuid());
                member.setRole(GuildRole.OWNER);
                if (memberSender != null)
                {
                    memberSender.setRole(GuildRole.MEMBER);
                }
                send(player, messageComponent("guildOwnerSet", plexPlayer.name()));
            });
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
