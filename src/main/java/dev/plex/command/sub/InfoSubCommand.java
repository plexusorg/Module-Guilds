package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.cache.DataUtils;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.rank.enums.Rank;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@CommandParameters(name = "info", aliases = "information", usage = "/guild <command>")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.info")
public class InfoSubCommand extends PlexCommand
{
    public InfoSubCommand()
    {
        super(false);
    }

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm:ss a");

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] strings)
    {
        assert player != null;
        CompletableFuture.runAsync(() ->
        {
            Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
            {
                send(player, mmString("<gradient:yellow:gold>====<aqua>" + guild.getName() + "<gradient:yellow:gold>===="));
                send(player, mmString(""));
                try
                {
                    send(player, mmString("<gold>Owner: <yellow>" + DataUtils.getPlayer(guild.getOwner().getUuid(), false).getName()));
                } catch (NullPointerException e)
                {
                    send(player, mmString("<gold>Owner: <yellow>Unable to load cache..."));
                }
                send(player, mmString("<gold>Members (" + guild.getMembers().size() + "): " + StringUtils.join(guild.getMembers().stream().map(member -> DataUtils.getPlayer(member.getUuid(), false).getName()).toList(), ", ")));
                send(player, mmString("<gold>Moderators (" + guild.getModerators().size() + "): " + StringUtils.join(guild.getModerators().stream().map(uuid -> DataUtils.getPlayer(uuid, false).getName()).toList(), ", ")));
                send(player, mmString("<gold>Prefix: " + (guild.getPrefix() == null ? "N/A" : guild.getPrefix())));
                send(player, mmString("<gold>Created At: " + formatter.format(guild.getCreatedAt())));
            }, () -> send(player, messageComponent("guildNotFound")));
        });
        return null;
    }
}
