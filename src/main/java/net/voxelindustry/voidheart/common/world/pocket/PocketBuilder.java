package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static net.voxelindustry.voidheart.common.world.pocket.PocketGrid.WALL_REACH;

/**
 * Writes a plan into the world. Still water is placed without scheduling fluid ticks, since it has nowhere to flow.
 */
public final class PocketBuilder
{
    private static final int TERRAIN_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SKIP_ON_PLACE;
    private static final int ARRIVAL_CLEARANCE = 3;

    private PocketBuilder()
    {
    }

    public static PocketPlan build(ServerLevel level, BlockPos heart, PocketBlueprint blueprint)
    {
        var plan = PocketPlan.generate(blueprint);
        var palette = PocketPalette.of(blueprint.variant());

        tintBiome(level, heart, blueprint.variant());
        placeTerrain(level, heart, plan.grid(), palette);

        var decorator = new PocketDecorator(level, heart, blueprint.variant());
        plan.crystals().forEach(decorator::place);

        var decor = plan.decor();
        for (int i = 0; i < decor.size(); i++)
            decorator.place(decor.get(i), RandomSource.create(blueprint.seed() + i));

        clearArrival(level, heart);
        return plan;
    }

    private static void tintBiome(ServerLevel level, BlockPos heart, PocketVariant variant)
    {
        var biome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(PocketPalette.biome(variant));
        for (int chunkZ = SectionPos.blockToSectionCoord(heart.getZ() - WALL_REACH); chunkZ <= SectionPos.blockToSectionCoord(heart.getZ() + WALL_REACH); chunkZ++)
        {
            for (int chunkX = SectionPos.blockToSectionCoord(heart.getX() - WALL_REACH); chunkX <= SectionPos.blockToSectionCoord(heart.getX() + WALL_REACH); chunkX++)
            {
                var chunk = level.getChunk(chunkX, chunkZ);
                chunk.fillBiomesFromNoise((x, y, z) -> biome);
                chunk.markUnsaved();
                level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
            }
        }
    }

    private static void placeTerrain(ServerLevel level, BlockPos heart, PocketGrid grid, PocketPalette palette)
    {
        var cursor = new BlockPos.MutableBlockPos();
        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -WALL_REACH; z <= WALL_REACH; z++)
            {
                for (int x = -WALL_REACH; x <= WALL_REACH; x++)
                {
                    var slot = grid.slot(x, y, z);
                    if (slot != PocketSlot.AIR)
                        level.setBlock(cursor.set(heart.getX() + x, heart.getY() + y, heart.getZ() + z), palette.state(slot), TERRAIN_FLAGS);
                }
            }
        }
    }

    /**
     * Trees grow by their own rules and can reach over the heart: the spot players arrive at must stay free.
     */
    private static void clearArrival(ServerLevel level, BlockPos heart)
    {
        for (int y = 1; y <= ARRIVAL_CLEARANCE; y++)
        {
            for (int z = -1; z <= 1; z++)
            {
                for (int x = -1; x <= 1; x++)
                {
                    var pos = heart.offset(x, y, z);
                    if (!level.getBlockState(pos).isAir())
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }
}
