package dev.plex.guild;

import dev.plex.Guilds;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildTimeMode;
import dev.plex.guild.data.GuildWeatherMode;
import dev.plex.guild.data.Member;
import dev.plex.guild.data.GuildWorldAccess;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
            return module.getGuildRepository().recordInviteAttempt(guild.getGuildUuid(), now).thenCompose(unused ->
            {
                guild.getInviteHistory().removeIf(createdAt -> !createdAt.isAfter(cutoff));
                guild.getInviteHistory().add(now);
                if (module.getGuildHolder().guild(inviteeUuid).isPresent())
                {
                    throw new IllegalArgumentException("The player is already in a guild");
                }
                return module.getGuildRepository().createInvite(guild.getGuildUuid(), actorId, inviteeUuid, now.plus(INVITE_DURATION));
            });
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
                        module.getGuildWorldAccessListener().applyAccess(guild);
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

    public CompletableFuture<Void> updatePrefix(Guild guild, UUID actorId, GuildPrefix prefix)
    {
        return serialize(guild, () ->
        {
            requireGuild(guild);
            if (!guild.isOwner(actorId))
            {
                throw new SecurityException("Only the guild owner can set the prefix");
            }
            return module.getGuildRepository().updatePrefix(guild.getGuildUuid(), prefix)
                    .thenRun(() -> guild.setPrefix(prefix));
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

    public CompletableFuture<Void> cycleWorldAccess(Guild guild, UUID actorId)
    {
        return changeWorldAccess(guild, actorId, () -> guild.getWorldAccess().next());
    }

    public CompletableFuture<Void> setWorldAccess(Guild guild, UUID actorId, GuildWorldAccess access)
    {
        return changeWorldAccess(guild, actorId, () -> access);
    }

    private CompletableFuture<Void> changeWorldAccess(Guild guild, UUID actorId, Supplier<GuildWorldAccess> update)
    {
        return serialize(guild, () ->
        {
            requireManager(guild, actorId);
            GuildWorldAccess access = update.get();
            return module.getGuildRepository().updateWorldAccess(guild.getGuildUuid(), access)
                    .thenRun(() -> guild.setWorldAccess(access))
                    .thenRun(() -> module.getGuildWorldAccessListener().applyAccess(guild));
        });
    }

    public CompletableFuture<Void> cycleTimeMode(Guild guild, UUID actorId)
    {
        return serialize(guild, () ->
        {
            requireManager(guild, actorId);
            GuildTimeMode mode = guild.getTimeMode().next();
            return module.getGuildRepository().updateTimeMode(guild.getGuildUuid(), mode)
                    .thenRun(() -> guild.setTimeMode(mode))
                    .thenCompose(unused -> applyWorldSettings(guild));
        });
    }

    public CompletableFuture<Void> cycleWeatherMode(Guild guild, UUID actorId)
    {
        return serialize(guild, () ->
        {
            requireManager(guild, actorId);
            GuildWeatherMode mode = guild.getWeatherMode().next();
            return module.getGuildRepository().updateWeatherMode(guild.getGuildUuid(), mode)
                    .thenRun(() -> guild.setWeatherMode(mode))
                    .thenCompose(unused -> applyWorldSettings(guild));
        });
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
