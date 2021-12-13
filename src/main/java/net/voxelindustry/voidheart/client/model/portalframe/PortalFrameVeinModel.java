package net.voxelindustry.voidheart.client.model.portalframe;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;

public class PortalFrameVeinModel
{
    static void createPortalVeinQuadsAllDirections(BlockState state, RenderContext context, RenderMaterial outerMaterial, int variant)
    {
        for (Direction direction : Direction.values())
            createPortalVeinQuads(direction, state, context, outerMaterial, variant);
    }

    static void createPortalVeinQuadsExpectDirection(Direction forbiddenDirection, BlockState state, RenderContext context, RenderMaterial outerMaterial, int variant)
    {
        for (Direction direction : Direction.values())
        {
            if (direction == forbiddenDirection)
                continue;
            createPortalVeinQuads(direction, state, context, outerMaterial, variant);
        }
    }

    static void createPortalVeinQuads(Direction direction, BlockState state, RenderContext context, RenderMaterial outerMaterial, int variant)
    {
        int uvFlag = MutableQuadView.BAKE_LOCK_UV;

        var sprite = getSpriteForState(state, direction, variant);
        if (sprite == null)
            return;

        context.getEmitter()
                .material(outerMaterial)
                .square(direction, 0, 0, 1, 1, 0)
                .spriteBake(0, sprite, uvFlag)
                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                .tag(1)
                .emit();
    }

    static Sprite getSpriteForState(BlockState state, Direction direction, int variant)
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
            left = PortalFrameStateProperties.getSideConnection(state, direction.rotateYCounterclockwise());
            right = PortalFrameStateProperties.getSideConnection(state, direction.rotateYClockwise());
            up = PortalFrameStateProperties.getSideConnection(state, Direction.UP);
            down = PortalFrameStateProperties.getSideConnection(state, Direction.DOWN);
        }

        return PortalFrameVeinSpriteManager.getOverlaySpriteForSide(left, right, up, down, variant);
    }
}
