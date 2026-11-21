package net.voxelindustry.voidheart.common.energy.grid;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where Deep azurium's fields reach through open portal-frame pairs. A Deep pylon's field holding a portal's core continues out of its partner, with
 * what is left of its radius past that core, so machines on the far side draw from the grid at home. Only portal frames carry it, never the armillary's
 * rifts.
 * <p>
 * Each grid lists the reaches its Deep pylons make when it rebuilds, and a far-side draw is taken at the near core, which lies inside the Deep field.
 */
public final class DeepReach
{
    /** Reaches not rebuilt this long ago belong to a grid that is gone. */
    private static final int STALE_TICKS = 200;

    /**
     * @param bundle     the Deep bundle whose field reaches through
     * @param nearCore   the portal core inside that field, where far draws are taken
     * @param nearMiddle the middle of the near portal, where the thread runs to
     * @param farCore    the partner core, which the reach is centered on
     * @param radius     what is left of the field past the far core
     */
    public record Reach(ResourceKey<Level> nearLevel, BlockPos bundle, BlockPos nearCore, Vec3 nearMiddle, ResourceKey<Level> farLevel, BlockPos farCore,
                        double radius, long builtAt)
    {
        public boolean covers(BlockPos pos)
        {
            return farCore.distSqr(pos) <= radius * radius;
        }
    }

    private static final Map<ResourceKey<Level>, List<Reach>> BY_NEAR = new HashMap<>();
    private static final Map<ResourceKey<Level>, List<Reach>> BY_FAR  = new HashMap<>();

    private DeepReach()
    {
    }

    /**
     * The reaches a Deep pylon's field makes through the portal cores it holds, open ones only.
     */
    static List<Reach> through(ServerLevel level, BlockPos bundle, int radius)
    {
        var reaches = new ArrayList<Reach>();
        for (int chunkX = (bundle.getX() - radius) >> 4; chunkX <= (bundle.getX() + radius) >> 4; chunkX++)
        {
            for (int chunkZ = (bundle.getZ() - radius) >> 4; chunkZ <= (bundle.getZ() + radius) >> 4; chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;
                for (var entity : chunk.getBlockEntities().values())
                {
                    if (!(entity instanceof PortalFrameCoreTile core) || core.getLink() == null || core.isBroken() || core.isDark())
                        continue;
                    double left = radius - Math.sqrt(bundle.distSqr(core.getBlockPos()));
                    if (left <= 0)
                        continue;
                    var link = core.getLink();
                    reaches.add(new Reach(level.dimension(), bundle.immutable(), core.getBlockPos().immutable(), core.getPortalMiddlePos(true),
                            link.world(), link.pos().immutable(), left, level.getServer().getTickCount()));
                }
            }
        }
        return reaches;
    }

    /**
     * Replaces the reaches a level's grid makes.
     */
    static void set(ServerLevel level, List<Reach> reaches)
    {
        if (reaches.isEmpty() && !BY_NEAR.containsKey(level.dimension()))
            return;
        if (reaches.isEmpty())
            BY_NEAR.remove(level.dimension());
        else
            BY_NEAR.put(level.dimension(), List.copyOf(reaches));
        BY_FAR.clear();
        BY_NEAR.values().forEach(list -> list.forEach(reach -> BY_FAR.computeIfAbsent(reach.farLevel(), key -> new ArrayList<>()).add(reach)));
    }

    /**
     * The reaches coming out into a level, fresh ones only.
     */
    public static List<Reach> into(ServerLevel level)
    {
        var reaches = BY_FAR.get(level.dimension());
        if (reaches == null)
            return List.of();
        long now = level.getServer().getTickCount();
        return reaches.stream().filter(reach -> now - reach.builtAt() <= STALE_TICKS).toList();
    }

    /**
     * What is left of a Deep field past this portal core, or 0 when none reaches out of it.
     */
    public static float reachAt(ServerLevel level, BlockPos core)
    {
        float best = 0;
        for (var reach : into(level))
        {
            if (reach.farCore().equals(core))
                best = Math.max(best, (float) reach.radius());
        }
        return best;
    }

    /**
     * Takes up to amount for pos through the Deep fields reaching it, from the grids they belong to.
     */
    public static long draw(ServerLevel level, BlockPos pos, long amount)
    {
        long got = 0;
        for (var reach : into(level))
        {
            if (got >= amount)
                break;
            if (!reach.covers(pos))
                continue;
            var near = level.getServer().getLevel(reach.nearLevel());
            if (near != null)
                got += VoidEnergy.drawHere(near, reach.nearCore(), amount - got);
        }
        return got;
    }

    public static long available(ServerLevel level, BlockPos pos)
    {
        long available = 0;
        // Reaches out of one core share that core's pool, so it counts once
        var counted = new java.util.HashSet<BlockPos>();
        for (var reach : into(level))
        {
            if (!reach.covers(pos) || !counted.add(reach.nearCore()))
                continue;
            var near = level.getServer().getLevel(reach.nearLevel());
            if (near == null)
                continue;
            long here = VoidEnergy.availableHere(near, reach.nearCore());
            available = here >= Long.MAX_VALUE / 2 - available ? Long.MAX_VALUE / 2 : available + here;
        }
        return available;
    }

    public static void clear()
    {
        BY_NEAR.clear();
        BY_FAR.clear();
    }
}
