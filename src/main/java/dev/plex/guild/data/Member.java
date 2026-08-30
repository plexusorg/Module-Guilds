package dev.plex.guild.data;

import dev.plex.Guilds;
import dev.plex.api.player.PlexPlayerView;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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

    public CompletableFuture<Optional<PlexPlayerView>> getPlexPlayer()
    {
        return Guilds.get().api().players().player(this.uuid);
    }

    public CompletableFuture<String> name()
    {
        return getPlexPlayer()
                .thenApply(player -> player.map(PlexPlayerView::name).orElse(this.uuid.toString()));
    }
}
