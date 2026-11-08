package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnTile;
import net.voxelindustry.voidheart.common.content.research.KnowledgeMath;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Opaque boxes that move every frame, so they cannot be part of the chunk. Solid and depth-writing like any block, and neighbors are
 * still drawn with their inner faces because they float at different heights. At most 24 quads per block space, 48 while it is being broken.
 * Those no repository can use sit still on the ground with their runes dark.
 */
public class KnowledgeColumnRender implements BlockEntityRenderer<KnowledgeColumnTile, KnowledgeColumnRender.State>
{
    private static final RenderType ACTIVE_TYPE   = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/knowledge_column.png"));
    private static final RenderType INACTIVE_TYPE = RenderTypes.entitySolid(Identifier.fromNamespaceAndPath(MODID, "textures/block/knowledge_column_inactive.png"));

    private static final float HALF_WIDTH        = 4 / 16F;
    // Resting heights are in KnowledgeColumnBlock, which selects each column where it floats. Its height step is larger than the most the wave can
    // do between two touching columns, about 1.1/16, so that their heights stay apart at every moment
    private static final float WAVE_HEIGHT       = KnowledgeColumnBlock.COLUMN_WAVE_HEIGHT;
    private static final int   WAVE_PERIOD_TICKS = 140;
    private static final float WAVELENGTH_BLOCKS = 6;
    private static final float RISE_TICKS        = 30;

    // Texture layout in a 32x32 image: four 8x16 sides in a row, then the 8x8 top and bottom under them
    private static final float PIXEL       = 1 / 32F;
    private static final float SIDE_WIDTH  = 8 * PIXEL;
    private static final float SIDE_HEIGHT = 16 * PIXEL;
    private static final float CAP_SIZE    = 8 * PIXEL;

    public static class State extends BlockEntityRenderState
    {
        public int mask;
        public int variety;
        public boolean linked;
        public final float[] rises = new float[4];
        /** Columns the cracks of breaking show on: only the one the player breaks, when it is this player. */
        public int crackedMask;
    }

    public KnowledgeColumnRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(KnowledgeColumnTile tile, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTicks, cameraPosition, breakProgress);

        var pos = tile.getBlockPos();
        state.mask = KnowledgeColumnBlock.maskOf(tile.getBlockState());
        state.variety = pos.hashCode();
        state.linked = tile.isActive();
        for (int quarter = 0; quarter < state.rises.length; quarter++)
            state.rises[quarter] = rise(tile, quarter, partialTicks);

