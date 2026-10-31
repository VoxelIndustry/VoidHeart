package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.PermeableBarrierRenderState;
import net.voxelindustry.voidheart.client.render.state.PermeableBarrierRenderState.Mode;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierStructure;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierTile;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws the barrier as seen by the local player: its picked block when solid for them,
 * a ghost when open, nothing when hidden, and a floating sigil while unconfigured.
 */
public class PermeableBarrierRender implements BlockEntityRenderer<PermeableBarrierTile, PermeableBarrierRenderState>
{
    private static final Identifier SIGIL_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/permeable_barrier_sigil.png");
    static final         Identifier WAVE_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/block/permeable_barrier_wave.png");
    static final         int        SIGIL_GLOW    = ARGB.color(255, 150, 255, 190);

    private static final int GHOST_COLOR       = ARGB.color(90, 200, 170, 255);
    private static final int FAINT_GHOST_COLOR = ARGB.color(35, 200, 170, 255);
    private static final int FULL_BRIGHT       = 15728880;

    private final RandomSource random = RandomSource.create();

    public PermeableBarrierRender(BlockEntityRendererProvider.Context context)
    {
    }

    public static Mode getMode(BlockGetter level, BlockPos pos, @Nullable Player viewer)
    {
        var tile = PermeableBarrierStructure.getTile(level, pos);
        if (tile == null)
            return Mode.HIDDEN;

        var config = tile.getConfig();
        if (!config.isConfigured())
            return Mode.SIGIL;
        if (config.isSolidFor(viewer, tile.getBlockState().getValue(BlockStateProperties.POWERED)))
            return Mode.SOLID;
        if (!config.has(BarrierConfig.Flag.INVISIBLE_WHEN_OPEN))
            return Mode.GHOST;
        return viewer != null && config.isAlly(viewer) ? Mode.FAINT_GHOST : Mode.HIDDEN;
    }

    @Override
    public PermeableBarrierRenderState createRenderState()
    {
        return new PermeableBarrierRenderState();
    }

    @Override
    public void extractRenderState(PermeableBarrierTile barrier, PermeableBarrierRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(barrier, state, partialTicks, cameraPosition, breakProgress);

        var level = barrier.getLevel();
        var pos = barrier.getBlockPos();
        var viewer = Minecraft.getInstance().player;

        state.worldTime = level.getGameTime() + partialTicks;
        state.waveIntensity = BarrierWaveTracker.getIntensity(pos, state.worldTime);
        state.mode = getMode(level, pos, viewer);
        state.seed = pos.asLong();
        state.transition = BarrierTransitionTracker.update(pos, state.mode == Mode.SOLID, state.worldTime);

        state.appearanceParts.clear();
        var appearance = barrier.getConfig().appearance();
        if (appearance.isEmpty() || state.mode == Mode.SIGIL || state.mode == Mode.HIDDEN && state.transition < 0)
            return;

        random.setSeed(pos.asLong());
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(appearance.get()).collectParts(random, state.appearanceParts);
        collectFaceSprites(state);
        if (state.transition >= 0)
            BarrierMosaicRender.collectSeedPoints(level, pos, state);

        for (var direction : Direction.values())
        {
            var neighborPos = pos.relative(direction);
            var neighborState = level.getBlockState(neighborPos);
            boolean culled = neighborState.is(barrier.getBlockState().getBlock())
                    ? getMode(level, neighborPos, viewer) == state.mode
                    : state.mode == Mode.SOLID && neighborState.isSolidRender();
            state.culledFaces[direction.ordinal()] = culled;

            // Like a placed block, each face takes the light of the space it faces
            state.faceLight[direction.ordinal()] = LightCoordsUtil.getLightCoords(level, neighborPos);
            state.faceShade[direction.ordinal()] = Minecraft.getInstance().level.cardinalLighting().byFace(direction);
        }
    }

    /**
     * The texture of each face of the picked block, cut into tiles by the mosaic transition.
     */
    private static void collectFaceSprites(PermeableBarrierRenderState state)
    {
        TextureAtlasSprite fallback = null;
        for (var direction : Direction.values())
        {
            var sprite = findFaceSprite(state, direction);
            state.faceSprites[direction.ordinal()] = sprite;
            if (fallback == null)
                fallback = sprite;
        }
        // Faces the model leaves out borrow any other face, so the whole cube takes part
        for (int i = 0; i < state.faceSprites.length; i++)
        {
            if (state.faceSprites[i] == null)
                state.faceSprites[i] = fallback;
        }
    }

    private static @Nullable TextureAtlasSprite findFaceSprite(PermeableBarrierRenderState state, Direction direction)
    {
        for (var part : state.appearanceParts)
        {
            var culledQuads = part.getQuads(direction);
            if (!culledQuads.isEmpty())
                return culledQuads.getFirst().materialInfo().sprite();
            for (var quad : part.getQuads(null))
            {
                if (quad.direction() == direction)
                    return quad.materialInfo().sprite();
            }
        }
        return null;
    }

