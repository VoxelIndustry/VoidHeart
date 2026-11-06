package net.voxelindustry.voidheart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.voxelindustry.voidheart.common.content.oil.Elixirs;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;

/**
 * What the drinker of Insight sees of wall-crossers through stone. The Bishop's warning and the Dragoon's readout ask {@link #active()} too.
 */
public final class InsightSight
{
    public static final int   SILHOUETTE_COLOR = 0x8A3DF0;
    public static final float SILHOUETTE_ALPHA = 0.35F;
    public static final float CROSSING_ALPHA   = 0.6F;
    public static final float FULL_RANGE       = 8;
    public static final float RANGE            = 24;
    public static final float BREATH_DEPTH     = 0.3F;
    public static final long  BREATH_MILLIS    = 2000;

    private InsightSight()
    {
    }

    public static boolean active()
    {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(VoidHeartEffects.INSIGHT);
    }

    /**
     * @return how far the player's Insight reaches, longer for a stronger brew
     */
    public static float range()
    {
        var player = Minecraft.getInstance().player;
        var insight = player == null ? null : player.getEffect(VoidHeartEffects.INSIGHT);
        return insight == null ? RANGE : Elixirs.byLevel(Elixirs.INSIGHT_RANGE, insight.getAmplifier());
    }

    /**
     * @return the silhouette's alpha for this mob, 0 when it isn't to be drawn: no Insight, out of range, or in plain view
     */
    public static float silhouette(Entity entity, float partialTicks, boolean crossing)
    {
        if (!active())
            return 0;
        var camera = Minecraft.getInstance().gameRenderer.mainCamera();
        var eye = camera.position();
        var center = entity.getPosition(partialTicks).add(0, entity.getBbHeight() / 2, 0);
        double distance = eye.distanceTo(center);
        float range = range();
        float full = range * FULL_RANGE / RANGE;
        if (distance > range)
            return 0;
        // In plain view the mob is simply seen, and its far limbs behind its own body mustn't light up
        if (!crossing && clear(entity, eye, center) && clear(entity, eye, center.add(0, entity.getBbHeight() * 0.4, 0)))
            return 0;

        float reach = 1 - Mth.clamp((float) (distance - full) / (range - full), 0, 1);
        // Each mob breathes on its own beat, so a group doesn't pulse as one
        float phase = (Util.getMillis() + entity.getId() * 977L) % BREATH_MILLIS / (float) BREATH_MILLIS;
        float breath = 1 - BREATH_DEPTH * (0.5F + 0.5F * Mth.sin(phase * Mth.TWO_PI));
        return (crossing ? CROSSING_ALPHA : SILHOUETTE_ALPHA) * reach * breath;
    }

    private static boolean clear(Entity entity, Vec3 from, Vec3 to)
    {
        var hit = entity.level().clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS;
    }
}
