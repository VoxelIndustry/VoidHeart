package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
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

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws the barrier as seen by the local player: its picked block when solid for them,
 * a ghost when open, nothing when hidden, and a floating sigil while unconfigured.
 */
public class PermeableBarrierRender implements BlockEntityRenderer<PermeableBarrierTile, PermeableBarrierRenderState>
{
    private static final Identifier SIGIL_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/permeable_barrier_sigil.png");
    private static final Identifier WAVE_TEXTURE  = Identifier.fromNamespaceAndPath(MODID, "textures/block/permeable_barrier_wave.png");

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

        state.appearanceParts.clear();
        var appearance = barrier.getConfig().appearance();
        if (appearance.isEmpty() || state.mode == Mode.SIGIL || state.mode == Mode.HIDDEN)
            return;

        random.setSeed(pos.asLong());
        Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(appearance.get()).collectParts(random, state.appearanceParts);

        for (var direction : Direction.values())
        {
            var neighborPos = pos.relative(direction);
            var neighborState = level.getBlockState(neighborPos);
            boolean culled = neighborState.is(barrier.getBlockState().getBlock())
                    ? getMode(level, neighborPos, viewer) == state.mode
                    : state.mode == Mode.SOLID && neighborState.isSolidRender();
            state.culledFaces[direction.ordinal()] = culled;
        }
    }

    @Override
    public void submit(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        switch (state.mode)
        {
            case SIGIL -> submitSigil(state, poseStack, collector);
            case SOLID -> submitAppearance(state, poseStack, collector, ARGB.white(1F), state.lightCoords);
            case GHOST -> submitAppearance(state, poseStack, collector, GHOST_COLOR, FULL_BRIGHT);
            case FAINT_GHOST -> submitAppearance(state, poseStack, collector, FAINT_GHOST_COLOR, FULL_BRIGHT);
            case HIDDEN -> {}
        }

        if (state.waveIntensity > 0 && state.mode != Mode.HIDDEN)
            submitWave(state, poseStack, collector);
    }

    private static void submitAppearance(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int color, int light)
    {
        var instance = new QuadInstance();
        instance.setColor(color);
        instance.setLightCoords(light);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);

        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
        {
            for (var part : state.appearanceParts)
            {
                for (var quad : part.getQuads(null))
                    buffer.putBakedQuad(pose, quad, instance);
                for (var direction : Direction.values())
                {
                    if (state.culledFaces[direction.ordinal()])
                        continue;
                    for (var quad : part.getQuads(direction))
                        buffer.putBakedQuad(pose, quad, instance);
                }
            }
        });
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
        vertex(pose, buffer, -half * facing, -half, 0, 0, 1, alpha, 0, 0, facing);
        vertex(pose, buffer, half * facing, -half, 0, 1, 1, alpha, 0, 0, facing);
        vertex(pose, buffer, half * facing, half, 0, 1, 0, alpha, 0, 0, facing);
        vertex(pose, buffer, -half * facing, half, 0, 0, 0, alpha, 0, 0, facing);
    }

    private static void submitWave(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        int alpha = (int) (state.waveIntensity * 220);
        float min = -0.002F;
        float max = 1.002F;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(WAVE_TEXTURE), (pose, buffer) ->
        {
            for (var direction : Direction.values())
            {
                if (state.culledFaces[direction.ordinal()] && state.mode != Mode.SIGIL)
                    continue;
                waveFace(pose, buffer, direction, min, max, alpha);
            }
        });
    }

    private static void waveFace(PoseStack.Pose pose, VertexConsumer buffer, Direction direction, float min, float max, int alpha)
    {
        var normal = direction.getUnitVec3i();
        int nx = normal.getX();
        int ny = normal.getY();
        int nz = normal.getZ();

        // Corners walked counter-clockwise when looking at the face from outside
        float[][] corners = switch (direction)
        {
            case DOWN -> new float[][]{{min, min, max}, {min, min, min}, {max, min, min}, {max, min, max}};
            case UP -> new float[][]{{min, max, min}, {min, max, max}, {max, max, max}, {max, max, min}};
            case NORTH -> new float[][]{{max, max, min}, {max, min, min}, {min, min, min}, {min, max, min}};
            case SOUTH -> new float[][]{{min, max, max}, {min, min, max}, {max, min, max}, {max, max, max}};
            case WEST -> new float[][]{{min, max, min}, {min, min, min}, {min, min, max}, {min, max, max}};
            case EAST -> new float[][]{{max, max, max}, {max, min, max}, {max, min, min}, {max, max, min}};
        };
        float[][] uvs = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        for (int i = 0; i < 4; i++)
            vertex(pose, buffer, corners[i][0], corners[i][1], corners[i][2], uvs[i][0], uvs[i][1], alpha, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int alpha, int nx, int ny, int nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(150, 255, 190, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }
}
