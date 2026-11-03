package net.voxelindustry.voidheart.client.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.content.beam.BeamNodeKind;
import net.voxelindustry.voidheart.common.content.beam.BeamWorldView;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scene's finished structure as the beam's rules see a world.
 */
public final class SceneBeamView implements BeamWorldView
{
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();

    SceneBeamView(List<Scene.Place> finished)
    {
        finished.forEach(place -> blocks.put(place.pos(), place.state()));
    }

    @Override
    public BeamNodeKind kindAt(BlockPos pos)
    {
        var state = blocks.get(pos);
        if (state == null)
            return BeamNodeKind.OTHER;
        if (state.is(VoidHeartBlocks.VOID_CONDUIT))
            return BeamNodeKind.CONDUIT;
        if (state.is(VoidHeartBlocks.VOID_MONOLITH))
            return BeamNodeKind.MONOLITH;
        if (state.is(VoidHeartBlocks.VOID_PILLAR))
            return BeamNodeKind.PILLAR;
        if (state.is(VoidHeartBlocks.SHATTER_FORGE))
            return BeamNodeKind.FORGE;
        return BeamNodeKind.OTHER;
    }

    @Override
    public EssenceType collectorAspect(BlockPos pos)
    {
        return EssenceRegistry.EMPTY;
    }

    @Override
    public Direction focusFacing(BlockPos pos)
    {
        return Direction.NORTH;
    }

    @Override
    public Direction.Axis forgeAxis(BlockPos pos)
    {
        return blocks.get(pos).getValue(BlockStateProperties.HORIZONTAL_AXIS);
    }
}
