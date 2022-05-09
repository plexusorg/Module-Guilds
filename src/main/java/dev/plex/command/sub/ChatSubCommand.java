package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.PlexCommand;
import dev.plex.command.annotation.CommandParameters;
import dev.plex.command.annotation.CommandPermissions;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.data.Member;
import dev.plex.rank.enums.Rank;
import dev.plex.util.PlexLog;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@CommandParameters(name = "chat", usage = "/guild <command> [message]", description = "Toggles guild chat or sends a guild chat message")
@CommandPermissions(level = Rank.OP, source = RequiredCommandSource.IN_GAME, permission = "plex.guilds.chat")
public class ChatSubCommand extends PlexCommand
{
    public ChatSubCommand()
    {
        super(false);
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().getGuild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (args.length == 0)
            {
                Member member = guild.getMember(player.getUniqueId());
                member.setChat(!member.isChat());
                send(player, messageComponent("guildChatToggled", BooleanUtils.toStringOnOff(member.isChat())));
                return;
            }
            guild.getMembers().stream().map(Member::getPlayer).filter(Objects::nonNull).forEach(player1 ->
            {
                send(player1, messageComponent("guildChatMessage", player.getName(), StringUtils.join(args, " ")));
            });
            if (Guilds.get().getConfig().isBoolean("guilds.log-chat-message"))
            {
                send(Bukkit.getConsoleSender(), messageComponent("guildChatConsoleLog", guild.getName(), guild.getGuildUuid(), player.getName(), StringUtils.join(args, " ")));
            }
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }
}
