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
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.MeatProxyTile;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A Cogitant's brain grown into one mass of flesh, bulging through the bars and the cap of its cage. It darkens in three stages as it wears, and
 * throbs faster while it bears strain, with dark strands running into it from the repository whose burden it takes.
 * The flesh is solid like the cage, at most 30 quads, the strands translucent and unlit like the relays', 3 of 16 segments, only while bearing.
 */
public class MeatProxyRender implements BlockEntityRenderer<MeatProxyTile, MeatProxyRender.State>
{
    private static final RenderType TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/meat_proxy_flesh.png"));
    private static final int        BANDS = 3;

    private static final float CALM_TICKS    = 60;
    private static final float BEARING_TICKS = 18;
    private static final float CALM_THROB    = 0.02F;
    private static final float BEARING_THROB = 0.05F;

    /** The mass and its bulges, in pixels: the mass fills the cage, the rest push out through its sides and cap. */
    private static final float[][] LUMPS = {
            { 3, 2.5F, 3, 13, 11.5F, 13 },
            { 5, 11.5F, 4.5F, 11, 14.5F, 11 },
            { 1.5F, 4, 5, 3, 9, 10 },
            { 13, 5, 6, 14.5F, 10, 11 },
            { 6, 3.5F, 13, 11, 8, 14.5F },
    };

    private static final int   STRAIN_RGB      = 0x9A2A5A;
    private static final int   STRANDS         = 3;
    private static final Vec3  CENTER          = new Vec3(0.5, 0.45, 0.5);
    /** Near the tablet of the repository, from its corner. */
    private static final Vec3  REPOSITORY_FROM = new Vec3(0.5, 1.05, 0.5);

    public static class State extends BlockEntityRenderState
    {
        public float swell = 1;
        public int band;
        public float time;
        public EssenceStrandRender.@Nullable Bundle strain;
    }

    public MeatProxyRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(MeatProxyTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var pos = tile.getBlockPos();
        state.time = tile.getLevel().getGameTime() % 24000 + partialTicks;
        boolean bearing = tile.isBearing() && tile.isActive();
        state.swell = BrainBoxes.pulse(state.time, bearing ? BEARING_TICKS : CALM_TICKS, bearing ? BEARING_THROB : CALM_THROB, pos.hashCode());

        float share = tile.share();
        state.band = share > MeatProxyTile.WORN ? 0 : share > MeatProxyTile.FAILING ? 1 : 2;

        state.strain = null;
        if (bearing && tile.getLink().isPresent())
        {
            var repository = tile.getLink().get().repository();
            var from = new Vec3(repository.getX() - pos.getX(), repository.getY() - pos.getY(), repository.getZ() - pos.getZ()).add(REPOSITORY_FROM);
            state.strain = new EssenceStrandRender.Bundle(from, CENTER, 0.05F, 0.06F, 0.12F, 1, STRAIN_RGB, repository.asLong() ^ pos.asLong(), STRANDS);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> flesh(state, pose, buffer));

        var breaking = state.breakProgress;
        if (breaking != null)
            collector.submitCustomGeometry(poseStack, ModelBakery.DESTROY_TYPES.get(breaking.progress()),
                    (pose, buffer) -> flesh(state, pose, new SheetedDecalTextureGenerator(buffer, breaking.cameraPose(), 1)));

        var strain = state.strain;
        if (strain != null)
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(VoidBeamRender.VEIL_TEXTURE, true),
                    (pose, buffer) -> EssenceStrandRender.render(pose, buffer, strain, state.time));
    }

    /** Each lump swells around the middle of the cage, so the whole mass breathes as one. */
    private static void flesh(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        float s = state.swell;
        for (var lump : LUMPS)
        {
            BrainBoxes.box(pose, buffer, state.lightCoords,
                    grow(lump[0], 8, s), grow(lump[1], 2.5F, s), grow(lump[2], 8, s),
                    grow(lump[3], 8, s), grow(lump[4], 2.5F, s), grow(lump[5], 8, s), state.band, BANDS);
        }
    }

    private static float grow(float pixel, float around, float swell)
    {
        return (around + (pixel - around) * swell) / 16;
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }
}
