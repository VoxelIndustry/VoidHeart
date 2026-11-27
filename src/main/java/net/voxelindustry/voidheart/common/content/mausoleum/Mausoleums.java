package net.voxelindustry.voidheart.common.content.mausoleum;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.util.TeleportUtil;
import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumAddress;
import net.voxelindustry.voidheart.common.world.mausoleum.MausoleumPlan;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The way into a shroud gate's mausoleum and back out. Whoever walks into an open gate's void comes out in the antechamber of the mausoleum that
 * gate was given the first time, before a gate standing open for good; walking into that one leads back to the Daedalus.
 */
public final class Mausoleums
{
    /** Ticks after passing a gate during which no gate takes someone, so nobody is sent straight back through. */
    private static final int PASS_GRACE_TICKS = 60;
    /** How far into the void one must press, from the seal's face. */
    private static final double MOUTH_NEAR = -0.05, MOUTH_FAR = 0.3;
    /** Where one comes back out, in blocks from the seal, between the gate and its altar. */
    private static final int HOME_DEPTH = 2;

    private static final Map<UUID, Integer> PASSED = new HashMap<>();

    private Mausoleums()
    {
    }

    public static void register()
    {
        // Tick counts start over with each server, so times kept from one would hold players back in the next
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PASSED.clear());
    }

    public static MausoleumState state(MinecraftServer server)
    {
        return VoidDungeons.level(server).getDataStorage().computeIfAbsent(MausoleumState.TYPE);
    }

    /** The space just in front of a gate's seal, where whoever presses into its void is taken through. */
    public static AABB mouth(ShroudGate gate)
    {
        int facing = gate.facing();
        int lateral = gate.lateral();
        double half = ShroudGate.SEAL_HALF_WIDTH + 0.5;
        double cx = gate.x() + 0.5, cz = gate.z() + 0.5;
        double nearX = cx + Compass.dx(facing) * (0.5 + MOUTH_NEAR), nearZ = cz + Compass.dz(facing) * (0.5 + MOUTH_NEAR);
        double farX = cx + Compass.dx(facing) * (0.5 + MOUTH_FAR), farZ = cz + Compass.dz(facing) * (0.5 + MOUTH_FAR);
        double sideX = Compass.dx(lateral) * half, sideZ = Compass.dz(lateral) * half;
        int bottom = gate.y() + ShroudGate.SEAL_BOTTOM;
        return new AABB(nearX - sideX, bottom, nearZ - sideZ, farX + sideX, bottom + ShroudGate.SEAL_HEIGHT, farZ + sideZ);
    }

    /**
     * Called for a player pressing into an open gate's void.
     *
     * @param kept whether the gate stands open for good, as a mausoleum's way back does
     */
    public static void pass(ServerPlayer player, ShroudGate gate, boolean kept)
    {
        if (justPassed(player))
            return;
        if (kept)
            leave(player, indexAt(player.blockPosition()));
        else
            enter(player, gate);
    }

    public static void enter(ServerPlayer player, ShroudGate gate)
    {
        var server = player.level().getServer();
        var home = new BlockPos(gate.worldX(0, HOME_DEPTH), gate.y(), gate.worldZ(0, HOME_DEPTH));
        int index = state(server).indexFor(BlockPos.asLong(gate.x(), gate.y(), gate.z()), GlobalPos.of(player.level().dimension(), home), Compass.yaw(gate.facing()));
        if (index < 0)
        {
            // Every mausoleum the dimension holds is taken: the void stays a void
            passed(player);
            player.sendOverlayMessage(Component.translatable(MODID + ".mausoleum.unreachable"));
            return;
        }
        arrive(player, index);
    }

    /** Sends someone to a mausoleum's antechamber, between its gate and the altar, facing in. */
    public static void arrive(ServerPlayer player, int index)
    {
        var server = player.level().getServer();
        var level = VoidDungeons.level(server);
        var origin = MausoleumAddress.origin(index);
        var at = new Vec3(origin.getX() + MausoleumPlan.ARRIVAL_X + 0.5, origin.getY() + MausoleumPlan.FLOOR_Y, origin.getZ() + MausoleumPlan.ARRIVAL_Z + 0.5);
        passed(player);
        TeleportUtil.teleport(player, level, at, MausoleumPlan.ARRIVAL_YAW, 0);

        var mausoleum = state(server).get(index);
        if (mausoleum != null && mausoleum.slain() && !mausoleum.opened())
            Sarcophagi.open(level, index);
    }

    public static void leave(ServerPlayer player, int index)
    {
        var server = player.level().getServer();
        var mausoleum = state(server).get(index);
        passed(player);
        if (mausoleum == null || mausoleum.home().isEmpty() || server.getLevel(mausoleum.home().get().dimension()) == null)
        {
            player.teleport(player.findRespawnPositionAndUseSpawnBlock(true, TeleportTransition.DO_NOTHING));
            return;
        }
        var home = mausoleum.home().get();
        TeleportUtil.teleport(player, server.getLevel(home.dimension()), Vec3.atBottomCenterOf(home.pos()), mausoleum.homeYaw(), 0);
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

    /** @return the mausoleum a place in the dungeons' dimension lies in, or -1 */
    public static int indexAt(BlockPos pos)
    {
        return MausoleumAddress.at(pos.getX(), pos.getZ());
    }

    /** @return whether a level is the dungeons' dimension and a place in it lies in a mausoleum */
    public static boolean inMausoleum(ServerLevel level, BlockPos pos)
    {
        return level.dimension() == VoidDungeons.WORLD_KEY && indexAt(pos) >= 0;
    }

    public static BlockPos local(int index, int x, int y, int z)
    {
        return MausoleumAddress.origin(index).offset(x, y, z);
    }

    /** The middle block of a mausoleum's sarcophagus. */
    public static BlockPos sarcophagus(int index)
    {
        return local(index, MausoleumPlan.SARCOPHAGUS_X, MausoleumPlan.SARCOPHAGUS_Y, MausoleumPlan.SARCOPHAGUS_Z);
    }
}
