package net.voxelindustry.voidheart.common.curse;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * How much a rise in corruption really adds once every modifier has scaled it. Kept apart from {@link Corruption} so it can be tested without attachments.
 *
 * @param points whole points to add now
 * @param carry  the fraction of a point left for the next rise
 */
record CorruptionRise(int points, float carry)
{
    static float multiplier(ServerPlayer player, List<CorruptionModifier> modifiers)
    {
        float multiplier = 1;
        for (var modifier : modifiers)
        {
            float factor = modifier.multiplier(player);
            if (!Float.isNaN(factor))
                multiplier *= Math.max(0, factor);
        }
        return multiplier;
    }

    static CorruptionRise of(int amount, float multiplier, float carry)
    {
        // A saved carry is only ever a fraction, anything else is stale
        float total = (carry >= 0 && carry < 1 ? carry : 0) + amount * multiplier;
        if (!(total < Corruption.MAX))
            return new CorruptionRise(Corruption.MAX, 0);

        int points = (int) Math.floor(total);
        return new CorruptionRise(points, total - points);
    }
}
