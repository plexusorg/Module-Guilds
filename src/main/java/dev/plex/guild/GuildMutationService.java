package dev.plex.guild;

import dev.plex.Guilds;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.Member;
import dev.plex.guild.data.Guest;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import dev.plex.util.CustomLocation;

/** Owns durable guild mutations and their in-memory projection. */
public final class GuildMutationService
{
    private final Guilds module;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> mutationTails = new ConcurrentHashMap<>();

    public GuildMutationService(Guilds module)
    {
        this.module = module;
    }

    public CompletableFuture<Void> removeMember(Guild guild, UUID memberUuid)
    {
        return serialize(guild, () -> module.getGuildRepository().removeMember(guild.getGuildUuid(), memberUuid).thenRun(() ->
        {
            guild.getGuests().remove(memberUuid);
            guild.removeMember(memberUuid);
            module.getGuildHolder().unindexMember(memberUuid);
            module.getGuildWorldAccessListener().revoke(memberUuid);
        }));
    }

    public CompletableFuture<Void> addMember(Guild guild, UUID memberUuid, boolean deleteInvite)
    {
        return serialize(guild, () -> module.getGuildRepository().addMember(
                guild.getGuildUuid(), memberUuid, GuildRole.MEMBER)
                .thenCompose(unused -> deleteInvite
                        ? module.getGuildRepository().deleteInvite(guild.getGuildUuid(), memberUuid)
                        : CompletableFuture.completedFuture(null))
                .thenRun(() ->
                {
                    guild.getGuests().remove(memberUuid);
                    guild.addMember(memberUuid);
                    module.getGuildHolder().indexMember(guild.getGuildUuid(), memberUuid);
                }));
    }

    public CompletableFuture<Void> deleteGuild(Guild guild)
    {
        return serialize(guild, () -> module.getGuildRepository().deleteGuild(guild.getGuildUuid())
                .thenRun(() ->
                {
                    module.getGuildHolder().removeGuild(guild.getGuildUuid());
                    for (Member member : guild.getMembers())
                    {
                        module.getGuildWorldAccessListener().revoke(member.getUuid());
                    }
                    for (UUID guestId : guild.getGuests().keySet())
                    {
                        module.getGuildWorldAccessListener().revoke(guestId);
                    }
                    if (!guild.isMember(guild.getOwnerUuid()))
                    {
                        module.getGuildWorldAccessListener().revoke(guild.getOwnerUuid());
                    }
                }));
    }

    public CompletableFuture<Void> grantGuest(Guild guild, UUID managerId, Guest guest)
    {
        return serialize(guild, () ->
        {
            requireGuestManager(guild, managerId);
            if (guild.isOwner(guest.playerUuid()) || guild.isMember(guest.playerUuid()))
            {
                throw new IllegalArgumentException("Guild members do not need guest access");
            }
            if (!guest.isActive(Instant.now()))
            {
                throw new IllegalArgumentException("Guest access must expire in the future");
            }
            return module.getGuildRepository().upsertGuest(guild.getGuildUuid(), guest)
                    .thenRun(() -> guild.getGuests().put(guest.playerUuid(), guest));
        });
    }

    public CompletableFuture<Void> revokeGuest(Guild guild, UUID managerId, UUID guestId)
    {
        return serialize(guild, () ->
        {
            requireGuestManager(guild, managerId);
            return module.getGuildRepository().removeGuest(guild.getGuildUuid(), guestId).thenRun(() ->
            {
                guild.getGuests().remove(guestId);
                module.getGuildWorldAccessListener().revoke(guestId);
            });
        });
    }

    private void requireGuestManager(Guild guild, UUID managerId)
    {
        if (!module.isReady() || module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild
                || !guild.hasPermission(managerId, GuildPermission.MANAGE_GUESTS))
        {
            throw new SecurityException("You cannot manage guests for this guild");
        }
    }

    public CompletableFuture<Void> updatePrefix(Guild guild, String prefix)
    {
        return serialize(guild, () -> module.getGuildRepository().updatePrefix(guild.getGuildUuid(), prefix)
                .thenRun(() -> guild.setPrefix(prefix)));
    }

    public CompletableFuture<Void> updateHome(Guild guild, CustomLocation home)
    {
        return serializeLocationChange(guild, () -> module.getGuildRepository().updateHome(guild.getGuildUuid(), home)
                .thenRun(() -> guild.setHome(home)));
    }

    public CompletableFuture<Void> upsertWarp(Guild guild, String name, CustomLocation location)
    {
        return serializeLocationChange(guild, () -> module.getGuildRepository().upsertWarp(guild.getGuildUuid(), name, location)
                .thenRun(() -> guild.getWarps().put(name, location)));
    }

    public CompletableFuture<Void> resetWorld(Guild guild, Supplier<CompletableFuture<Void>> prepare,
                                            Supplier<CompletableFuture<Void>> replace)
    {
        return serialize(guild, () ->
        {
            if (module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild)
            {
                return CompletableFuture.failedFuture(new IllegalStateException("The guild no longer exists"));
            }
            String worldName = guild.getWorldName();
            return prepare.get()
                    .thenCompose(unused -> module.getGuildRepository().clearWorldLocations(guild.getGuildUuid(), worldName))
                    .thenRun(() ->
                    {
                        CustomLocation home = guild.getHome();
                        if (home != null && worldName.equals(home.getWorldName()))
                        {
                            guild.setHome(null);
                        }
                        guild.getWarps().values().removeIf(location -> worldName.equals(location.getWorldName()));
                    })
                    .thenCompose(unused -> replace.get());
        });
    }

    public CompletableFuture<Boolean> toggleMemberPermission(Guild guild, UUID actorId, GuildPermission permission)
    {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        serialize(guild, () ->
        {
            if (module.getGuildHolder().guildById(guild.getGuildUuid()).orElse(null) != guild || !guild.isOwner(actorId))
            {
                throw new SecurityException("Only the guild owner can change member permissions");
            }
            boolean enabled = !guild.isMemberPermissionEnabled(permission);
            return module.getGuildRepository().updateMemberPermission(guild.getGuildUuid(), permission, enabled)
                    .thenRun(() -> guild.setPermission(permission, enabled))
                    .thenRun(() -> result.complete(enabled));
        }).exceptionally(failure ->
        {
            result.completeExceptionally(failure);
            return null;
        });
        return result;
    }

    public CompletableFuture<Void> transferOwnership(Guild guild, Member newOwner, UUID oldOwnerUuid, Member oldOwner)
    {
        return serialize(guild, () -> module.getGuildRepository().transferOwner(
                guild.getGuildUuid(), newOwner.getUuid(), oldOwnerUuid).thenRun(() ->
        {
            guild.setOwnerUuid(newOwner.getUuid());
            newOwner.setRole(GuildRole.OWNER);
            if (oldOwner != null)
            {
                oldOwner.setRole(GuildRole.MEMBER);
            }
        }));
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
        CompletableFuture<Void> operation = mutationTails.compute(guildUuid, (ignored, tail) ->
                (tail == null ? CompletableFuture.completedFuture(null) : tail.handle((unused, failure) -> null))
                        .thenCompose(unused -> mutation.get()));
        operation.whenComplete((unused, failure) -> mutationTails.remove(guildUuid, operation));
        return operation;
    }
}
