package net.voxelindustry.voidheart.common.world.sanctum;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.voxelindustry.voidheart.common.content.sanctum.VeilGate;
import net.voxelindustry.voidheart.common.content.underway.UnderwayPropBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;

import java.util.concurrent.ExecutionException;

/**
 * Writes the part of a sanctum falling in one chunk, and posts what lives in it once, as that chunk generates.
 */
public final class SanctumPlacer
{
    private static final long SALT  = 0x53414E4354L;
    private static final int  FLAGS = Block.UPDATE_CLIENTS;

    private static final Cache<Long, SanctumPlan> PLANS = CacheBuilder.newBuilder().maximumSize(4).build();

    private static BlockState[] states;

    private SanctumPlacer()
    {
    }

    public static SanctumPlan plan(long worldSeed, int index)
    {
        long seed = DaedalusHash.mix(worldSeed, index, 0, SALT);
        try
        {
            return PLANS.get(seed, () -> SanctumPlan.generate(seed));
        } catch (ExecutionException e)
        {
            throw new IllegalStateException("Could not plan sanctum " + index, e);
        }
    }

    /** @return whether the chunk lies in a sanctum's cell, so the dungeons have nothing to write there */
    public static boolean place(WorldGenLevel level, ChunkAccess chunk)
    {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int index = SanctumAddress.at(minX, minZ);
        if (index < 0)
            return false;

        var origin = SanctumAddress.origin(index);
        int x0 = Math.max(0, minX - origin.getX()), x1 = Math.min(SanctumPlan.WIDTH, minX + 16 - origin.getX());
        int z0 = Math.max(0, minZ - origin.getZ()), z1 = Math.min(SanctumPlan.LENGTH, minZ + 16 - origin.getZ());
        if (x0 >= x1 || z0 >= z1)
            return true;

        var plan = plan(level.getSeed(), index);
        var states = states();
        var pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < SanctumPlan.HEIGHT; y++)
        {
            for (int z = z0; z < z1; z++)
            {
                for (int x = x0; x < x1; x++)
                {
                    var material = plan.material(x, y, z);
                    if (material == SanctumMaterial.AIR)
                        continue;
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    chunk.setBlockState(pos, states[material.ordinal()], 0);
                }
            }
        }
        for (var prop : plan.props())
        {
            if (prop.x() < x0 || prop.x() >= x1 || prop.z() < z0 || prop.z() >= z1)
                continue;
            pos.set(origin.getX() + prop.x(), origin.getY() + prop.y(), origin.getZ() + prop.z());
            var state = VoidHeartBlocks.underwayProp(prop.prop()).defaultBlockState().setValue(UnderwayPropBlock.FACING, prop.facing());
            level.setBlock(pos, state, FLAGS);
        }
        for (var post : plan.posts())
        {
            if (post.x() >= x0 && post.x() < x1 && post.z() >= z0 && post.z() < z1)
                post(level, origin, post);
        }
        if (SanctumPlan.ARRIVAL_X >= x0 && SanctumPlan.ARRIVAL_X < x1 && SanctumPlan.GATE_Z + 1 >= z0 && SanctumPlan.GATE_Z + 1 < z1)
        {
            var gate = VeilGate.returnGate(level.getLevel(), origin);
            level.addFreshEntity(gate);
        }
        return true;
    }

    private static void post(WorldGenLevel level, BlockPos origin, SanctumPlan.Post post)
    {
        var type = switch (post.kind())
        {
            case MASS -> VoidHeartEntities.VILE_FLESH_MASS;
            case SHOGGOTH -> VoidHeartEntities.LESSER_SHOGGOTH;
        };
        Mob mob = type.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (mob == null)
            return;
        var pos = origin.offset(post.x(), post.y(), post.z());
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, post.yaw(), 0);
        mob.setYHeadRot(post.yaw());
        mob.setYBodyRot(post.yaw());
        mob.setPersistenceRequired();
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        level.addFreshEntityWithPassengers(mob);
    }

    private static synchronized BlockState[] states()
    {
        if (states != null)
            return states;
        var built = new BlockState[SanctumMaterial.values().length];
        built[SanctumMaterial.AIR.ordinal()] = Blocks.AIR.defaultBlockState();
        built[SanctumMaterial.FOUNDATION.ordinal()] = VoidHeartBlocks.VOIDSTONE.defaultBlockState();
        built[SanctumMaterial.WALL.ordinal()] = VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState();
        built[SanctumMaterial.WALL_CRACKED.ordinal()] = VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED.defaultBlockState();
        built[SanctumMaterial.PANEL.ordinal()] = VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState();
        built[SanctumMaterial.TRIM.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CHISELED.defaultBlockState();
        built[SanctumMaterial.COLUMN.ordinal()] = VoidHeartBlocks.VOIDSTONE_PILLAR.defaultBlockState();
        built[SanctumMaterial.PLINTH.ordinal()] = VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState();
        built[SanctumMaterial.CAPITAL.ordinal()] = VoidHeartBlocks.VOIDSTONE_CHISELED.defaultBlockState();
        built[SanctumMaterial.FLOOR.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE.defaultBlockState();
        built[SanctumMaterial.INLAY.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CARVED.defaultBlockState();
        built[SanctumMaterial.GILT.ordinal()] = VoidHeartBlocks.BRONZE_INLAID_VOIDSTONE.defaultBlockState();
        built[SanctumMaterial.CEILING.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE_SMALL.defaultBlockState();
        built[SanctumMaterial.COFFER.ordinal()] = VoidHeartBlocks.VOIDSTONE_ENGRAVED_EYE.defaultBlockState();
        built[SanctumMaterial.GATE_FRAME.ordinal()] = VoidHeartBlocks.ELDRITCH_VOIDSTONE.defaultBlockState();
        built[SanctumMaterial.FLESH.ordinal()] = VoidHeartBlocks.VILE_FLESH.defaultBlockState();
        built[SanctumMaterial.FLESH_BRUISED.ordinal()] = VoidHeartBlocks.VILE_FLESH_BRUISED.defaultBlockState();
        built[SanctumMaterial.WRITHING.ordinal()] = VoidHeartBlocks.VOIDSTONE_WRITHING.defaultBlockState();
        built[SanctumMaterial.WRITHING_MAW.ordinal()] = VoidHeartBlocks.VOIDSTONE_WRITHING_MAW.defaultBlockState();
        built[SanctumMaterial.WRITHING_BLOSSOM.ordinal()] = VoidHeartBlocks.VOIDSTONE_WRITHING_BLOSSOM.defaultBlockState();
        states = built;
        return built;
    }
}
