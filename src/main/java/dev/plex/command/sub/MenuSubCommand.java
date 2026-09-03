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
    public MenuSubCommand(Guilds module)
    {
        super(module, command("menu")
                .description("Opens the guild management menu")
                .usage("/guild <command>")
                .aliases("gui,panel")
                .permission("plex.guilds.menu")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(
                guild -> module.getGuildMenuListener().openHome(player, guild),
                () -> player.sendMessage(messageComponent("guildNotFound"))
        );
        return null;
    }
}
