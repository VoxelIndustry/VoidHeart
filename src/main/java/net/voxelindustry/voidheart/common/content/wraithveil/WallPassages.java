package net.voxelindustry.voidheart.common.content.wraithveil;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Cell;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Phase;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Plan;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * Whose tunnel is open right now. While it is, the blocks it crosses have no collision for its bearer alone, so they walk and climb in it with
 * ordinary movement and the server checks their moves as usual. Each side keeps its own record of the same {@link Plan} the server chose.
 * <p>
 * While the tunnel is open, the bearer's client also erases those blocks from its own copy of the world, so they neither draw nor hide the faces
 * of the blocks around them, and the marks' holes look down the tunnel to the far side. The faces of those blocks that would show from outside
 * other than through a mark, such as the grass on top of a hill the tunnel runs under, or a post or step it crosses, are drawn again on their
 * own, see {@link #shownFromOutside}, so from outside the world looks as it is. Moving through them never depends on this. The server never changes them, and sends them again at the end in case the client
 * missed a change made meanwhile.
 */
public final class WallPassages
{
    // Ticks the server keeps the tunnel past its end for a lagging client to be pushed out by itself
    private static final int    GRACE_TICKS     = 40;
    private static final double STAND_LIMIT     = 0.5;
    private static final double PUSH_SPEED      = 0.3;
    private static final float  WHISPER_VOLUME  = 0.5F;
    private static final float  EMERGE_VOLUME   = 0.6F;
    private static final float  COLLAPSE_VOLUME = 0.7F;
    // Erasing touches no neighbour and drops nothing, as it is only the client's own picture of the world
    private static final int    ERASE_FLAGS     = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final Map<UUID, Active> SERVER = new HashMap<>();
    private static final Map<UUID, Active> CLIENT = new HashMap<>();

    private static final class Active
    {
        final Plan   plan;
        final Player body;
        final Level  level;
        // On the client, what each erased block was
        final Map<BlockPos, BlockState> erased = new HashMap<>();
        int ticks;

        Active(Plan plan, Player body)
        {
            this.plan = plan;
            this.body = body;
            this.level = body.level();
        }

        Phase phase()
        {
            return Plan.phaseAt(ticks);
        }

        /**
         * Stays open past the collapse until its bearer is out, so a lagging client is never caught in stone.
         */
        boolean open()
        {
            return ticks >= WallPassagePlan.MARK_TICKS;
        }
    }

    private WallPassages()
    {
    }

    public static void register()
    {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.getPlayer()));
    }

    private static Map<UUID, Active> side(Level level)
    {
        return level.isClientSide() ? CLIENT : SERVER;
    }

    private static @Nullable Active activeOf(Player player)
    {
        var map = side(player.level());
        return map.isEmpty() ? null : map.get(player.getUUID());
    }

    public static boolean isPassing(Player player)
    {
        return activeOf(player) != null;
    }

    public static @Nullable Plan passageOf(Player player)
    {
        var active = activeOf(player);
        return active == null ? null : active.plan;
    }

    public static int ticksOf(Player player)
    {
        var active = activeOf(player);
        return active == null ? 0 : active.ticks;
    }

    /**
     * Asked for every block an entity collides with, so anyone without an open tunnel pays one map check.
     */
    public static boolean isPassable(@Nullable Entity entity, BlockPos pos)
    {
        if (!(entity instanceof Player player))
            return false;
        var active = activeOf(player);
        return active != null && active.open() && active.plan.contains(pos);
    }

    /**
     * @return whether the player is in their tunnel's open blocks, where they neither suffocate nor see the inside of a wall
     */
    public static boolean isInside(Player player)
    {
        var active = activeOf(player);
        return active != null && active.open() && active.plan.box().intersects(player.getBoundingBox());
    }

    /**
     * A tunnel up or down is climbed like a ladder, and so is the air under one going up.
     */
    public static boolean isClimbing(Player player)
    {
        var active = activeOf(player);
        return active != null && active.open() && active.plan.vertical() && active.plan.climbBox().intersects(player.getBoundingBox());
    }

    /**
     * @return the block here as it really is, even while the local player's tunnel has erased it from their sight
     */
    public static BlockState trueState(Level level, BlockPos pos)
    {
        if (level.isClientSide() && !CLIENT.isEmpty())
            for (var active : CLIENT.values())
            {
                var erased = active.erased.get(pos);
                if (erased != null && active.level == level)
                    return erased;
            }
        return level.getBlockState(pos);
    }

    public static WallPassagePlan.Cells cells(Level level, Entity body)
    {
        return new WallPassagePlan.Cells()
        {
            @Override
            public Cell at(BlockPos pos)
            {
                if (level.isOutsideBuildHeight(pos) || !level.isLoaded(pos))
                    return Cell.FORBIDDEN;
                var state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty())
                    return Cell.FORBIDDEN;
                if (state.getCollisionShape(level, pos).isEmpty())
                    return Cell.OPEN;
                // Containers and other block entities are never erased, even from sight
                if (state.is(VoidHeartBlocks.POCKET_WALL) || state.getDestroySpeed(level, pos) < 0 || state.hasBlockEntity())
                    return Cell.FORBIDDEN;
                return state.isSolidRender() && state.isCollisionShapeFullBlock(level, pos) ? Cell.SOLID : Cell.PARTIAL;
            }

            @Override
            public double standHeight(BlockPos pos)
            {
                var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                if (shape.isEmpty())
                    return 0;
                double top = shape.max(Direction.Axis.Y);
                return top <= STAND_LIMIT ? top : 0;
            }

            @Override
            public boolean fits(Vec3 feet)
            {
                var box = body.getDimensions(Pose.STANDING).makeBoundingBox(feet);
                return level.getWorldBorder().isWithinBounds(box) && level.noCollision(body, box);
            }
        };
    }

    public static boolean canStart(Player player)
    {
        return !isPassing(player) && !player.isPassenger() && !player.isSleeping() && !player.isSpectator() && player.isAlive();
    }

    public static void begin(ServerPlayer player, Plan plan)
    {
        var level = player.level();
        SERVER.put(player.getUUID(), new Active(plan, player));
        ServerPlayNetworking.send(player, new WallPassagePayloads.Begin(plan));

        // The marks hold their holes open until the collapse has sealed them, then fade
        WraithStain.at(level, plan.entry(), WallPassagePlan.MARK_TICKS, WallPassagePlan.OPEN_TICKS + WallPassagePlan.COLLAPSE_TICKS);
        WraithStain.at(level, plan.exit(), WallPassagePlan.MARK_TICKS, WallPassagePlan.OPEN_TICKS + WallPassagePlan.COLLAPSE_TICKS);
        var entry = plan.entry().center();
        level.playSound(null, entry.x, entry.y, entry.z, VoidHeartSounds.WRAITH_WHISPER, SoundSource.PLAYERS, WHISPER_VOLUME, 1.2F);
    }

    public static void beginClient(Player player, Plan plan)
    {
        var previous = CLIENT.put(player.getUUID(), new Active(plan, player));
        if (previous != null)
            restore(previous);
    }

    public static void endClient(Player player)
    {
        removeClient(player);
    }

    /**
     * Forgets the client's tunnels on leaving a world, which goes with them.
     */
    public static void clearClient()
    {
        CLIENT.clear();
    }

    private static void removeClient(Player player)
    {
        var active = CLIENT.remove(player.getUUID());
        if (active != null)
            restore(active);
    }

    /**
     * Erases the tunnel's blocks from the client's world, and again any the server has sent back since.
     */
    private static void erase(Active active)
    {
        var plan = active.plan;
        for (var at : BlockPos.betweenClosed(plan.tubeMin(), plan.tubeMax()))
        {
            var current = active.level.getBlockState(at);
            // A sign or banner has nothing to bump into, so a tunnel may hold one, but erasing it would lose what it shows: it stays
            if (current.isAir() || current.hasBlockEntity())
                continue;
            // Erasing it, or again after the server sent it back
            var pos = at.immutable();
            active.erased.put(pos, current);
            active.level.setBlock(pos, Blocks.AIR.defaultBlockState(), ERASE_FLAGS);
        }
    }

    /**
     * An erased block of the local player's tunnel and the sides on which it would show from outside.
     */
    public record Shown(BlockPos pos, BlockState state, Set<Direction> sides)
    {
    }

    /**
     * @return the erased blocks of the local player's tunnel that would show from outside other than through a mark, each with the sides it
     * shows on: those toward anything outside the realm that does not hide them
     */
    public static List<Shown> shownFromOutside(Player player)
    {
        var active = activeOf(player);
        if (active == null || active.erased.isEmpty())
            return List.of();
        var plan = active.plan;
        var realm = plan.realm();
        var shown = new ArrayList<Shown>();
        for (var entry : active.erased.entrySet())
        {
            var pos = entry.getKey();
            var sides = EnumSet.noneOf(Direction.class);
            for (var side : Direction.values())
            {
                var next = pos.relative(side);
                if (realm.contains(Vec3.atCenterOf(next)) || onMark(plan.entry(), pos, side) || onMark(plan.exit(), pos, side))
                    continue;
                if (!trueState(active.level, next).canOcclude())
                    sides.add(side);
            }
            if (!sides.isEmpty())
                shown.add(new Shown(pos, entry.getValue(), sides));
        }
        return shown;
    }

    private static boolean onMark(WallPassagePlan.Mark mark, BlockPos pos, Direction side)
    {
        if (side != mark.face())
            return false;
        var axis = side.getAxis();
        int face = pos.get(axis) + (side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0);
        return face == Math.round(axis.choose(mark.center().x, mark.center().y, mark.center().z));
    }

    /**
     * Puts back what was erased, unless the server has changed it since.
     */
    private static void restore(Active active)
    {
        for (var entry : active.erased.entrySet())
            if (active.level.getBlockState(entry.getKey()).isAir())
                active.level.setBlock(entry.getKey(), entry.getValue(), ERASE_FLAGS);
        active.erased.clear();
    }

    /**
     * Run at the end of each player tick on both sides, once their own movement is done.
     */
    public static void tick(Player player)
    {
        var map = side(player.level());
        if (map.isEmpty())
            return;
        var active = map.get(player.getUUID());
        if (active == null)
            return;

        if (active.body != player || active.level != player.level() || !player.isAlive())
        {
            map.remove(player.getUUID());
            if (map == CLIENT)
                restore(active);
            return;
        }

        active.ticks++;
        if (player.level().isClientSide())
            tickClient(player, active);
        else
            tickServer((ServerPlayer) player, active);
    }

    /**
     * While the tunnel collapses, whoever is still inside is carried to the nearer mouth, fast enough to be out before it closes.
     */
    private static void tickClient(Player player, Active active)
    {
        var phase = active.phase();
        if (active.open())
            erase(active);
        if (phase != Phase.COLLAPSING && phase != Phase.CLOSED)
            return;

        var plan = active.plan;
        if (plan.box().intersects(player.getBoundingBox()))
        {
            var target = plan.nearestEnd(player.position());
            var toTarget = target.subtract(player.position());
            int left = Math.max(1, Plan.totalTicks() - active.ticks);
            double step = Math.max(PUSH_SPEED, toTarget.length() / left);
            player.setPos(toTarget.length() <= step ? target : player.position().add(toTarget.normalize().scale(step)));
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            if (active.ticks > Plan.totalTicks() + GRACE_TICKS)
            {
                player.setPos(target);
                removeClient(player);
            }
        }
        else if (phase == Phase.CLOSED)
            removeClient(player);
    }

    private static void tickServer(ServerPlayer player, Active active)
    {
        var plan = active.plan;
        var level = (ServerLevel) player.level();
        if (active.ticks == WallPassagePlan.MARK_TICKS)
        {
            var entry = plan.entry().center();
            level.playSound(null, entry.x, entry.y, entry.z, VoidHeartSounds.WRAITH_EMERGE, SoundSource.PLAYERS, EMERGE_VOLUME, 0.7F);
        }
        if (active.ticks == WallPassagePlan.MARK_TICKS + WallPassagePlan.OPEN_TICKS)
        {
            var middle = plan.box().getCenter();
            level.playSound(null, middle.x, middle.y, middle.z, VoidHeartSounds.WRAITH_WHISPER, SoundSource.PLAYERS, COLLAPSE_VOLUME, 0.6F);
        }
        if (plan.box().intersects(player.getBoundingBox()))
            player.resetFallDistance();

        if (active.ticks < Plan.totalTicks())
            return;
        if (active.ticks == Plan.totalTicks())
            puffs(level, plan);
        if (!plan.box().intersects(player.getBoundingBox()))
        {
            SERVER.remove(player.getUUID());
            resend(player, plan);
        }
        else if (active.ticks > Plan.totalTicks() + GRACE_TICKS)
            finish(player);
    }

    /**
     * The true blocks of a tunnel, for a client that erased them.
     */
    private static void resend(ServerPlayer player, Plan plan)
    {
        for (var pos : BlockPos.betweenClosed(plan.tubeMin(), plan.tubeMax()))
            player.connection.send(new ClientboundBlockUpdatePacket(player.level(), pos));
    }

    private static void puffs(ServerLevel level, Plan plan)
    {
        for (var mark : new WallPassagePlan.Mark[]{plan.entry(), plan.exit()})
        {
            var at = mark.center().add(mark.face().getStepX() * 0.3, mark.face().getStepY() * 0.3, mark.face().getStepZ() * 0.3);
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 10, 0.25, 0.4, 0.25, 0.02);
        }
    }

    /**
     * Someone leaving while inside is put out at the nearer mouth first, so they are not saved inside the wall.
     */
    private static void leave(ServerPlayer player)
    {
        var active = SERVER.remove(player.getUUID());
        if (active == null || !active.plan.box().intersects(player.getBoundingBox()))
            return;
        var out = active.plan.nearestEnd(player.position());
        player.setPos(out.x, out.y, out.z);
    }

    /**
     * Ends the tunnel on the server for a client that never came out, putting the player out at the nearer mouth.
     */
    private static void finish(ServerPlayer player)
    {
        var active = SERVER.remove(player.getUUID());
        ServerPlayNetworking.send(player, new WallPassagePayloads.End());
        if (active == null)
            return;
        resend(player, active.plan);
        var out = active.plan.nearestEnd(player.position());
        player.teleportTo(out.x, out.y, out.z);
    }
}
