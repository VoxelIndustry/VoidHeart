package net.voxelindustry.voidheart.common.content.armor;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The modules set in the frame's three grids, each at the top left corner of its turned shape. Saved on the frame, so it can hold placements the rules
 * would refuse only if the data was edited or a module it rested on was taken out; those are skipped when the frame's stats are worked out.
 */
public record ArmorGrids(List<Placement> helm, List<Placement> core, List<Placement> limbs)
{
    public static final ArmorGrids EMPTY = new ArmorGrids(List.of(), List.of(), List.of());

    public record Placement(ArmorModule module, int x, int y, int rotation)
    {
        public static final Codec<Placement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ArmorModule.CODEC.fieldOf("module").forGetter(Placement::module),
                Codec.intRange(0, 15).fieldOf("x").forGetter(Placement::x),
                Codec.intRange(0, 15).fieldOf("y").forGetter(Placement::y),
                Codec.intRange(0, 3).optionalFieldOf("rotation", 0).forGetter(Placement::rotation)
        ).apply(instance, Placement::new));

        public ComponentShape shape()
        {
            return module.shape().rotated(rotation);
        }

        public boolean covers(int cellX, int cellY)
        {
            return shape().cells().stream().anyMatch(cell -> x + cell.x() == cellX && y + cell.y() == cellY);
        }

        /**
         * Whether a cell of this piece shares an edge with a cell of the other.
         */
        public boolean touches(Placement other)
        {
            for (var cell : shape().cells())
            {
                int cellX = x + cell.x();
                int cellY = y + cell.y();
                if (other.covers(cellX + 1, cellY) || other.covers(cellX - 1, cellY) || other.covers(cellX, cellY + 1) || other.covers(cellX, cellY - 1))
                    return true;
            }
            return false;
        }
    }

    public static final Codec<ArmorGrids> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Placement.CODEC.listOf().optionalFieldOf("helm", List.of()).forGetter(ArmorGrids::helm),
            Placement.CODEC.listOf().optionalFieldOf("core", List.of()).forGetter(ArmorGrids::core),
            Placement.CODEC.listOf().optionalFieldOf("limbs", List.of()).forGetter(ArmorGrids::limbs)
    ).apply(instance, ArmorGrids::new));

    public ArmorGrids
    {
        helm = List.copyOf(helm);
        core = List.copyOf(core);
        limbs = List.copyOf(limbs);
    }

    /**
     * Why a module can't go where it was dropped, or null when it can.
     */
    public enum Refusal
    {
        WRONG_GRID,
        OUTSIDE,
        SOCKET,
        OVERLAPS,
        SECOND,
        NEEDS_SUPPORT
    }

    public List<Placement> placements(ArmorGridKind kind)
    {
        return switch (kind)
        {
            case HELM -> helm;
            case CORE -> core;
            case LIMBS -> limbs;
        };
    }

    public boolean isEmpty()
    {
        return helm.isEmpty() && core.isEmpty() && limbs.isEmpty();
    }

    private ArmorGrids replace(ArmorGridKind kind, List<Placement> placements)
    {
        return switch (kind)
        {
            case HELM -> new ArmorGrids(placements, core, limbs);
            case CORE -> new ArmorGrids(helm, placements, limbs);
            case LIMBS -> new ArmorGrids(helm, core, placements);
        };
    }

    public ArmorGrids with(ArmorGridKind kind, Placement placement)
    {
        var next = new ArrayList<>(placements(kind));
        next.add(placement);
        return replace(kind, next);
    }

    public ArmorGrids without(ArmorGridKind kind, Placement placement)
    {
        var next = new ArrayList<>(placements(kind));
        next.remove(placement);
        return replace(kind, next);
    }

    public @Nullable Placement at(ArmorGridKind kind, int cellX, int cellY)
    {
        for (var placement : placements(kind))
            if (placement.covers(cellX, cellY))
                return placement;
        return null;
    }

    public @Nullable Refusal refusal(ArmorGridKind kind, ArmorModule module, int x, int y, int rotation)
    {
        if (!module.fits(kind))
            return Refusal.WRONG_GRID;
        var placement = new Placement(module, x, y, rotation);
        for (var cell : placement.shape().cells())
        {
            int cellX = x + cell.x();
            int cellY = y + cell.y();
            if (!kind.contains(cellX, cellY))
                return Refusal.OUTSIDE;
            if (kind.isSocket(cellX, cellY))
                return Refusal.SOCKET;
            if (at(kind, cellX, cellY) != null)
                return Refusal.OVERLAPS;
        }
        if (module.unique() && count(module) > 0)
            return Refusal.SECOND;
        var support = module.restsOn();
        if (support != null && placements(kind).stream().noneMatch(other -> other.module() == support && other.touches(placement)))
            return Refusal.NEEDS_SUPPORT;
        return null;
    }

    public boolean canPlace(ArmorGridKind kind, ArmorModule module, int x, int y, int rotation)
    {
        return refusal(kind, module, x, y, rotation) == null;
    }

    private int count(ArmorModule module)
    {
        int count = 0;
        for (var kind : ArmorGridKind.values())
            for (var placement : placements(kind))
                if (placement.module() == module)
                    count++;
        return count;
    }

    /**
     * The placements that work, grid by grid: those that fit their grid without overlapping, in the order they were set and within one of each unique
     * module per frame, then of those resting on another only the ones still touching a working one.
     */
    public Map<ArmorGridKind, List<Placement>> valid()
    {
        var result = new EnumMap<ArmorGridKind, List<Placement>>(ArmorGridKind.class);
        var seenUnique = new HashSet<ArmorModule>();
        for (var kind : ArmorGridKind.values())
        {
            var kept = EMPTY;
            for (var placement : placements(kind))
            {
                var module = placement.module();
                if (!module.fits(kind) || (module.unique() && seenUnique.contains(module)) || !fitsCells(kind, kept, placement))
                    continue;
                kept = kept.with(kind, placement);
                if (module.unique())
                    seenUnique.add(module);
            }

            var standing = new ArrayList<Placement>();
            for (var placement : kept.placements(kind))
            {
                var support = placement.module().restsOn();
                if (support == null || kept.placements(kind).stream().anyMatch(other -> other.module() == support && other.touches(placement)))
                    standing.add(placement);
            }
            result.put(kind, List.copyOf(standing));
        }
        return result;
    }

    private static boolean fitsCells(ArmorGridKind kind, ArmorGrids kept, Placement placement)
    {
        for (var cell : placement.shape().cells())
        {
            int cellX = placement.x() + cell.x();
            int cellY = placement.y() + cell.y();
            if (!kind.contains(cellX, cellY) || kind.isSocket(cellX, cellY) || kept.at(kind, cellX, cellY) != null)
                return false;
        }
        return true;
    }

    public boolean isValid(ArmorGridKind kind, Placement placement)
    {
        return valid().get(kind).contains(placement);
    }

    public ArmorStats stats()
    {
        int batteries = 0;
        boolean nightVision = false, spectral = false, exoskeleton = false;
        for (var placements : valid().values())
            for (var placement : placements)
                switch (placement.module())
                {
                    case BATTERY_CELL -> batteries++;
                    case NIGHT_VISION -> nightVision = true;
                    case SPECTRAL_LENS -> spectral = true;
                    case EXOSKELETON_POWER -> exoskeleton = true;
                }
        return new ArmorStats(batteries, nightVision, spectral, exoskeleton);
    }
}
