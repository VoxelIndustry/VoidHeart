package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;
import java.util.function.Predicate;

import static java.lang.Math.*;
import static net.minecraft.core.Direction.*;
import static net.minecraft.core.Direction.Axis.X;
import static net.minecraft.core.Direction.Axis.Y;

public class PortalFormer
{
    public static boolean isInPocket(Level world, BlockPos pos, UUID playerUUID)
    {
        ServerLevel voidWorld = world.getServer().getLevel(VoidHeart.VOID_WORLD_KEY);

        if (world != voidWorld)
            return false;

        // Looking up the pocket position would reserve a pocket for a player that never created one
        var heartData = VoidPocketState.getVoidPocketState((ServerLevel) world).getHeartData(playerUUID);
        if (heartData == null)
            return false;

        return heartData.contains(pos);
    }

    public static DeferredRollbackWork<PortalFormerState> tryForm(Level world, BlockState state, BlockPos brickPos, Direction direction)
    {
        Pair<BlockPos, BlockPos> portalPoints = PortalFormer.tryFloodFill(
                brickPos,
                16,
                pos -> PortalFormer.canUseBlock(world, brickPos, pos, world.getBlockState(pos)),
                world::isEmptyBlock,
                direction,
                PortalFrameTile.getAdjacentDirection(direction));

        if (portalPoints.getLeft().equals(brickPos) && portalPoints.getRight().equals(brickPos))
            return DeferredRollbackWork.willFail();

        return DeferredRollbackWork.maySucceed(
                PortalFormerState.of(portalPoints.getLeft(), portalPoints.getRight(), direction),
                portalFormerState ->
                {
                    createCoreState(state, world, brickPos, portalFormerState.getFacing());

                    var coreFrameOpt = world.getBlockEntity(brickPos, VoidHeartTiles.PORTAL_FRAME_CORE);

                    if (coreFrameOpt.isEmpty())
                        return false;

                    var coreFrame = coreFrameOpt.get();
                    coreFrame.detachFrames();

                    BlockPos.betweenClosedStream(portalFormerState.getFrom(), portalFormerState.getTo()).forEach(pos ->
                    {
                        BlockState frameState = world.getBlockState(pos);

                        setFrameBlockState(world, frameState, pos, portalFormerState);

                        // Corners can be any block, and it's valid to have a core as a corner but not a wall of the portal
                        if (!(world.getBlockEntity(pos) instanceof PortalFrameTile wall) || wall.isCore())
                            return;

                        wall.addCore(coreFrame);
                        coreFrame.addFrame(pos);
                    });

                    coreFrame.setPortalState(portalFormerState);
                    coreFrame.setBroken(false);
                    coreFrame.setChanged();

                    return true;
                });
    }

    private static void setFrameBlockState(Level world, BlockState state, BlockPos pos, PortalFormerState portalFormerState)
    {
        if (isStateInvalidPotentialFrame(state))
            return;

        var newState = VoidHeartBlocks.PORTAL_FRAME.defaultBlockState();
        FrameConnection up = FrameConnection.NONE;
        FrameConnection down = FrameConnection.NONE;
        FrameConnection north = FrameConnection.NONE;
        FrameConnection south = FrameConnection.NONE;
        FrameConnection east = FrameConnection.NONE;
        FrameConnection west = FrameConnection.NONE;

        if (state.getBlock() != VoidHeartBlocks.VOIDSTONE_BRICKS)
        {
            up = state.getValue(PortalFrameStateProperties.UP);
            down = state.getValue(PortalFrameStateProperties.DOWN);
            north = state.getValue(PortalFrameStateProperties.NORTH);
            south = state.getValue(PortalFrameStateProperties.SOUTH);
            east = state.getValue(PortalFrameStateProperties.EAST);
            west = state.getValue(PortalFrameStateProperties.WEST);

            newState = state;
        }

        if (portalFormerState.isCorner(pos))
        {
            if (state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS)
                world.setBlockAndUpdate(pos, VoidHeartBlocks.PORTAL_FRAME.defaultBlockState());
            return;
        }

        if (!portalFormerState.getFacing().getAxis().isVertical())
        {
            up = portalFormerState.getFrom().getY() == pos.getY() ? FrameConnection.INTERIOR : up;
            down = portalFormerState.getTo().getY() == pos.getY() ? FrameConnection.INTERIOR : down;

            if (portalFormerState.getFacing().getAxis() == Axis.Z)
            {
                east = portalFormerState.getFrom().getX() == pos.getX() ? FrameConnection.INTERIOR : east;
                west = portalFormerState.getTo().getX() == pos.getX() ? FrameConnection.INTERIOR : west;
            }
            else
            {
                north = portalFormerState.getTo().getZ() == pos.getZ() ? FrameConnection.INTERIOR : north;
                south = portalFormerState.getFrom().getZ() == pos.getZ() ? FrameConnection.INTERIOR : south;
            }
        }
        else
        {
            east = portalFormerState.getFrom().getX() == pos.getX() ? FrameConnection.INTERIOR : east;
            west = portalFormerState.getTo().getX() == pos.getX() ? FrameConnection.INTERIOR : west;
            north = portalFormerState.getTo().getZ() == pos.getZ() ? FrameConnection.INTERIOR : north;
            south = portalFormerState.getFrom().getZ() == pos.getZ() ? FrameConnection.INTERIOR : south;
        }

        world.setBlockAndUpdate(pos, newState
                .setValue(PortalFrameStateProperties.UP, up)
                .setValue(PortalFrameStateProperties.DOWN, down)
                .setValue(PortalFrameStateProperties.NORTH, north)
                .setValue(PortalFrameStateProperties.SOUTH, south)
                .setValue(PortalFrameStateProperties.EAST, east)
                .setValue(PortalFrameStateProperties.WEST, west)
        );
    }

