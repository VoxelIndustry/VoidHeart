package net.voxelindustry.voidheart.common.curse;

import net.minecraft.server.level.ServerPlayer;

/**
 * Scales how fast a player's corruption rises: wards give less than 1, the void's seep more than 1. Modifiers multiply together.
 */
@FunctionalInterface
public interface CorruptionModifier
{
    float multiplier(ServerPlayer player);
}
