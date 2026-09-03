package dev.plex.guild;

import dev.plex.Guilds;
import dev.plex.guild.data.GuildRole;
import dev.plex.guild.data.GuildPermission;
import dev.plex.guild.data.Member;
import java.util.UUID;
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
        return removeMember(guild, memberUuid, true);
    }

    public CompletableFuture<Void> removeMember(Guild guild, UUID memberUuid, boolean ejectNonMembers)
    {
        return serialize(guild, () -> module.getGuildRepository().removeMember(guild.getGuildUuid(), memberUuid).thenRun(() ->
        {
            guild.removeMember(memberUuid);
            module.getGuildHolder().unindexMember(memberUuid);
            if (ejectNonMembers && module.isGuildWorldsEnabled())
            {
                module.getGuildWorldService().ejectNonMembers(guild);
            }
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
                    guild.addMember(memberUuid);
                    module.getGuildHolder().indexMember(guild.getGuildUuid(), memberUuid);
                }));
    }

    public CompletableFuture<Void> deleteGuild(Guild guild)
    {
        return serialize(guild, () -> module.getGuildRepository().deleteGuild(guild.getGuildUuid())
                .thenRun(() -> module.getGuildHolder().removeGuild(guild.getGuildUuid())));
    }

    public CompletableFuture<Void> updatePrefix(Guild guild, String prefix)
    {
        return serialize(guild, () -> module.getGuildRepository().updatePrefix(guild.getGuildUuid(), prefix)
                .thenRun(() -> guild.setPrefix(prefix)));
    }

    public CompletableFuture<Void> updateHome(Guild guild, CustomLocation home)
    {
        return serialize(guild, () -> module.getGuildRepository().updateHome(guild.getGuildUuid(), home)
                .thenRun(() -> guild.setHome(home)));
    }

    public CompletableFuture<Void> upsertWarp(Guild guild, String name, CustomLocation location)
    {
        return serialize(guild, () -> module.getGuildRepository().upsertWarp(guild.getGuildUuid(), name, location)
                .thenRun(() -> guild.getWarps().put(name, location)));
    }

    public CompletableFuture<Boolean> toggleMemberPermission(Guild guild, GuildPermission permission)
    {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        serialize(guild, () ->
        {
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
