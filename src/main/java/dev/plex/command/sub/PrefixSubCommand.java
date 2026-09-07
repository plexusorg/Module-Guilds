package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.util.GuildUtil;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PrefixSubCommand extends GuildSubCommand
{
    public PrefixSubCommand(Guilds module)
    {
        super(module, command("prefix")
                .description("Sets the guild's default prefix")
                .usage("/guild <command> <prefix>")
                .aliases("tag,settag,setprefix")
                .permission("plex.guilds.prefix")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        if (first == null)
        {
            return usage();
        }
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (!guild.isOwner(player.getUniqueId()))
            {
                player.sendMessage(messageComponent("guildNotOwner"));
                return;
            }
            if (first.equalsIgnoreCase("clear") || first.equalsIgnoreCase("off"))
            {
                module.getGuildMutationService().updatePrefix(guild, null).whenComplete((unused, throwable) ->
                {
                    if (throwable != null)
                    {
                        player.sendMessage(messageComponent("guildStorageFailed"));
                        return;
                    }
                    player.sendMessage(messageComponent("guildPrefixCleared"));
                });
                return;
            }
            String prefix = arguments(first, remaining);
            module.getGuildMutationService().updatePrefix(guild, prefix).whenComplete((unused, throwable) ->
            {
                if (throwable != null)
                {
                    player.sendMessage(messageComponent("guildStorageFailed"));
                    return;
                }
                player.sendMessage(messageComponent("guildPrefixSet", Placeholder.component("prefix", GuildUtil.miniMessageWithoutEvents(guild.getPrefix()))));
            });
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
