package dev.plex.util;

import dev.plex.Guilds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class GuildUtil
{
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static void throwExceptionSync(Throwable throwable)
    {
        if (Guilds.get() != null && Guilds.get().getLogger() != null)
        {
            Guilds.get().getLogger().error("Guild module task failed", throwable);
            return;
        }
        throwable.printStackTrace();
    }

    public static Component miniMessageWithoutEvents(String text)
    {
        return MINI_MESSAGE.deserialize(text).clickEvent(null).hoverEvent(null);
    }

}
