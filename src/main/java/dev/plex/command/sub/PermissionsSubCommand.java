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
    public PermissionsSubCommand(Guilds module)
    {
        super(module, command("permissions")
                .description("Opens the rank permissions GUI")
                .usage("/guild <command>")
                .aliases("perms")
                .permission("plex.guilds.permissions")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            module.getRankPermissionMenuListener().openRankList(player, guild);
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }
}
