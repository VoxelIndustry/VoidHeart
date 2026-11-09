package net.voxelindustry.voidheart.client.render.energy.fissure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureContent;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureTile;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * A seep's edge, shown on demand as the energy system's shared dashed ring in fissure violet: while holding Contravoid, a ward, a standing stone or a
 * Fissure Seed, or looking at the fissure. Dashes about a block long with block gaps follow the ground and drift a full turn a minute. Each ring finds its
 * ground every couple of seconds, or sooner while the seep spreads. Blended rather than added, so the violet holds on bright ground.
 */
final class SeepRings
{
    private static final float  DASH         = 1;
    private static final float  GAP          = 1;
    private static final int    DASH_STEPS   = 4;
    private static final float  SAMPLE       = 0.25F;
    private static final float  HALF_WIDTH   = 0.07F;
    private static final double LIFT         = 0.04;
    private static final float  ALPHA        = 0.55F;
    private static final float  TURN_TICKS   = 1200;
    private static final float  FADE_IN      = 1 / 5F;
    private static final float  FADE_OUT     = 1 / 10F;
    private static final int    GROUND_TICKS = 40;
    private static final float  RESAMPLE_AT  = 0.5F;

    private static final class Ring
    {
        float    alpha;
        boolean  wanted;
        float    radius;
        float    sampledRadius;
        double[] heights;
        long     groundAt;
    }

    private static final Map<BlockPos, Ring> RINGS = new HashMap<>();

    private SeepRings()
    {
    }

    static void clear()
    {
        RINGS.clear();
    }

    static boolean any()
    {
        return !RINGS.isEmpty();
    }

    static void tick(Minecraft minecraft, long time)
    {
        var player = minecraft.player;
        boolean holding = shows(player.getMainHandItem()) || shows(player.getOffhandItem());
        var looked = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;

        var wanted = new HashSet<BlockPos>();
        for (var tile : VoidFissureTile.CLIENT_LOADED)
        {
            if (tile.getLevel() != minecraft.level || tile.isRemoved() || !(holding || tile.getBlockPos().equals(looked)))
                continue;
            wanted.add(tile.getBlockPos());
            var ring = RINGS.computeIfAbsent(tile.getBlockPos(), pos -> new Ring());
            ring.radius = tile.seepRadius(time);
        }

        RINGS.forEach((pos, ring) ->
        {
            ring.wanted = wanted.contains(pos);
            ring.alpha = Mth.clamp(ring.alpha + (ring.wanted ? FADE_IN : -FADE_OUT), 0, 1);
            if (ring.alpha > 0 && ring.radius > 0.5F
                    && (ring.heights == null || time - ring.groundAt >= GROUND_TICKS || Math.abs(ring.radius - ring.sampledRadius) > RESAMPLE_AT))
            {
                ring.heights = ground(minecraft, pos, ring.radius);
                ring.sampledRadius = ring.radius;
                ring.groundAt = time;
            }
        });
        RINGS.values().removeIf(ring -> !ring.wanted && ring.alpha <= 0);
    }

    private static boolean shows(ItemStack stack)
    {
        if (stack.is(VoidFissureContent.FISSURE_SEED) || stack.is(LeyStoneContent.LEY_WARD.asItem()) || stack.is(LeyStoneContent.LEY_STONE.asItem()))
            return true;
        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null && storage.type() == EssenceRegistry.CONTRAVOID && storage.amount() > 0)
            return true;
        var matrix = stack.get(VoidHeartDataComponents.MULTI_ESSENCE_STORAGE);
        if (matrix != null)
        {
            for (int i = 0; i < matrix.storageCount(); i++)
            {
                if (matrix.types()[i] == EssenceRegistry.CONTRAVOID && matrix.amounts()[i] > 0)
                    return true;
            }
        }
        return false;
    }

    private static double[] ground(Minecraft minecraft, BlockPos pos, float radius)
    {
        double perimeter = Mth.TWO_PI * radius;
        int samples = Math.max(8, (int) Math.ceil(perimeter / SAMPLE));
        var heights = new double[samples];
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;
        int reference = pos.getY();
        for (int i = 0; i < samples; i++)
        {
            double angle = Mth.TWO_PI * i / samples;
            double y = FissureLook.surface(minecraft.level, cx + Math.cos(angle) * radius, cz + Math.sin(angle) * radius, reference);
            heights[i] = Double.isNaN(y) ? pos.getY() : y;
            reference = Mth.floor(heights[i]);
        }
        return heights;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time)
    {
        RINGS.forEach((pos, ring) ->
        {
            if (ring.heights == null || ring.alpha <= 0 || ring.sampledRadius <= 0.5F)
                return;
            float radius = ring.sampledRadius;
            double perimeter = Mth.TWO_PI * radius;
            double drift = (time / TURN_TICKS) * perimeter;
            int dashes = Math.max(1, (int) Math.round(perimeter / (DASH + GAP)));
            double period = perimeter / dashes;
            int color = FissureLook.color(FissureLook.FISSURE_VIOLET, ALPHA * ring.alpha);
            double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5;

            for (int dash = 0; dash < dashes; dash++)
            {
                double start = dash * period + drift;
                double length = period * DASH / (DASH + GAP);
                Vector3f previous = null;
                for (int step = 0; step <= DASH_STEPS; step++)
                {
                    double along = start + length * step / DASH_STEPS;
                    double angle = along / radius;
                    var here = FissureLook.relative(cx + Math.cos(angle) * radius, height(ring.heights, along / perimeter) + LIFT, cz + Math.sin(angle) * radius, camera);
                    if (previous != null)
                        segment(pose, buffer, previous, here, color);
                    previous = here;
                }
            }
        });
    }

    private static double height(double[] heights, double fraction)
    {
        double index = ((fraction % 1) + 1) % 1 * heights.length;
        int i = (int) index % heights.length;
        return Mth.lerp(index - Math.floor(index), heights[i], heights[(i + 1) % heights.length]);
    }

    private static void segment(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, int color)
    {
        float dx = b.x - a.x, dz = b.z - a.z;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-5F)
            return;
        float sx = -dz / length * HALF_WIDTH, sz = dx / length * HALF_WIDTH;
        FissureLook.vertex(buffer, pose, a.x - sx, a.y, a.z - sz, color);
        FissureLook.vertex(buffer, pose, a.x + sx, a.y, a.z + sz, color);
        FissureLook.vertex(buffer, pose, b.x + sx, b.y, b.z + sz, color);
        FissureLook.vertex(buffer, pose, b.x - sx, b.y, b.z - sz, color);
    }
}
