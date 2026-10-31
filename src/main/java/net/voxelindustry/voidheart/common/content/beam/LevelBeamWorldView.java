package net.voxelindustry.voidheart.common.content.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

public class LevelBeamWorldView implements BeamWorldView
{
    private final Level level;

    public LevelBeamWorldView(Level level)
    {
        this.level = level;
    }

    @Override
    public BeamNodeKind kindAt(BlockPos pos)
    {
        var block = level.getBlockState(pos).getBlock();

        if (block == VoidHeartBlocks.VOID_CONDUIT)
            return BeamNodeKind.CONDUIT;
        if (block == VoidHeartBlocks.VOID_MONOLITH)
            return BeamNodeKind.MONOLITH;
        if (block == VoidHeartBlocks.VOID_PILLAR)
            return BeamNodeKind.PILLAR;
        if (block == VoidHeartBlocks.RAVENOUS_COLLECTOR)
            return BeamNodeKind.COLLECTOR;
        if (block == VoidHeartBlocks.BEAM_FOCUS)
            return BeamNodeKind.FOCUS;
        if (block == VoidHeartBlocks.SHATTER_FORGE)
            return BeamNodeKind.FORGE;
        if (block == VoidHeartBlocks.EXPERIENCE_SKULL)
            return BeamNodeKind.EXPERIENCE_SKULL;
        return BeamNodeKind.OTHER;
    }

    @Override
    public EssenceType collectorAspect(BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof RavenousCollectorTile collector)
            return collector.storage().type();
        return EssenceRegistry.EMPTY;
    }

    @Override
    public Direction focusFacing(BlockPos pos)
    {
        return BeamFocusBlock.getOutputFace(level.getBlockState(pos));
    }

    @Override
    public Direction.Axis forgeAxis(BlockPos pos)
    {
        return level.getBlockState(pos).getValue(BlockStateProperties.HORIZONTAL_AXIS);
    }
}
