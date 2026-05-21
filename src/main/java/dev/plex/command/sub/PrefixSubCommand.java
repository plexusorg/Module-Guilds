package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.GuildUtil;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixSubCommand extends SimplePlexCommand
{
    public PrefixSubCommand()
    {
        super(command("prefix")
                .description("Sets the guild's default prefix")
                .usage("/guild <command> <prefix>")
                .aliases("tag,settag,setprefix")
                .permission("plex.guilds.prefix")
                .source(RequiredCommandSource.IN_GAME)
                .build());
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
            send(player, messageComponent("guildPrefixSet", GuildUtil.miniMessageWithoutEvents(guild.getPrefix())));
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
