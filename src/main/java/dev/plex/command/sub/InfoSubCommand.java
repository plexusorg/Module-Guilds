package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@CommandParameters(name = "info", usage = "/guild <command> [name]", description = "Shows your or a specified guild's information")
@CommandPermissions(permission = "plex.guilds.info")
public class InfoSubCommand extends SubCommand
{
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    @Override
    public Component run(CommandSender sender, Player player, String[] args)
    {
        if (args.length == 0)
        {
            if (isConsole(sender))
            {
                return messageComponent("noPermissionConsole");
            }

            assert player != null;
            GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
            member.getGuild().ifPresentOrElse(guild ->
                    {
                        Component info = Component.empty()
                                .append(mmString("<gradient:yellow:gold>====<aqua>" + guild.getName() + "<gradient:yellow:gold>===="))
                                .appendNewline()
                                .append(mmString("<gold>UUID: <yellow>" + guild.getUuid().toString()))
                                .appendNewline()
                                .append(mmString("<gold>Display Name:</gold> " + guild.getDisplayName()))
                                .appendNewline()
                                .append(mmString("<gold>Owner: <yellow>" + guild.getOwner().getPlayer().getName()))
                                .appendNewline()
                                .append(mmString("<gold>Members (" + guild.getMemberNames().size() + "): <yellow>" + StringUtils.join(guild.getMemberNames(), ", ")))
                                .appendNewline()
                                .append(mmString("<gold>Moderators (" + guild.getModerators().size() + "): <yellow>" + StringUtils.join(guild.getModeratorNames(), ", ")))
                                .appendNewline()
                                .append(mmString("<gold>Privacy: <yellow>" + guild.getPrivacy().toString()))
                                .appendNewline()
                                .append(mmString("<gold>Created At: <yellow>" + dateFormat.format(guild.getCreatedAt())));
                        send(player, info);
                    },
                    () -> send(player, messageComponent("guildNotFound")));
        }
        else
        {
            String name = StringUtils.join(args, " ");
            Optional<Guild> optionalGuild;

            Player target = Bukkit.getPlayer(name);
            if (target != null)
            {
                optionalGuild = Guilds.get().getMemberData().getMember(target).orElseThrow().getGuild();
            }
            else
            {
                optionalGuild = Guilds.get().getGuildData().getGuildByName(name);
            }

            optionalGuild.ifPresentOrElse(guild ->
                    {
                        Component info = Component.empty()
                                .append(mmString("<gradient:yellow:gold>====<aqua>" + guild.getName() + "<gradient:yellow:gold>===="))
                                .appendNewline()
                                .append(mmString("<gold>UUID: <yellow>" + guild.getUuid().toString()))
                                .appendNewline()
                                .append(mmString("<gold>Display Name:</gold> " + guild.getDisplayName()))
                                .appendNewline()
                                .append(mmString("<gold>Owner: <yellow>" + guild.getOwner().getPlayer().getName()))
                                .appendNewline()
                                .append(mmString("<gold>Members (" + guild.getMemberNames().size() + "): <yellow>" + StringUtils.join(guild.getMemberNames(), ", ")))
                                .appendNewline()
                                .append(mmString("<gold>Moderators (" + guild.getModerators().size() + "): <yellow>" + StringUtils.join(guild.getModeratorNames(), ", ")))
                                .appendNewline()
                                .append(mmString("<gold>Privacy: <yellow>" + guild.getPrivacy().toString()))
                                .appendNewline()
                                .append(mmString("<gold>Created At: <yellow>" + dateFormat.format(guild.getCreatedAt())));
                        send(sender, info);
                    },
                    () -> send(sender, messageComponent("guildNotExist", name)));
        }
        return null;
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
