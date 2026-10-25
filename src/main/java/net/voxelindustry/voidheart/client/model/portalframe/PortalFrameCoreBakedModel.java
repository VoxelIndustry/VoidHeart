package net.voxelindustry.voidheart.client.model.portalframe;

import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;
import net.voxelindustry.voidheart.common.block.StateProperties;

import java.util.function.Function;
import java.util.function.Supplier;

import static net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager.getCoreBrokenOverlaySprite;
import static net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager.getCoreOverlaySprite;

public class PortalFrameCoreBakedModel extends ForwardingBakedModel
{
    private RenderMaterial outerMaterial;

    public PortalFrameCoreBakedModel(BakedModel wrapped, Function<Material, TextureAtlasSprite> spriteGetter)
    {
        this.wrapped = wrapped;
    }

    private RenderMaterial getOuterMaterial(Renderer renderer)
    {
        if (outerMaterial == null)
            outerMaterial = renderer.materialFinder().blendMode(BlendMode.CUTOUT).emissive(true).find();
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
        var variant = randomSupplier.get().nextInt(2);

        Direction facing = state.getValue(BlockStateProperties.FACING);

        context.pushTransform(quad ->
        {
            if (quad.tag() == 0)
            {
                var direction = quad.nominalFace();

                int uvFlag = MutableQuadView.BAKE_LOCK_UV;

                if (direction == facing)
                {
                    var sprite = PortalFrameVeinSpriteManager.getCoreSprite();
                    quad.spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV);
                    return true;
                }

                // The quad is directly facing a portal interior
                if (PortalFrameStateProperties.getSideConnection(state, direction).isConnected())
                {
                    var sprite = PortalFrameVeinSpriteManager.getBackgroundSpriteForFront(variant);
                    quad.spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV);
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
                quad.spriteBake(sprite, uvFlag);
            }
            return true;
        });

        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();

        TextureAtlasSprite sprite;
        if (state.getValue(StateProperties.BROKEN))
            sprite = getCoreBrokenOverlaySprite();
        else if (state.getValue(BlockStateProperties.LIT))
        {
            PortalFrameVeinModel.createPortalVeinQuadsExpectDirection(facing, state, context, outerMaterial, variant);
            sprite = getCoreOverlaySprite();
        }
        else
            sprite = getCoreBrokenOverlaySprite();

        context.getEmitter()
                .material(outerMaterial)
                .square(facing, 0, 0, 1, 1, 0)
                .spriteBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                .tag(1)
                .emit();

    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context)
    {
        throw new UnsupportedOperationException("ItemStack attempted to render PortalFrame Core model as an item: " + stack);
    }
}