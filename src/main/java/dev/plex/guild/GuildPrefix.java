package dev.plex.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;

public final class GuildPrefix
{
    private static final Pattern TEXT = Pattern.compile("[A-Za-z0-9\\[\\]]*");
    private static final Pattern SHAPE = Pattern.compile("(?:[A-Za-z0-9\uFFFC]{1,5}|\\[[A-Za-z0-9\uFFFC]{1,5}\\])");
    private static final GuildPrefix EMPTY = new GuildPrefix(null, Component.empty(), null);

    private final String text;
    private final Component component;
    private final String key;

    private GuildPrefix(String text, Component component, String key)
    {
        this.text = text;
        this.component = component;
        this.key = key;
    }

    public static GuildPrefix empty()
    {
        return EMPTY;
    }

    public static GuildPrefix parse(String input, Function<String, Component> parser)
    {
        if (input == null)
        {
            return empty();
        }
        StringBuilder visible = new StringBuilder();
        Component safe = visual(parser.apply(input), visible);
        if (!SHAPE.matcher(visible).matches())
        {
            throw new IllegalArgumentException("Use 1-5 letters, digits, sprites or heads, with optional paired brackets");
        }
        String key = visible.toString().replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        if (key.isEmpty())
        {
            throw new IllegalArgumentException("A guild prefix must contain a letter or digit");
        }
        return new GuildPrefix(MiniMessage.miniMessage().serialize(safe), safe, key);
    }

    private static Component visual(Component component, StringBuilder visible)
    {
        if (component instanceof TextComponent text)
        {
            if (!TEXT.matcher(text.content()).matches())
            {
                throw new IllegalArgumentException("Guild prefix text supports ASCII letters, digits and square brackets only");
            }
            visible.append(text.content());
        }
        else if (component instanceof ObjectComponent object
                && (object.contents() instanceof SpriteObjectContents || object.contents() instanceof PlayerHeadObjectContents))
        {
            visible.append('\uFFFC');
            component = object.fallback(null);
        }
        else
        {
            throw new IllegalArgumentException("Guild prefixes support text, sprites and player heads only");
        }
        List<Component> children = new ArrayList<>();
        for (Component child : component.children())
        {
            children.add(visual(child, visible));
        }
        return component.clickEvent(null).hoverEvent(null).insertion(null)
                .decoration(TextDecoration.OBFUSCATED, TextDecoration.State.FALSE)
                .children(children);
    }

    public String text()
    {
        return text;
    }

    public Component component()
    {
        return component;
    }

    public String key()
    {
        return key;
    }
}
