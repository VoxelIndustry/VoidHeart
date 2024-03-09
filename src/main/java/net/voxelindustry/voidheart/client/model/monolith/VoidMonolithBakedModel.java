package net.voxelindustry.voidheart.client.model.monolith;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import net.voxelindustry.voidheart.common.block.StateProperties;

import java.util.function.Supplier;

public class VoidMonolithBakedModel extends ForwardingBakedModel
{
    private final Direction[] horizontals = new Direction[] { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

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
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context)
    {
        RenderMaterial outerMaterial = getOuterMaterial(RendererAccess.INSTANCE.getRenderer());
        var variant = randomSupplier.get().nextInt(3);

        context.pushTransform(quad ->
        {
            if (quad.tag() != 0)
                return true;

            Direction facing = quad.nominalFace();
            Sprite sprite;

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

        if (state.get(Properties.LIT))
        {
            for (var direction : Direction.values())
            {
                Sprite sprite;

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
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context)
    {
        throw new UnsupportedOperationException("ItemStack attempted to render VoidMonolith model as an item: " + stack);
    }
}