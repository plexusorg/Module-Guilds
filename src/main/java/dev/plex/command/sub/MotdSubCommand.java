package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@CommandParameters(name = "motd", usage = "/g <command> <set <message> | clear>", description = "Set or clear your guild's MOTD")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.motd")
public class MotdSubCommand extends SubCommand
{

    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        assert player != null;
        if (args.length > 0)
        {
            GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
            if (member.getGuild().isEmpty())
            {
                return messageComponent("guildNotFound");
            }

            Guild guild = member.getGuild().get();
            if (!guild.isModerator(member))
            {
                return messageComponent("guildNotMod");
            }

            if (args.length == 1 && args[0].equalsIgnoreCase("clear"))
            {
                guild.setMotd(null);
                return messageComponent("guildMotdCleared");
            }

            if (args.length > 1 && args[0].equalsIgnoreCase("set"))
            {
                String message = StringUtils.join(args, " ", 1, args.length);
                if (MiniMessage.miniMessage().stripTags(message).length() > 256)
                {
                    return messageComponent("guildMotdExceededLimit");
                }

                guild.setMotd(message);
                return messageComponent("guildMotdSet", mmString(message));
            }
        }
        return usage();
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        if (args.length == 1)
        {
            return Arrays.asList("set", "clear");
        }
        return Collections.emptyList();
    }
}
