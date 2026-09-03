package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HomeSubCommand extends GuildSubCommand
{
    public HomeSubCommand(Guilds module)
    {
        super(module, command("home")
                .description("Teleports to the guild home")
                .usage("/guild <command>")
                .aliases("spawn")
                .permission("plex.guilds.home")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (guild.getHome() == null)
            {
                player.sendMessage(messageComponent("guildHomeNotFound"));
                return;
            }
            player.teleportAsync(guild.getHome().toLocation());
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
