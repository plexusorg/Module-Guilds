package dev.plex.guild;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import dev.plex.Guilds;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildTimeMode;
import dev.plex.guild.data.GuildWeatherMode;
import dev.plex.guild.data.Member;
import dev.plex.guild.data.Guest;
import dev.plex.storage.HistoryPurge;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import dev.plex.util.CustomLocation;
import dev.plex.world.GuildWorldDeletion;
import org.jetbrains.annotations.Nullable;

/**
 * Owns durable guild mutations and their in-memory projection.
 * Each mutation that has an actor checks the actor's permission again inside the guild's serial queue.
 * Failures: SecurityException when the actor is not allowed, IllegalArgumentException for an invalid target or value,
 * IllegalStateException when the guild no longer exists or its world is being reset.
 */
public final class GuildMutationService
{
    private static final Duration INVITE_DURATION = Duration.ofMinutes(5);

    private final Guilds module;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> mutationTails = new ConcurrentHashMap<>();

    public GuildMutationService(Guilds module)
    {
        this.module = module;
    }

    private GuildAction action(Guild guild, Actor actor, Long sessionId, String type, JsonElement before, JsonElement after)
    {
        return new GuildAction(0, sessionId, actor, guild.getGuildUuid(), type,
                System.currentTimeMillis(), before, after, null, null);
    }

