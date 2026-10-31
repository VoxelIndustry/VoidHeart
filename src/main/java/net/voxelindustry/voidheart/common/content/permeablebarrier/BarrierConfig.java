package net.voxelindustry.voidheart.common.content.permeablebarrier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Shared settings of a whole barrier structure, replicated on every block of it.
 * A barrier is solid for an entity when (active AND entity matches a filter) XOR inverted,
 * where no ticked filter matches every entity.
 */
public record BarrierConfig(Optional<UUID> owner,
                            Optional<String> ownerTeam,
                            List<CoOwner> coOwners,
                            Optional<BlockState> appearance,
                            int flags)
{
    public static final BarrierConfig EMPTY = new BarrierConfig(Optional.empty(), Optional.empty(), List.of(), Optional.empty(), 0);

    public static final Codec<BarrierConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(BarrierConfig::owner),
            Codec.STRING.optionalFieldOf("ownerTeam").forGetter(BarrierConfig::ownerTeam),
            CoOwner.CODEC.listOf().optionalFieldOf("coOwners", List.of()).forGetter(BarrierConfig::coOwners),
            BlockState.CODEC.optionalFieldOf("appearance").forGetter(BarrierConfig::appearance),
            Codec.INT.optionalFieldOf("flags", 0).forGetter(BarrierConfig::flags)
    ).apply(instance, BarrierConfig::new));

    public enum Flag
    {
        ALLIES,
        PEACEFUL_MOBS,
        HOSTILE_MOBS,
        INVERTED,
        REDSTONE,
        INVISIBLE_WHEN_OPEN;

        int mask()
        {
            return 1 << ordinal();
        }
    }

    public record CoOwner(UUID id, String name)
    {
        public static final Codec<CoOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(CoOwner::id),
                Codec.STRING.fieldOf("name").forGetter(CoOwner::name)
        ).apply(instance, CoOwner::new));
    }

    public static BarrierConfig ownedBy(UUID owner)
    {
        return new BarrierConfig(Optional.of(owner), Optional.empty(), List.of(), Optional.empty(), Flag.ALLIES.mask());
    }

    /**
     * A freshly placed block not yet joined to any structure.
     */
    public boolean isBlank()
    {
        return owner.isEmpty() && appearance.isEmpty();
    }

    /**
     * Settings of structures joined into one. The configured structure with the most blocks keeps its look,
     * behavior and team, while every co-owner is kept and the other owners become co-owners.
     *
     * @param blockCounts each distinct non-blank config with how many blocks carry it
     * @param nameOf      resolves the display name of an owner turned co-owner
     */
    public static Optional<BarrierConfig> merge(Map<BarrierConfig, Integer> blockCounts, Function<UUID, String> nameOf)
    {
        var primary = blockCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<BarrierConfig, Integer>, Boolean>comparing(entry -> entry.getKey().isConfigured())
                        .thenComparing(Map.Entry::getValue))
                .map(Map.Entry::getKey);
        if (primary.isEmpty())
            return Optional.empty();

        var merged = primary.get();
        for (var config : blockCounts.keySet())
        {
            if (config == merged)
                continue;
            for (var coOwner : config.coOwners())
                merged = merged.withCoOwner(coOwner);
            var otherOwner = config.owner();
            if (otherOwner.isPresent())
                merged = merged.withCoOwner(new CoOwner(otherOwner.get(), nameOf.apply(otherOwner.get())));
        }
        return Optional.of(merged);
    }

    /**
     * Adds a co-owner unless they already own or co-own the barrier.
     */
    public BarrierConfig withCoOwner(CoOwner coOwner)
    {
        if (owner.filter(coOwner.id()::equals).isPresent() || coOwners.stream().anyMatch(existing -> existing.id().equals(coOwner.id())))
            return this;
        return toggleCoOwner(coOwner);
    }

    public boolean has(Flag flag)
    {
        return (flags & flag.mask()) != 0;
    }

    public boolean isConfigured()
    {
        return appearance.isPresent();
    }

    public boolean isAlly(Player player)
    {
        if (owner.isEmpty())
            return true;
        if (owner.get().equals(player.getUUID()))
            return true;
        if (coOwners.stream().anyMatch(coOwner -> coOwner.id().equals(player.getUUID())))
            return true;
        return ownerTeam.isPresent() && player.getTeam() != null && ownerTeam.get().equals(player.getTeam().getName());
    }

    /**
     * With no filter ticked the barrier reacts to every entity, making it a plain redstone wall or door.
     */
    public boolean matches(@Nullable Entity entity)
    {
        if (!has(Flag.ALLIES) && !has(Flag.PEACEFUL_MOBS) && !has(Flag.HOSTILE_MOBS))
            return true;
        if (has(Flag.ALLIES) && entity instanceof Player player && !player.isSpectator() && isAlly(player))
            return true;
        if (has(Flag.HOSTILE_MOBS) && entity instanceof Enemy)
            return true;
        return has(Flag.PEACEFUL_MOBS) && entity instanceof Mob && !(entity instanceof Enemy);
    }

    public boolean isActive(boolean powered)
    {
        return !has(Flag.REDSTONE) || powered;
    }

    public boolean isSolidFor(@Nullable Entity entity, boolean powered)
    {
        if (!isConfigured())
            return false;
        return (isActive(powered) && matches(entity)) != has(Flag.INVERTED);
    }

    public BarrierConfig toggle(Flag flag)
    {
        return new BarrierConfig(owner, ownerTeam, coOwners, appearance, flags ^ flag.mask());
    }

    public BarrierConfig withAppearance(Optional<BlockState> appearance)
    {
        return new BarrierConfig(owner, ownerTeam, coOwners, appearance, flags);
    }

    public BarrierConfig withOwnerTeam(Optional<String> ownerTeam)
    {
        return new BarrierConfig(owner, ownerTeam, coOwners, appearance, flags);
    }

    public BarrierConfig toggleCoOwner(CoOwner coOwner)
    {
        var updated = new ArrayList<>(coOwners);
        if (!updated.removeIf(existing -> existing.id().equals(coOwner.id())))
            updated.add(coOwner);
        return new BarrierConfig(owner, ownerTeam, List.copyOf(updated), appearance, flags);
    }
}
