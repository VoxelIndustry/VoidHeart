package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.render.state.UnderwayKeyRenderState;
import net.voxelindustry.voidheart.common.content.underway.HatchBalance;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An Underway key at work: a low steel lock risen from the line where the hatch's leaves meet, and the key standing in it, turning faster as the charge fills.
 * <p>
 * Render types. The lock is {@link RenderTypes#entityCutout}: opaque, writing depth, lit by the world, a handful of quads built each frame. The key is the item's own model.
 */
public class UnderwayKeyRender extends EntityRenderer<UnderwayKeyEntity, UnderwayKeyRenderState>
{
    private static final Identifier LOCK_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/steel_dark.png");
    private static final RenderType LOCK_TYPE = RenderTypes.entityCutout(LOCK_TEXTURE);

    private static final float LOCK_HALF = 0.3125F;
    private static final float LOCK_HEIGHT = 0.25F;
    private static final float COLLAR_HALF = 0.1875F;
    private static final float COLLAR_HEIGHT = 0.375F;
    private static final float KEY_HEIGHT = 0.8F;
    private static final float KEY_SCALE = 0.9F;
    private static final float KEY_BOB = 0.03F;

    // Made on first use: item components are not bound yet when renderers are built
    private ItemStack key = ItemStack.EMPTY;

    public UnderwayKeyRender(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public UnderwayKeyRenderState createRenderState()
    {
        return new UnderwayKeyRenderState();
    }

    @Override
    public void extractRenderState(UnderwayKeyEntity entity, UnderwayKeyRenderState state, float partialTicks)
    {
        super.extractRenderState(entity, state, partialTicks);
        state.charge = entity.charge() / (float) HatchBalance.CHARGE_TICKS;
        state.opening = entity.isOpening();
        state.turn = Mth.lerp(partialTicks, entity.turnO, entity.turn);
        if (key.isEmpty())
            key = new ItemStack(VoidHeartItems.UNDERWAY_KEY);
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.key, key, ItemDisplayContext.FIXED, entity.level(), null, entity.getId());
    }

    @Override
    public void submit(UnderwayKeyRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, LOCK_TYPE, (pose, buffer) ->
        {
            box(pose, buffer, -LOCK_HALF, 0, -LOCK_HALF, LOCK_HALF, LOCK_HEIGHT, LOCK_HALF, light);
            box(pose, buffer, -COLLAR_HALF, LOCK_HEIGHT, -COLLAR_HALF, COLLAR_HALF, COLLAR_HEIGHT, COLLAR_HALF, light);
        });

        poseStack.pushPose();
        poseStack.translate(0, KEY_HEIGHT + Mth.sin(state.ageInTicks * 0.1F) * KEY_BOB, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(state.turn));
        poseStack.scale(KEY_SCALE, KEY_SCALE, KEY_SCALE);
        state.key.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** A box whose faces each take the texture at a pixel a sixteenth of a block, corners counter-clockwise seen from outside. */
    static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int light)
    {
        float w = x1 - x0;
        float h = y1 - y0;
        float d = z1 - z0;
        quad(pose, buffer, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, w, h, light, 0, 0, 1);
        quad(pose, buffer, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, w, h, light, 0, 0, -1);
        quad(pose, buffer, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, d, h, light, -1, 0, 0);
        quad(pose, buffer, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, d, h, light, 1, 0, 0);
        quad(pose, buffer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, w, d, light, 0, 1, 0);
        quad(pose, buffer, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, w, d, light, 0, -1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float uMax, float vMax, int light, float nx, float ny, float nz)
    {
        vertex(pose, buffer, x0, y0, z0, 0, 0, light, nx, ny, nz);
        vertex(pose, buffer, x1, y1, z1, 0, vMax, light, nx, ny, nz);
        vertex(pose, buffer, x2, y2, z2, uMax, vMax, light, nx, ny, nz);
        vertex(pose, buffer, x3, y3, z3, uMax, 0, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int light, float nx, float ny, float nz)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
