package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.rank.enums.Rank;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "home", aliases = "spawn", usage = "/guild <command>", description = "Teleports to the guild home")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.home")
public class HomeSubCommand extends PlexCommand
{
    public HomeSubCommand()
    {
        super(false);
    }
    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild -> {
            if (guild.getHome() == null)
            {
                send(player, messageComponent("guildHomeNotFound"));
                return;
            }
            player.teleportAsync(guild.getHome().toLocation());
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
