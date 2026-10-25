package net.voxelindustry.voidheart.client.model.portalframe;

import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;

public class PortalFrameBakedModel extends WrapperBlockStateModel
{
    public PortalFrameBakedModel(BlockStateModel wrapped)
    {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest)
    {
        var variant = random.nextInt(2);

        emitter.pushTransform(quad ->
        {
            if (quad.tag() == 0)
            {
                var direction = quad.nominalFace();

                // The quad is directly facing a portal interior
                if (PortalFrameStateProperties.getSideConnection(state, direction).isConnected())
                {
                    var sprite = PortalFrameVeinSpriteManager.getBackgroundSpriteForFront(variant);
                    quad.materialBake(sprite, MutableQuadView.BAKE_LOCK_UV);
                    return true;
                }

                var left = FrameConnection.NONE;
                var right = FrameConnection.NONE;
                var up = FrameConnection.NONE;
                var down = FrameConnection.NONE;

                if (direction.getAxis().isVertical())
                {
                    if (direction == Direction.UP)
                    {
                        left = PortalFrameStateProperties.getSideConnection(state, Direction.EAST);
                        right = PortalFrameStateProperties.getSideConnection(state, Direction.WEST);
                        up = PortalFrameStateProperties.getSideConnection(state, Direction.NORTH);
                        down = PortalFrameStateProperties.getSideConnection(state, Direction.SOUTH);
                    }
                    else
                    {
                        left = PortalFrameStateProperties.getSideConnection(state, Direction.EAST);
                        right = PortalFrameStateProperties.getSideConnection(state, Direction.WEST);
                        up = PortalFrameStateProperties.getSideConnection(state, Direction.SOUTH);
                        down = PortalFrameStateProperties.getSideConnection(state, Direction.NORTH);
                    }
                }
                else
                {
                    left = PortalFrameStateProperties.getSideConnection(state, direction.getCounterClockWise());
                    right = PortalFrameStateProperties.getSideConnection(state, direction.getClockWise());
                    up = PortalFrameStateProperties.getSideConnection(state, Direction.UP);
                    down = PortalFrameStateProperties.getSideConnection(state, Direction.DOWN);
                }

                var sprite = PortalFrameVeinSpriteManager.getBackgroundSpriteForSide(left, right, up, down, variant);
                quad.materialBake(sprite, MutableQuadView.BAKE_LOCK_UV);
            }
            return true;
        });

        super.emitQuads(emitter, blockView, pos, state, random, cullTest);
        emitter.popTransform();

        if (state.getValue(BlockStateProperties.LIT))
            PortalFrameVeinModel.createPortalVeinQuadsAllDirections(state, emitter, variant);
    }
}