    @Override
    public void submit(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.transition >= 0 && state.mode != Mode.SIGIL)
        {
            BarrierMosaicRender.submitMosaic(state, poseStack, collector, state.mode == Mode.SOLID);
            if (state.mode == Mode.SOLID)
                return;
        }

        switch (state.mode)
        {
            case SIGIL -> submitSigil(state, poseStack, collector);
            case SOLID -> submitAppearance(state, poseStack, collector, 0, true);
            case GHOST -> submitAppearance(state, poseStack, collector, GHOST_COLOR, false);
            case FAINT_GHOST -> submitAppearance(state, poseStack, collector, FAINT_GHOST_COLOR, false);
            case HIDDEN -> {}
        }

        if (state.waveIntensity > 0 && state.mode != Mode.HIDDEN)
            submitWave(state, poseStack, collector);
    }

    /**
     * @param lit whether faces are drawn like a placed block, lit, shaded and in the render pass of their own layer,
     *            otherwise all translucent and full bright in the given color
     */
    private static void submitAppearance(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int color, boolean lit)
    {
        if (!lit)
        {
            submitQuads(state, poseStack, collector, RenderTypes.translucentMovingBlock(), color, false, quad -> true);
            return;
        }

        // Opaque faces must not go through the translucent pass, or they hide other translucent geometry behind sorting
        submitQuads(state, poseStack, collector, RenderTypes.cutoutMovingBlock(), color, true, quad -> !isTranslucent(quad));
        submitQuads(state, poseStack, collector, RenderTypes.translucentMovingBlock(), color, true, PermeableBarrierRender::isTranslucent);
    }

    private static boolean isTranslucent(BakedQuad quad)
    {
        return quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
    }

    private static void submitQuads(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector, RenderType renderType,
                                    int color, boolean lit, Predicate<BakedQuad> filter)
    {
        var instance = new QuadInstance();
        instance.setColor(color);
        instance.setLightCoords(FULL_BRIGHT);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
        {
            for (var part : state.appearanceParts)
            {
                for (var quad : part.getQuads(null))
                {
                    if (filter.test(quad))
                        putQuad(state, pose, buffer, quad, instance, lit);
                }
                for (var direction : Direction.values())
                {
                    if (state.culledFaces[direction.ordinal()])
                        continue;
                    for (var quad : part.getQuads(direction))
                    {
                        if (filter.test(quad))
                            putQuad(state, pose, buffer, quad, instance, lit);
                    }
                }
            }
        });
    }

    private static void putQuad(PermeableBarrierRenderState state, PoseStack.Pose pose, VertexConsumer buffer, BakedQuad quad, QuadInstance instance, boolean lit)
    {
        if (lit)
        {
            var shadeOverride = quad.materialInfo().shadeDirectionOverride();
            int face = (shadeOverride != null ? shadeOverride : quad.direction()).ordinal();
            int brightness = (int) (state.faceShade[face] * 255);
            instance.setColor(ARGB.color(255, brightness, brightness, brightness));
            instance.setLightCoords(state.faceLight[quad.direction().ordinal()]);
        }
        buffer.putBakedQuad(pose, quad, instance);
    }

    private static void submitSigil(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5 + Math.sin(state.worldTime / 10.0) / 16.0, 0.5);
        poseStack.rotate(MathUtil.quatFromAngleDegrees(state.worldTime * 2, MathUtil.NEGATIVE_Y));

        float half = 0.35F;
        int alpha = 200 + (int) (55 * Math.sin(state.worldTime / 6.0));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(SIGIL_TEXTURE), (pose, buffer) ->
        {
            sigilQuad(pose, buffer, half, alpha, 1);
            sigilQuad(pose, buffer, half, alpha, -1);
        });
        poseStack.popPose();
    }

    private static void sigilQuad(PoseStack.Pose pose, VertexConsumer buffer, float half, int alpha, int facing)
    {
        int color = ARGB.color(alpha, SIGIL_GLOW);
        GlowCube.vertex(pose, buffer, -half * facing, -half, 0, 0, 1, color, 0, 0, facing);
        GlowCube.vertex(pose, buffer, half * facing, -half, 0, 1, 1, color, 0, 0, facing);
        GlowCube.vertex(pose, buffer, half * facing, half, 0, 1, 0, color, 0, 0, facing);
        GlowCube.vertex(pose, buffer, -half * facing, half, 0, 0, 0, color, 0, 0, facing);
    }

    private static void submitWave(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        int color = ARGB.color((int) (state.waveIntensity * 220), SIGIL_GLOW);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(WAVE_TEXTURE), (pose, buffer) ->
        {
            for (var direction : Direction.values())
            {
                if (state.culledFaces[direction.ordinal()] && state.mode != Mode.SIGIL)
                    continue;
                GlowCube.face(pose, buffer, direction, 0, 0, 0, 0.002F, color);
            }
        });
    }
}
