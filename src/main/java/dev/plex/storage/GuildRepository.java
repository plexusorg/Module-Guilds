package dev.plex.storage;

import dev.plex.guild.Guild;
import dev.plex.guild.GuildAction;
import dev.plex.guild.GuildSession;
import dev.plex.guild.GuildPrefix;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildTimeMode;
import dev.plex.guild.data.GuildWeatherMode;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.util.CustomLocation;
import org.jetbrains.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildRepository
{
    CompletableFuture<LoadedGuilds> loadGuilds();

    CompletableFuture<Guild> createGuild(Guild guild, GuildAction action);

    /** Marks the guild pending and deletes its members, guests, and invites. Its warps and invite history stay. */
    CompletableFuture<Void> markDeleted(UUID guildUuid, long deletedAt, @Nullable UUID deletedBy, GuildAction action);

    /** Writes the candidate over the pending guild's row and makes the guild active again. */
    CompletableFuture<Void> restoreGuild(Guild candidate, GuildAction action);

    /** Deletes the pending guild's rows and actions and records the history purge of its world. */
    CompletableFuture<Void> finalizeDeletion(UUID guildUuid, String world, long untilAt);

    CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role, long joinedAt, boolean deleteInvite, GuildAction action);

    CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid, GuildAction action);

    CompletableFuture<Void> upsertGuest(UUID guildUuid, Guest guest, GuildAction action);

    CompletableFuture<Void> removeGuest(UUID guildUuid, UUID playerUuid, GuildAction action);

    CompletableFuture<Void> updateRole(UUID guildUuid, UUID playerUuid, GuildRole role, GuildAction action);

    /** Sets the new owner and makes the old owner an officer in one transaction. */
    CompletableFuture<Void> transferOwner(UUID guildUuid, UUID newOwnerUuid, UUID oldOwnerUuid, GuildAction action);

    CompletableFuture<Void> updatePrefix(UUID guildUuid, GuildPrefix prefix, GuildAction action);

    CompletableFuture<Void> updateTimeMode(UUID guildUuid, GuildTimeMode mode, GuildAction action);

    CompletableFuture<Void> updateWeatherMode(UUID guildUuid, GuildWeatherMode mode, GuildAction action);

    CompletableFuture<Void> updateSpawn(UUID guildUuid, CustomLocation spawn, GuildAction action);

    CompletableFuture<Void> clearWorldLocations(UUID guildUuid, String worldName, GuildAction action, @Nullable HistoryPurge purge);

    CompletableFuture<Void> upsertWarp(UUID guildUuid, String name, CustomLocation location, GuildAction action);

    CompletableFuture<Void> deleteWarp(UUID guildUuid, String name, GuildAction action);

    CompletableFuture<Void> createInvite(UUID guildUuid, UUID inviterUuid, UUID inviteeUuid, Instant createdAt, Instant expiresAt, GuildAction action);

    CompletableFuture<Void> deleteInvite(UUID guildUuid, UUID inviteeUuid, GuildAction action);

    CompletableFuture<List<GuildInviteEntity>> invitesFor(UUID inviteeUuid);

    CompletableFuture<Void> recordAction(GuildAction action);

    CompletableFuture<Void> saveGuild(Guild guild, GuildAction action, @Nullable HistoryPurge purge);

    CompletableFuture<List<GuildAction>> actions(Long sessionId, long cutoff);

    CompletableFuture<List<GuildSession>> sessions(UUID playerUuid, long cutoff);

    CompletableFuture<Long> openSession(UUID playerUuid, String name, long now);

    CompletableFuture<Void> closeSession(long id, long now);

    CompletableFuture<Void> closeOpenSessions(long now);

    CompletableFuture<Void> purgeRecovery(long cutoff);

    /** One purge per world, up to the highest bound recorded for it. */
    CompletableFuture<List<HistoryPurge>> historyPurges();

    CompletableFuture<Void> deleteHistoryPurges(String world, long untilAt);

    /** The newest DISBAND action of the guild that is not undone and not older than the cutoff. */
    CompletableFuture<Optional<GuildAction>> disbandAction(UUID guildUuid, long cutoff);
}
