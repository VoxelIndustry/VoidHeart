package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.HatchOpening;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** A district's hatch as it lies in the world. Whether it is open is read from its plates, so an opened hatch stays open with nothing else saved. */
public record UnderwayHatch(UnderwayFort fort)
{
    private static final Direction[] FACING = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };
    private static final int MIDDLE = DistrictPos.CELL / 2;
    private static final int HEADROOM = 3;

    /** @return the hatch whose plates or coaming hold that block, if any */
    public static @Nullable UnderwayHatch at(ServerLevel level, BlockPos pos)
    {
        if (level.dimension() != VoidDaedalus.WORLD_KEY || pos.getY() != UnderwayLevels.HATCH_FLOOR || !DistrictPos.isInsideDistrict(pos.getX(), pos.getZ()))
            return null;
        var fort = UnderwayPlans.fort(level.getSeed(), DistrictPos.at(pos.getX(), pos.getZ()));
        int u = pos.getX() - fort.hatchX();
        int v = pos.getZ() - fort.hatchZ();
        return u >= 0 && u < DistrictPos.CELL && v >= 0 && v < DistrictPos.CELL ? new UnderwayHatch(fort) : null;
    }

    /** Where a key stands: on the line where the leaves meet. */
    public Vec3 keySpot()
    {
        return new Vec3(fort.hatchX() + MIDDLE, UnderwayLevels.HATCH_FLOOR + 1, fort.hatchZ() + MIDDLE);
    }

    public boolean isClosed(ServerLevel level)
    {
        return level.getBlockState(new BlockPos(fort.hatchX() + MIDDLE, UnderwayLevels.HATCH_FLOOR, fort.hatchZ() + MIDDLE)).is(VoidHeartBlocks.UNDERWAY_HATCH);
    }

    public AABB above()
    {
        int floor = UnderwayLevels.HATCH_FLOOR + 1;
        return new AABB(fort.hatchX(), floor, fort.hatchZ(), fort.hatchX() + DistrictPos.CELL, floor + HEADROOM, fort.hatchZ() + DistrictPos.CELL);
    }

    public void retreat(ServerLevel level, int stage)
    {
        apply(level, HatchOpening.retreat(fort, stage));
    }

    public void layStairs(ServerLevel level)
    {
        apply(level, HatchOpening.stairs(fort));
    }

    private static void apply(ServerLevel level, List<HatchOpening.Change> changes)
    {
        for (var change : changes)
            level.setBlock(new BlockPos(change.x(), change.y(), change.z()), state(change), Block.UPDATE_ALL);
    }

    private static BlockState state(HatchOpening.Change change)
    {
        return switch (change.part())
        {
            case AIR -> Blocks.AIR.defaultBlockState();
            case STEP -> VoidHeartBlocks.UNDERWAY_STEEL_STAIRS.defaultBlockState().setValue(StairBlock.FACING, FACING[change.climb()]);
            case LANDING, CAP -> VoidHeartBlocks.UNDERWAY_GRATING.defaultBlockState();
        };
    }
}
