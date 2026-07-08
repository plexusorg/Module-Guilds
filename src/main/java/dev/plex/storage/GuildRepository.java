package dev.plex.storage;

import dev.plex.guild.Guild;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.GuildRole;
import dev.plex.storage.entity.GuildInviteEntity;
import dev.plex.util.CustomLocation;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface GuildRepository
{
    CompletableFuture<List<Guild>> loadGuilds();

    CompletableFuture<Guild> createGuild(Player owner, String name);

    CompletableFuture<Void> deleteGuild(UUID guildUuid);

    CompletableFuture<Void> addMember(UUID guildUuid, UUID playerUuid, GuildRole role);

    CompletableFuture<Void> removeMember(UUID guildUuid, UUID playerUuid);

    CompletableFuture<Void> transferOwner(UUID guildUuid, UUID newOwnerUuid, UUID oldOwnerUuid);

    CompletableFuture<Void> updateMemberPermission(UUID guildUuid, GuildPermission permission, boolean enabled);

    CompletableFuture<Void> updatePrefix(UUID guildUuid, String prefix);

    CompletableFuture<Void> updateHome(UUID guildUuid, CustomLocation home);

    CompletableFuture<Void> upsertWarp(UUID guildUuid, String name, CustomLocation location);

    CompletableFuture<Void> deleteWarp(UUID guildUuid, String name);

    CompletableFuture<Void> createInvite(UUID guildUuid, UUID inviterUuid, UUID inviteeUuid, Instant expiresAt);

    CompletableFuture<Void> deleteInvite(UUID guildUuid, UUID inviteeUuid);

    CompletableFuture<List<GuildInviteEntity>> invitesFor(UUID inviteeUuid);
}
