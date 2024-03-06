package dev.plex.command.sub;

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

@CommandParameters(name = "setwarp", aliases = "createwarp", usage = "/guild <command> <name>", description = "Creates a new guild warp at your location with a specified name")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.setwarp")
public class SetWarpSubCommand extends SubCommand
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
                    if (name.length() > 16)
                    {
                        send(player, mmString("<red>The max length of a warp name is 16 characters!"));
                        return;
                    }

                    if (!StringUtils.isAlphanumericSpace(name))
                    {
                        send(player, messageComponent("guildWarpAlphanumeric"));
                        return;
                    }

                    guild.getWarp(name).ifPresentOrElse(guildWarp -> send(player, messageComponent("guildWarpExists", name)),
                            () ->
                            {
                                guild.createWarp(name, player.getLocation());
                                send(player, messageComponent("guildWarpCreated", name));
                            });
                },
                () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
