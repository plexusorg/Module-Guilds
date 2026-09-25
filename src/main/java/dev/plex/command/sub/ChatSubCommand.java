package dev.plex.command.sub;

import dev.plex.Guilds;
import dev.plex.command.source.RequiredCommandSource;
import dev.plex.guild.Guild;
import dev.plex.guild.data.Member;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.apache.commons.lang3.BooleanUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ChatSubCommand extends GuildSubCommand
{
    public ChatSubCommand(Guilds module)
    {
        super(module, command("chat")
                .description("Toggle guild chat or send a message")
                .usage("/guild <command> [message]")
                .permission("plex.guilds.chat")
                .source(RequiredCommandSource.IN_GAME)
                .build());
    }

    @Override
    public boolean isAvailable(@Nullable Player player)
    {
        return guildOf(player) != null;
    }

    @Override
    public Component executeSubCommand(@NotNull CommandSender commandSender, @Nullable Player player, @Nullable String first, @Nullable String remaining)
    {
        assert player != null;
        Guild guild = guildOf(player);
        Member member = guild == null ? null : guild.getMember(player.getUniqueId());
        if (member == null)
        {
            return messageComponent("guildNotFound");
        }
        if (first == null)
        {
            member.setChat(!member.isChat());
            return messageComponent("guildChatToggled", Placeholder.unparsed("status", BooleanUtils.toStringOnOff(member.isChat())));
        }
        module.sendChat(guild, player.getName(), module.api().messages().playerText(arguments(first, remaining)));
        return null;
    }
}
