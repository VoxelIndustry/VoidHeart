package net.voxelindustry.voidheart.client.render.energy.reactor;

import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quads a block state shows in the world, emitted through Fabric's renderer as ForgePreviewRender does, cached per state since the preview only
 * ever shows two: plain containment and an intake.
 */
final class ReactorGhostQuads
{
    private static final Map<BlockState, Cached> CACHE  = new HashMap<>();
    private static final RandomSource            RANDOM = RandomSource.create();

    /**
     * A reload bakes new models with new sprites, so quads are only reused while the state's model is the same object.
     */
    private record Cached(BlockStateModel model, List<BakedQuad> quads)
    {
    }

    private ReactorGhostQuads()
    {
    }

    static List<BakedQuad> of(BlockState state)
    {
        var minecraft = Minecraft.getInstance();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(state);
        var cached = CACHE.get(state);
        if (cached != null && cached.model() == model)
            return cached.quads();

        var mesh = Renderer.get().mutableMesh();
        RANDOM.setSeed(42);
        model.emitQuads(mesh.emitter(), BlockAndTintGetter.EMPTY, BlockPos.ZERO, state, RANDOM, direction -> false);

        var sprites = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).spriteFinder();
        var quads = new ArrayList<BakedQuad>(mesh.size());
        mesh.forEach(quad -> quads.add(quad.toBakedQuad(sprites.find(quad))));
        CACHE.put(state, new Cached(model, quads));
        return quads;
    }
}
