package net.voxelindustry.voidheart.client.model.portalframe;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.resources.model.sprite.Material;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;

public class PortalFrameVeinModel
{
    static void createPortalVeinQuadsAllDirections(BlockState state, QuadEmitter emitter, int variant)
    {
        for (Direction direction : Direction.values())
            createPortalVeinQuads(direction, state, emitter, variant);
    }

    static void createPortalVeinQuadsExpectDirection(Direction forbiddenDirection, BlockState state, QuadEmitter emitter, int variant)
    {
        for (Direction direction : Direction.values())
        {
            if (direction == forbiddenDirection)
                continue;
            createPortalVeinQuads(direction, state, emitter, variant);
        }
    }

    static void createPortalVeinQuads(Direction direction, BlockState state, QuadEmitter emitter, int variant)
    {
        int uvFlag = MutableQuadView.BAKE_LOCK_UV;

        var sprite = getSpriteForState(state, direction, variant);
        if (sprite == null)
            return;

        emitter
                .chunkLayer(ChunkSectionLayer.CUTOUT)
                .emissive(true)
                .square(direction, 0, 0, 1, 1, 0)
                .cullFace(direction)
                .materialBake(sprite, uvFlag)
                .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                .tag(1)
                .emit();
    }

    static Material.Baked getSpriteForState(BlockState state, Direction direction, int variant)
    {
        // The quad is directly facing a portal interior
        if (PortalFrameStateProperties.getSideConnection(state, direction).isConnected())
        {
            return PortalFrameVeinSpriteManager.getOverlaySpriteForFront(variant);
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

        return PortalFrameVeinSpriteManager.getOverlaySpriteForSide(left, right, up, down, variant);
    }
}
