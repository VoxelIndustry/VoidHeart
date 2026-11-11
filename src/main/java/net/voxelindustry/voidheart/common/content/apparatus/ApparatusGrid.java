package net.voxelindustry.voidheart.common.content.apparatus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * The components set in an apparatus, each at the top left corner of its turned shape. Saved on the apparatus item, so it can hold placements a smaller grid
 * would refuse only if the data was edited, and those are skipped when the shield is worked out.
 */
public record ApparatusGrid(List<Placement> placements)
{
    public static final ApparatusGrid EMPTY = new ApparatusGrid(List.of());

    public record Placement(ApparatusComponent component, int x, int y, int rotation)
    {
        public static final Codec<Placement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ApparatusComponent.CODEC.fieldOf("component").forGetter(Placement::component),
                Codec.intRange(0, 15).fieldOf("x").forGetter(Placement::x),
                Codec.intRange(0, 15).fieldOf("y").forGetter(Placement::y),
                Codec.intRange(0, 3).optionalFieldOf("rotation", 0).forGetter(Placement::rotation)
        ).apply(instance, Placement::new));

        public ComponentShape shape()
        {
            return component.shape().rotated(rotation);
        }

        public boolean covers(int cellX, int cellY)
        {
            return shape().cells().stream().anyMatch(cell -> x + cell.x() == cellX && y + cell.y() == cellY);
        }
    }

    public static final Codec<ApparatusGrid> CODEC = Placement.CODEC.listOf().xmap(ApparatusGrid::new, ApparatusGrid::placements);

    public ApparatusGrid
    {
        placements = List.copyOf(placements);
    }

    public boolean isEmpty()
    {
        return placements.isEmpty();
    }

    /**
     * Why a component can't go where it was dropped, or null when it can.
     */
    public enum Refusal
    {
        OUTSIDE,
        OVERLAPS,
        SECOND_CORE,
        SAME_WARD
    }

    public @Nullable Refusal refusal(int size, ApparatusComponent component, int x, int y, int rotation)
    {
        var shape = component.shape().rotated(rotation);
        for (var cell : shape.cells())
        {
            int cellX = x + cell.x();
            int cellY = y + cell.y();
            if (cellX < 0 || cellY < 0 || cellX >= size || cellY >= size)
                return Refusal.OUTSIDE;
            if (at(cellX, cellY) != null)
                return Refusal.OVERLAPS;
        }
        if (component.breakEffect() != null && placements.stream().anyMatch(placement -> placement.component().breakEffect() != null))
            return Refusal.SECOND_CORE;
        if (component.ward() != null && placements.stream().anyMatch(placement -> placement.component().ward() == component.ward()))
            return Refusal.SAME_WARD;
        return null;
    }

    public boolean canPlace(int size, ApparatusComponent component, int x, int y, int rotation)
    {
        return refusal(size, component, x, y, rotation) == null;
    }

    public ApparatusGrid with(Placement placement)
    {
        var next = new ArrayList<>(placements);
        next.add(placement);
        return new ApparatusGrid(next);
    }

    public @Nullable Placement at(int cellX, int cellY)
    {
        for (var placement : placements)
            if (placement.covers(cellX, cellY))
                return placement;
        return null;
    }

    public ApparatusGrid without(Placement placement)
    {
        var next = new ArrayList<>(placements);
        next.remove(placement);
        return new ApparatusGrid(next);
    }

    /**
     * Placements that fit a grid of this size without overlapping and within the one core and one ward of each kind rule, in the order they were set.
     */
    public List<Placement> valid(int size)
    {
        var kept = EMPTY;
        for (var placement : placements)
            if (kept.canPlace(size, placement.component(), placement.x(), placement.y(), placement.rotation()))
                kept = kept.with(placement);
        return kept.placements();
    }

    public ShieldStats stats(ApparatusTier tier)
    {
        float capacity = tier.capacity();
        float regen = 1;
        float delay = 1;
        var wards = EnumSet.noneOf(WardKind.class);
        BreakEffect breakEffect = null;

        for (var placement : valid(tier.size()))
        {
            var component = placement.component();
            capacity += component.capacity();
            regen += component.regen();
            delay *= 1 - component.quickening();
            if (component.ward() != null)
                wards.add(component.ward());
            if (component.breakEffect() != null)
                breakEffect = component.breakEffect();
        }

        int delayTicks = Math.max(ShieldStats.MIN_DELAY_TICKS, Math.round(tier.delayTicks() * delay));
        return new ShieldStats(capacity, tier.regenPerSecond() * regen / 20, delayTicks, wards, Optional.ofNullable(breakEffect));
    }
}
