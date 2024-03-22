package net.voxelindustry.voidheart.client.model.conduit;

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

public class ConduitBakedModel extends ForwardingBakedModel
{
    private RenderMaterial outerMaterial;

    public ConduitBakedModel(BakedModel wrapped)
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
        var outerMaterial = getOuterMaterial(RendererAccess.INSTANCE.getRenderer());

        context.pushTransform(quad ->
        {
            if (quad.tag() != 0)
                return true;

            var facing = quad.nominalFace();
            var sprite = spriteFromBlockState(state, facing, false);

            quad.spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV);
            return true;
        });

        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();

        if (state.get(Properties.LIT))
        {
            for (var facing : Direction.values())
            {
                var sprite = spriteFromBlockState(state, facing, true);

                context.getEmitter()
                        .material(outerMaterial)
                        .square(facing, 0, 0, 1, 1, 0)
                        .spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                        .cullFace(facing)
                        .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                        .tag(1)
                        .emit();
            }
        }
    }

    private static Sprite spriteFromBlockState(BlockState state, Direction facing, boolean lit)
    {
        boolean up = false;
        boolean down = false;
        boolean left = false;
        boolean right = false;

        switch (facing)
        {
            case DOWN ->
            {
                up = StateProperties.isSideConnected(state, Direction.SOUTH);
                down = StateProperties.isSideConnected(state, Direction.NORTH);
                left = StateProperties.isSideConnected(state, Direction.WEST);
                right = StateProperties.isSideConnected(state, Direction.EAST);
            }
            case UP ->
            {
                up = StateProperties.isSideConnected(state, Direction.NORTH);
                down = StateProperties.isSideConnected(state, Direction.SOUTH);
                left = StateProperties.isSideConnected(state, Direction.WEST);
                right = StateProperties.isSideConnected(state, Direction.EAST);
            }
            case NORTH ->
            {
                up = StateProperties.isSideConnected(state, Direction.UP);
                down = StateProperties.isSideConnected(state, Direction.DOWN);
                left = StateProperties.isSideConnected(state, Direction.EAST);
                right = StateProperties.isSideConnected(state, Direction.WEST);
            }
            case SOUTH ->
            {
                up = StateProperties.isSideConnected(state, Direction.UP);
                down = StateProperties.isSideConnected(state, Direction.DOWN);
                left = StateProperties.isSideConnected(state, Direction.WEST);
                right = StateProperties.isSideConnected(state, Direction.EAST);
            }
            case WEST ->
            {
                up = StateProperties.isSideConnected(state, Direction.UP);
                down = StateProperties.isSideConnected(state, Direction.DOWN);
                left = StateProperties.isSideConnected(state, Direction.NORTH);
                right = StateProperties.isSideConnected(state, Direction.SOUTH);
            }
            case EAST ->
            {
                up = StateProperties.isSideConnected(state, Direction.UP);
                down = StateProperties.isSideConnected(state, Direction.DOWN);
                left = StateProperties.isSideConnected(state, Direction.SOUTH);
                right = StateProperties.isSideConnected(state, Direction.NORTH);
            }
        }
        return ConduitSpriteManager.getSpriteFor(up, down, left, right, lit);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context)
    {
        throw new UnsupportedOperationException("ItemStack attempted to render Conduit model as an item: " + stack);
    }
}