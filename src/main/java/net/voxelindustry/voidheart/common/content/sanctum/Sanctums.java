package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumAddress;
import net.voxelindustry.voidheart.common.world.sanctum.SanctumPlan;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The way into a temple's sanctum and back out. A Wraith Veil pressed to the door in a temple's mosaic opens it for a while, and whoever walks
 * in comes out in the porch of the sanctum that temple was given the first time; the gate there leads back to the temple's dais.
 */
public final class Sanctums
{
    /** Ticks after passing a gate during which no gate takes someone, so nobody is sent straight back through a door still open. */
    private static final int PASS_GRACE_TICKS = 60;

    private static final Map<UUID, Integer> PASSED = new HashMap<>();

    private Sanctums()
    {
    }

    public static SanctumState state(MinecraftServer server)
    {
        return VoidDungeons.level(server).getDataStorage().computeIfAbsent(SanctumState.TYPE);
    }

    /**
     * Called for a Wraith Veil pressed to a block, before it looks for a way through.
     *
     * @return whether the block was in a temple's door, which then opens, or is opening already
     */
    public static boolean tryOpen(ServerLevel level, BlockPos clicked, Direction face)
    {
        var door = MosaicDoor.at(level, clicked, face);
        if (door == null)
            return false;
        var open = level.getEntitiesOfClass(VeilGate.class, door.mouth(0.1).inflate(0.2), gate -> !gate.stays() && gate.tickCount < VeilGate.closesAt());
        if (open.isEmpty())
            VeilGate.open(level, door);
        return true;
    }

    public static void enter(ServerPlayer player, MosaicDoor door)
    {
        if (justPassed(player))
            return;
        var server = player.level().getServer();
        var home = GlobalPos.of(player.level().dimension(), BlockPos.containing(door.threshold()));
        int index = state(server).indexFor(door.key(), home, door.outwardYaw());
        arrive(player, index);
    }

    /** Sends someone to a sanctum's porch, facing in. */
    public static void arrive(ServerPlayer player, int index)
    {
        var server = player.level().getServer();
        var level = VoidDungeons.level(server);
        var origin = SanctumAddress.origin(index);
        var at = new Vec3(origin.getX() + SanctumPlan.ARRIVAL_X + 0.5, origin.getY() + SanctumPlan.FLOOR_Y, origin.getZ() + SanctumPlan.ARRIVAL_Z + 0.5);
        passed(player);
        TeleportUtil.teleport(player, level, at, SanctumPlan.GATE_FACING.toYRot(), 0);

        var sanctum = state(server).get(index);
        if (sanctum != null && sanctum.slain() && !sanctum.raised())
            SanctumPillar.raiseAll(level, index);
    }

    public static void leave(ServerPlayer player, int index)
    {
        if (justPassed(player))
            return;
        var server = player.level().getServer();
        var sanctum = state(server).get(index);
        passed(player);
        if (sanctum == null || sanctum.home().isEmpty() || server.getLevel(sanctum.home().get().dimension()) == null)
        {
            player.teleport(player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING));
            return;
        }
        var home = sanctum.home().get();
        TeleportUtil.teleport(player, server.getLevel(home.dimension()), Vec3.atBottomCenterOf(home.pos()), sanctum.homeYaw(), 0);
    }

    private static boolean justPassed(ServerPlayer player)
    {
        var at = PASSED.get(player.getUUID());
        return at != null && player.level().getServer().getTickCount() - at < PASS_GRACE_TICKS;
    }

    private static void passed(ServerPlayer player)
    {
        PASSED.put(player.getUUID(), player.level().getServer().getTickCount());
    }

    /** @return the sanctum a place in the dungeons' dimension lies in, or -1 */
    public static int indexAt(BlockPos pos)
    {
        return SanctumAddress.at(pos.getX(), pos.getZ());
    }

    /** The middle of the sanctuary where a sanctum's guardian sits, and its pillar rises. */
    public static BlockPos altar(int index)
    {
        return SanctumAddress.origin(index).offset(SanctumPlan.MASS_X, SanctumPlan.MASS_Y, SanctumPlan.MASS_Z);
    }
}
