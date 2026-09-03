package dev.plex.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class GuildUtil
{
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static Component miniMessageWithoutEvents(String text)
    {
        return MINI_MESSAGE.deserialize(text).clickEvent(null).hoverEvent(null);
    }

}
