package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.CustomLocation;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetHomeSubCommand extends SimplePlexCommand
{
    public SetHomeSubCommand()
    {
        super(command("sethome")
                .description("Sets the guild's home")
                .usage("/guild <command>")
                .aliases("setspawn")
                .permission("plex.guilds.sethome")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.getOwner().getUuid().equals(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("unset") || args[0].equalsIgnoreCase("clear")))
            {
                if (guild.getHome() == null)
                {
                    send(player, messageComponent("guildHomeNotFound"));
                    return;
                }
                guild.setHome(null);
                send(player, messageComponent("guildHomeRemoved"));
                return;
            }
            guild.setHome(CustomLocation.fromLocation(player.getLocation()));
            send(player, messageComponent("guildHomeSet"));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
