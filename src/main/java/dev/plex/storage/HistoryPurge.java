package dev.plex.storage;

/** A whole-world Oasis history purge up to untilAt (epoch milliseconds) that Oasis still has to do. */
public record HistoryPurge(String world, long untilAt)
{
}
