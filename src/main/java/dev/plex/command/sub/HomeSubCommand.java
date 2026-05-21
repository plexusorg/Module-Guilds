package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HomeSubCommand extends SimplePlexCommand
{
    public HomeSubCommand()
    {
        super(command("home")
                .description("Teleports to the guild home")
                .usage("/guild <command>")
                .aliases("spawn")
                .permission("plex.guilds.home")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (guild.getHome() == null)
            {
                send(player, messageComponent("guildHomeNotFound"));
                return;
            }
            player.teleportAsync(guild.getHome().toLocation());
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
