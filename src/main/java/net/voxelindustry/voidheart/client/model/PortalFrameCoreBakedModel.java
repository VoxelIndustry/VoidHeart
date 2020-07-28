package net.voxelindustry.voidheart.client.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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

import java.util.Random;
import java.util.function.Function;
import java.util.function.Supplier;

import static net.voxelindustry.voidheart.client.model.PortalFrameVeinSpriteManager.*;

@Environment(EnvType.CLIENT)
public class PortalFrameCoreBakedModel extends ForwardingBakedModel
{
    public PortalFrameCoreBakedModel(BakedModel wrapped, Function<SpriteIdentifier, Sprite> spriteGetter)
    {
        this.wrapped = wrapped;
        updateSpriteGetter(spriteGetter);
    }

    @Override
    public boolean isVanillaAdapter()
    {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockRenderView blockView, BlockState state, BlockPos pos, Supplier<Random> randomSupplier, RenderContext context)
    {
        Renderer renderer = RendererAccess.INSTANCE.getRenderer();
        RenderMaterial outerMaterial = renderer.materialFinder().blendMode(0, BlendMode.CUTOUT).emissive(0, true).find();

        PortalFrameVeinModel.createPortalVeinQuads(state, context, outerMaterial);

        Direction facing = state.get(Properties.FACING);
        context.getEmitter()
                .material(outerMaterial)
                .square(facing, 0, 0, 1, 1, 0)
                .spriteBake(0, state.get(Properties.LIT) ? getActiveCoreSprite() : getInactiveCoreSprite(), MutableQuadView.BAKE_LOCK_UV)
                .spriteColor(0, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                .emit();

        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<Random> randomSupplier, RenderContext context)
    {
        throw new UnsupportedOperationException("ItemStack attempted to render PortalFrame Core model as an item: " + stack);
    }
}