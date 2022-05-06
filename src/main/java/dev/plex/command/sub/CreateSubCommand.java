package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.rank.enums.Rank;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@CommandParameters(name = "create", aliases = "make", usage = "/guild <command> <name>")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.create")
public class CreateSubCommand extends PlexCommand
{
    public CreateSubCommand()
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
        if (Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).isPresent())
        {
            return messageComponent("alreadyInGuild");
        }
        Guilds.get().getSqlGuildManager().insertGuild(Guild.create(player, StringUtils.join(args, " "))).whenComplete((guild, throwable) -> {
            Guilds.get().getGuildHolder().addGuild(guild);
            send(player, mmString("Created guild named " + guild.getName()));
        });
        return null;
    }
}
