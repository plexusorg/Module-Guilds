package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.SubCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.GuildMember;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@CommandParameters(name = "create", usage = "/guild <command> <name>", description = "Create a brand new guild")
@CommandPermissions(source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.create")
public class CreateSubCommand extends SubCommand
{
    @Override
    public Component run(@NotNull CommandSender sender, @Nullable Player player, @NotNull String[] args)
    {
        if (args.length > 0)
        {
            assert player != null;
            GuildMember member = Guilds.get().getMemberData().getMember(player).orElseThrow();
            if (member.getGuild().isPresent())
            {
                return messageComponent("alreadyInGuild");
            }

            String name = StringUtils.join(args, " ");
            if (!StringUtils.isAlphanumericSpace(name))
            {
                return messageComponent("guildAlphanumericName");
            }

            Guild guild = Guild.create(member, name);
            return mmString("Successfully created guild named " + guild.getName());
        }

        return usage();
    }

    @Override
    public @NotNull List<String> smartTabComplete(@NotNull CommandSender sender, @NotNull String s, @NotNull String[] args) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
