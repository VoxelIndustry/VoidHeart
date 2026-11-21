package net.voxelindustry.voidheart.client.model.portalframe;

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

import static net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager.getCoreBrokenOverlaySprite;
import static net.voxelindustry.voidheart.client.model.portalframe.PortalFrameVeinSpriteManager.getCoreOverlaySprite;

public class PortalFrameCoreBakedModel extends WrapperBlockStateModel
{
    public PortalFrameCoreBakedModel(BlockStateModel wrapped)
    {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest)
    {
        var variant = random.nextInt(2);

        Direction facing = state.getValue(BlockStateProperties.FACING);

        emitter.pushTransform(quad ->
        {
            if (quad.tag() == 0)
            {
                var direction = quad.nominalFace();
                var sprite = direction == facing ? PortalFrameVeinSpriteManager.getCoreSprite() : PortalFrameVeinModel.backgroundSprite(state, direction, variant);
                quad.materialBake(sprite, MutableQuadView.BAKE_LOCK_UV);
            }
            return true;
        });

        super.emitQuads(emitter, blockView, pos, state, random, cullTest);
        emitter.popTransform();

        Material.Baked sprite;
        if (state.getValue(StateProperties.BROKEN))
            sprite = getCoreBrokenOverlaySprite();
        else if (state.getValue(BlockStateProperties.LIT))
        {
            PortalFrameVeinModel.createPortalVeinQuadsExpectDirection(facing, state, emitter, variant);
            sprite = getCoreOverlaySprite();
        }
        else
            sprite = getCoreBrokenOverlaySprite();

        emitter
                .chunkLayer(ChunkSectionLayer.CUTOUT)
                .emissive(true)
                .square(facing, 0, 0, 1, 1, 0)
                .materialBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                .tag(1)
                .emit();

    }
}