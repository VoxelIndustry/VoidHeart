package net.voxelindustry.voidheart.client.model;

import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameBlock;

public class PortalFrameVeinModel
{
    static void createPortalVeinQuads(BlockState state, RenderContext context, RenderMaterial outerMaterial)
    {
        for (Direction direction : Direction.values())
        {
            if (!PortalFrameBlock.hasPortalToSide(state, direction))
                continue;

            int rotation = MutableQuadView.BAKE_LOCK_UV;

            if (direction == Direction.UP)
                rotation += MutableQuadView.BAKE_FLIP_V;

            context.getEmitter()
                    .material(outerMaterial)
                    .square(direction, 0, 0, 1, 1, 0)
                    .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(Direction.NORTH), rotation)
                    .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                    .emit();

            for (Direction adjacent : Direction.values())
            {
                if (adjacent == direction || adjacent == direction.getOpposite())
                    continue;

                if (PortalFrameBlock.hasPortalToSide(state, direction.getOpposite()))
                {
                    int spriteRotation = MutableQuadView.BAKE_LOCK_UV;
                    if (adjacent == Direction.UP)
                        spriteRotation += MutableQuadView.BAKE_FLIP_V;
                    context.getEmitter()
                            .material(outerMaterial)
                            .square(adjacent, 0, 0, 1, 1, 0)
                            .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(Direction.NORTH), spriteRotation)
                            .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                            .emit();
                }
                else if (direction.getAxis().isHorizontal())
                {
                    if (direction.rotateYCounterclockwise() == adjacent)
                        context.getEmitter()
                                .material(outerMaterial)
                                .square(adjacent, 0, 0, 1, 1, 0)
                                .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(Direction.EAST), MutableQuadView.BAKE_LOCK_UV)
                                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                                .emit();
                    else if (direction.rotateYClockwise() == adjacent)
                        context.getEmitter()
                                .material(outerMaterial)
                                .square(adjacent, 0, 0, 1, 1, 0)
                                .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(Direction.WEST), MutableQuadView.BAKE_LOCK_UV)
                                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                                .emit();
                    else if (adjacent.getAxis().isVertical())
                    {
                        Direction spriteOrientation;

                        int spriteRotation = MutableQuadView.BAKE_LOCK_UV;

                        if (direction == Direction.NORTH)
                        {
                            spriteOrientation = Direction.DOWN;
                            spriteRotation += MutableQuadView.BAKE_FLIP_V;
                        }
                        else if (direction == Direction.SOUTH)
                        {
                            spriteOrientation = Direction.UP;
                            spriteRotation += MutableQuadView.BAKE_FLIP_V;
                        }
                        else if (direction == Direction.EAST)
                        {
                            spriteOrientation = Direction.WEST;
                            if (adjacent == Direction.UP)
                                spriteRotation += MutableQuadView.BAKE_FLIP_V;
                        }
                        else
                        {
                            spriteOrientation = Direction.EAST;
                            if (adjacent == Direction.UP)
                                spriteRotation += MutableQuadView.BAKE_FLIP_V;
                        }

                        context.getEmitter()
                                .material(outerMaterial)
                                .square(adjacent, 0, 0, 1, 1, 0)
                                .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(spriteOrientation), spriteRotation)
                                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                                .emit();
                    }
                }
                else
                {
                    if (direction == Direction.DOWN)
                        context.getEmitter()
                                .material(outerMaterial)
                                .square(adjacent, 0, 0, 1, 1, 0)
                                .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(Direction.UP), MutableQuadView.BAKE_LOCK_UV)
                                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                                .emit();
                    else
                        context.getEmitter()
                                .material(outerMaterial)
                                .square(adjacent, 0, 0, 1, 1, 0)
                                .spriteBake(0, PortalFrameVeinSpriteManager.getFrameSprite(Direction.DOWN), MutableQuadView.BAKE_LOCK_UV)
                                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                                .emit();
                }
            }
        }
    }
}
