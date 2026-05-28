package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
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

public class InfoSubCommand extends GuildSubCommand
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
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        CompletableFuture.runAsync(() ->
        {
            Guild guild = resolveGuild(player, args);
            if (guild == null)
            {
                send(player, messageComponent("guildNotFound"));
                return;
            }
            {
                send(player, mmString("<gradient:yellow:gold>====<aqua>" + guild.getName() + "<gradient:yellow:gold>===="));
                send(player, mmString(""));
                send(player, mmString("<gold>Owner: <yellow>" + playerName(guild.getOwnerUuid())));
                List<String> members = guild.getMembers().stream().filter(member -> !member.getUuid().equals(guild.getOwnerUuid())).map(member -> playerName(member.getUuid())).toList();
                send(player, mmString("<gold>Members (" + members.size() + "): " + StringUtils.join(members, ", ")));
                send(player, mmString("<gold>Prefix: " + (guild.getPrefix() == null ? "N/A" : guild.getPrefix())));
                send(player, mmString("<gold>Created At: " + formatter.format(guild.getCreatedAt())));
            }
        }, Guilds.get().api().scheduler().asyncExecutor());
        return null;
    }

    private Guild resolveGuild(Player sender, String[] args)
    {
        if (args.length == 0)
        {
            return Guilds.get().getGuildHolder().guild(sender.getUniqueId()).orElse(null);
        }
        PlexPlayerView player = api().players().byName(args[0]).orElse(null);
        if (player != null)
        {
            Guild guild = Guilds.get().getGuildHolder().guild(player.uuid()).orElse(null);
            if (guild != null)
            {
                return guild;
            }
        }
        return Guilds.get().getGuildHolder().guildByName(StringUtils.join(args, " ")).orElse(null);
    }

    private String playerName(java.util.UUID uuid)
    {
        return api().players().player(uuid).map(PlexPlayerView::name).orElse(uuid.toString());
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
