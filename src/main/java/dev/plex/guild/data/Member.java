package dev.plex.guild.data;

import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

@Data
public class Member
{
    private final UUID uuid;
    private Rank rank;
    private boolean chat, prefix;

    public Player getPlayer()
    {
        return Bukkit.getPlayer(this.uuid);
    }
}
