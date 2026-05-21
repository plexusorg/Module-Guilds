package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.command.SimplePlexCommand;
import dev.plex.command.source.RequiredCommandSource;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class InfoSubCommand extends SimplePlexCommand
{
    public InfoSubCommand()
    {
        super(command("info")
                .description("Shows the guild's information")
                .usage("/guild <command>")
                .aliases("information")
                .permission("plex.guilds.info")
                .source(RequiredCommandSource.IN_GAME)
                .build());
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
                send(player, mmString("<gold>Owner: <yellow>" + playerName(guild.getOwner().getUuid())));
                List<String> members = guild.getMembers().stream().filter(member -> !member.getUuid().equals(guild.getOwner().getUuid())).map(member -> playerName(member.getUuid())).toList();
                send(player, mmString("<gold>Members (" + members.size() + "): " + StringUtils.join(members, ", ")));
                send(player, mmString("<gold>Moderators (" + guild.getModerators().size() + "): " + StringUtils.join(guild.getModerators().stream().map(this::playerName).toList(), ", ")));
                send(player, mmString("<gold>Prefix: " + (guild.getPrefix() == null ? "N/A" : guild.getPrefix())));
                send(player, mmString("<gold>Created At: " + formatter.format(guild.getCreatedAt())));
            }, () -> send(player, messageComponent("guildNotFound")));
        }, Guilds.get().api().scheduler().asyncExecutor());
        return null;
    }

    private String playerName(java.util.UUID uuid)
    {
        return api().players().byUuid(uuid).map(PlexPlayerView::name).orElse("Unable to load cache...");
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
