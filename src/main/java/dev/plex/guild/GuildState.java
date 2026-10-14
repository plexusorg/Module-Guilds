package dev.plex.guild;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import dev.plex.Guilds;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildTimeMode;
import dev.plex.guild.data.GuildWeatherMode;
import dev.plex.guild.data.Member;
import dev.plex.util.CustomLocation;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

public final class GuildState
{
    private static final Gson GSON = new Gson();

    private GuildState()
    {
    }

    public static JsonElement value(Object value)
    {
        if (value instanceof JsonElement element)
        {
            return element.deepCopy();
        }
        if (value instanceof Guest guest)
        {
            return object("player", guest.playerUuid(), "editing", guest.editing(), "expires", guest.expiresAt().toEpochMilli());
        }
        return GSON.toJsonTree(value);
    }

    public static JsonObject object(Object... pairs)
    {
        JsonObject result = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2)
        {
            result.add((String) pairs[i], value(pairs[i + 1]));
        }
        return result;
    }

    public static JsonObject snapshot(Guild guild)
    {
        JsonObject members = new JsonObject();
        guild.getMembers().forEach(member -> members.addProperty(member.getUuid().toString(), member.getRole().name()));
        JsonObject joined = new JsonObject();
        guild.getMembers().forEach(member -> joined.addProperty(member.getUuid().toString(), member.getJoinedAt()));
        JsonObject guests = new JsonObject();
        guild.getGuests().values().stream().filter(guest -> guest.isActive(Instant.now()))
                .forEach(guest -> guests.add(guest.playerUuid().toString(), value(guest)));
        return object("uuid", guild.getGuildUuid(), "name", guild.getName(), "owner", guild.getOwnerUuid(),
                "created", guild.getCreatedAt().toInstant().toEpochMilli(), "prefix", guild.getPrefix(),
                "members", members, "joined", joined, "guests", guests, "spawn", guild.getSpawn(), "warps", guild.getWarps(),
                "time", guild.getTimeMode(), "weather", guild.getWeatherMode());
    }

    public static Guild guild(JsonElement state, Guilds module)
    {
        JsonObject data = state.getAsJsonObject();
        Guild guild = new Guild(UUID.fromString(data.get("uuid").getAsString()),
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(data.get("created").getAsLong()), module.getZoneId()));
        guild.setName(data.get("name").getAsString());
        guild.setOwnerUuid(UUID.fromString(data.get("owner").getAsString()));
        guild.setPrefix(GuildPrefix.parse(text(data.get("prefix")), module.api().messages()::playerText));
        guild.setSpawn(location(data.get("spawn")));
        guild.setTimeMode(GuildTimeMode.valueOf(data.get("time").getAsString()));
        guild.setWeatherMode(GuildWeatherMode.valueOf(data.get("weather").getAsString()));
        data.getAsJsonObject("members").entrySet().forEach(entry ->
                guild.addMember(new Member(UUID.fromString(entry.getKey()), GuildRole.valueOf(entry.getValue().getAsString()),
                        data.getAsJsonObject("joined").get(entry.getKey()).getAsLong())));
        data.getAsJsonObject("warps").entrySet().forEach(entry -> guild.getWarps().put(entry.getKey(), location(entry.getValue())));
        data.getAsJsonObject("guests").entrySet().forEach(entry ->
        {
            Guest guest = guest(entry.getValue());
            if (guest.isActive(Instant.now()))
            {
                guild.getGuests().put(guest.playerUuid(), guest);
            }
        });
        return guild;
    }

    public static String text(JsonElement value)
    {
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }

    public static CustomLocation location(JsonElement value)
    {
        return GSON.fromJson(value, CustomLocation.class);
    }

    public static Guest guest(JsonElement value)
    {
        if (value == null || value instanceof JsonNull)
        {
            return null;
        }
        JsonObject data = value.getAsJsonObject();
        return new Guest(UUID.fromString(data.get("player").getAsString()), data.get("editing").getAsBoolean(),
                Instant.ofEpochMilli(data.get("expires").getAsLong()));
    }
}
