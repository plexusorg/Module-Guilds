package dev.plex.guild;

import dev.plex.Guilds;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.UUID;

@Data
public class GuildMember
{
    private final UUID uuid;
    private int id = 0;
    private boolean chat = false;
    private boolean prefix = true;

    public OfflinePlayer getPlayer()
    {
        return Bukkit.getOfflinePlayer(uuid);
    }

    public Optional<Guild> getGuild()
    {
        return Guilds.get().getGuildData().getGuildByMember(this);
    }
}
