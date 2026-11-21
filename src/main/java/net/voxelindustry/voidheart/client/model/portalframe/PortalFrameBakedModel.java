package net.voxelindustry.voidheart.client.model.portalframe;

import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class PortalFrameBakedModel extends WrapperBlockStateModel
{
    public PortalFrameBakedModel(BlockStateModel wrapped)
    {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest)
    {
        var variant = random.nextInt(2);

        emitter.pushTransform(quad ->
        {
            if (quad.tag() == 0)
                quad.materialBake(PortalFrameVeinModel.backgroundSprite(state, quad.nominalFace(), variant), MutableQuadView.BAKE_LOCK_UV);
            return true;
        });

        super.emitQuads(emitter, blockView, pos, state, random, cullTest);
        emitter.popTransform();

        if (state.getValue(BlockStateProperties.LIT))
            PortalFrameVeinModel.createPortalVeinQuadsAllDirections(state, emitter, variant);
    }
}