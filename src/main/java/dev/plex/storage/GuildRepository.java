package dev.plex.storage;

import dev.plex.guild.Guild;
import dev.plex.guild.data.Guest;
import dev.plex.guild.data.GuildRole;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.util.CustomLocation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildRepository
{
    CompletableFuture<List<Guild>> loadGuilds();

    CompletableFuture<Guild> createGuild(Guild guild);

    CompletableFuture<Void> deleteGuild(UUID guildUuid);

    CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role);

    CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid);

    CompletableFuture<Void> upsertGuest(UUID guildUuid, Guest guest);

    CompletableFuture<Void> removeGuest(UUID guildUuid, UUID playerUuid);

    CompletableFuture<Void> updateRole(UUID guildUuid, UUID playerUuid, GuildRole role);

    /** Sets the new owner and makes the old owner an officer in one transaction. */
    CompletableFuture<Void> transferOwner(UUID guildUuid, UUID newOwnerUuid, UUID oldOwnerUuid);

    CompletableFuture<Void> updatePrefix(UUID guildUuid, String prefix);

    CompletableFuture<Void> updateSpawn(UUID guildUuid, CustomLocation spawn);

    CompletableFuture<Void> clearWorldLocations(UUID guildUuid, String worldName);

    CompletableFuture<Void> upsertWarp(UUID guildUuid, String name, CustomLocation location);

    CompletableFuture<Void> deleteWarp(UUID guildUuid, String name);

    CompletableFuture<Void> createInvite(UUID guildUuid, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt);

    CompletableFuture<Void> deleteInvite(UUID guildUuid, UUID inviteeUuid);

    CompletableFuture<List<GuildInviteEntity>> invitesFor(UUID inviteeUuid);
}
