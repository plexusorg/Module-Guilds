package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.data.Member;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChatSubCommand extends GuildSubCommand
{
    public ChatSubCommand()
    {
        super(command("chat")
                .description("Toggles guild chat or sends a guild chat message")
                .usage("/guild <command> [message]")
                .permission("plex.guilds.chat")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    protected Component execute(@NotNull CommandSender commandSender, @Nullable Player player, @NotNull String[] args)
    {
        assert player != null;
        Guilds.get().getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (args.length == 0)
            {
                Member member = guild.getMember(player.getUniqueId());
                member.setChat(!member.isChat());
                send(player, messageComponent("guildChatToggled", BooleanUtils.toStringOnOff(member.isChat())));
                return;
            }
            Guilds.get().broadcastToGuild(guild, messageComponent("guildChatMessage", player.getName(), StringUtils.join(args, " ")));
            if (Guilds.get().getConfig().getBoolean("guilds.log-chat-message"))
            {
                send(Bukkit.getConsoleSender(),
                        messageComponent("guildChatConsoleLog", guild.getName(), guild.getGuildUuid(), player.getName(), StringUtils.join(args, " ")));
            }
        }, () -> send(player, messageComponent("guildNotFound")));
        return null;
    }

    @Override
    protected @NotNull List<String> suggestions(@NotNull CommandSender commandSender, @NotNull String s, @NotNull String[] strings) throws IllegalArgumentException
    {
        return Collections.emptyList();
    }
}
