package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PermissionsSubCommand extends GuildSubCommand
{
    public PermissionsSubCommand()
    {
        super(command("permissions")
                .description("Opens the rank permissions GUI")
                .usage("/guild <command>")
                .aliases("perms")
                .permission("plex.guilds.permissions")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                send(player, messageComponent("guildNotOwner"));
                return;
            }
            Guilds.get().getRankPermissionMenuListener().openRankList(player, guild);
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
