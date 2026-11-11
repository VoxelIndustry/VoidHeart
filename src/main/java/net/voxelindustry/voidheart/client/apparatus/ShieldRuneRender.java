package net.voxelindustry.voidheart.client.apparatus;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.voxelindustry.voidheart.client.render.VoidHeartRenderTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * A shield hit lights a patch of runes on a shell around the wearer, centred where it struck and as wide as the hit was hard. Only a hit that breaks the shield
 * spreads over the whole shell.
 * The patch is a fractal mosaic: the few cells nearest the impact hold one rune each at full size, the rest are quartered, and quartered again toward the edge.
 * It grows outward from the impact with each tile fading in, then shrinks back toward it with each tile fading out, so the whole runes are the first and last lit.
 * Every hit on one wearer shares a single tiling and each tile takes the brightest hit over it, so runes never overlap however many hits land together.
 * The hull is convex, turned with the body and drawn additively with back faces culled and no depth writes, so it can't flicker.
 * The wearer's own shell is left out in first person, where the HUD shows the hit at the edge of the view instead.
 */
public final class ShieldRuneRender
{
    public static final int LIFE_TICKS = 22;

    /** The green of ley lattice nodes, softer than the hostile green of beams and bolts. */
    public static final int COLOR        = 0x6FCF97;
    public static final int BROKEN_COLOR = 0xEF8A98;

    private static final RenderType TYPE = VoidHeartRenderTypes.SHIELD_RUNES;

    /** Clear of armor and held items on every side, so no tile sits on another surface. */
    private static final float MARGIN        = 0.18F;
    /** The size of a whole rune, the largest of the mosaic. */
    private static final float TILE          = 0.26F;
    /** Taken off each side of a tile, as a share of its size, so every rune keeps an edge of its own at any scale. */
    private static final float TILE_GAP      = 0.1F;
    private static final int   ATLAS_CELLS   = 4;

    /** How far over the shell a hit reaches, from one that barely scratched it to one that took it all. */
    private static final float REACH_FAINT   = 0.45F;
    private static final float REACH_FULL    = 0.85F;
    /** Where in its reach a patch starts to fade into nothing, so its edge is soft. */
    private static final float EDGE_FADE     = 0.7F;
    /** How bright a hit that barely scratched the shield lights it, against one that took all of it. */
    private static final float FAINT_HIT     = 0.35F;

    /** Ticks the front takes to grow from the impact to the edge of the patch. */
    private static final float SPREAD_TICKS  = 6;
    /** Ticks the edge of the patch goes dark before the impact does, as it shrinks back. */
    private static final float RETREAT_TICKS = 9;
    private static final float FADE_IN       = 3;
    private static final float FADE_OUT      = 4;
    /** Each tile comes and goes a little early or late, so the fronts never look ruled. */
    private static final float JITTER        = 1.5F;

    /** Cells whose middle is this close to an impact keep a whole rune, which makes one to four of them. */
    private static final float WHOLE_REACH   = 1.15F * TILE;
    /** Beyond this share of its reach, a hit's quarters are always quartered again. */
    private static final float FINE_FROM     = 0.55F;
    /** The chance a quarter nearer than that is quartered again anyway, so the mosaic never looks ruled either. */
    private static final float SPLIT_CHANCE  = 0.3F;
    /** How bright the small runes are against the whole ones, so the impact stands out of the mosaic. */
    private static final float MOSAIC_DIM    = 0.7F;

    private ShieldRuneRender()
    {
    }

    /**
     * One hit, in the frame of the body with its feet at the origin and the impact on the hull.
     */
    private record Impact(float age, float x, float y, float z, float reach, float brightness, int rgb)
    {
    }