    private static boolean isStateInvalidPotentialFrame(BlockState state)
    {
        if (state.isAir())
            return true;

        if (state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS)
            return false;

        // The tag can be extended by datapacks, only our frames carry the connection properties
        return !state.is(VoidHeartTags.PORTAL_FRAME_TAG) || !(state.getBlock() instanceof PortalFrameBlock);
    }

    public static void createCoreState(BlockState state, Level world, BlockPos pos, Direction facing)
    {
        BlockState coreState = VoidHeartBlocks.PORTAL_FRAME_CORE.defaultBlockState();

        coreState = coreState.setValue(BlockStateProperties.FACING, facing);

        if (!(state.getBlock() instanceof PortalFrameBlock))
        {
            world.setBlockAndUpdate(pos, coreState);
            return;
        }

        for (var direction : Direction.values())
        {
            coreState = switch (direction)
                    {
                        case DOWN -> coreState.setValue(PortalFrameStateProperties.DOWN, state.getValue(PortalFrameStateProperties.DOWN));
                        case UP -> coreState.setValue(PortalFrameStateProperties.UP, state.getValue(PortalFrameStateProperties.UP));
                        case NORTH -> coreState.setValue(PortalFrameStateProperties.NORTH, state.getValue(PortalFrameStateProperties.NORTH));
                        case SOUTH -> coreState.setValue(PortalFrameStateProperties.SOUTH, state.getValue(PortalFrameStateProperties.SOUTH));
                        case WEST -> coreState.setValue(PortalFrameStateProperties.WEST, state.getValue(PortalFrameStateProperties.WEST));
                        case EAST -> coreState.setValue(PortalFrameStateProperties.EAST, state.getValue(PortalFrameStateProperties.EAST));
                    };
        }

        world.setBlockAndUpdate(pos, coreState);
    }

    public static boolean canUseBlock(Level world, BlockPos ownPos, BlockPos pos, BlockState state)
    {
        if (pos.equals(ownPos))
            return true;

        // Allow raw voidstone bricks and already formed portals
        if (state.getBlock() != VoidHeartBlocks.PORTAL_FRAME)
            return state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS;

        return world.getBlockEntity(pos) instanceof PortalFrameTile tile && !tile.isCore();
    }

    public static Pair<BlockPos, BlockPos> excludeBorders(PortalFormerState area)
    {
        BlockPos min = new BlockPos(
                min(area.getFrom().getX(), area.getTo().getX()) + (area.getFrom().getX() != area.getTo().getX() ? 1 : 0),
                min(area.getFrom().getY(), area.getTo().getY()) + (area.getFrom().getY() != area.getTo().getY() ? 1 : 0),
                min(area.getFrom().getZ(), area.getTo().getZ()) + (area.getFrom().getZ() != area.getTo().getZ() ? 1 : 0));

        BlockPos max = new BlockPos(
                max(area.getFrom().getX(), area.getTo().getX()) - (area.getFrom().getX() != area.getTo().getX() ? 1 : 0),
                max(area.getFrom().getY(), area.getTo().getY()) - (area.getFrom().getY() != area.getTo().getY() ? 1 : 0),
                max(area.getFrom().getZ(), area.getTo().getZ()) - (area.getFrom().getZ() != area.getTo().getZ() ? 1 : 0));

        return Pair.of(min, max);
    }

