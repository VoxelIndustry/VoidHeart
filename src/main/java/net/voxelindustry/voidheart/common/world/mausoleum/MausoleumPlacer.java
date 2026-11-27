package net.voxelindustry.voidheart.common.world.mausoleum;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.voxelindustry.voidheart.common.content.mausoleum.SarcophagusBlock;
import net.voxelindustry.voidheart.common.content.shroud.ShroudAltarBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusHash;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;

import java.util.concurrent.ExecutionException;

/**
 * Writes the part of a mausoleum falling in one chunk, and posts its rider once, as that chunk generates.
 */
public final class MausoleumPlacer
{
    private static final long SALT  = 0x4D4155534FL;
    private static final int  FLAGS = Block.UPDATE_CLIENTS;

    private static final Cache<Long, MausoleumPlan> PLANS = CacheBuilder.newBuilder().maximumSize(4).build();

    private static BlockState[] states;

    private MausoleumPlacer()
    {
    }

    public static MausoleumPlan plan(long worldSeed, int index)
    {
        long seed = DaedalusHash.mix(worldSeed, index, 0, SALT);
        try
        {
            return PLANS.get(seed, () -> MausoleumPlan.generate(seed));
        } catch (ExecutionException e)
        {
            throw new IllegalStateException("Could not plan mausoleum " + index, e);
        }
    }

    /** @return the mausoleum's gate, in the world */
    public static ShroudGate gate(BlockPos origin)
    {
        var gate = MausoleumPlan.GATE;
        return new ShroudGate(origin.getX() + gate.x(), origin.getY() + gate.y(), origin.getZ() + gate.z(), gate.facing());
    }

