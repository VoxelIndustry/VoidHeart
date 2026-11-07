package net.voxelindustry.voidheart.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import net.voxelindustry.voidheart.common.content.oil.Elixirs;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

/**
 * Pushes the fog of the dungeons and the Daedalus back while the player has Clarity. It eases in after drinking and out as the effect runs down, so the
 * fog never snaps, and some always stays at the edge.
 */
public final class ClarityFog
{
    /**
     * How far a plain brew pushes the fog's start and end. Stronger brews push further, see {@link Elixirs#CLARITY_START}.
     */
    public static final float START_SCALE  = Elixirs.CLARITY_START[0];
    public static final float END_SCALE    = Elixirs.CLARITY_END[0];
    public static final int   EASE_IN_TICKS  = 60;
    public static final int   EASE_OUT_TICKS = 200;

    private static float strength;
    private static float strengthO;
    /**
     * Level of the Clarity last seen, kept while the fog eases back so a fading brew doesn't drop to a weaker push.
     */
    private static int   level;

    private ClarityFog()
    {
    }

    public static void tick(Minecraft minecraft)
    {
        strengthO = strength;
        var player = minecraft.player;
        float target = 0;
        if (player != null && inFoggedDimension(minecraft))
        {
            var effect = player.getEffect(VoidHeartEffects.CLARITY);
            if (effect != null)
                level = effect.getAmplifier();
            if (effect != null)
                target = effect.isInfiniteDuration() ? 1 : Mth.clamp(effect.getDuration() / (float) EASE_OUT_TICKS, 0, 1);
        }
        // Rising follows the ease in, falling follows the remaining duration, or drops at once when the effect is gone or the player leaves
        strength = target > strength ? Math.min(target, strength + 1F / EASE_IN_TICKS) : target;
    }

    public static float strength(float partialTicks)
    {
        return Mth.lerp(partialTicks, strengthO, strength);
    }

    public static void apply(FogData fog, float renderDistance, float partialTicks)
    {
        float clarity = strength(partialTicks);
        if (clarity <= 0)
            return;

        float end = Math.min(renderDistance, fog.environmentalEnd * Mth.lerp(clarity, 1, Elixirs.byLevel(Elixirs.CLARITY_END, level)));
        float start = Math.min(end * 0.9F, fog.environmentalStart * Mth.lerp(clarity, 1, Elixirs.byLevel(Elixirs.CLARITY_START, level)));
        fog.environmentalEnd = Math.max(fog.environmentalEnd, end);
        fog.environmentalStart = Math.max(fog.environmentalStart, start);
    }

    private static boolean inFoggedDimension(Minecraft minecraft)
    {
        if (minecraft.level == null)
            return false;
        var dimension = minecraft.level.dimension();
        return dimension == VoidDungeons.WORLD_KEY || dimension == VoidDaedalus.WORLD_KEY;
    }
}
