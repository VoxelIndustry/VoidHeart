package net.voxelindustry.voidheart.client.armor;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.InsightSight;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.mob.DormantTreantBlock;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * What the Void Armor's sights show its wearer. Night vision lifts the dark of the lightmap with a faint violet cast, its own rather than the vanilla
 * effect, so it shows no effect icon and never blinks before running out. The spectral lens shows living things nearby through walls as pale lilac
 * silhouettes, hostile ones brighter, other players included. Both fade in and out over a few ticks as they are switched, cut or the frame goes limp.
 */
public final class ArmorSight
{
    /** The floor night vision lifts the lightmap to: vanilla's grey, a little toward violet. */
    public static final Vector3fc NIGHT_VISION_COLOR = new Vector3f(0.57F, 0.54F, 0.69F);
    public static final int       FADE_TICKS         = 8;

    public static final int   PASSIVE_COLOR = 0xA48CDC;
    public static final int   HOSTILE_COLOR = 0xC4A2FF;
    public static final float PASSIVE_ALPHA = 0.3F;
    public static final float HOSTILE_ALPHA = 0.55F;
    /** A Treant's model is a dense knot of parts, all hidden by its trunk at once, and its added layers would glare without this. */
    public static final float DORMANT_SCALE = 0.4F;
    /** Silhouettes show at full strength up to this far, then fade out by {@link #RANGE}. */
    public static final float FULL_RANGE    = 20;
    public static final float RANGE         = 32;
    /** Ticks between sight checks of each living thing, spread over them by id. */
    public static final int   CHECK_TICKS   = 4;

    /** Each living thing's silhouette as a colour with its strength in alpha, 0 for none. */
    public static final RenderStateDataKey<Integer> SPECTRAL = RenderStateDataKey.create(() -> "voidheart:spectral_silhouette");

    private static float nightVision;
    private static float nightVisionBefore;
    private static float spectral;
    private static float spectralBefore;

    /** Living things the lens last found hidden from the eye, by entity id. Unchecked ones show nothing until checked. */
    private static final Map<Integer, Boolean> HIDDEN = new HashMap<>();

    private ArmorSight()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        var player = minecraft.player;
        var load = player == null ? null : ArmorWear.load(player);
        nightVisionBefore = nightVision;
        spectralBefore = spectral;
        nightVision = approach(nightVision, load != null && load.nightVision());
        spectral = approach(spectral, load != null && load.spectral());

        if (spectral <= 0 || minecraft.level == null)
        {
            HIDDEN.clear();
            return;
        }
        if (minecraft.isPaused())
            return;

        var eye = minecraft.gameRenderer.mainCamera().position();
        var present = new HashSet<Integer>();
        for (var entity : minecraft.level.entitiesForRendering())
        {
            if (!(entity instanceof LivingEntity living) || entity == player || !living.isAlive() || entity.position().distanceTo(eye) > RANGE)
                continue;
            int id = entity.getId();
            present.add(id);
            if (HIDDEN.containsKey(id) && (entity.tickCount + id) % CHECK_TICKS != 0)
                continue;
            HIDDEN.put(id, !InsightSight.inPlainView(entity, eye));
        }
        HIDDEN.keySet().retainAll(present);
    }

    private static float approach(float value, boolean on)
    {
        return Mth.clamp(value + (on ? 1F : -1F) / FADE_TICKS, 0, 1);
    }

    /**
     * How strongly the frame's night vision lifts the lightmap this frame, 0 when it is off.
     */
    public static float nightVision(float partialTicks)
    {
        return Mth.lerp(partialTicks, nightVisionBefore, nightVision);
    }

    public static float spectral(float partialTicks)
    {
        return Mth.lerp(partialTicks, spectralBefore, spectral);
    }

    /**
     * @return the living thing's silhouette under the spectral lens, its colour with its strength in alpha, 0 when the lens is off, it is too far or in
     * plain view
     */
    public static int silhouette(LivingEntity entity, float partialTicks)
    {
        // Runs for every living thing drawn each frame, so the usual case of the lens being off costs one check
        if (spectral <= 0 && spectralBefore <= 0)
            return 0;
        var hidden = HIDDEN.get(entity.getId());
        if (hidden == null || !hidden)
            return 0;

        var center = entity.getPosition(partialTicks).add(0, entity.getBbHeight() / 2, 0);
        boolean hostile = entity instanceof Enemy;
        return color(center, hostile, partialTicks, InsightSight.breath(entity));
    }

    /**
     * @return the silhouette of a Treant asleep in the trunk whose base is at that spot, 0 when the lens is off or it is too far. The trunk always hides it,
     * so it needs no sight check.
     */
    public static int dormant(BlockPos base, float partialTicks)
    {
        if (spectral <= 0 && spectralBefore <= 0)
            return 0;
        var center = Vec3.atBottomCenterOf(base).add(0, DormantTreantBlock.HEIGHT / 2D, 0);
        return color(center, true, partialTicks, DORMANT_SCALE * breath(base.asLong()));
    }

    private static int color(Vec3 center, boolean hostile, float partialTicks, float breath)
    {
        var eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
        float reach = 1 - Mth.clamp((float) (eye.distanceTo(center) - FULL_RANGE) / (RANGE - FULL_RANGE), 0, 1);
        float alpha = (hostile ? HOSTILE_ALPHA : PASSIVE_ALPHA) * reach * spectral(partialTicks) * breath;
        if (alpha <= 0)
            return 0;
        return ARGB.color(Math.round(alpha * 255), hostile ? HOSTILE_COLOR : PASSIVE_COLOR);
    }

    /**
     * Insight's slow pulse, on a beat of its own for something that isn't an entity.
     */
    private static float breath(long seed)
    {
        float phase = Math.floorMod(Util.getMillis() + seed * 977L, InsightSight.BREATH_MILLIS) / (float) InsightSight.BREATH_MILLIS;
        return 1 - InsightSight.BREATH_DEPTH * (0.5F + 0.5F * Mth.sin(phase * Mth.TWO_PI));
    }

    /**
     * For tests: how many living things the lens finds hidden right now.
     */
    public static int hidden()
    {
        return (int) HIDDEN.values().stream().filter(Boolean::booleanValue).count();
    }
}