    /** @return whether the chunk lies in a mausoleum's cell, so the dungeons have nothing to write there */
    public static boolean place(WorldGenLevel level, ChunkAccess chunk)
    {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        int index = MausoleumAddress.at(minX, minZ);
        if (index < 0)
            return false;

        var origin = MausoleumAddress.origin(index);
        int x0 = Math.max(0, minX - origin.getX()), x1 = Math.min(MausoleumPlan.WIDTH, minX + 16 - origin.getX());
        int z0 = Math.max(0, minZ - origin.getZ()), z1 = Math.min(MausoleumPlan.LENGTH, minZ + 16 - origin.getZ());
        if (x0 >= x1 || z0 >= z1)
            return true;

        var plan = plan(level.getSeed(), index);
        var states = states();
        var pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < MausoleumPlan.HEIGHT; y++)
        {
            for (int z = z0; z < z1; z++)
            {
                for (int x = x0; x < x1; x++)
                {
                    var material = plan.material(x, y, z);
                    if (material == MausoleumMaterial.AIR)
                        continue;
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    chunk.setBlockState(pos, states[material.ordinal()], 0);
                }
            }
        }
        placeGate(level, origin, minX, minZ);
        for (var orb : plan.orbs())
        {
            if (orb.x() >= x0 && orb.x() < x1 && orb.z() >= z0 && orb.z() < z1)
                level.setBlock(origin.offset(orb.x(), orb.y(), orb.z()), VoidHeartBlocks.FUNERARY_ORB.defaultBlockState(), FLAGS);
        }
        placeSarcophagus(level, origin, x0, x1, z0, z1);
        if (MausoleumPlan.RIDER_X >= x0 && MausoleumPlan.RIDER_X < x1 && MausoleumPlan.RIDER_Z >= z0 && MausoleumPlan.RIDER_Z < z1)
            postRider(level, origin);
        return true;
    }

    private static void placeGate(WorldGenLevel level, BlockPos origin, int minX, int minZ)
    {
        var gate = gate(origin);
        gate.forEach((x, y, z, part) ->
        {
            if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16)
                return;
            var state = ShroudBlocks.state(gate, x, y, z);
            if (part == ShroudGate.Part.ALTAR)
                state = state.setValue(ShroudAltarBlock.KEPT, true);
            level.setBlock(new BlockPos(x, y, z), state, FLAGS);
        });
    }

    private static void placeSarcophagus(WorldGenLevel level, BlockPos origin, int x0, int x1, int z0, int z1)
    {
        var head = ShroudBlocks.direction(MausoleumPlan.SARCOPHAGUS_HEAD);
        for (var part : SarcophagusBlock.Part.values())
        {
            int x = MausoleumPlan.SARCOPHAGUS_X + head.getStepX() * part.offset;
            int z = MausoleumPlan.SARCOPHAGUS_Z + head.getStepZ() * part.offset;
            if (x < x0 || x >= x1 || z < z0 || z >= z1)
                continue;
            var state = VoidHeartBlocks.SARCOPHAGUS.defaultBlockState().setValue(SarcophagusBlock.FACING, head).setValue(SarcophagusBlock.PART, part);
            level.setBlock(origin.offset(x, MausoleumPlan.SARCOPHAGUS_Y, z), state, FLAGS);
        }
    }

    private static void postRider(WorldGenLevel level, BlockPos origin)
    {
        var rider = VoidHeartEntities.DULLAHAN.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
        if (rider == null)
            return;
        var pos = origin.offset(MausoleumPlan.RIDER_X, MausoleumPlan.RIDER_Y, MausoleumPlan.RIDER_Z);
        rider.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, MausoleumPlan.RIDER_YAW, 0);
        rider.setYHeadRot(MausoleumPlan.RIDER_YAW);
        rider.setYBodyRot(MausoleumPlan.RIDER_YAW);
        rider.setPersistenceRequired();
        rider.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
        level.addFreshEntity(rider);
    }

    private static synchronized BlockState[] states()
    {
        if (states != null)
            return states;
        var built = new BlockState[MausoleumMaterial.values().length];
        built[MausoleumMaterial.AIR.ordinal()] = Blocks.AIR.defaultBlockState();
        built[MausoleumMaterial.FOUNDATION.ordinal()] = VoidHeartBlocks.VOIDSTONE.defaultBlockState();
        built[MausoleumMaterial.WALL.ordinal()] = VoidHeartBlocks.VOIDSTONE_BRICKS.defaultBlockState();
        built[MausoleumMaterial.WALL_CRACKED.ordinal()] = VoidHeartBlocks.VOIDSTONE_BRICKS_CRACKED.defaultBlockState();
        built[MausoleumMaterial.GLYPHS.ordinal()] = VoidHeartBlocks.VOIDSTONE_GLYPHS.defaultBlockState();
        built[MausoleumMaterial.PANEL.ordinal()] = VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState();
        built[MausoleumMaterial.TRIM.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CHISELED.defaultBlockState();
        built[MausoleumMaterial.CORNICE.ordinal()] = VoidHeartBlocks.VOIDSTONE_CHISELED.defaultBlockState();
        built[MausoleumMaterial.COLUMN.ordinal()] = VoidHeartBlocks.VOIDSTONE_PILLAR.defaultBlockState();
        built[MausoleumMaterial.BAND.ordinal()] = VoidHeartBlocks.BRONZE_INLAID_VOIDSTONE.defaultBlockState();
        built[MausoleumMaterial.CAPITAL.ordinal()] = VoidHeartBlocks.VOIDSTONE_CHISELED.defaultBlockState();
        built[MausoleumMaterial.FLOOR.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE.defaultBlockState();
        built[MausoleumMaterial.INLAY.ordinal()] = VoidHeartBlocks.VOIDSTONE_TILE_SMALL_CARVED.defaultBlockState();
        built[MausoleumMaterial.GILT.ordinal()] = VoidHeartBlocks.BRONZE_INLAID_VOIDSTONE.defaultBlockState();
        built[MausoleumMaterial.CEILING.ordinal()] = VoidHeartBlocks.VOIDSTONE_STARRED.defaultBlockState();
        built[MausoleumMaterial.DAIS.ordinal()] = VoidHeartBlocks.VOIDSTONE_POLISHED.defaultBlockState();
        built[MausoleumMaterial.FALSE_DOOR.ordinal()] = VoidHeartBlocks.VOIDSTONE_ENGRAVED_EYE.defaultBlockState();
        states = built;
        return built;
    }
}
