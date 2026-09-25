package dev.plex.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser
{
    private static final Pattern FORMAT = Pattern.compile("([0-9]{1,9})([mhd])");

    private DurationParser()
    {
    }

    /** Parses a positive whole number followed by m, h, or d. Returns null when the text is invalid. */
    public static Duration parse(String text)
    {
        if (text == null)
        {
            return null;
        }
        Matcher matcher = FORMAT.matcher(text.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches())
        {
            return null;
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0)
        {
            return null;
        }
        return switch (matcher.group(2))
        {
            case "m" -> Duration.ofMinutes(amount);
            case "h" -> Duration.ofHours(amount);
            default -> Duration.ofDays(amount);
        };
    }
}
