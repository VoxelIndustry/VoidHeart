package net.voxelindustry.voidheart.common.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

public class TeleportUtil
{
    public static void teleport(ServerPlayer player, ServerLevel level, Vec3 pos, float yaw, float pitch)
    {
        player.teleportTo(level, pos.x(), pos.y(), pos.z(), Set.of(), yaw, pitch, false);
    }
}
