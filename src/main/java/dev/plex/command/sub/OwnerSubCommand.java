package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.cache.DataUtils;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.data.Member;
import dev.plex.player.PlexPlayer;
import dev.plex.rank.enums.Rank;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "owner", aliases = "setowner", usage = "/guild <command> <player name>", description = "Sets the guild owner")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.owner")
public class OwnerSubCommand extends PlexCommand
{
    public OwnerSubCommand()
    {
        super(false);
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
            if (!guild.getOwner().getUuid().equals(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            Member memberSender = guild.getMember(player.getUniqueId());
            PlexPlayer plexPlayer = DataUtils.getPlayer(args[0], false);
            if (plexPlayer == null)
            {
                send(player, messageComponent("playerNotFound"));
                return;
            }
            Member member = guild.getMember(plexPlayer.getUuid());
            if (member == null)
            {
                send(player, messageComponent("guildMemberNotFound"));
                return;
            }
            guild.setOwner(member);
            guild.getMembers().remove(member);
            guild.getMembers().add(memberSender);
            send(player, messageComponent("guildOwnerSet", plexPlayer.getName()));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
