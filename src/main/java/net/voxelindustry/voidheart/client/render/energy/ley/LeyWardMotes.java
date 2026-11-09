package net.voxelindustry.voidheart.client.render.energy.ley;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.ley.LeyStoneBalance;
import net.voxelindustry.voidheart.common.energy.ley.LeyWardTile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sparse motes hanging almost still inside a ward's radius, as many as its strength allows. A weakened ward's motes drift toward mauve-grey, and past half
 * its strength lost some wink out early with a short flicker. Each mote finds its ground once, when it appears. At most about 50 soft dots per ward.
 */
final class LeyWardMotes
{
    /**
     * About one mote per 4x4 blocks of ground at full strength.
     */
    private static final float  GROUND_PER_MOTE = 16;
    private static final int    LIFE_MIN        = 60;
    private static final int    LIFE_SPREAD     = 41;
    private static final float  RISE_PER_TICK   = 0.05F / 20;
    private static final float  LOW             = 0.5F;
    private static final float  HIGH            = 2.5F;
    private static final int    FADE_IN         = 15;
    private static final int    FADE_OUT        = 20;
    private static final int    FLICKER_TICKS   = 6;
    private static final float  ALPHA           = 0.55F;
    private static final float  RADIUS          = 0.1F;
    private static final int    FILL_TICKS      = 40;
    private static final double DRAW_DISTANCE   = 96;

    private static final class Mote
    {
        double x, y, z;
        long   born;
        int    life;
        long   winks = -1;
    }

    private static final Map<LeyWardTile, List<Mote>> FIELDS = new WeakHashMap<>();
    private static final RandomSource                 RANDOM = RandomSource.create();

    private LeyWardMotes()
    {
    }

    static void clear()
    {
        FIELDS.clear();
    }

    static void tick(Level level, long time)
    {
        FIELDS.keySet().removeIf(ward -> !LeyWardTile.CLIENT_LOADED.contains(ward));
        for (var ward : LeyWardTile.CLIENT_LOADED)
        {
            var motes = FIELDS.computeIfAbsent(ward, key -> new ArrayList<>());
            motes.removeIf(mote -> time - mote.born >= mote.life || (mote.winks >= 0 && time >= mote.winks));

            float radius = LeyStoneBalance.WARD_RADIUS;
            int target = Math.round(Mth.PI * radius * radius / GROUND_PER_MOTE * ward.strength());
            int spawns = Math.min(target - motes.size(), Math.max(1, target / FILL_TICKS));
            for (int i = 0; i < spawns; i++)
                spawn(level, ward, motes, time, radius);
        }
    }

    private static void spawn(Level level, LeyWardTile ward, List<Mote> motes, long time, float radius)
    {
        var centre = ward.getBlockPos();
        float distance = radius * Mth.sqrt(RANDOM.nextFloat());
        float angle = RANDOM.nextFloat() * Mth.TWO_PI;
        double x = centre.getX() + 0.5 + Mth.cos(angle) * distance;
        double z = centre.getZ() + 0.5 + Mth.sin(angle) * distance;
        double ground = LeyGround.surface(level, x, z, centre.getY());
        if (Double.isNaN(ground))
            return;

        var mote = new Mote();
        mote.x = x;
        mote.y = ground + LOW + RANDOM.nextFloat() * (HIGH - LOW);
        mote.z = z;
        mote.born = time;
        mote.life = LIFE_MIN + RANDOM.nextInt(LIFE_SPREAD);

        float lost = 1 - ward.health();
        if (lost > 0.5F && RANDOM.nextFloat() < (lost - 0.5F) * 1.2F)
            mote.winks = time + (long) (mote.life * (0.3F + 0.5F * RANDOM.nextFloat()));
        motes.add(mote);
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, Vec3 camera, float time)
    {
        FIELDS.forEach((ward, motes) ->
        {
            if (motes.isEmpty() || Vec3.atCenterOf(ward.getBlockPos()).distanceToSqr(camera) > DRAW_DISTANCE * DRAW_DISTANCE)
                return;
            int rgb = LeyLook.lerp(LeyLook.TEAL, LeyLook.MAUVE_GREY, 1 - ward.health());
            for (var mote : motes)
            {
                float age = time - mote.born;
                float envelope = Mth.clamp(Math.min(age / FADE_IN, (mote.life - age) / FADE_OUT), 0, 1);
                if (mote.winks >= 0)
                {
                    float left = mote.winks - time;
                    if (left < FLICKER_TICKS)
                        envelope *= ((int) left % 2 == 0) ? 1 : 0.15F;
                }
                var at = LeyLook.relative(mote.x, mote.y + age * RISE_PER_TICK, mote.z, camera);
                LeyLook.softDot(buffer, pose, at, RADIUS, rgb, ALPHA * envelope);
            }
        });
    }
}
