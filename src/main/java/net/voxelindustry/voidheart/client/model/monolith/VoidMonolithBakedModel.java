package net.voxelindustry.voidheart.client.model.monolith;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.block.StateProperties;

import java.util.function.Supplier;

public class VoidMonolithBakedModel extends ForwardingBakedModel
{
    private RenderMaterial outerMaterial;

    public VoidMonolithBakedModel(BakedModel wrapped)
    {
        this.wrapped = wrapped;
    }

    private RenderMaterial getOuterMaterial(Renderer renderer)
    {
        if (outerMaterial == null)
            outerMaterial = renderer.materialFinder().blendMode(0, BlendMode.CUTOUT).emissive(0, true).find();
        return outerMaterial;
    }

    @Override
    public boolean isVanillaAdapter()
    {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context)
    {
        RenderMaterial outerMaterial = getOuterMaterial(RendererAccess.INSTANCE.getRenderer());
        var variant = randomSupplier.get().nextInt(3);

        context.pushTransform(quad ->
        {
            if (quad.tag() != 0)
                return true;

            Direction facing = quad.nominalFace();
            TextureAtlasSprite sprite;

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

            quad.spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV);
            return true;
        });

        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();

        if (state.getValue(StateProperties.BROKEN))
        {
            for (var direction : Direction.values())
            {
                var sprite = VoidMonolithSpriteManager.getBrokenSprite();

                context.getEmitter()
                        .square(direction, 0, 0, 1, 1, 0)
                        .spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV)
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
                TextureAtlasSprite sprite;

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

                context.getEmitter()
                        .material(outerMaterial)
                        .square(direction, 0, 0, 1, 1, 0)
                        .spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                        .cullFace(direction)
                        .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                        .tag(1)
                        .emit();
            }
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context)
    {
        throw new UnsupportedOperationException("ItemStack attempted to render VoidMonolith model as an item: " + stack);
    }
}