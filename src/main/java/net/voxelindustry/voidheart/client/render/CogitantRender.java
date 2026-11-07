package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.CogitantTile;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The brain in its cage, two solid hemispheres that swell slowly while it thinks, one now and then twitching. The cage is the chunk model.
 * Solid and depth-writing like any block, 12 quads, 24 while it is being broken.
 */
public class CogitantRender implements BlockEntityRenderer<CogitantTile, CogitantRender.State>
{
    private static final RenderType TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/cogitant_brain.png"));

    private static final float BOTTOM       = 2.5F / 16;
    private static final float HEIGHT       = 7F / 16;
    private static final float HALF_WIDTH   = 3.5F / 16;
    private static final float HALF_DEPTH   = 4F / 16;
    private static final float FISSURE      = 0.15F / 16;
    private static final float PULSE_TICKS  = 60;
    private static final float PULSE        = 0.03F;
    private static final float IDLE_PULSE   = 0.008F;
    private static final int   TWITCH_EVERY = 90;
    private static final float TWITCH_TICKS = 4;
    private static final float TWITCH_CHANCE = 0.45F;
    private static final float TWITCH_LIFT  = 0.4F / 16;

    public static class State extends BlockEntityRenderState
    {
        public float swell = 1;
        public float leftLift;
        public float rightLift;
    }

    public CogitantRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(CogitantTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        int seed = tile.getBlockPos().hashCode();
        float time = tile.getLevel().getGameTime() % 24000 + partialTicks;
        boolean thinking = tile.isActive();
        state.swell = BrainBoxes.pulse(time, PULSE_TICKS, thinking ? PULSE : IDLE_PULSE, seed);
        state.leftLift = thinking ? twitch(time, seed) : 0;
        state.rightLift = thinking ? twitch(time, seed * 31 + 7) : 0;
    }

    /** A short jolt at a random moment of each stretch, or none. */
    static float twitch(float time, int seed)
    {
        int stretch = (int) (time / TWITCH_EVERY);
        long hash = (stretch * 0x9E3779B97F4A7C15L) ^ seed;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        hash ^= hash >>> 32;
        float roll = (hash & 0xFFFF) / 65536F;
        if (roll > TWITCH_CHANCE)
            return 0;

        float at = ((hash >>> 16) & 0xFFFF) / 65536F * (TWITCH_EVERY - TWITCH_TICKS);
        float into = time - stretch * TWITCH_EVERY - at;
        if (into < 0 || into > TWITCH_TICKS)
            return 0;
        return TWITCH_LIFT * Mth.sin(Mth.PI * into / TWITCH_TICKS);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> brain(state, pose, buffer));

        var breaking = state.breakProgress;
        if (breaking != null)
            collector.submitCustomGeometry(poseStack, ModelBakery.DESTROY_TYPES.get(breaking.progress()),
                    (pose, buffer) -> brain(state, pose, new SheetedDecalTextureGenerator(buffer, breaking.cameraPose(), 1)));
    }

    private static void brain(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float halfWidth = HALF_WIDTH * state.swell;
        float halfDepth = HALF_DEPTH * state.swell;
        float top = BOTTOM + HEIGHT * state.swell;
        BrainBoxes.box(pose, buffer, state.lightCoords, 0.5F - halfWidth, BOTTOM + state.leftLift, 0.5F - halfDepth,
                0.5F - FISSURE, top + state.leftLift, 0.5F + halfDepth, 0, 1);
        BrainBoxes.box(pose, buffer, state.lightCoords, 0.5F + FISSURE, BOTTOM + state.rightLift, 0.5F - halfDepth,
                0.5F + halfWidth, top + state.rightLift, 0.5F + halfDepth, 0, 1);
    }
}