    public static Pair<BlockPos, BlockPos> tryFloodFill(BlockPos origin,
                                                        int maxLength,
                                                        Predicate<BlockPos> borderChecker,
                                                        Predicate<BlockPos> contentChecker,
                                                        Direction perpendicular,
                                                        Direction... directions)
    {
        for (Direction direction : directions)
        {
            Pair<BlockPos, BlockPos> flood = floodFill(origin, maxLength, borderChecker, contentChecker, direction, perpendicular);

            if (!flood.getLeft().equals(origin) && !flood.getRight().equals(origin))
                return flood;
        }

        return Pair.of(origin, origin);
    }

    static Pair<BlockPos, BlockPos> floodFill(BlockPos origin,
                                              int maxLength,
                                              Predicate<BlockPos> borderChecker,
                                              Predicate<BlockPos> contentChecker,
                                              Direction direction,
                                              Direction perpendicular)
    {
        MutableBlockPos offset = origin.mutable().move(direction);

        while (contentChecker.test(offset) && origin.distManhattan(offset) < maxLength)
            offset.move(direction);

        int offsetDist = origin.distManhattan(offset);
        if (offsetDist >= maxLength || offsetDist == 1)
            return Pair.of(origin, origin);

        if (!borderChecker.test(offset))
            return Pair.of(origin, origin);

        // The frame's width runs across both the direction searched and the perpendicular
        var width = widthAxis(direction, perpendicular);

        MutableBlockPos originWidth = origin.mutable().move(direction);
        MutableBlockPos offsetWidth = offset.mutable().move(direction.getOpposite());
        widen(originWidth, offsetWidth, direction, fromAxisAndDirection(width, AxisDirection.POSITIVE), contentChecker, borderChecker);
        int maxInvertedAxis = originWidth.get(width) - 1;
        if (!isLineValid(originWidth, offsetWidth, borderChecker))
            return Pair.of(origin, origin);

        originWidth.set(origin).move(direction);
        offsetWidth.set(offset).move(direction.getOpposite());
        widen(originWidth, offsetWidth, direction, fromAxisAndDirection(width, AxisDirection.NEGATIVE), contentChecker, borderChecker);
        int minInvertedAxis = originWidth.get(width) + 1;
        if (!isLineValid(originWidth, offsetWidth, borderChecker))
            return Pair.of(origin, origin);

        originWidth.move(direction.getOpposite());
        offsetWidth.move(direction);
        minInvertedAxis--;
        maxInvertedAxis++;

        var min = new BlockPos(min(originWidth.getX(), offsetWidth.getX()), min(originWidth.getY(), offsetWidth.getY()), min(originWidth.getZ(), offsetWidth.getZ()));
        var max = new BlockPos(max(originWidth.getX(), offsetWidth.getX()), max(originWidth.getY(), offsetWidth.getY()), max(originWidth.getZ(), offsetWidth.getZ()));
        return Pair.of(min.relative(width, minInvertedAxis - min.get(width)), max.relative(width, maxInvertedAxis - max.get(width)));
    }

    private static Axis widthAxis(Direction direction, Direction perpendicular)
    {
        if (perpendicular.getAxis() == Y)
            return direction.getAxis() == X ? Axis.Z : X;
        if (direction.getAxis().isHorizontal())
            return Y;
        return perpendicular.getAxis() == X ? Axis.Z : X;
    }

    /**
     * Moves the line between both positions sideways while the frame's interior and its two ends hold.
     */
    private static void widen(MutableBlockPos originWidth, MutableBlockPos offsetWidth, Direction direction, Direction toMove,
                              Predicate<BlockPos> contentChecker, Predicate<BlockPos> borderChecker)
    {
        while (isLineValid(originWidth, offsetWidth, contentChecker)
                && borderChecker.test(originWidth.relative(direction.getOpposite()))
                && borderChecker.test(offsetWidth.relative(direction)))
        {
            originWidth.move(toMove);
            offsetWidth.move(toMove);
        }
    }

    private static boolean isLineValid(BlockPos from, BlockPos to, Predicate<BlockPos> checker)
    {
        return BlockPos.betweenClosedStream(from, to).allMatch(checker);
    }
}
