package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jspecify.annotations.Nullable;

/**
 * Fills the rectangle between two points with barrier blocks. The first point is a clicked block,
 * the second can also be picked in the air, on the plane through the first point giving the largest rectangle.
 */
public class PermeableBarrierItem extends Item
{
    public static final double AIR_REACH = 32;

    /**
     * How many times larger than the horizontal rectangle a vertical one must be to be picked.
     */
    private static final long VERTICAL_PREFERENCE_RATIO = 4;

    public PermeableBarrierItem(Properties properties)
    {
        super(properties
                .rarity(Rarity.UNCOMMON)
                .stacksTo(1)
                .durability(128));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player))
            return InteractionResult.SUCCESS;

        var stack = context.getItemInHand();
        var clicked = context.getClickedPos().relative(context.getClickedFace());
        BlockPos first = stack.get(VoidHeartDataComponents.BARRIER_FIRST_POINT);
        if (first == null)
        {
            stack.set(VoidHeartDataComponents.BARRIER_FIRST_POINT, clicked);
            return InteractionResult.SUCCESS;
        }

        var second = arePointsSamePlane(first, clicked) ? clicked : getAirPoint(first, player);
        return fill(level, player, stack, first, second);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        var stack = player.getItemInHand(hand);
        BlockPos first = stack.get(VoidHeartDataComponents.BARRIER_FIRST_POINT);
        if (first == null)
            return InteractionResult.PASS;
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;

        return fill(serverLevel, serverPlayer, stack, first, getAirPoint(first, player));
    }

    private static InteractionResult fill(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos first, @Nullable BlockPos second)
    {
        if (second == null)
            return InteractionResult.FAIL;
        stack.remove(VoidHeartDataComponents.BARRIER_FIRST_POINT);

        BlockPos firstPlaced = null;
        for (var pos : BlockPos.betweenClosed(first, second))
        {
            if (!level.getBlockState(pos).isAir() || !PermeableBarrierStructure.canExtend(level, pos, player))
                continue;

            level.setBlockAndUpdate(pos, VoidHeartBlocks.PERMEABLE_BARRIER.defaultBlockState());
            if (firstPlaced == null)
                firstPlaced = pos.immutable();

            stack.hurtAndBreak(1, level, player, item -> {});
            if (stack.isEmpty())
                break;
        }

        if (firstPlaced != null)
            PermeableBarrierStructure.onBlockAdded(level, firstPlaced, player.getUUID());
        return InteractionResult.SUCCESS;
    }

    /**
     * The second point the weaver would use now: the targeted block when in a plane with the first point,
     * otherwise a point picked in the air.
     */
    public static @Nullable BlockPos getSecondPoint(BlockPos first, Player player, @Nullable BlockHitResult blockHit)
    {
        if (blockHit != null)
        {
            var clicked = blockHit.getBlockPos().relative(blockHit.getDirection());
            if (arePointsSamePlane(first, clicked))
                return clicked;
        }
        return getAirPoint(first, player);
    }

    /**
     * Casts the player's look against the three axis planes through the first point and keeps the hit
     * spanning the largest rectangle, so the plane follows where the player points.
     * A plane only counts where the look reaches it before any block, never through the ground or a wall.
     * The horizontal plane wins unless a vertical one is clearly larger, so floors stay floors.
     */
    public static @Nullable BlockPos getAirPoint(BlockPos first, Player player)
    {
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        double reach = getUnobstructedReach(player, eye, look);

        BlockPos horizontal = null;
        long horizontalArea = 0;
        BlockPos best = null;
        long bestArea = 0;
        for (var axis : Direction.Axis.values())
        {
            double along = look.get(axis);
            if (Math.abs(along) < 1.0E-4)
                continue;

            double distance = (first.get(axis) + 0.5 - eye.get(axis)) / along;
            if (distance <= 0 || distance > reach)
                continue;

            // Snapped onto the plane, rounding cannot move it off the first point's layer
            var hit = BlockPos.containing(eye.add(look.scale(distance)));
            var point = switch (axis)
            {
                case X -> new BlockPos(first.getX(), hit.getY(), hit.getZ());
                case Y -> new BlockPos(hit.getX(), first.getY(), hit.getZ());
                case Z -> new BlockPos(hit.getX(), hit.getY(), first.getZ());
            };
            long area = (long) (Math.abs(point.getX() - first.getX()) + 1)
                    * (Math.abs(point.getY() - first.getY()) + 1)
                    * (Math.abs(point.getZ() - first.getZ()) + 1);
            if (axis == Direction.Axis.Y)
            {
                horizontal = point;
                horizontalArea = area;
            }
            else if (area > bestArea)
            {
                bestArea = area;
                best = point;
            }
        }

        if (horizontal != null && bestArea <= horizontalArea * VERTICAL_PREFERENCE_RATIO)
            return horizontal;
        return best != null ? best : horizontal;
    }

    private static double getUnobstructedReach(Player player, Vec3 eye, Vec3 look)
    {
        var end = eye.add(look.scale(AIR_REACH));
        var hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().distanceTo(eye) + 0.01 : AIR_REACH;
    }

    public static boolean arePointsSamePlane(BlockPos firstPoint, BlockPos secondPoint)
    {
        return firstPoint.getX() == secondPoint.getX() || firstPoint.getY() == secondPoint.getY() || firstPoint.getZ() == secondPoint.getZ();
    }
}
