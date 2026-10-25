package net.voxelindustry.voidheart.client.model.monolith;

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
import net.voxelindustry.voidheart.common.block.StateProperties;

public class VoidMonolithBakedModel extends WrapperBlockStateModel
{
    public VoidMonolithBakedModel(BlockStateModel wrapped)
    {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest)
    {
        var variant = random.nextInt(3);

        emitter.pushTransform(quad ->
        {
            if (quad.tag() != 0)
                return true;

            Direction facing = quad.nominalFace();
            Material.Baked sprite;

            if (facing == null || facing.getAxis().isVertical())
                sprite = VoidMonolithSpriteManager.getAboveSprite(variant);
            else
            {
                boolean top = StateProperties.isSideConnected(state, Direction.UP);
                boolean bottom = StateProperties.isSideConnected(state, Direction.DOWN);
                if (top && bottom)
                    sprite = VoidMonolithSpriteManager.getMiddleSprite(variant);
                else if (top)
                    sprite = VoidMonolithSpriteManager.getBottomSprite(variant);
                else if (bottom)
                    sprite = VoidMonolithSpriteManager.getTopSprite(variant);
                else
                    sprite = VoidMonolithSpriteManager.getAboveSprite(variant);
            }

            quad.materialBake(sprite, MutableQuadView.BAKE_LOCK_UV);
            return true;
        });

        super.emitQuads(emitter, blockView, pos, state, random, cullTest);
        emitter.popTransform();

        if (state.getValue(StateProperties.BROKEN))
        {
            for (var direction : Direction.values())
            {
                var sprite = VoidMonolithSpriteManager.getBrokenSprite();

                emitter
                        .square(direction, 0, 0, 1, 1, 0)
                        .materialBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                        .cullFace(direction)
                        .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                        .tag(1)
                        .emit();
            }
        }

        if (state.getValue(BlockStateProperties.LIT))
        {
            for (var direction : Direction.values())
            {
                Material.Baked sprite;

                if (direction.getAxis().isVertical())
                    sprite = VoidMonolithSpriteManager.getAboveOverlaySprite(variant);
                else
                {
                    boolean top = StateProperties.isSideConnected(state, Direction.UP);
                    boolean bottom = StateProperties.isSideConnected(state, Direction.DOWN);
                    if (top && bottom)
                        sprite = VoidMonolithSpriteManager.getMiddleOverlaySprite(variant);
                    else if (top)
                        sprite = VoidMonolithSpriteManager.getBottomOverlaySprite(variant);
                    else if (bottom)
                        sprite = VoidMonolithSpriteManager.getTopOverlaySprite(variant);
                    else
                        sprite = VoidMonolithSpriteManager.getAboveOverlaySprite(variant);
                }

                emitter
                        .chunkLayer(ChunkSectionLayer.CUTOUT)
                .emissive(true)
                        .square(direction, 0, 0, 1, 1, 0)
                        .materialBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                        .cullFace(direction)
                        .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                        .tag(1)
                        .emit();
            }
        }
    }
}