        state.crackedMask = state.mask;
        var minecraft = Minecraft.getInstance();
        if (state.breakProgress != null && minecraft.gameMode != null && minecraft.gameMode.isDestroying()
                && minecraft.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos))
            state.crackedMask = 1 << KnowledgeColumnBlock.quarterHit(tile.getBlockState(), pos, hit.getLocation(), hit.getDirection());
    }

    /**
     * How high a column floats above its space at this moment, rising or settling as it is linked or unlinked.
     */
    public static float rise(KnowledgeColumnTile tile, int quarter, float partialTicks)
    {
        var pos = tile.getBlockPos();
        // Horizontal only, so that a stack rises as one and its columns never run into each other
        var center = tile.getClusterCenter();
        long gameTime = tile.getLevel().getGameTime();
        float time = gameTime % WAVE_PERIOD_TICKS + partialTicks;

        float change = Math.clamp((gameTime - tile.getLinkChangedAt() + partialTicks) / RISE_TICKS, 0, 1);
        float eased = change * change * (3 - 2 * change);
        float lift = tile.isActive() ? eased : 1 - eased;

        double dx = pos.getX() + KnowledgeMath.columnCenterX(quarter) - center.x;
        double dz = pos.getZ() + KnowledgeMath.columnCenterZ(quarter) - center.z;
        float wave = WAVE_HEIGHT * (float) KnowledgeMath.columnWave(time, Math.sqrt(dx * dx + dz * dz), WAVE_PERIOD_TICKS, WAVELENGTH_BLOCKS);
        return lift * (KnowledgeColumnBlock.restingRise(pos, quarter) + wave);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        collector.submitCustomGeometry(poseStack, state.linked ? ACTIVE_TYPE : INACTIVE_TYPE, (pose, buffer) -> columns(state, pose, buffer));

        var breaking = state.breakProgress;
        if (breaking != null)
            collector.submitCustomGeometry(poseStack, ModelBakery.DESTROY_TYPES.get(breaking.progress()),
                    (pose, buffer) -> columns(state, state.crackedMask, pose, new SheetedDecalTextureGenerator(buffer, breaking.cameraPose(), 1)));
    }

    private static void columns(State state, PoseStack.Pose pose, VertexConsumer buffer)
    {
        columns(state, state.mask, pose, buffer);
    }

    private static void columns(State state, int mask, PoseStack.Pose pose, VertexConsumer buffer)
    {
        for (int quarter = 0; quarter < state.rises.length; quarter++)
        {
            if ((mask & 1 << quarter) == 0)
                continue;

            float x = (float) KnowledgeMath.columnCenterX(quarter);
            float z = (float) KnowledgeMath.columnCenterZ(quarter);
            float y = state.rises[quarter];
            column(pose, buffer, state.lightCoords, x - HALF_WIDTH, y, z - HALF_WIDTH, x + HALF_WIDTH, y + 1, z + HALF_WIDTH, state.variety + quarter);
        }
    }

    private static void column(PoseStack.Pose pose, VertexConsumer buffer, int light, float x0, float y0, float z0, float x1, float y1, float z1, int variety)
    {
        side(pose, buffer, light, variety, x1, y0, z0, x0, y1, z0, 0, -1);
        side(pose, buffer, light, variety + 1, x1, y0, z1, x1, y1, z0, 1, 0);
        side(pose, buffer, light, variety + 2, x0, y0, z1, x1, y1, z1, 0, 1);
        side(pose, buffer, light, variety + 3, x0, y0, z0, x0, y1, z1, -1, 0);

        float v = SIDE_HEIGHT;
        vertex(pose, buffer, light, x0, y1, z0, 0, v, 0, 1, 0);
        vertex(pose, buffer, light, x0, y1, z1, 0, v + CAP_SIZE, 0, 1, 0);
        vertex(pose, buffer, light, x1, y1, z1, CAP_SIZE, v + CAP_SIZE, 0, 1, 0);
        vertex(pose, buffer, light, x1, y1, z0, CAP_SIZE, v, 0, 1, 0);

        vertex(pose, buffer, light, x0, y0, z1, CAP_SIZE, v, 0, -1, 0);
        vertex(pose, buffer, light, x0, y0, z0, CAP_SIZE, v + CAP_SIZE, 0, -1, 0);
        vertex(pose, buffer, light, x1, y0, z0, CAP_SIZE * 2, v + CAP_SIZE, 0, -1, 0);
        vertex(pose, buffer, light, x1, y0, z1, CAP_SIZE * 2, v, 0, -1, 0);
    }

    /** From the bottom left to the top right, as seen from outside. */
    private static void side(PoseStack.Pose pose, VertexConsumer buffer, int light, int variety, float leftX, float bottom, float leftZ, float rightX, float top, float rightZ, int normalX, int normalZ)
    {
        float u0 = Math.floorMod(variety, 4) * SIDE_WIDTH;
        float u1 = u0 + SIDE_WIDTH;
        vertex(pose, buffer, light, leftX, top, leftZ, u0, 0, normalX, 0, normalZ);
        vertex(pose, buffer, light, leftX, bottom, leftZ, u0, SIDE_HEIGHT, normalX, 0, normalZ);
        vertex(pose, buffer, light, rightX, bottom, rightZ, u1, SIDE_HEIGHT, normalX, 0, normalZ);
        vertex(pose, buffer, light, rightX, top, rightZ, u1, 0, normalX, 0, normalZ);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float x, float y, float z, float u, float v, int normalX, int normalY, int normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
