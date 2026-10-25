package net.voxelindustry.voidheart.client.model.conduit;

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

public class ConduitBakedModel extends WrapperBlockStateModel
{
    public ConduitBakedModel(BlockStateModel wrapped)
    {
        super(wrapped);
    }

    @Override
    public void emitQuads(QuadEmitter emitter, BlockAndTintGetter blockView, BlockPos pos, BlockState state, RandomSource random, Predicate<@Nullable Direction> cullTest)
    {
        emitter.pushTransform(quad ->
        {
            if (quad.tag() != 0)
                return true;

            var facing = quad.nominalFace();
            var sprite = spriteFromBlockState(state, facing, false);

            quad.materialBake(sprite, MutableQuadView.BAKE_LOCK_UV);
            return true;
        });

        super.emitQuads(emitter, blockView, pos, state, random, cullTest);
        emitter.popTransform();

        if (state.getValue(BlockStateProperties.LIT))
        {
            for (var facing : Direction.values())
            {
                var sprite = spriteFromBlockState(state, facing, true);

                emitter
                        .chunkLayer(ChunkSectionLayer.CUTOUT)
                .emissive(true)
                        .square(facing, 0, 0, 1, 1, 0)
                        .materialBake(sprite, MutableQuadView.BAKE_LOCK_UV)
                        .cullFace(facing)
                        .color(0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA, 0xAAAAAAAA)
                        .tag(1)
                        .emit();
            }
        }
    }

    private static Material.Baked spriteFromBlockState(BlockState state, Direction facing, boolean lit)
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
}