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
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.render.state.UnderwayKeyRenderState;
import net.voxelindustry.voidheart.common.content.underway.HatchBalance;
import net.voxelindustry.voidheart.common.content.underway.KeyDefence;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An Underway key at work: a low steel lock risen from the line where the hatch's leaves meet, and the key standing in it, turning faster as the charge fills.
 * Over it the hatch's shield: four short armoured posts risen from the corners of the coaming, each capped with a warm lens, and six faint amber panes thrown between them,
 * four leaning in from the sides and two for the roof. A pane flares when it takes a hit, then goes dark. With every pane dark the posts sink back.
 * <p>
 * Render types. Lock and posts are {@link RenderTypes#entityCutout}: opaque, writing depth, lit by the world. The lenses are the same with full light, so they glow in the dark.
 * The panes are {@link VoidHeartRenderTypes#radiant}: added to what is behind, never writing depth and never culled, so they cross each other in any order without flicker
 * and show from inside as well. No pane lies in the plane of another or of a block's face.
 * <p>
 * Per frame: 2 boxes for the lock, 8 for the posts and at most 6 quads of panes, all rebuilt, nothing cached.
 */
public class UnderwayKeyRender extends EntityRenderer<UnderwayKeyEntity, UnderwayKeyRenderState>
{
    private static final Identifier STEEL_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/steel_dark.png");
    private static final Identifier LENS_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/underway/lens_warm.png");
    private static final Identifier PANE_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/underway_shield_pane.png");
    private static final RenderType STEEL_TYPE = RenderTypes.entityCutout(STEEL_TEXTURE);
    private static final RenderType LENS_TYPE = RenderTypes.entityCutout(LENS_TEXTURE);
    private static final RenderType PANE_TYPE = VoidHeartRenderTypes.radiant(PANE_TEXTURE);

    private static final float LOCK_HALF = 0.3125F;
    private static final float LOCK_HEIGHT = 0.25F;
    private static final float COLLAR_HALF = 0.1875F;
    private static final float COLLAR_HEIGHT = 0.375F;
    private static final float KEY_HEIGHT = 0.8F;
    private static final float KEY_SCALE = 0.9F;
    private static final float KEY_BOB = 0.03F;

    // The posts stand on the corners of the coaming
    private static final float POST_OFFSET = UnderwayKeyEntity.SHIELD_WIDTH / 2;
    private static final float POST_HALF = 0.1875F;
    private static final float POST_HEIGHT = 1.125F;
    private static final float LENS_HALF = 0.125F;
    private static final float LENS_HEIGHT = 0.1875F;

    // The panes lean in from the posts' feet to a flat roof half as wide
    private static final float PANE_BASE = POST_OFFSET;
    private static final float PANE_FOOT = 0.06F;
    private static final float ROOF_HALF = PANE_BASE / 2;
    private static final float ROOF_HEIGHT = UnderwayKeyEntity.SHIELD_HEIGHT;
    private static final float SLANT = (float) Math.hypot(PANE_BASE - ROOF_HALF, ROOF_HEIGHT - PANE_FOOT);
    private static final int PANE_COLOR = 0xFFD890;
    private static final int FLARE_COLOR = 0xFFF6DC;
    private static final int PANE_ALPHA = 105;
    private static final int FLARE_ALPHA = 255;
    // The panes breathe a little, each out of step with the others
    private static final float PANE_PULSE = 0.18F;
    private static final float PANE_PULSE_RATE = 0.07F;

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
        state.rise = Mth.lerp(partialTicks, entity.riseO, entity.rise);
        for (int pane = 0; pane < KeyDefence.PANES; pane++)
        {
            float light = Mth.lerp(partialTicks, entity.paneLightO[pane], entity.paneLight[pane]);
            float pulse = 1 - PANE_PULSE + PANE_PULSE * Mth.sin((state.ageInTicks + pane * 23) * PANE_PULSE_RATE);
            state.paneFlare[pane] = Mth.clamp((entity.paneFlare[pane] - partialTicks) / UnderwayKeyEntity.FLARE_TICKS, 0, 1);
            state.paneAlpha[pane] = Math.max(light * pulse * PANE_ALPHA, state.paneFlare[pane] * FLARE_ALPHA);
        }
        if (key.isEmpty())
            key = new ItemStack(VoidHeartItems.UNDERWAY_KEY);
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.key, key, ItemDisplayContext.FIXED, entity.level(), null, entity.getId());
    }

    /** The shield reaches past the bare key's own box, and its posts still sink when that box has already shrunk. */
    @Override
    protected AABB getBoundingBoxForCulling(UnderwayKeyEntity entity, float partialTicks)
    {
        return AABB.ofSize(entity.position().add(0, UnderwayKeyEntity.SHIELD_HEIGHT / 2, 0), UnderwayKeyEntity.SHIELD_WIDTH + 1, UnderwayKeyEntity.SHIELD_HEIGHT + 1, UnderwayKeyEntity.SHIELD_WIDTH + 1);
    }

    @Override
    public void submit(UnderwayKeyRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        int light = state.lightCoords;
        float post = POST_HEIGHT * state.rise;
        collector.submitCustomGeometry(poseStack, STEEL_TYPE, (pose, buffer) ->
        {
            box(pose, buffer, -LOCK_HALF, 0, -LOCK_HALF, LOCK_HALF, LOCK_HEIGHT, LOCK_HALF, light);
            box(pose, buffer, -COLLAR_HALF, LOCK_HEIGHT, -COLLAR_HALF, COLLAR_HALF, COLLAR_HEIGHT, COLLAR_HALF, light);
            if (post > 0)
                forEachPost((x, z) -> box(pose, buffer, x - POST_HALF, 0, z - POST_HALF, x + POST_HALF, post, z + POST_HALF, light));
        });
        if (post > 0)
            collector.submitCustomGeometry(poseStack, LENS_TYPE, (pose, buffer) ->
                    forEachPost((x, z) -> box(pose, buffer, x - LENS_HALF, post, z - LENS_HALF, x + LENS_HALF, post + LENS_HEIGHT * state.rise, z + LENS_HALF, LightCoordsUtil.FULL_BRIGHT)));

        poseStack.pushPose();
        poseStack.translate(0, KEY_HEIGHT + Mth.sin(state.ageInTicks * 0.1F) * KEY_BOB, 0);
        poseStack.rotate(Axis.YP.rotationDegrees(state.turn));
        poseStack.scale(KEY_SCALE, KEY_SCALE, KEY_SCALE);
        state.key.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();

        collector.submitCustomGeometry(poseStack, PANE_TYPE, (pose, buffer) -> panes(pose, buffer, state));
        super.submit(state, poseStack, collector, camera);
    }

    private interface PostSpot
    {
        void at(float x, float z);
    }

    private static void forEachPost(PostSpot spot)
    {
        for (int corner = 0; corner < 4; corner++)
            spot.at((corner & 1) == 0 ? -POST_OFFSET : POST_OFFSET, (corner & 2) == 0 ? -POST_OFFSET : POST_OFFSET);
    }

    /** The sides in the order of {@link KeyDefence}'s panes, north first, each the north one turned a quarter more, then the two halves of the roof. */
    private static void panes(PoseStack.Pose pose, VertexConsumer buffer, UnderwayKeyRenderState state)
    {
        for (int side = 0; side < 4; side++)
        {
            int color = paneColor(state, side);
            if (ARGB.alpha(color) == 0)
                continue;
            float inset = (PANE_BASE - ROOF_HALF);
            paneVertex(pose, buffer, side, -PANE_BASE, PANE_FOOT, -PANE_BASE, 0, SLANT, color);
            paneVertex(pose, buffer, side, PANE_BASE, PANE_FOOT, -PANE_BASE, PANE_BASE * 2, SLANT, color);
            paneVertex(pose, buffer, side, ROOF_HALF, ROOF_HEIGHT, -ROOF_HALF, PANE_BASE * 2 - inset, 0, color);
            paneVertex(pose, buffer, side, -ROOF_HALF, ROOF_HEIGHT, -ROOF_HALF, inset, 0, color);
        }
        for (int half = 0; half < 2; half++)
        {
            int color = paneColor(state, KeyDefence.ROOF_WEST + half);
            if (ARGB.alpha(color) == 0)
                continue;
            float x0 = half == 0 ? -ROOF_HALF : 0;
            float x1 = x0 + ROOF_HALF;
            paneVertex(pose, buffer, 0, x0, ROOF_HEIGHT, -ROOF_HALF, 0, 0, color);
            paneVertex(pose, buffer, 0, x0, ROOF_HEIGHT, ROOF_HALF, 0, ROOF_HALF * 2, color);
            paneVertex(pose, buffer, 0, x1, ROOF_HEIGHT, ROOF_HALF, ROOF_HALF, ROOF_HALF * 2, color);
            paneVertex(pose, buffer, 0, x1, ROOF_HEIGHT, -ROOF_HALF, ROOF_HALF, 0, color);
        }
    }

    private static int paneColor(UnderwayKeyRenderState state, int pane)
    {
        return ARGB.color((int) state.paneAlpha[pane], ARGB.srgbLerp(state.paneFlare[pane], PANE_COLOR, FLARE_COLOR));
    }

    private static void paneVertex(PoseStack.Pose pose, VertexConsumer buffer, int quarterTurns, float x, float y, float z, float u, float v, int color)
    {
        for (int turn = 0; turn < quarterTurns; turn++)
        {
            float turned = -z;
            z = x;
            x = turned;
        }
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
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
