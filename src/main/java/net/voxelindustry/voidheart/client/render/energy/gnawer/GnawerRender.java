package net.voxelindustry.voidheart.client.render.energy.gnawer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerBlock;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerContent;
import net.voxelindustry.voidheart.common.energy.gnawer.GnawerTile;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The Gnawer's jaw: a ring of teeth turning over its top while it eats, gold while it keeps and violet while it erases. A tether runs to the block being
 * eaten, and the box shows as thin violet edges to a player holding a stake.
 * <p>
 * Everything is additive {@link VoidHeartRenderTypes#GLOW}, so the crossed ribbons and the teeth overlapping each other need no sorting and never flicker.
 * The worst case is 8 teeth, 2 tether ribbons and 24 edge ribbons, rebuilt each frame.
 */
public class GnawerRender implements BlockEntityRenderer<GnawerTile, GnawerRender.State>
{
    private static final int   TEETH        = 8;
    /**
     * Over the dark pit inside the rim, since gold added onto the gold rim doesn't show.
     */
    private static final float TEETH_RADIUS = 0.22F;
    /**
     * Just above the pit's floor, which ends at 12 pixels, so the teeth never share its plane.
     */
    private static final float TEETH_BOTTOM = 12.2F / 16;
    private static final float TEETH_TOP    = 16.5F / 16;
    private static final float TOOTH_HALF   = 0.06F;

    private static final int KEEP_COLOR    = 0xE2B740;
    private static final int ERASE_COLOR   = 0x8A3DF0;
    private static final int TETHER_COLOR  = 0x8A3DF0;
    private static final int OUTLINE_COLOR = 0x8F78B8;

    private static final int   LIT_ALPHA      = 0xC0;
    private static final int   DULL_ALPHA     = 0x30;
    private static final int   TETHER_ALPHA   = 0x90;
    private static final int   OUTLINE_ALPHA  = 0x70;
    private static final float TETHER_HALF    = 0.03F;
    private static final float OUTLINE_HALF   = 0.025F;

    public static class State extends BlockEntityRenderState
    {
        public float   spin;
        public boolean erase;
        public boolean dull;
        @Nullable
        public Vector3f tether;
        @Nullable
        public Vector3f boxMin;
        @Nullable
        public Vector3f boxMax;
    }

    public GnawerRender(BlockEntityRendererProvider.Context context)
    {
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(GnawerTile gnawer, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress)
    {
        BlockEntityRenderer.super.extractRenderState(gnawer, state, partialTicks, cameraPosition, breakProgress);

        var origin = gnawer.getBlockPos();
        state.spin = gnawer.spin(partialTicks);
        state.erase = gnawer.getBlockState().getValue(GnawerBlock.ERASE);
        state.dull = gnawer.power() == MachinePower.UNPOWERED;

        var target = gnawer.target();
        state.tether = target == null || state.dull ? null
                : new Vector3f(target.getX() - origin.getX() + 0.5F, target.getY() - origin.getY() + 0.5F, target.getZ() - origin.getZ() + 0.5F);

        var box = gnawer.box();
        if (box != null && holdsStake())
        {
            state.boxMin = new Vector3f(box.min().getX() - origin.getX(), box.min().getY() - origin.getY(), box.min().getZ() - origin.getZ());
            state.boxMax = new Vector3f(box.max().getX() - origin.getX() + 1, box.max().getY() - origin.getY() + 1, box.max().getZ() - origin.getZ() + 1);
        }
        else
        {
            state.boxMin = null;
            state.boxMax = null;
        }
    }

    private static boolean holdsStake()
    {
        var player = Minecraft.getInstance().player;
        var stake = GnawerContent.GNAWING_STAKE.asItem();
        return player != null && (player.getMainHandItem().is(stake) || player.getOffhandItem().is(stake));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            teeth(pose, buffer, state);
            if (state.tether != null)
                segment(pose, buffer, new Vector3f(0.5F, TEETH_TOP, 0.5F), state.tether, TETHER_HALF, argb(TETHER_ALPHA, TETHER_COLOR));
            if (state.boxMin != null && state.boxMax != null)
                outline(pose, buffer, state.boxMin, state.boxMax);
        });
    }

    private static void teeth(PoseStack.Pose pose, VertexConsumer buffer, State state)
    {
        var color = argb(state.dull ? DULL_ALPHA : LIT_ALPHA, state.erase ? ERASE_COLOR : KEEP_COLOR);
        for (int i = 0; i < TEETH; i++)
        {
            var angle = state.spin + i * (float) (Math.PI * 2 / TEETH);
            var cos = (float) Math.cos(angle);
            var sin = (float) Math.sin(angle);
            var x = 0.5F + cos * TEETH_RADIUS;
            var z = 0.5F + sin * TEETH_RADIUS;
            // A tooth faces the jaw's centre, narrowing to a point at its tip
            var sideX = -sin * TOOTH_HALF;
            var sideZ = cos * TOOTH_HALF;
            buffer.addVertex(pose, x - sideX, TEETH_BOTTOM, z - sideZ).setColor(color);
            buffer.addVertex(pose, x + sideX, TEETH_BOTTOM, z + sideZ).setColor(color);
            buffer.addVertex(pose, x - cos * 0.05F, TEETH_TOP, z - sin * 0.05F).setColor(color);
            buffer.addVertex(pose, x - cos * 0.05F, TEETH_TOP, z - sin * 0.05F).setColor(color);
        }
    }

    private static void outline(PoseStack.Pose pose, VertexConsumer buffer, Vector3f min, Vector3f max)
    {
        var color = argb(OUTLINE_ALPHA, OUTLINE_COLOR);
        float[] xs = {min.x, max.x};
        float[] ys = {min.y, max.y};
        float[] zs = {min.z, max.z};
        for (var y : ys)
        {
            for (var z : zs)
                segment(pose, buffer, new Vector3f(min.x, y, z), new Vector3f(max.x, y, z), OUTLINE_HALF, color);
            for (var x : xs)
                segment(pose, buffer, new Vector3f(x, y, min.z), new Vector3f(x, y, max.z), OUTLINE_HALF, color);
        }
        for (var x : xs)
        {
            for (var z : zs)
                segment(pose, buffer, new Vector3f(x, min.y, z), new Vector3f(x, max.y, z), OUTLINE_HALF, color);
        }
    }

    /**
     * Two ribbons crossed along the segment, so it reads as a line from any side.
     */
    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, float half, int color)
    {
        var direction = new Vector3f(end).sub(start);
        if (direction.lengthSquared() < 1.0E-6F)
            return;
        direction.normalize();
        var helper = Math.abs(direction.y) > 0.9F ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
        var first = new Vector3f(direction).cross(helper).normalize().mul(half);
        var second = new Vector3f(direction).cross(first).normalize().mul(half);
        ribbon(pose, buffer, start, end, first, color);
        ribbon(pose, buffer, start, end, second, color);
    }

    private static void ribbon(PoseStack.Pose pose, VertexConsumer buffer, Vector3f start, Vector3f end, Vector3f side, int color)
    {
        buffer.addVertex(pose, start.x - side.x, start.y - side.y, start.z - side.z).setColor(color);
        buffer.addVertex(pose, start.x + side.x, start.y + side.y, start.z + side.z).setColor(color);
        buffer.addVertex(pose, end.x + side.x, end.y + side.y, end.z + side.z).setColor(color);
        buffer.addVertex(pose, end.x - side.x, end.y - side.y, end.z - side.z).setColor(color);
    }

    private static int argb(int alpha, int rgb)
    {
        return alpha << 24 | rgb;
    }

    @Override
    public boolean shouldRenderOffScreen()
    {
        return true;
    }

    @Override
    public int getViewDistance()
    {
        return 96;
    }
}
