package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * A player moves on their own client, so a push the server gives one only takes if their client is told at once.
 */
final class SanctumPush
{
    private SanctumPush()
    {
    }

    static void sync(Entity entity)
    {
        if (entity instanceof ServerPlayer player)
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        else
            entity.needsSync = true;
    }
}
