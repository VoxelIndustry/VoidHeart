package net.voxelindustry.voidheart.common.content.salt;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.content.salt.SaltPatch.Cell;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Laying salt in the world, and what mobs ask of it.
 */
public final class SaltWard
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "salt"));

    /**
     * How much each family would rather go around salt, through the cautious path type salt is registered with. Wraiths and barnacles refuse it outright.
     */
    public static final float FALLEN_MALUS = 8;
    public static final float GHOUL_MALUS = 4;
    public static final float CRAB_MALUS = 2;

    private static final int FALL_DEPTH = 4;
    private static final int CRACKLE_PARTICLES = 6;
    private static final double CRACKLE_SPREAD = 0.3;
    private static final float CRACKLE_VOLUME = 0.35F;
    private static final float CRACKLE_PITCH = 1.8F;
    private static final int FLOOD_LIMIT = SaltPatch.MAX_SIZE * SaltPatch.MAX_SIZE * 2;

    private SaltWard()
    {
    }

    public static boolean isSalt(LevelReader level, BlockPos pos)
    {
        return level.getBlockState(pos).is(VoidHeartBlocks.SALT_WARD);
    }

    /**
     * @return whether the mob stands in salt
     */
    public static boolean isOn(Entity entity)
    {
        return isSalt(entity.level(), entity.blockPosition());
    }

    /**
     * @return whether any salt lies within a block of this spot, which is too close for a wraith to come out of a wall or a barnacle to settle
     */
    public static boolean isWarded(LevelReader level, BlockPos pos)
    {
        for (var near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
        {
            if (isSalt(level, near))
                return true;
        }
        return false;
    }

    /**
     * @return whether salt lies within a block of this spot or of anywhere it could fall to, so a wraith coming out of a wall high up can't drop into a ward
     */
    public static boolean isWardedBelow(LevelReader level, BlockPos pos)
    {
        return wardBelow(level, pos) != null;
    }

    /**
     * @return the salt that wards the spot, as {@link #isWardedBelow} finds it, or null
     */
    public static @Nullable BlockPos wardBelow(LevelReader level, BlockPos pos)
    {
        for (var near : BlockPos.betweenClosed(pos.offset(-1, -FALL_DEPTH, -1), pos.offset(1, 1, 1)))
        {
            if (isSalt(level, near))
                return near.immutable();
        }
        return null;
    }

    /**
     * The brief white crackle along salt that just turned something away or burned it.
     */
    public static void crackle(ServerLevel level, BlockPos pos)
    {
        level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, CRACKLE_PARTICLES, CRACKLE_SPREAD, 0.05, CRACKLE_SPREAD, 0);
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, CRACKLE_VOLUME, CRACKLE_PITCH);
    }

    /**
     * Lays salt from a use on a block: a fresh 3x3 on bare ground, or one more ring around the patch that was used on.
     *
     * @return how many blocks of salt were laid, at most the given number
     */
    public static int lay(ServerLevel level, BlockPos clicked, Direction face, int maxCells)
    {
        BlockPos start = isSalt(level, clicked) ? clicked : face == Direction.UP && isSalt(level, clicked.above()) ? clicked.above() : null;

        List<Cell> plan;
        Map<Cell, Integer> heights;
        if (start != null)
        {
            heights = flood(level, start);
            plan = SaltPatch.ring(heights.keySet(), cellOf(start));
        }
        else
        {
            if (face != Direction.UP)
                return 0;
            var center = clicked.above();
            heights = Map.of(cellOf(center), center.getY());
            plan = SaltPatch.fresh(cellOf(center));
        }

        var block = (SaltWardBlock) VoidHeartBlocks.SALT_WARD;
        int laid = 0;
        for (var cell : plan)
        {
            if (laid >= maxCells)
                break;
            Integer y = heights.get(cell);
            if (y == null)
                y = heightNear(heights, cell);
            if (y == null)
                continue;
            var pos = floorAt(level, cell, y);
            if (pos == null)
                continue;
            level.setBlock(pos, block.stateAt(level, pos), Block.UPDATE_ALL);
            laid++;
        }
        if (laid > 0)
            level.playSound(null, clicked, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1, 1.2F);
        return laid;
    }

    /**
     * The salt joined to this one, each block's floor plan cell with its height, stepping a block up or down as the floor does.
     */
    private static Map<Cell, Integer> flood(LevelReader level, BlockPos start)
    {
        Map<Cell, Integer> found = new HashMap<>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(start);
        found.put(cellOf(start), start.getY());
        while (!queue.isEmpty() && found.size() < FLOOD_LIMIT)
        {
            var pos = queue.poll();
            for (var direction : Direction.Plane.HORIZONTAL)
            {
                for (int dy = -1; dy <= 1; dy++)
                {
                    var next = pos.relative(direction).above(dy);
                    var cell = cellOf(next);
                    if (!found.containsKey(cell) && isSalt(level, next))
                    {
                        found.put(cell, next.getY());
                        queue.add(next);
                    }
                }
            }
        }
        return found;
    }

    private static @Nullable Integer heightNear(Map<Cell, Integer> heights, Cell cell)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                var y = heights.get(new Cell(cell.x() + dx, cell.z() + dz));
                if (y != null)
                    return y;
            }
        }
        return null;
    }

    /**
     * @return where salt can lie in this cell, at the given height or a step up or down from it
     */
    private static @Nullable BlockPos floorAt(LevelReader level, Cell cell, int y)
    {
        for (int dy : new int[] { 0, 1, -1 })
        {
            var pos = new BlockPos(cell.x(), y + dy, cell.z());
            var state = level.getBlockState(pos);
            if ((state.isAir() || state.canBeReplaced()) && level.getFluidState(pos).isEmpty() && !state.is(VoidHeartBlocks.SALT_WARD) && SaltWardBlock.canLayOn(level, pos))
                return pos;
        }
        return null;
    }

    private static Cell cellOf(BlockPos pos)
    {
        return new Cell(pos.getX(), pos.getZ());
    }
}
