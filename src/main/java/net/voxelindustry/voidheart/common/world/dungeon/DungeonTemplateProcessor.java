package net.voxelindustry.voidheart.common.world.dungeon;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

/**
 * Adapts old hand built pockets to life inside a dungeon: keeps only the part in use and removes what must not be found there, like working portals.
 */
final class DungeonTemplateProcessor implements StructureProcessor
{
    private static final int LIGHT_SPACING = 6;
    private static final int LIGHT_OFFSET = 2;
    private static final int LIGHT_LEVEL = 15;

    private final DungeonTemplate template;
    private final DungeonPalette palette;
    private final String lootTable;
    private final long lootSeed;

    DungeonTemplateProcessor(DungeonTemplate template, DungeonPalette palette, String lootTable, long lootSeed)
    {
        this.template = template;
        this.palette = palette;
        this.lootTable = lootTable;
        this.lootSeed = lootSeed;
    }

    @Override
    public StructureTemplate.@Nullable StructureBlockInfo processBlock(LevelReader level,
                                                                       BlockPos targetPosition,
                                                                       BlockPos referencePos,
                                                                       BlockPos templateRelativePos,
                                                                       StructureTemplate.StructureBlockInfo processedBlockInfo,
                                                                       StructurePlaceSettings settings)
    {
        if (!isKept(templateRelativePos))
            return null;

        var state = processedBlockInfo.state();
        var pos = processedBlockInfo.pos();
        if (state.isAir() && isLightSpot(templateRelativePos))
            return new StructureTemplate.StructureBlockInfo(pos, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, LIGHT_LEVEL), null);
        if (state.is(Blocks.STRUCTURE_BLOCK))
            return new StructureTemplate.StructureBlockInfo(pos, Blocks.AIR.defaultBlockState(), null);
        if (state.is(VoidHeartBlocks.POCKET_WALL))
            return new StructureTemplate.StructureBlockInfo(pos, palette.window(pos.getX(), pos.getY(), pos.getZ()), null);
        if (state.is(VoidHeartBlocks.VOID_MONOLITH))
            return new StructureTemplate.StructureBlockInfo(pos, state.setValue(StateProperties.BROKEN, true), null);
        if (state.is(VoidHeartBlocks.PORTAL_FRAME) || state.is(VoidHeartBlocks.PORTAL_FRAME_CORE))
            return new StructureTemplate.StructureBlockInfo(pos, VoidHeartBlocks.ELDRITCH_VOIDSTONE.defaultBlockState(), null);
        if (state.is(Blocks.CHEST))
        {
            var tag = new CompoundTag();
            tag.putString("LootTable", lootTable);
            tag.putLong("LootTableSeed", lootSeed + pos.asLong());
            return new StructureTemplate.StructureBlockInfo(pos, state, tag);
        }
        var redrawn = palette.redraw(state, pos.getX(), pos.getY(), pos.getZ());
        return redrawn == state ? processedBlockInfo : new StructureTemplate.StructureBlockInfo(pos, redrawn, processedBlockInfo.nbt());
    }

    /**
     * These pockets were built for a world with more ambient light than a dungeon has.
     */
    private static boolean isLightSpot(BlockPos pos)
    {
        return pos.getX() % LIGHT_SPACING == LIGHT_OFFSET && pos.getY() % LIGHT_SPACING == LIGHT_OFFSET && pos.getZ() % LIGHT_SPACING == LIGHT_OFFSET;
    }

    private boolean isKept(BlockPos pos)
    {
        var from = template.from();
        var size = template.size();
        return pos.getX() >= from.getX() && pos.getX() < from.getX() + size.getX()
               && pos.getY() >= from.getY() && pos.getY() < from.getY() + size.getY()
               && pos.getZ() >= from.getZ() && pos.getZ() < from.getZ() + size.getZ();
    }

    @Override
    public MapCodec<? extends StructureProcessor> codec()
    {
        return MapCodec.unit(this);
    }
}
