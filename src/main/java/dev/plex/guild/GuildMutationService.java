package dev.plex.guild;

import dev.plex.Guilds;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.Member;
import dev.plex.guild.data.Guest;
import net.kyori.adventure.text.Component;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import dev.plex.util.CustomLocation;

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

    /** Kicks a member (actor is not the member) or leaves the guild (actor is the member). The owner cannot leave. */
    public CompletableFuture<Void> removeMember(Guild guild, UUID actorId, UUID memberUuid)
    {
        return serialize(guild, () ->
        {
            requireGuild(guild);
            if (!guild.isMember(memberUuid))
            {
                throw new IllegalArgumentException("The player is not a member of this guild");
            }
            boolean leave = actorId.equals(memberUuid);
            if (leave ? guild.isOwner(memberUuid) : !guild.canKick(actorId, memberUuid))
            {
                throw new SecurityException("You cannot remove this member");
            }
            return module.getGuildRepository().removeMember(guild.getGuildUuid(), memberUuid).thenRun(() ->
            {
                guild.getGuests().remove(memberUuid);
                guild.removeMember(memberUuid);
                module.getGuildHolder().unindexMember(memberUuid);
                module.getGuildWorldAccessListener().revoke(memberUuid);
            });
        });
    }

    /** Adds a member with the MEMBER role. The caller checks the invite. */
    public CompletableFuture<Void> addMember(Guild guild, UUID memberUuid, boolean deleteInvite)
    {
        return serialize(guild, () ->
        {
            requireGuild(guild);
            return module.getGuildRepository().addMember(guild.getGuildUuid(), memberUuid, GuildRole.MEMBER)
                    .thenCompose(unused -> deleteInvite
                            ? module.getGuildRepository().deleteInvite(guild.getGuildUuid(), memberUuid)
                            : CompletableFuture.completedFuture(null))
                    .thenRun(() ->
                    {
                        guild.getGuests().remove(memberUuid);
                        guild.addMember(memberUuid);
                        module.getGuildHolder().indexMember(guild.getGuildUuid(), memberUuid);
                    });
        });
    }

    /** Creates or replaces an invite that expires in five minutes. */
    public CompletableFuture<Void> createInvite(Guild guild, UUID actorId, UUID inviteeUuid)
    {
        return serialize(guild, () ->
        {
            requireManager(guild, actorId);
            if (guild.isMember(inviteeUuid))
            {
                throw new IllegalArgumentException("The player is already a member of this guild");
            }
            return module.getGuildRepository().createInvite(guild.getGuildUuid(), actorId, inviteeUuid,
                    Instant.now().plus(INVITE_DURATION));
        });
    }

    /**
     * Disbands the guild and permanently deletes its world. Only the owner can do this.
     * The rows go first, so a failed world deletion never leaves a guild without its world.
     * A failed world deletion is logged and does not fail the disband.
     */
    public CompletableFuture<Void> deleteGuild(Guild guild, UUID actorId)
    {
        return serialize(guild, () ->
        {
            requireGuild(guild);
            if (!guild.isOwner(actorId))
            {
                throw new SecurityException("Only the guild owner can disband the guild");
            }
            return module.getGuildRepository().deleteGuild(guild.getGuildUuid())
                    .thenRun(() ->
                    {
                        // Without the holder entry, canEnter is false for everyone in this world.
                        module.getGuildHolder().removeGuild(guild.getGuildUuid());
                        for (Member member : guild.getMembers())
                        {
                            module.getGuildWorldAccessListener().revoke(member.getUuid());
                        }
                        for (UUID guestId : guild.getGuests().keySet())
                        {
                            module.getGuildWorldAccessListener().revoke(guestId);
                        }
                    })
                    .thenCompose(unused -> deleteWorld(guild));
        });
    }

    private CompletableFuture<Void> deleteWorld(Guild guild)
    {
        if (!module.isGuildWorldsEnabled())
        {
            return CompletableFuture.completedFuture(null);
        }
        return module.getGuildWorldService().deleteWorld(guild).exceptionally(failure ->
        {
            module.getLogger().error("Failed to delete guild world {} of the disbanded guild {}",
                    guild.getWorldName(), guild.getGuildUuid(), failure);
            return null;
        });
    }

    /**
     * Promotes MEMBER to OFFICER or OFFICER to OWNER, or demotes OFFICER to MEMBER. Only the owner can change roles.
     * Promotion to OWNER makes the old owner an OFFICER in the same transaction.
     */
    public CompletableFuture<Void> setRole(Guild guild, UUID actorId, UUID targetId, GuildRole role)
    {
        return serialize(guild, () ->
        {
            requireGuild(guild);
            Member target = guild.getMember(targetId);
            if (target == null)
            {
                throw new IllegalArgumentException("The player is not a member of this guild");
            }
            GuildRole promotion = target.getRole() == GuildRole.MEMBER ? GuildRole.OFFICER : GuildRole.OWNER;
            boolean promote = role == promotion && guild.canPromote(actorId, targetId);
            boolean demote = role == GuildRole.MEMBER && guild.canDemote(actorId, targetId);
            if (!promote && !demote)
            {
                throw new SecurityException("You cannot give this role to this member");
            }
            if (role != GuildRole.OWNER)
            {
                return module.getGuildRepository().updateRole(guild.getGuildUuid(), targetId, role)
                        .thenRun(() -> target.setRole(role));
            }
            UUID oldOwnerId = guild.getOwnerUuid();
            return module.getGuildRepository().transferOwner(guild.getGuildUuid(), targetId, oldOwnerId).thenRun(() ->
            {
                guild.setOwnerUuid(targetId);
                target.setRole(GuildRole.OWNER);
                Member oldOwner = guild.getMember(oldOwnerId);
                if (oldOwner != null)
                {
                    oldOwner.setRole(GuildRole.OFFICER);
                }
            });
        });
    }

    /**
     * Adds a view-only guest, or resets the expiry of an active guest and keeps the mode.
     * The duration must be positive and not more than the configured maximum.
     */
    public CompletableFuture<Guest> addGuest(Guild guild, UUID actorId, UUID playerId, Duration duration)
    {
        if (duration.isNegative() || duration.isZero() || duration.compareTo(module.getGuestMaxDuration()) > 0)
        {
            return CompletableFuture.failedFuture(new IllegalArgumentException("The guest duration is not valid"));
        }
        return upsertGuest(guild, actorId, playerId, existing -> new Guest(playerId,
                existing != null && existing.editing(), Instant.now().plus(duration).truncatedTo(ChronoUnit.MILLIS)));
    }

    /** Switches an active guest between build (editing) and view mode. The expiry does not change. */
    public CompletableFuture<Guest> setGuestMode(Guild guild, UUID actorId, UUID playerId, boolean editing)
    {
        return upsertGuest(guild, actorId, playerId, existing ->
        {
            if (existing == null)
            {
                throw new IllegalArgumentException("The player is not an active guest");
            }
            return new Guest(playerId, editing, existing.expiresAt());
        });
    }

    public CompletableFuture<Void> revokeGuest(Guild guild, UUID actorId, UUID guestId)
    {
        return serialize(guild, () ->
        {
            requireManager(guild, actorId);
            return module.getGuildRepository().removeGuest(guild.getGuildUuid(), guestId).thenRun(() ->
            {
                guild.getGuests().remove(guestId);
                module.getGuildWorldAccessListener().revoke(guestId);
            });
        });
    }

    /** Sets the prefix. Only the owner can do this. A null prefix clears it. */
    public CompletableFuture<Void> updatePrefix(Guild guild, UUID actorId, String prefix)
    {
        return serialize(guild, () ->
        {
            requireGuild(guild);
            if (!guild.isOwner(actorId))
            {
                throw new SecurityException("Only the guild owner can set the prefix");
            }
            Component parsedPrefix = prefix == null || prefix.isEmpty() ? Component.empty() : module.api().messages().playerText(prefix);
            return module.getGuildRepository().updatePrefix(guild.getGuildUuid(), prefix)
                    .thenRun(() -> guild.setPrefix(prefix, parsedPrefix));
        });
    }

    /** Sets the world spawn. The location must be in the guild world. A null spawn resets it to the world default. */
    public CompletableFuture<Void> updateSpawn(Guild guild, UUID actorId, CustomLocation spawn)
    {
        return serializeLocationChange(guild, () ->
        {
            requireManager(guild, actorId);
            requireGuildWorld(guild, spawn);
            return module.getGuildRepository().updateSpawn(guild.getGuildUuid(), spawn)
                    .thenRun(() -> guild.setSpawn(spawn));
        });
    }

    /** Creates or moves a warp. The location must be in the guild world. Warp names are stored in lower case. */
    public CompletableFuture<Void> upsertWarp(Guild guild, UUID actorId, String name, CustomLocation location)
    {
        String key = name.toLowerCase(Locale.ROOT);
        return serializeLocationChange(guild, () ->
        {
            requireManager(guild, actorId);
            if (location == null)
            {
                throw new IllegalArgumentException("A warp needs a location");
            }
            requireGuildWorld(guild, location);
            return module.getGuildRepository().upsertWarp(guild.getGuildUuid(), key, location)
                    .thenRun(() -> guild.getWarps().put(key, location));
        });
    }

    public CompletableFuture<Void> deleteWarp(Guild guild, UUID actorId, String name)
    {
        String key = name.toLowerCase(Locale.ROOT);
        return serialize(guild, () ->
        {
            requireManager(guild, actorId);
            if (!guild.getWarps().containsKey(key))
            {
                throw new IllegalArgumentException("The warp does not exist");
            }
            return module.getGuildRepository().deleteWarp(guild.getGuildUuid(), key)
                    .thenRun(() -> guild.getWarps().remove(key));
        });
    }

    public <T> CompletableFuture<T> generateWorld(Guild guild, UUID actor, Supplier<CompletableFuture<T>> generate)
    {
        CompletableFuture<T> result = new CompletableFuture<>();
        serialize(guild, () ->
        {
            requireGuild(guild);
            if (!guild.isOwner(actor))
            {
                throw new SecurityException("Only the owner can generate this world");
            }
            return generate.get().thenAccept(result::complete);
        }).whenComplete((unused, failure) ->
        {
            if (failure != null)
            {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    public CompletableFuture<Void> resetWorld(Guild guild, UUID actor, Supplier<CompletableFuture<Void>> prepare,
                                            Supplier<CompletableFuture<Void>> replace)
    {
        return serialize(guild, () ->
        {
            if (module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild)
            {
                return CompletableFuture.failedFuture(new IllegalStateException("The guild no longer exists"));
            }
            if (actor != null && !guild.isOwner(actor))
            {
                throw new SecurityException("Only the owner can reset this world");
            }
            String worldName = guild.getWorldName();
            return prepare.get()
                    .thenCompose(unused -> module.getGuildRepository().clearWorldLocations(guild.getGuildUuid(), worldName))
                    .thenRun(() ->
                    {
                        CustomLocation spawn = guild.getSpawn();
                        if (spawn != null && worldName.equals(spawn.getWorldName()))
                        {
                            guild.setSpawn(null);
                        }
                        guild.getWarps().values().removeIf(location -> worldName.equals(location.getWorldName()));
                    })
                    .thenCompose(unused -> replace.get());
        });
    }

    private CompletableFuture<Guest> upsertGuest(Guild guild, UUID actorId, UUID playerId,
                                                 Function<Guest, Guest> update)
    {
        CompletableFuture<Guest> result = new CompletableFuture<>();
        serialize(guild, () ->
        {
            requireManager(guild, actorId);
            if (guild.isMember(playerId))
            {
                throw new IllegalArgumentException("Guild members cannot be guests");
            }
            Guest guest = update.apply(guild.getActiveGuest(playerId));
            return module.getGuildRepository().upsertGuest(guild.getGuildUuid(), guest)
                    .thenRun(() -> guild.getGuests().put(playerId, guest))
                    .thenRun(() -> result.complete(guest));
        }).exceptionally(failure ->
        {
            result.completeExceptionally(failure);
            return null;
        });
        return result;
    }

    private void requireGuild(Guild guild)
    {
        if (!module.isReady() || module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild)
        {
            throw new IllegalStateException("The guild no longer exists");
        }
    }

    private void requireManager(Guild guild, UUID actorId)
    {
        requireGuild(guild);
        if (!guild.canManage(actorId))
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

    private CompletableFuture<Void> serialize(Guild guild, Supplier<CompletableFuture<Void>> mutation)
    {
        UUID guildUuid = guild.getGuildUuid();
        // The gate keeps the mutation from running inside compute(), which holds the map lock.
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> operation = mutationTails.compute(guildUuid, (ignored, tail) ->
                (tail == null ? gate : tail.handle((unused, failure) -> null).thenCombine(gate, (unused, open) -> null))
                        .thenCompose(unused -> mutation.get()));
        operation.whenComplete((unused, failure) -> mutationTails.remove(guildUuid, operation));
        gate.complete(null);
        return operation;
    }
}
