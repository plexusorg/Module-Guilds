package dev.plex.listener;

import dev.plex.Guilds;
import dev.plex.guild.GuildMember;
import dev.plex.util.PlexUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener extends PlexListener
{
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();
        GuildMember member = Guilds.get().getMemberData().getMember(player).orElse(Guilds.get().getMemberData().addNewMember(player));
        if (member == null)
        {
            throw new RuntimeException("Unable to obtain member data for %s".formatted(player.getName()));
        }

        member.getGuild().ifPresent(guild ->
        {
            if (guild.getMotd() != null)
            {
                player.sendMessage(PlexUtils.mmDeserialize(guild.getMotd()));
            }
        });
    }
}
