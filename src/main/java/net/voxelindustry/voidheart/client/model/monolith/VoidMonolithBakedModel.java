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
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.voxelindustry.voidheart.common.block.StateProperties;

import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

public class VoidMonolithBakedModel extends ForwardingBakedModel
{
    private final Direction[] horizontals = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    public VoidMonolithBakedModel(BakedModel wrapped, Function<SpriteIdentifier, Sprite> spriteGetter)
    {
        this.wrapped = wrapped;
        VoidMonolithSpriteManager.updateSpriteGetter(spriteGetter);
    }

    @Override
    public boolean isVanillaAdapter()
    {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context)
    {
        context.pushTransform(quad ->
        {
            Direction facing = quad.nominalFace();
            if (facing == null || facing.getAxis().isVertical())
            {
                return true;
            }

            boolean top = StateProperties.isConnectedToSide(state, Direction.UP);
            boolean bottom = StateProperties.isConnectedToSide(state, Direction.DOWN);
            if (top && bottom)
                quad.spriteBake(0, VoidMonolithSpriteManager.getFrameSprite(Direction.NORTH), MutableQuadView.BAKE_LOCK_UV);
            else if (top)
                quad.spriteBake(0, VoidMonolithSpriteManager.getFrameSprite(Direction.UP), MutableQuadView.BAKE_LOCK_UV);
            else if (bottom)
                quad.spriteBake(0, VoidMonolithSpriteManager.getFrameSprite(Direction.DOWN), MutableQuadView.BAKE_LOCK_UV);
            else
                quad.spriteBake(0, VoidMonolithSpriteManager.getFrameSprite(Direction.SOUTH), MutableQuadView.BAKE_LOCK_UV);
            return true;
        });

        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();

        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        RenderMaterial outerMaterial = renderer.materialFinder().blendMode(0, BlendMode.CUTOUT).emissive(0, true).find();

        Random random = randomSupplier.get();

        for (Direction direction : horizontals)
        {
            context.getEmitter()
                    .material(outerMaterial)
                    .square(direction, 0, 0, 1, 1, 0)
                    .spriteBake(0, VoidMonolithSpriteManager.getGlyphSprite(random.nextInt(15), state.get(Properties.LIT)), MutableQuadView.BAKE_LOCK_UV)
                    .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                    .emit();
        }
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context)
    {
        throw new UnsupportedOperationException("ItemStack attempted to render VoidMonolith model as an item: " + stack);
    }
}