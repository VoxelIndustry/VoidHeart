package net.voxelindustry.voidheart.client;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Insight brewed with Memoriam remembers: a hostile mob seen in plain view keeps a violet silhouette through walls once it goes out of sight, following it,
 * and fading out over {@link #MEMORY_MILLIS}. Seeing it again starts the memory over.
 */
public final class InsightMemory
{
    public static final float MEMORY_ALPHA  = 0.3F;
    public static final long  MEMORY_MILLIS = 10_000;
    /**
     * Ticks between sight checks of each mob, spread over the mobs by id.
     */
    public static final int   CHECK_TICKS   = 4;

    public static final RenderStateDataKey<Float> ALPHA = RenderStateDataKey.create(() -> "voidheart:insight_memory");

    /**
     * When each mob was last in plain view, by entity id.
     */
    private static final Map<Integer, Long> LAST_SEEN = new HashMap<>();
    private static final Set<Integer>       IN_VIEW   = new HashSet<>();

    private InsightMemory()
    {
    }

    /**
     * Wraiths show under Insight through walls already, so they need no memory.
     */
    public static boolean ignores(EntityType<?> type)
    {
        return type == VoidHeartEntities.WRAITH || type == VoidHeartEntities.STALKING_WRAITH;
    }

    public static boolean remembers()
    {
        var player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(VoidHeartEffects.RECOLLECTION) && player.hasEffect(VoidHeartEffects.INSIGHT);
    }

    public static void tick(Minecraft minecraft)
    {
        if (minecraft.level == null || !remembers())
        {
            LAST_SEEN.clear();
            IN_VIEW.clear();
            return;
        }
        if (minecraft.isPaused())
            return;

        var eye = minecraft.gameRenderer.mainCamera().position();
        float range = InsightSight.range();
        long now = Util.getMillis();
        var present = new HashSet<Integer>();
        for (var entity : minecraft.level.entitiesForRendering())
        {
            if (!(entity instanceof LivingEntity living) || !(entity instanceof Enemy) || entity instanceof Wraith || !living.isAlive())
                continue;
            if (entity.position().distanceTo(eye) > range)
                continue;

            int id = entity.getId();
            present.add(id);
            if ((entity.tickCount + id) % CHECK_TICKS != 0)
                continue;
            if (InsightSight.inPlainView(entity, eye))
            {
                LAST_SEEN.put(id, now);
                IN_VIEW.add(id);
            }
            else
                IN_VIEW.remove(id);
        }
        LAST_SEEN.keySet().removeIf(id -> !present.contains(id) || now - LAST_SEEN.get(id) > MEMORY_MILLIS);
        IN_VIEW.retainAll(present);
    }

    /**
     * @return the remembered silhouette's alpha for this mob, 0 when it isn't remembered or is in plain view
     */
    public static float alpha(LivingEntity entity)
    {
        if (!(entity instanceof Enemy) || !remembers())
            return 0;
        var seen = LAST_SEEN.get(entity.getId());
        if (seen == null || IN_VIEW.contains(entity.getId()))
            return 0;

        float t = Mth.clamp((Util.getMillis() - seen) / (float) MEMORY_MILLIS, 0, 1);
        // Slow at first, faster near the end
        float fade = 1 - t * t;
        return MEMORY_ALPHA * fade * InsightSight.breath(entity);
    }

    /**
     * For tests: how many mobs are remembered right now.
     */
    public static int remembered()
    {
        return LAST_SEEN.size();
    }
}
