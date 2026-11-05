package net.voxelindustry.voidheart.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jspecify.annotations.Nullable;

/**
 * Sends shoals of light drifting past in the distance while a player is in a Still Depths dungeon. Only ever seen from afar, so there is nothing to catch.
 */
public final class DepthsShoals
{
    private static final int MIN_INTERVAL_TICKS = 70;
    private static final int INTERVAL_SPREAD_TICKS = 150;
    private static final int RETRY_TICKS = 20;
    private static final int PLACE_ATTEMPTS = 12;
    private static final float VIEW_ARC = 2.4F;
    private static final double MIN_DISTANCE = 10;
    private static final double DISTANCE_SPREAD = 13;
    private static final double VERTICAL_SPREAD = 10;
    private static final int MIN_MEMBERS = 20;
    private static final int MEMBER_SPREAD = 18;
    private static final double SHOAL_RADIUS = 2.4;
    private static final double SPEED = 0.07;
    private static final int PATH_TICKS = 160;
    private static final int PATH_SAMPLES = 6;

    private static int cooldown;
    private static @Nullable Vec3 lastSent;

    private DepthsShoals()
    {
    }

    public static @Nullable Vec3 lastSent()
    {
        return lastSent;
    }

    public static void tick(Minecraft minecraft)
    {
        var level = minecraft.level;
        var player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused() || level.dimension() != VoidDungeons.WORLD_KEY || --cooldown > 0)
            return;

        var address = DungeonAddress.at(player.getBlockX(), player.getBlockZ());
        if (address == null || address.theme() != DungeonTheme.DEPTHS)
        {
            cooldown = RETRY_TICKS;
            return;
        }

        var random = level.getRandom();
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++)
        {
            if (trySendShoal(level, random, player))
            {
                cooldown = MIN_INTERVAL_TICKS + random.nextInt(INTERVAL_SPREAD_TICKS);
                return;
            }
        }
        cooldown = RETRY_TICKS;
    }

    private static boolean trySendShoal(ClientLevel level, RandomSource random, Player player)
    {
        var look = player.getLookAngle();
        float bearing = (float) Math.atan2(look.z, look.x) + (random.nextFloat() - 0.5F) * VIEW_ARC;
        double distance = MIN_DISTANCE + random.nextDouble() * DISTANCE_SPREAD;
        double x = player.getX() + Mth.cos(bearing) * distance;
        double y = player.getEyeY() + (random.nextDouble() - 0.5) * VERTICAL_SPREAD;
        double z = player.getZ() + Mth.sin(bearing) * distance;
        if (!isInSight(level, player, x, y, z))
            return false;

        float heading = bearing + Mth.HALF_PI + (random.nextFloat() - 0.5F);
        double headingX = Mth.cos(heading) * SPEED;
        double headingZ = Mth.sin(heading) * SPEED;
        if (!isPathOpen(level, x, y, z, headingX, headingZ))
            return false;

        lastSent = new Vec3(x, y, z);
        int members = MIN_MEMBERS + random.nextInt(MEMBER_SPREAD);
        for (int member = 0; member < members; member++)
        {
            level.addParticle(VoidHeart.SHOAL_SPECK_PARTICLE,
                    x + (random.nextDouble() - 0.5) * SHOAL_RADIUS * 2,
                    y + (random.nextDouble() - 0.5) * SHOAL_RADIUS,
                    z + (random.nextDouble() - 0.5) * SHOAL_RADIUS * 2,
                    headingX, 0, headingZ);
        }
        return true;
    }

    private static boolean isInSight(ClientLevel level, Player player, double x, double y, double z)
    {
        var sight = new ClipContext(player.getEyePosition(), new Vec3(x, y, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        return level.clip(sight).getType() == HitResult.Type.MISS;
    }

    private static boolean isPathOpen(ClientLevel level, double x, double y, double z, double headingX, double headingZ)
    {
        for (int sample = 0; sample <= PATH_SAMPLES; sample++)
        {
            double ticks = PATH_TICKS * sample / (double) PATH_SAMPLES;
            var pos = BlockPos.containing(x + headingX * ticks, y, z + headingZ * ticks);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() || !level.hasChunkAt(pos))
                return false;
        }
        return true;
    }
}
