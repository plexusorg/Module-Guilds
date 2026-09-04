package dev.plex.command.sub;

import static dev.plex.api.message.MessagePlaceholder.placeholder;

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
    public ChatSubCommand(Guilds module)
    {
        super(module, command("chat")
                .description("Toggles guild chat or sends a guild chat message")
                .usage("/guild <command> [message]")
                .permission("plex.guilds.chat")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        module.getGuildHolder().guild(player.getUniqueId()).ifPresentOrElse(guild ->
        {
            if (first == null)
            {
                Member member = guild.getMember(player.getUniqueId());
                member.setChat(!member.isChat());
                player.sendMessage(messageComponent("guildChatToggled", placeholder("status", BooleanUtils.toStringOnOff(member.isChat()))));
                return;
            }
            module.broadcastToGuild(guild, messageComponent("guildChatMessage", placeholder("player", player.getName()), placeholder("content", arguments(first, remaining))));
            if (module.getConfig().getBoolean("guilds.log-chat-message"))
            {
                Bukkit.getConsoleSender().sendMessage(messageComponent("guildChatConsoleLog", placeholder("guild", guild.getName()), placeholder("guild_id", guild.getGuildUuid()), placeholder("player", player.getName()), placeholder("content", arguments(first, remaining))));
            }
        }, () -> player.sendMessage(messageComponent("guildNotFound")));
        return null;
    }


}
