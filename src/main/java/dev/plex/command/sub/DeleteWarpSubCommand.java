package dev.plex.command.sub;

import com.google.common.collect.Lists;
import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "deletewarp", aliases = "delwarp,removewarp", usage = "/guild <command> <name>", description = "Deletes a guild warp")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.deletewarp")
public class DeleteWarpSubCommand extends SubCommand
{
    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        if (args.length == 0)
        {
            return usage();
        }

        assert player != null;
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
        member.getGuild().ifPresentOrElse(guild ->
                {
                    if (!guild.isModerator(member))
                    {
                        send(player, messageComponent("guildNotMod"));
                        return;
                    }

                    String name = StringUtils.join(args, " ").toLowerCase();
                    guild.getWarp(name).ifPresentOrElse(guildWarp ->
                            {
                                guild.deleteWarp(guildWarp);
                                send(player, messageComponent("guildWarpRemoved", name));
                            },
                            () -> send(player, messageComponent("guildWarpNotFound", name)));
                },
                () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1 && silentCheckPermission(sender, getPermission()))
        {
            GuildMember member = Guilds.get().getMemberData().getMember((Player) sender).orElseThrow();
            List<String> names = Lists.newArrayList();
            member.getGuild().ifPresent(guild -> names.addAll(guild.getWarpNames()));
            return names;
        }
        return Collections.emptyList();
    }
}
