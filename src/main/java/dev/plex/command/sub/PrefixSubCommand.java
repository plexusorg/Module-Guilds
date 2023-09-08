package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.minimessage.SafeMiniMessage;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "prefix", aliases = "tag,settag,setprefix", usage = "/guild <command> <prefix>", description = "Sets the guild's default prefix")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.prefix")
public class PrefixSubCommand extends PlexCommand
{
    public PrefixSubCommand()
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
            if (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("off"))
            {
                guild.setPrefix(null);
                send(player, messageComponent("guildPrefixCleared"));
                return;
            }
            guild.setPrefix(StringUtils.join(args, " "));
            send(player, messageComponent("guildPrefixSet", SafeMiniMessage.mmDeserializeWithoutEvents(guild.getPrefix())));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
