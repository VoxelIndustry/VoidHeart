package net.voxelindustry.voidheart.common.content.sin;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.Map;

/**
 * What greed repays once a stolen boon ends: its opposite, or weakness when it has none.
 */
public final class GreedDebts
{
    private static final Map<Holder<MobEffect>, Holder<MobEffect>> OPPOSITES = Map.ofEntries(
            Map.entry(MobEffects.STRENGTH, MobEffects.WEAKNESS),
            Map.entry(MobEffects.SPEED, MobEffects.SLOWNESS),
            Map.entry(MobEffects.HASTE, MobEffects.MINING_FATIGUE),
            Map.entry(MobEffects.JUMP_BOOST, MobEffects.SLOWNESS),
            Map.entry(MobEffects.REGENERATION, MobEffects.POISON),
            Map.entry(MobEffects.RESISTANCE, MobEffects.SLOWNESS),
            Map.entry(MobEffects.ABSORPTION, MobEffects.HUNGER),
            Map.entry(MobEffects.HEALTH_BOOST, MobEffects.HUNGER),
            Map.entry(MobEffects.SATURATION, MobEffects.HUNGER),
            Map.entry(MobEffects.INVISIBILITY, MobEffects.GLOWING),
            Map.entry(MobEffects.NIGHT_VISION, MobEffects.DARKNESS),
            Map.entry(MobEffects.WATER_BREATHING, MobEffects.NAUSEA),
            Map.entry(MobEffects.LUCK, MobEffects.UNLUCK),
            Map.entry(MobEffects.SLOW_FALLING, MobEffects.SLOWNESS),
            Map.entry(MobEffects.DOLPHINS_GRACE, MobEffects.SLOWNESS),
            Map.entry(MobEffects.CONDUIT_POWER, MobEffects.MINING_FATIGUE));

    private GreedDebts()
    {
    }

    public static Holder<MobEffect> opposite(Holder<MobEffect> effect)
    {
        return OPPOSITES.getOrDefault(effect, MobEffects.WEAKNESS);
    }
}