    public CompletableFuture<Guild> createGuild(Guild guild, Actor actor)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return session.thenApply(sessionId -> action(guild, actor, sessionId, "CREATE", null, GuildState.snapshot(guild)))
                .thenCompose(action -> module.getGuildRepository().createGuild(guild, action))
                .thenApply(created ->
                {
                    module.getGuildHolder().addGuild(created);
                    return created;
                });
    }

    /** Kicks a member (actor is not the member) or leaves the guild (actor is the member). The owner cannot leave. */
    public CompletableFuture<Void> removeMember(Guild guild, Actor actor, UUID memberUuid)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireGuild(guild);
            if (!guild.isMember(memberUuid))
            {
                throw new IllegalArgumentException("The player is not a member of this guild");
            }
            if (guild.isOwner(memberUuid))
            {
                throw new IllegalArgumentException("The owner cannot be removed");
            }
            boolean leave = !actor.staffOverride() && memberUuid.equals(actor.uuid());
            if (!actor.staffOverride() && !leave && !guild.canKick(actor.uuid(), memberUuid))
            {
                throw new SecurityException("You cannot remove this member");
            }
            return module.getGuildRepository().removeMember(guild.getGuildUuid(), memberUuid, action(guild, actor, sessionId, "REMOVE_MEMBER", GuildState.object("player", memberUuid, "role", guild.getRole(memberUuid), "joined", guild.getMember(memberUuid).getJoinedAt()), null)).thenRun(() ->
            {
                guild.getGuests().remove(memberUuid);
                guild.removeMember(memberUuid);
                module.getGuildHolder().unindexMember(memberUuid);
                module.getGuildWorldAccessListener().revoke(memberUuid);
            });
        }));
    }

    /** Adds a member with the MEMBER role. The caller checks the invite. */
    public CompletableFuture<Void> addMember(Guild guild, Actor actor, UUID memberUuid, boolean deleteInvite)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireGuild(guild);
            if (module.getGuildHolder().guild(memberUuid).isPresent())
            {
                throw new IllegalArgumentException("The player is already in a guild");
            }
            Member member = new Member(memberUuid);
            return module.getGuildRepository().addMember(guild.getGuildUuid(), memberUuid, GuildRole.MEMBER, member.getJoinedAt(), deleteInvite, action(guild, actor, sessionId, "JOIN", null, GuildState.object("player", memberUuid, "role", GuildRole.MEMBER, "joined", member.getJoinedAt())))
                    .thenRun(() ->
                    {
                        guild.getGuests().remove(memberUuid);
                        guild.addMember(member);
                        module.getGuildHolder().indexMember(guild.getGuildUuid(), memberUuid);
                    });
        }));
    }

    /** Creates or replaces an invite that expires in five minutes. */
    public CompletableFuture<Void> createInvite(Guild guild, Actor actor, UUID inviteeUuid)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            if (module.getGuildHolder().guild(inviteeUuid).isPresent())
            {
                throw new IllegalArgumentException("The player is already in a guild");
            }
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Instant cutoff = now.minus(Guild.INVITE_WINDOW);
            long count = 0;
            Instant oldest = now;
            for (Instant createdAt : guild.getInviteHistory())
            {
                if (createdAt.isAfter(cutoff))
                {
                    count++;
                    if (createdAt.isBefore(oldest))
                    {
                        oldest = createdAt;
                    }
                }
            }
            if (count >= module.getInviteDailyLimit())
            {
                throw new GuildInviteLimitException(Duration.between(now, oldest.plus(Guild.INVITE_WINDOW)));
            }
            return module.getGuildRepository().createInvite(guild.getGuildUuid(), actor.uuid(), inviteeUuid,
                    now, now.plus(INVITE_DURATION), action(guild, actor, sessionId, "INVITE", null, GuildState.object("player", inviteeUuid, "inviter", actor.uuid(), "expires", now.plus(INVITE_DURATION).toEpochMilli()))).thenRun(() ->
            {
                guild.getInviteHistory().removeIf(createdAt -> !createdAt.isAfter(cutoff));
                guild.getInviteHistory().add(now);
            });
        }));
    }

    /**
     * Disbands the guild. The guild waits for finalization, and staff can restore it until then.
     */
    public CompletableFuture<Void> deleteGuild(Guild guild, Actor actor)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireGuild(guild);
            if (!actor.staffOverride() && !guild.isOwner(actor.uuid()))
            {
                throw new SecurityException("Only the guild owner can disband the guild");
            }
            return disband(guild, actor, sessionId, null);
        }));
    }

    /**
     * Marks the guild pending, releases its members, and closes its world. Call it inside the guild's serial queue.
     * The DISBAND action keeps the snapshot that a restore uses. A world that does not close goes to the log only.
     */
    public CompletableFuture<Void> disband(Guild guild, Actor actor, @Nullable Long sessionId, @Nullable Long undoId)
    {
        long now = System.currentTimeMillis();
        GuildAction action = new GuildAction(0, sessionId, actor, guild.getGuildUuid(), "DISBAND", now,
                GuildState.snapshot(guild), GuildState.object("deleted", now), null, undoId);
        return module.getGuildRepository().markDeleted(guild.getGuildUuid(), now, actor.uuid(), action).thenRun(() ->
        {
            module.getGuildHolder().addPending(new PendingGuild(guild, now, actor.uuid()));
            // Without the active entry, canEnter is false for everyone in this world.
            module.getGuildHolder().removeGuild(guild.getGuildUuid());
            for (Member member : guild.getMembers())
            {
                module.getGuildWorldAccessListener().revoke(member.getUuid());
            }
            for (UUID guestId : guild.getGuests().keySet())
            {
                module.getGuildWorldAccessListener().revoke(guestId);
            }
        }).thenCompose(unused -> module.isGuildWorldsEnabled() ? module.getGuildWorldService().closeWorld(guild).exceptionally(failure ->
        {
            // Finalization closes the world again before it deletes the file.
            module.getLogger().error("Failed to close the world of disbanded guild {}", guild.getGuildUuid(), failure);
            return null;
        }) : CompletableFuture.completedFuture(null));
    }

    /** Finalizes each pending guild past its due time, one at a time. A guild that fails stays pending for the next run. */
    public CompletableFuture<Void> finalizeDeletions()
    {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (PendingGuild pending : List.copyOf(module.getGuildHolder().pending()))
        {
            if (pending.isDue(module.getRecoveryCutoff()))
            {
                chain = chain.thenCompose(unused -> finalizeDeletion(pending.guild()).exceptionally(failure ->
                {
                    module.getLogger().error("Failed to finalize the deletion of guild {}", pending.guild().getGuildUuid(), failure);
                    return null;
                }));
            }
        }
        return chain;
    }

    // Each step is safe to repeat. Until the rows go, the guild row is the record of the world files; then the purge row replaces it.
    private CompletableFuture<Void> finalizeDeletion(Guild guild)
    {
        UUID guildUuid = guild.getGuildUuid();
        String worldName = guild.getWorldName();
        return serialize(guild, () ->
        {
            PendingGuild pending = module.getGuildHolder().pendingById(guildUuid).orElse(null);
            if (pending == null || !pending.isDue(module.getRecoveryCutoff()))
            {
                return CompletableFuture.completedFuture(null);
            }
            return deleteWorldFiles(pending.guild())
                    .thenCompose(unused -> module.getGuildRepository().finalizeDeletion(guildUuid, worldName, System.currentTimeMillis()))
                    .thenRun(() ->
                    {
                        module.getGuildHolder().removePending(guildUuid);
                        if (module.isGuildWorldsEnabled())
                        {
                            module.getGuildWorldService().openWorld(worldName);
                        }
                        module.getHistoryPurgeQueue().drain();
                    });
        });
    }

    private CompletableFuture<Void> deleteWorldFiles(Guild guild)
    {
        if (module.isGuildWorldsEnabled())
        {
            return module.getGuildWorldService().deleteWorld(guild);
        }
        return CompletableFuture.runAsync(() ->
        {
            try
            {
                GuildWorldDeletion.delete(module.getDataFolder().toPath().resolve("slime-worlds"), guild.getWorldName());
            }
            catch (IOException exception)
            {
                throw new CompletionException(exception);
            }
        }, module.executor());
    }

    /**
     * Promotes MEMBER to OFFICER or OFFICER to OWNER, or demotes OFFICER to MEMBER. Only the owner can change roles.
     * Promotion to OWNER makes the old owner an OFFICER in the same transaction.
     */
    public CompletableFuture<Void> setRole(Guild guild, Actor actor, UUID targetId, GuildRole role)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireGuild(guild);
            Member target = guild.getMember(targetId);
            if (target == null)
            {
                throw new IllegalArgumentException("The player is not a member of this guild");
            }
            GuildRole promotion = target.getRole() == GuildRole.MEMBER ? GuildRole.OFFICER : GuildRole.OWNER;
            boolean promote = target.getRole() != GuildRole.OWNER && role == promotion && (actor.staffOverride() || guild.canPromote(actor.uuid(), targetId));
            boolean demote = target.getRole() == GuildRole.OFFICER && role == GuildRole.MEMBER && (actor.staffOverride() || guild.canDemote(actor.uuid(), targetId));
            if (!promote && !demote)
            {
                throw new SecurityException("You cannot give this role to this member");
            }
            if (role != GuildRole.OWNER)
            {
                return module.getGuildRepository().updateRole(guild.getGuildUuid(), targetId, role, action(guild, actor, sessionId, "SET_ROLE",
                                GuildState.object("player", targetId, "role", target.getRole()), GuildState.object("player", targetId, "role", role)))
                        .thenRun(() -> target.setRole(role));
            }
            UUID oldOwnerId = guild.getOwnerUuid();
            return module.getGuildRepository().transferOwner(guild.getGuildUuid(), targetId, oldOwnerId, action(guild, actor, sessionId, "TRANSFER_OWNER",
                    GuildState.object("owner", oldOwnerId, "player", targetId, "role", target.getRole()), GuildState.object("owner", targetId))).thenRun(() ->
            {
                guild.setOwnerUuid(targetId);
                target.setRole(GuildRole.OWNER);
                Member oldOwner = guild.getMember(oldOwnerId);
                if (oldOwner != null)
                {
                    oldOwner.setRole(GuildRole.OFFICER);
                }
            });
        }));
    }

    /**
     * Adds a guest or resets the expiry with the requested mode.
     * A null mode keeps an active guest's mode; a new guest gets view mode.
     * The duration must be positive and not more than the configured maximum.
     */
    public CompletableFuture<Guest> addGuest(Guild guild, Actor actor, UUID playerId, @Nullable Boolean editing, Duration duration)
    {
        if (duration.isNegative() || duration.isZero() || duration.compareTo(module.getGuestMaxDuration()) > 0)
        {
            return CompletableFuture.failedFuture(new IllegalArgumentException("The guest duration is not valid"));
        }
        return upsertGuest(guild, actor, playerId, existing -> new Guest(playerId,
                editing != null ? editing : existing != null && existing.editing(),
                Instant.now().plus(duration).truncatedTo(ChronoUnit.MILLIS)));
    }

    /** Switches an active guest between build (editing) and view mode. The expiry does not change. */
    public CompletableFuture<Guest> setGuestMode(Guild guild, Actor actor, UUID playerId, boolean editing)
    {
        return upsertGuest(guild, actor, playerId, existing ->
        {
            if (existing == null)
            {
                throw new IllegalArgumentException("The player is not an active guest");
            }
            return new Guest(playerId, editing, existing.expiresAt());
        });
    }

    public CompletableFuture<Void> revokeGuest(Guild guild, Actor actor, UUID guestId)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            Guest guest = guild.getGuests().get(guestId);
            if (guest == null)
            {
                throw new IllegalArgumentException("The player is not a guest");
            }
            return module.getGuildRepository().removeGuest(guild.getGuildUuid(), guestId, action(guild, actor, sessionId, "GUEST_REVOKE", GuildState.value(guest), null)).thenRun(() ->
            {
                guild.getGuests().remove(guestId);
                module.getGuildWorldAccessListener().revoke(guestId);
            });
        }));
    }

    public CompletableFuture<Void> updatePrefix(Guild guild, Actor actor, GuildPrefix prefix)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireGuild(guild);
            if (!actor.staffOverride() && !guild.isOwner(actor.uuid()))
            {
                throw new SecurityException("Only the guild owner can set the prefix");
            }
            return module.getGuildRepository().updatePrefix(guild.getGuildUuid(), prefix, action(guild, actor, sessionId, "PREFIX", GuildState.value(guild.getPrefix()), GuildState.value(prefix.text())))
                    .thenRun(() -> guild.setPrefix(prefix));
        }));
    }

    /** Sets the world spawn. The location must be in the guild world. A null spawn resets it to the world default. */
    public CompletableFuture<Void> updateSpawn(Guild guild, Actor actor, CustomLocation spawn)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serializeLocationChange(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            requireGuildWorld(guild, spawn);
            return module.getGuildRepository().updateSpawn(guild.getGuildUuid(), spawn, action(guild, actor, sessionId, "SPAWN", GuildState.value(guild.getSpawn()), GuildState.value(spawn)))
                    .thenRun(() -> guild.setSpawn(spawn));
        }));
    }

    /** Creates or moves a warp. The location must be in the guild world. Warp names are stored in lower case. */
    public CompletableFuture<Void> upsertWarp(Guild guild, Actor actor, String name, CustomLocation location)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        String key = name.toLowerCase(Locale.ROOT);
        return serializeLocationChange(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            if (location == null)
            {
                throw new IllegalArgumentException("A warp needs a location");
            }
            requireGuildWorld(guild, location);
            return module.getGuildRepository().upsertWarp(guild.getGuildUuid(), key, location, action(guild, actor, sessionId, "WARP_SET",
                            GuildState.object("name", key, "location", guild.getWarps().get(key)), GuildState.object("name", key, "location", location)))
                    .thenRun(() -> guild.getWarps().put(key, location));
        }));
    }

    public CompletableFuture<Void> deleteWarp(Guild guild, Actor actor, String name)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        String key = name.toLowerCase(Locale.ROOT);
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            if (!guild.getWarps().containsKey(key))
            {
                throw new IllegalArgumentException("The warp does not exist");
            }
            return module.getGuildRepository().deleteWarp(guild.getGuildUuid(), key, action(guild, actor, sessionId, "WARP_DELETE", GuildState.object("name", key, "location", guild.getWarps().get(key)), null))
                    .thenRun(() -> guild.getWarps().remove(key));
        }));
    }

    public <T> CompletableFuture<T> generateWorld(Guild guild, Actor actor, String type, Supplier<CompletableFuture<T>> generate)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        CompletableFuture<T> result = new CompletableFuture<>();
        serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireGuild(guild);
            if (!actor.staffOverride() && !guild.isOwner(actor.uuid()))
            {
                throw new SecurityException("Only the owner can generate this world");
            }
            return generate.get().thenCompose(world -> module.getGuildRepository().recordAction(action(guild, actor, sessionId, "WORLD_GENERATE", null, GuildState.value(type)))
                    .thenRun(() -> result.complete(world)));
        })).whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public CompletableFuture<Void> resetWorld(Guild guild, Actor actor, Supplier<CompletableFuture<String>> prepare,
                                            Supplier<CompletableFuture<Void>> replace)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            if (module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild)
            {
                return CompletableFuture.failedFuture(new IllegalStateException("The guild no longer exists"));
            }
            if (!actor.staffOverride() && !guild.isOwner(actor.uuid()))
            {
                throw new SecurityException("Only the owner can reset this world");
            }
            String worldName = guild.getWorldName();
            var locations = GuildState.object("spawn", guild.getSpawn(), "warps", guild.getWarps());
            // The prepared world is unloaded and saved, so no edit of the old world comes after this cut.
            return prepare.get()
                    .thenCompose(backup -> module.getGuildRepository().clearWorldLocations(guild.getGuildUuid(), worldName,
                            action(guild, actor, sessionId, "WORLD_RESET", GuildState.object("backup", backup, "locations", locations),
                                    GuildState.object("spawn", null, "warps", new JsonObject())),
                            new HistoryPurge(worldName, System.currentTimeMillis())))
                    .thenRun(() ->
                    {
                        CustomLocation spawn = guild.getSpawn();
                        if (spawn != null && worldName.equals(spawn.getWorldName()))
                        {
                            guild.setSpawn(null);
                        }
                        guild.getWarps().values().removeIf(location -> worldName.equals(location.getWorldName()));
                    })
                    .thenCompose(unused -> replace.get())
                    .thenRun(() -> module.getHistoryPurgeQueue().drain());
        }));
    }

    private CompletableFuture<Guest> upsertGuest(Guild guild, Actor actor, UUID playerId,
                                                 Function<Guest, Guest> update)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        CompletableFuture<Guest> result = new CompletableFuture<>();
        serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            if (guild.isMember(playerId))
            {
                throw new IllegalArgumentException("Guild members cannot be guests");
            }
            Guest guest = update.apply(guild.getActiveGuest(playerId));
            return module.getGuildRepository().upsertGuest(guild.getGuildUuid(), guest, action(guild, actor, sessionId, "GUEST_SET", GuildState.value(guild.getActiveGuest(playerId)), GuildState.value(guest)))
                    .thenRun(() -> guild.getGuests().put(playerId, guest))
                    .thenRun(() -> result.complete(guest));
        })).exceptionally(failure ->
        {
            result.completeExceptionally(failure);
            return null;
        });
        return result;
    }

    public CompletableFuture<Void> cycleTimeMode(Guild guild, Actor actor)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            GuildTimeMode mode = guild.getTimeMode().next();
            return module.getGuildRepository().updateTimeMode(guild.getGuildUuid(), mode, action(guild, actor, sessionId, "TIME_MODE", GuildState.value(guild.getTimeMode()), GuildState.value(mode)))
                    .thenRun(() -> guild.setTimeMode(mode))
                    .thenCompose(unused -> applyWorldSettings(guild));
        }));
    }

    public CompletableFuture<Void> cycleWeatherMode(Guild guild, Actor actor)
    {
        CompletableFuture<Long> session = module.getGuildSessionListener().session(actor.uuid());
        return serialize(guild, () -> session.thenCompose(sessionId ->
        {
            requireManager(guild, actor);
            GuildWeatherMode mode = guild.getWeatherMode().next();
            return module.getGuildRepository().updateWeatherMode(guild.getGuildUuid(), mode, action(guild, actor, sessionId, "WEATHER_MODE", GuildState.value(guild.getWeatherMode()), GuildState.value(mode)))
                    .thenRun(() -> guild.setWeatherMode(mode))
                    .thenCompose(unused -> applyWorldSettings(guild));
        }));
    }

    private CompletableFuture<Void> applyWorldSettings(Guild guild)
    {
        return module.isGuildWorldsEnabled() ? module.getGuildWorldService().applySettings(guild)
                : CompletableFuture.completedFuture(null);
    }

    private void requireGuild(Guild guild)
    {
        if (!module.isReady() || module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild)
        {
            throw new IllegalStateException("The guild no longer exists");
        }
    }

    private void requireManager(Guild guild, Actor actor)
    {
        requireGuild(guild);
        if (!actor.staffOverride() && !guild.canManage(actor.uuid()))
        {
            throw new SecurityException("Only the owner and officers can do this");
        }
    }

    private void requireGuildWorld(Guild guild, CustomLocation location)
    {
        if (location != null && !guild.getWorldName().equals(location.getWorldName()))
        {
            throw new IllegalArgumentException("The location must be in the guild world");
        }
    }

    private CompletableFuture<Void> serializeLocationChange(Guild guild, Supplier<CompletableFuture<Void>> mutation)
    {
        if (module.isGuildWorldsEnabled() && module.getGuildWorldService().isResetting(guild.getWorldName()))
        {
            return CompletableFuture.failedFuture(new IllegalStateException("The guild world is being reset"));
        }
        return serialize(guild, () ->
        {
            // A location captured before the reset must not be written after its cleanup.
            if (module.isGuildWorldsEnabled() && module.getGuildWorldService().isResetting(guild.getWorldName()))
            {
                return CompletableFuture.failedFuture(new IllegalStateException("The guild world is being reset"));
            }
            return mutation.get();
        });
    }

    public CompletableFuture<Void> serialize(Guild guild, Supplier<CompletableFuture<Void>> mutation)
    {
        UUID guildUuid = guild.getGuildUuid();
        // The gate keeps the mutation from running inside compute(), which holds the map lock.
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> operation = mutationTails.compute(guildUuid, (ignored, tail) ->
                (tail == null ? gate : tail.handle((unused, failure) -> null).thenCombine(gate, (unused, open) -> null))
                        .thenComposeAsync(unused -> mutation.get(), module.executor()));
        operation.whenComplete((unused, failure) -> mutationTails.remove(guildUuid, operation));
        gate.complete(null);
        return operation;
    }
}
