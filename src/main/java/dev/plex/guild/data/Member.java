package dev.plex.guild.data;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

@Data
public class Member
{
    private final UUID uuid;
    private GuildRole role;
    private boolean chat;

    public Member(UUID uuid)
    {
        this(uuid, GuildRole.MEMBER);
    }

    public Member(UUID uuid, GuildRole role)
    {
        this.uuid = uuid;
        this.role = role;
    }

    public Player getPlayer()
    {
        return Bukkit.getPlayer(this.uuid);
    }

    public Optional<? extends PlexPlayerView> getPlexPlayer()
    {
        return Guilds.get().api().players().player(this.uuid);
    }

    public String getName()
    {
        return getPlexPlayer()
                .map(PlexPlayerView::name)
                .orElse(this.uuid.toString());
    }
}