    private record Shell(PoseStack.Pose pose, VertexConsumer buffer, List<Impact> impacts)
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || ShieldHits.all().isEmpty())
            return;

        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partial;
        var camera = context.levelState().cameraRenderState.pos;
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        var poseStack = context.poseStack();

        for (var entry : ShieldHits.all().entrySet())
        {
            if (!(level.getEntity(entry.getKey()) instanceof LivingEntity entity) || entity.isInvisible())
                continue;
            if (firstPerson && entity == minecraft.getCameraEntity())
                continue;

            float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
            float half = entity.getBbWidth() / 2 + MARGIN;
            float height = entity.getBbHeight() + MARGIN;
            float lift = -MARGIN / 2;
            var impacts = impacts(entry.getValue(), now, bodyYaw, half, height);
            if (impacts.isEmpty())
                continue;

            double x = Mth.lerp(partial, entity.xo, entity.getX());
            double y = Mth.lerp(partial, entity.yo, entity.getY());
            double z = Mth.lerp(partial, entity.zo, entity.getZ());
            poseStack.pushPose();
            poseStack.translate(x - camera.x, y - camera.y + lift, z - camera.z);
            poseStack.rotate(Axis.YP.rotationDegrees(-bodyYaw));
            context.submitNodeCollector().submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> hull(new Shell(pose, buffer, impacts), half, height));
            poseStack.popPose();
        }
    }

    private static List<Impact> impacts(List<ShieldHits.Hit> hits, float now, float bodyYaw, float half, float height)
    {
        float yaw = bodyYaw * Mth.DEG_TO_RAD;
        float sin = Mth.sin(yaw);
        float cos = Mth.cos(yaw);
        float wholeShell = Mth.sqrt(8 * half * half + height * height);

        var impacts = new ArrayList<Impact>();
        for (var hit : hits)
        {
            float age = now - hit.tick();
            if (age < 0 || age >= LIFE_TICKS)
                continue;

            // Into the frame of the body, which the hull turns with, then out along the way the hit came until it meets the hull
            var from = hit.from();
            float awayX = from.x() * cos + from.z() * sin;
            float awayY = from.y();
            float awayZ = -from.x() * sin + from.z() * cos;
            float reach = Math.max(Math.max(Math.abs(awayX), Math.abs(awayZ)) / half, Math.abs(awayY) / (height / 2));
            float onHull = reach < 0.001F ? 0 : 1 / reach;

            impacts.add(new Impact(age, awayX * onHull, height / 2 + awayY * onHull, awayZ * onHull,
                    hit.broke() ? wholeShell : Mth.lerp(hit.strength(), REACH_FAINT, REACH_FULL),
                    Mth.lerp(hit.strength(), FAINT_HIT, 1),
                    hit.broke() ? BROKEN_COLOR : COLOR));
        }
        return impacts;
    }

    private static void hull(Shell shell, float half, float height)
    {
        int columns = Math.max(1, Math.round(2 * half / TILE));
        int rows = Math.max(1, Math.round(height / TILE));
        float width = 2 * half;

        // Each side runs from a corner along u then v, with u x v facing out, so its quads wind the way back face culling keeps
        face(shell, 101, half, 0, -half, -width, 0, 0, 0, height, 0, columns, rows, 0, 0, -1);
        face(shell, 211, -half, 0, half, width, 0, 0, 0, height, 0, columns, rows, 0, 0, 1);
        face(shell, 307, half, 0, half, 0, 0, -width, 0, height, 0, columns, rows, 1, 0, 0);
        face(shell, 401, -half, 0, -half, 0, 0, width, 0, height, 0, columns, rows, -1, 0, 0);
        face(shell, 503, -half, height, -half, 0, 0, width, width, 0, 0, columns, columns, 0, 1, 0);
    }

    private static void face(Shell shell, int seed, float originX, float originY, float originZ, float uX, float uY, float uZ, float vX, float vY, float vZ,
                             int cellsU, int cellsV, float normalX, float normalY, float normalZ)
    {
        float duX = uX / cellsU, duY = uY / cellsU, duZ = uZ / cellsU;
        float dvX = vX / cellsV, dvY = vY / cellsV, dvZ = vZ / cellsV;
        // Half the diagonal of a cell, so a cell is kept while any part of it could be within reach
        float cellRadius = Mth.sqrt((duX + dvX) * (duX + dvX) + (duY + dvY) * (duY + dvY) + (duZ + dvZ) * (duZ + dvZ)) / 2;

        for (int j = 0; j < cellsV; j++)
        {
            for (int i = 0; i < cellsU; i++)
            {
                float x = originX + duX * i + dvX * j;
                float y = originY + duY * i + dvY * j;
                float z = originZ + duZ * i + dvZ * j;
                float midX = x + (duX + dvX) / 2;
                float midY = y + (duY + dvY) / 2;
                float midZ = z + (duZ + dvZ) / 2;

                var nearest = nearest(shell, midX, midY, midZ);
                float distance = distance(nearest, midX, midY, midZ);
                if (distance - cellRadius > nearest.reach())
                    continue;

                int cellSeed = Mth.murmurHash3Mixer(seed * 7919 + j * 131 + i);
                if (distance < WHOLE_REACH)
                    rune(shell, cellSeed, 1, x, y, z, duX, duY, duZ, dvX, dvY, dvZ, normalX, normalY, normalZ);
                else
                    split(shell, cellSeed, 1, x, y, z, duX, duY, duZ, dvX, dvY, dvZ, normalX, normalY, normalZ);
            }
        }
    }

    /**
     * Quarters a cell, quartering each quarter again toward the edge of the patch, or by chance nearer in.
     */
    private static void split(Shell shell, int seed, int depth, float x, float y, float z, float duX, float duY, float duZ, float dvX, float dvY, float dvZ,
                              float normalX, float normalY, float normalZ)
    {
        float hux = duX / 2, huy = duY / 2, huz = duZ / 2;
        float hvx = dvX / 2, hvy = dvY / 2, hvz = dvZ / 2;
        for (int quarter = 0; quarter < 4; quarter++)
        {
            int a = quarter & 1;
            int b = quarter >> 1;
            float qx = x + hux * a + hvx * b;
            float qy = y + huy * a + hvy * b;
            float qz = z + huz * a + hvz * b;
            int quarterSeed = Mth.murmurHash3Mixer(seed + quarter + 1);

            if (depth < 2)
            {
                float midX = qx + (hux + hvx) / 2;
                float midY = qy + (huy + hvy) / 2;
                float midZ = qz + (huz + hvz) / 2;
                var nearest = nearest(shell, midX, midY, midZ);
                if (distance(nearest, midX, midY, midZ) > FINE_FROM * nearest.reach() || noise(quarterSeed) < SPLIT_CHANCE)
                {
                    split(shell, quarterSeed, depth + 1, qx, qy, qz, hux, huy, huz, hvx, hvy, hvz, normalX, normalY, normalZ);
                    continue;
                }
            }
            rune(shell, quarterSeed, MOSAIC_DIM, qx, qy, qz, hux, huy, huz, hvx, hvy, hvz, normalX, normalY, normalZ);
        }
    }

    /**
     * The impact a point is deepest into the reach of, which decides how finely the mosaic is cut there.
     */
    private static Impact nearest(Shell shell, float x, float y, float z)
    {
        Impact best = shell.impacts().getFirst();
        float bestShare = Float.MAX_VALUE;
        for (var impact : shell.impacts())
        {
            float share = distance(impact, x, y, z) / impact.reach();
            if (share < bestShare)
            {
                best = impact;
                bestShare = share;
            }
        }
        return best;
    }

    private static float distance(Impact impact, float x, float y, float z)
    {
        float dx = x - impact.x();
        float dy = y - impact.y();
        float dz = z - impact.z();
        return Mth.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * How lit a tile is by one hit: reached by the growing front and faded in, then faded out as the patch shrinks back past it, and softer near the patch's edge.
     */
    private static float light(Impact impact, int seed, float x, float y, float z)
    {
        float share = distance(impact, x, y, z) / impact.reach();
        if (share >= 1)
            return 0;

        float jitter = (noise(seed) - 0.5F) * JITTER;
        float arrives = Math.max(0, share * SPREAD_TICKS + jitter);
        float leaves = LIFE_TICKS - share * RETREAT_TICKS + jitter;
        float in = Mth.clamp((impact.age() - arrives) / FADE_IN, 0, 1);
        float out = Mth.clamp((leaves - impact.age()) / FADE_OUT, 0, 1);
        float edge = 1 - Mth.clamp((share - EDGE_FADE) / (1 - EDGE_FADE), 0, 1);
        return Math.min(in, out) * edge * impact.brightness();
    }

    private static void rune(Shell shell, int seed, float dim, float x, float y, float z, float duX, float duY, float duZ, float dvX, float dvY, float dvZ,
                             float normalX, float normalY, float normalZ)
    {
        float midX = x + (duX + dvX) / 2;
        float midY = y + (duY + dvY) / 2;
        float midZ = z + (duZ + dvZ) / 2;

        // The brightest hit over the tile lights it alone, so hits never stack runes on each other
        float lit = 0;
        int rgb = COLOR;
        for (var impact : shell.impacts())
        {
            float light = light(impact, seed, midX, midY, midZ);
            if (light > lit)
            {
                lit = light;
                rgb = impact.rgb();
            }
        }
        int alpha = (int) (255 * lit * dim);
        if (alpha <= 0)
            return;

        int glyph = (seed >>> 8) & (ATLAS_CELLS * ATLAS_CELLS - 1);
        float u0 = (glyph % ATLAS_CELLS) / (float) ATLAS_CELLS;
        float v0 = (glyph / ATLAS_CELLS) / (float) ATLAS_CELLS;
        float cell = 1F / ATLAS_CELLS;
        int color = ARGB.color(alpha, rgb);

        // Pulled in toward the middle at each corner by the same share at every size
        float keep = 1 - 2 * TILE_GAP;
        float startX = midX - (duX + dvX) / 2 * keep;
        float startY = midY - (duY + dvY) / 2 * keep;
        float startZ = midZ - (duZ + dvZ) / 2 * keep;
        float ux = duX * keep, uy = duY * keep, uz = duZ * keep;
        float vx = dvX * keep, vy = dvY * keep, vz = dvZ * keep;
        var pose = shell.pose();
        var buffer = shell.buffer();
        vertex(pose, buffer, color, startX, startY, startZ, u0, v0 + cell, normalX, normalY, normalZ);
        vertex(pose, buffer, color, startX + ux, startY + uy, startZ + uz, u0 + cell, v0 + cell, normalX, normalY, normalZ);
        vertex(pose, buffer, color, startX + ux + vx, startY + uy + vy, startZ + uz + vz, u0 + cell, v0, normalX, normalY, normalZ);
        vertex(pose, buffer, color, startX + vx, startY + vy, startZ + vz, u0, v0, normalX, normalY, normalZ);
    }

    private static float noise(int seed)
    {
        return (Mth.murmurHash3Mixer(seed) & 0xFFFF) / 65536F;
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int color, float x, float y, float z, float u, float v, float normalX, float normalY, float normalZ)
    {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
