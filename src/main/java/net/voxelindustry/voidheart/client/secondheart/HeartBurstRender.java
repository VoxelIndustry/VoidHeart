package net.voxelindustry.voidheart.client.secondheart;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.secondheart.HeartRules;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The second heart's burst as the reform ends: one heartbeat shoving the world away. A flat ring at foot height grows to the burst's reach,
 * dark violet broken into uneven cells behind a bright thin leading edge, fading as it widens, while motes are thrown outward.
 * <p>
 * Render type: vanilla's emissive entity translucency, so the violet stays dark in daylight and the edge stays bright at night, and it doesn't
 * write depth. It lies flat on its own and nothing else draws in that plane. Per frame: {@link #SEGMENTS} quads per burst, for eight ticks.
 */
public final class HeartBurstRender
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/second_heart/burst_ring.png");
    private static final RenderType TYPE    = RenderTypes.entityTranslucentEmissive(TEXTURE);

    private static final float RING_TICKS   = 8;
    private static final int   SEGMENTS     = 64;
    /** Times the texture repeats around the ring. */
    private static final float WRAPS        = 6;
    private static final float WIDTH        = 1.1F;
    private static final float LIFT         = 0.06F;
    private static final float ALPHA        = 0.95F;

    private static final int    MOTES        = 28;
    private static final double MOTE_SPEED   = 0.35;

    private HeartBurstRender()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null)
            return;
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        var camera = context.levelState().cameraRenderState.pos;
        var poseStack = context.poseStack();

        for (var player : level.players())
        {
            float age = SecondHeart.elapsed(player, partial) - HeartRules.BURST_TICK;
            if (age < 0 || age >= RING_TICKS)
                continue;
            float t = age / RING_TICKS;
            float edge = (float) HeartRules.BURST_RADIUS * (1 - (1 - t) * (1 - t));
            float inner = Math.max(0, edge - WIDTH);
            int color = ARGB.color(ALPHA * (1 - t * t), 0xFFFFFF);
            var feet = player.getPosition(partial);

            poseStack.pushPose();
            poseStack.translate(feet.x - camera.x, feet.y - camera.y + LIFT, feet.z - camera.z);
            context.submitNodeCollector().submitCustomGeometry(poseStack, TYPE, (pose, buffer) ->
            {
                for (int i = 0; i < SEGMENTS; i++)
                {
                    float a0 = i * Mth.TWO_PI / SEGMENTS, a1 = (i + 1) * Mth.TWO_PI / SEGMENTS;
                    float u0 = i * WRAPS / SEGMENTS, u1 = (i + 1) * WRAPS / SEGMENTS;
                    float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                    // Counter-clockwise seen from above, the inside at v 0 and the leading edge at v 1
                    vertex(buffer, pose, c0 * inner, s0 * inner, u0, 0, color);
                    vertex(buffer, pose, c0 * edge, s0 * edge, u0, 1, color);
                    vertex(buffer, pose, c1 * edge, s1 * edge, u1, 1, color);
                    vertex(buffer, pose, c1 * inner, s1 * inner, u1, 0, color);
                }
            });
            poseStack.popPose();
        }
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer buffer, com.mojang.blaze3d.vertex.PoseStack.Pose pose, float x, float z,
                               float u, float v, int color)
    {
        buffer.addVertex(pose, x, 0, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, 0, 1, 0);
    }

    /**
     * Motes thrown out with the burst, once, as it starts.
     */
    public static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        if (level == null || minecraft.isPaused())
            return;
        var random = level.getRandom();
        for (var player : level.players())
        {
            if ((int) SecondHeart.elapsed(player, 0) != HeartRules.BURST_TICK)
                continue;
            var at = player.position();
            for (int i = 0; i < MOTES; i++)
            {
                float angle = random.nextFloat() * Mth.TWO_PI;
                var out = new Vec3(Mth.cos(angle), 0.15 + random.nextFloat() * 0.25, Mth.sin(angle)).scale(MOTE_SPEED * (0.6 + random.nextFloat() * 0.6));
                level.addParticle(VoidHeart.SECOND_HEART_MOTE_PARTICLE, at.x, at.y + 0.2 + random.nextFloat() * 1.2, at.z, out.x, out.y, out.z);
            }
        }
    }
}
