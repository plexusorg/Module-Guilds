package dev.plex.util;

import dev.plex.Plex;
import org.bukkit.Bukkit;

public class GuildUtil
{

    public static void throwExceptionSync(Throwable throwable)
    {
        Bukkit.getScheduler().runTask(Plex.get(), () -> throwable.printStackTrace());
    }

}
