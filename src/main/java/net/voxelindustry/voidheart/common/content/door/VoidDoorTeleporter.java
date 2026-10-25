package net.voxelindustry.voidheart.common.content.door;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.util.TeleportUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Walking into an open linked door moves the player through its counterpart, keeping their heading relative to the doors.
 */
public class VoidDoorTeleporter
{
    private static final int COOLDOWN_TICKS = 20;

    private static final Map<UUID, Integer> lastTeleportTick = new HashMap<>();

    public static void tickServer(MinecraftServer server)
    {
        int tick = server.getTickCount();
        lastTeleportTick.values().removeIf(lastTick -> tick - lastTick >= COOLDOWN_TICKS);

        var voidLevel = server.getLevel(VoidHeart.VOID_WORLD_KEY);
        if (voidLevel == null)
            return;

        var links = VoidDoorLinks.get(server);
        if (links.isEmpty())
            return;

        for (var player : List.copyOf(server.getPlayerList().getPlayers()))
        {
            if (!lastTeleportTick.containsKey(player.getUUID()))
                tickPlayer(server, links, voidLevel, player);
        }
    }

    private static void tickPlayer(MinecraftServer server, VoidDoorLinks links, ServerLevel voidLevel, ServerPlayer player)
    {
        if (player.isSpectator() || player.isPassenger() || player.isVehicle() || player.isSleeping())
            return;

        var level = player.level();
        var doorPos = player.blockPosition();
        var doorState = level.getBlockState(doorPos);

        if (!isOpenLowerDoor(doorState))
            return;

        if (level == voidLevel)
        {
            if (!(doorState.getBlock() instanceof VoidDoorBlock))
                return;

            links.getSource(doorPos).ifPresent(source ->
            {
                var sourceLevel = server.getLevel(source.dimension());
                var sourceState = sourceLevel == null ? null : sourceLevel.getBlockState(source.pos());

                if (sourceState == null || !(sourceState.getBlock() instanceof DoorBlock))
                    links.unlinkVoidDoor(doorPos);
                else
                    travel(server, player, doorState, sourceLevel, source.pos(), sourceState);
            });
        }
        else
        {
            links.getVoidDoor(GlobalPos.of(level.dimension(), doorPos)).ifPresent(voidDoor ->
            {
                var voidDoorState = voidLevel.getBlockState(voidDoor);

                if (!(voidDoorState.getBlock() instanceof VoidDoorBlock))
                    links.unlinkVoidDoor(voidDoor);
                else
                    travel(server, player, doorState, voidLevel, voidDoor, voidDoorState);
            });
        }
    }

    private static boolean isOpenLowerDoor(BlockState state)
    {
        return state.getBlock() instanceof DoorBlock
                && state.getValue(DoorBlock.OPEN)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
    }

    private static void travel(MinecraftServer server, ServerPlayer player, BlockState fromState, ServerLevel toLevel, BlockPos toPos, BlockState toState)
    {
        Direction fromFacing = fromState.getValue(DoorBlock.FACING);
        Direction toFacing = toState.getValue(DoorBlock.FACING);

        int turns = player.getDirection().get2DDataValue() - fromFacing.get2DDataValue();
        Direction exit = Direction.from2DDataValue(toFacing.get2DDataValue() + turns + 4);

        var arrival = Vec3.atBottomCenterOf(toPos.relative(exit));
        if (!toLevel.noCollision(player, player.getBoundingBox().move(arrival.subtract(player.position()))))
            arrival = Vec3.atBottomCenterOf(toPos.relative(exit.getOpposite()));

        float yaw = player.getYRot() + toFacing.toYRot() - fromFacing.toYRot();
        TeleportUtil.teleport(player, toLevel, arrival, yaw, player.getXRot());
        lastTeleportTick.put(player.getUUID(), server.getTickCount());
    }
}
