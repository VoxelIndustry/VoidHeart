package net.voxelindustry.voidheart.common.curse;

import net.minecraft.server.level.ServerPlayer;

/**
 * The one part of a curse that really happens, so it runs on the server. Everything is derived from the seed for the client to stay in step.
 */
public final class RaptureLift
{
    private RaptureLift()
    {
    }

    public static int totalTicks(long seed)
    {
        return 400;
    }

    public static void tick(ServerPlayer player, int age, long seed)
    {
    }

    public static void finish(ServerPlayer player)
    {
    }
}
