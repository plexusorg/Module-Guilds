package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MenuSubCommand extends GuildSubCommand
{
    public MenuSubCommand()
    {
        super(command("menu")
                .description("Opens the guild management menu")
                .usage("/guild <command>")
                .aliases("gui,panel")
                .permission("plex.guilds.menu")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(
                guild -> Guilds.get().getGuildMenuListener().openHome(player, guild),
                () -> send(player, messageComponent("guildNotFound"))
        );
        return null;
    }
}
