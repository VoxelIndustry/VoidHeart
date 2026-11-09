package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.ley.LeyGrowerTile;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneContent;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneTile;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Radii shown on demand as the energy system's shared dashed ring, in ley teal: a ward's while holding a ward or a standing stone or looking at the ward, a
 * grower's square while holding a grower or looking at it, and where each connected stone reaches machines while holding any of the three.
 * Dashes about a block long with block gaps follow the ground and drift a full turn a minute. Each ring finds its ground every couple of seconds.
 * Blended rather than added, so the teal holds on bright ground.
 */
final class LeyRings
{
    private static final float DASH          = 1;
    private static final float GAP           = 1;
    private static final int   DASH_STEPS    = 4;
    private static final float SAMPLE        = 0.25F;
    private static final float HALF_WIDTH    = 0.07F;
    private static final double LIFT         = 0.035;
    private static final float ALPHA         = 0.5F;
    private static final float TURN_TICKS    = 1200;
    private static final float FADE_IN       = 1 / 5F;
    private static final float FADE_OUT      = 1 / 10F;
    private static final int   GROUND_TICKS  = 40;

    private enum Kind
    {
        WARD, GROWER, STONE
    }

    private record Key(BlockPos pos, Kind kind)
    {
    }

    private static final class Ring
    {
        float    alpha;
        boolean  wanted;
        double[] heights;
        long     groundAt;
    }

    private static final Map<Key, Ring> RINGS = new HashMap<>();

    private LeyRings()
    {
    }

    static void clear()
    {
        RINGS.clear();
    }

    static void tick(Minecraft minecraft, long time)
    {
        var player = minecraft.player;
        if (player == null || minecraft.level == null)
        {
            RINGS.clear();
            return;
        }

        boolean ward = holds(minecraft, LeyStoneContent.LEY_WARD.asItem());
        boolean stone = holds(minecraft, LeyStoneContent.LEY_STONE.asItem());
        boolean grower = holds(minecraft, LeyStoneContent.LEY_GROWER.asItem());
        var looked = minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;

        var wanted = new HashSet<Key>();
        for (var tile : LeyWardTile.CLIENT_LOADED)
        {
            if (ward || stone || tile.getBlockPos().equals(looked))
                wanted.add(new Key(tile.getBlockPos(), Kind.WARD));
        }
        for (var tile : LeyGrowerTile.CLIENT_LOADED)
        {
            if (grower || tile.getBlockPos().equals(looked))
                wanted.add(new Key(tile.getBlockPos(), Kind.GROWER));
        }
        if (ward || stone || grower)
        {
            for (var tile : LeyStoneTile.CLIENT_LOADED)
            {
                if (tile.isConnected())
                    wanted.add(new Key(tile.getBlockPos(), Kind.STONE));
            }
        }

        wanted.forEach(key -> RINGS.computeIfAbsent(key, k -> new Ring()));
        RINGS.forEach((key, ring) ->
        {
            ring.wanted = wanted.contains(key);
            ring.alpha = Mth.clamp(ring.alpha + (ring.wanted ? FADE_IN : -FADE_OUT), 0, 1);
            if (ring.alpha > 0 && (ring.heights == null || time - ring.groundAt >= GROUND_TICKS))
            {
                ring.heights = ground(minecraft, key);
                ring.groundAt = time;
            }
        });
        RINGS.values().removeIf(ring -> !ring.wanted && ring.alpha <= 0);
    }

    private static boolean holds(Minecraft minecraft, Item item)
    {
        return minecraft.player.getMainHandItem().is(item) || minecraft.player.getOffhandItem().is(item);
    }

    private static double perimeter(Kind kind)
    {
        return switch (kind)
        {
            case WARD -> Mth.TWO_PI * LeyStoneBalance.WARD_RADIUS;
            case STONE -> Mth.TWO_PI * LeyStoneBalance.STONE_SUPPLY_RADIUS;
            case GROWER -> 8 * (LeyStoneBalance.GROWER_RADIUS + 0.5);
        };
    }

    /**
     * The point at a distance along the ring's edge, relative to the block's centre.
     */
    private static void edge(Kind kind, double along, double[] out)
    {
        if (kind == Kind.GROWER)
        {
            double half = LeyStoneBalance.GROWER_RADIUS + 0.5;
            double side = 2 * half;
            double wrapped = ((along % (4 * side)) + 4 * side) % (4 * side);
            int edge = (int) (wrapped / side);
            double t = wrapped - edge * side - half;
            switch (edge)
            {
                case 0 -> { out[0] = t; out[1] = -half; }
                case 1 -> { out[0] = half; out[1] = t; }
                case 2 -> { out[0] = -t; out[1] = half; }
                default -> { out[0] = -half; out[1] = -t; }
            }
            return;
        }
        double radius = kind == Kind.WARD ? LeyStoneBalance.WARD_RADIUS : LeyStoneBalance.STONE_SUPPLY_RADIUS;
        double angle = along / radius;
        out[0] = Math.cos(angle) * radius;
        out[1] = Math.sin(angle) * radius;
    }

    private static double[] ground(Minecraft minecraft, Key key)
    {
        double perimeter = perimeter(key.kind());
        int samples = Math.max(8, (int) Math.ceil(perimeter / SAMPLE));
        var heights = new double[samples];
        var point = new double[2];
        double cx = key.pos().getX() + 0.5, cz = key.pos().getZ() + 0.5;
        int reference = key.pos().getY();
        for (int i = 0; i < samples; i++)
        {
            edge(key.kind(), perimeter * i / samples, point);
            double y = LeyGround.surface(minecraft.level, cx + point[0], cz + point[1], reference);
            heights[i] = Double.isNaN(y) ? key.pos().getY() : y;
            reference = Mth.floor(heights[i]);
        }
        return heights;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time)
    {
        RINGS.forEach((key, ring) ->
        {
            if (ring.heights == null || ring.alpha <= 0)
                return;
            double perimeter = perimeter(key.kind());
            double drift = (time / TURN_TICKS) * perimeter;
            int dashes = Math.max(1, (int) Math.round(perimeter / (DASH + GAP)));
            double period = perimeter / dashes;
            int color = LeyLook.color(LeyLook.TEAL, ALPHA * ring.alpha);
            var point = new double[2];
            double cx = key.pos().getX() + 0.5, cz = key.pos().getZ() + 0.5;

            for (int dash = 0; dash < dashes; dash++)
            {
                double start = dash * period + drift;
                double length = period * DASH / (DASH + GAP);
                Vector3f previous = null;
                for (int step = 0; step <= DASH_STEPS; step++)
                {
                    double along = start + length * step / DASH_STEPS;
                    edge(key.kind(), along, point);
                    var here = LeyLook.relative(cx + point[0], height(ring.heights, along / perimeter) + LIFT, cz + point[1], camera);
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
        LeyLook.vertex(buffer, pose, a.x - sx, a.y, a.z - sz, color);
        LeyLook.vertex(buffer, pose, a.x + sx, a.y, a.z + sz, color);
        LeyLook.vertex(buffer, pose, b.x + sx, b.y, b.z + sz, color);
        LeyLook.vertex(buffer, pose, b.x - sx, b.y, b.z - sz, color);
    }
}
