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
            return PortalFrameVeinSpriteManager.getOverlaySpriteForFront(variant);

        var edges = Edges.of(state, direction);
        return PortalFrameVeinSpriteManager.getOverlaySpriteForSide(edges.left, edges.right, edges.up, edges.down, variant);
    }

    /**
     * The frame's own texture on a face, its veins running toward the frame blocks around it.
     */
    static Material.Baked backgroundSprite(BlockState state, Direction direction, int variant)
    {
        // The quad is directly facing a portal interior
        if (PortalFrameStateProperties.getSideConnection(state, direction).isConnected())
            return PortalFrameVeinSpriteManager.getBackgroundSpriteForFront(variant);

        var edges = Edges.of(state, direction);
        return PortalFrameVeinSpriteManager.getBackgroundSpriteForSide(edges.left, edges.right, edges.up, edges.down, variant);
    }

    /**
     * The connections on a face's four edges, as seen looking at the face.
     */
    private record Edges(FrameConnection left, FrameConnection right, FrameConnection up, FrameConnection down)
    {
        static Edges of(BlockState state, Direction face)
        {
            if (face == Direction.UP)
                return of(state, Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH);
            if (face == Direction.DOWN)
                return of(state, Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH);
            return of(state, face.getCounterClockWise(), face.getClockWise(), Direction.UP, Direction.DOWN);
        }

        private static Edges of(BlockState state, Direction left, Direction right, Direction up, Direction down)
        {
            return new Edges(PortalFrameStateProperties.getSideConnection(state, left), PortalFrameStateProperties.getSideConnection(state, right),
                    PortalFrameStateProperties.getSideConnection(state, up), PortalFrameStateProperties.getSideConnection(state, down));
        }
    }
}
