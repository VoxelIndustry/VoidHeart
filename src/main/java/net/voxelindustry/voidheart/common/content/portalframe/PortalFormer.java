package net.voxelindustry.voidheart.common.content.portalframe;

import com.google.common.util.concurrent.Runnables;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties;
import net.voxelindustry.voidheart.common.block.PortalFrameStateProperties.FrameConnection;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;
import java.util.function.Predicate;

import static java.lang.Math.*;
import static net.minecraft.util.math.Direction.*;
import static net.minecraft.util.math.Direction.Axis.X;
import static net.minecraft.util.math.Direction.Axis.Y;

public class PortalFormer
{
    public static boolean isInPocket(World world, BlockPos pos, UUID playerUUID)
    {
        ServerWorld voidWorld = world.getServer().getWorld(VoidHeart.VOID_WORLD_KEY);

        if (world != voidWorld)
            return false;

        BlockPos pocketPos = VoidPocketState.getVoidPocketState((ServerWorld) world).getPosForPlayer(playerUUID);

        return abs(pocketPos.getX() - pos.getX()) < 9 &&
                abs(pocketPos.getY() + 6 - pos.getY()) < 9 &&
                abs(pocketPos.getZ() - pos.getZ()) < 9;
    }

    public static DeferredRollbackWork<PortalFormerState> tryForm(World world, BlockState state, BlockPos brickPos, Direction direction)
    {
        Pair<BlockPos, BlockPos> portalPoints = PortalFormer.tryFloodFill(
                brickPos,
                16,
                pos -> PortalFormer.canUseBlock(world, brickPos, pos, world.getBlockState(pos)),
                world::isAir,
                direction,
                PortalFrameTile.getAdjacentDirection(direction));

        if (portalPoints.getLeft().equals(brickPos) && portalPoints.getRight().equals(brickPos))
            return DeferredRollbackWork.willFail();

        return DeferredRollbackWork.maySucceed(false,
                PortalFormerState.of(portalPoints.getLeft(), portalPoints.getRight(), direction),
                portalFormerState ->
                {
                    createCoreState(state, world, brickPos, portalFormerState.getFacing());

                    var portalFrameTile = (PortalFrameTile) world.getBlockEntity(brickPos);

                    if (portalFrameTile == null)
                        return false;

                    BlockPos.stream(portalFormerState.getFrom(), portalFormerState.getTo()).forEach(pos ->
                    {
                        BlockState frameState = world.getBlockState(pos);

                        setFrameBlockState(world, frameState, pos, portalFormerState);

                        var wall = (PortalFrameTile) world.getBlockEntity(pos);

                        // Core check for corners (it's valid to have a core as a corner but not a wall of the portal)
                        if (wall == null || wall.isCore())
                            return;

                        wall.addCore(portalFrameTile);
                        portalFrameTile.getLinkedFrames().add(pos.toImmutable());
                    });

                    portalFrameTile.setCore(true);
                    portalFrameTile.setPortalState(portalFormerState);
                    portalFrameTile.markDirty();

                    return true;
                }, Runnables.doNothing());
    }

    private static void setFrameBlockState(World world, BlockState state, BlockPos pos, PortalFormerState portalFormerState)
    {
        if (isStateInvalidPotentialFrame(state))
            return;

        var newState = VoidHeartBlocks.PORTAL_FRAME.getDefaultState();
        FrameConnection up = FrameConnection.NONE;
        FrameConnection down = FrameConnection.NONE;
        FrameConnection north = FrameConnection.NONE;
        FrameConnection south = FrameConnection.NONE;
        FrameConnection east = FrameConnection.NONE;
        FrameConnection west = FrameConnection.NONE;

        if (state.getBlock() != VoidHeartBlocks.VOIDSTONE_BRICKS)
        {
            up = state.get(PortalFrameStateProperties.UP);
            down = state.get(PortalFrameStateProperties.DOWN);
            north = state.get(PortalFrameStateProperties.NORTH);
            south = state.get(PortalFrameStateProperties.SOUTH);
            east = state.get(PortalFrameStateProperties.EAST);
            west = state.get(PortalFrameStateProperties.WEST);

            newState = state;
        }

        if (portalFormerState.isCorner(pos))
        {
            if (state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS)
                world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME.getDefaultState());
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

        world.setBlockState(pos, newState
                .with(PortalFrameStateProperties.UP, up)
                .with(PortalFrameStateProperties.DOWN, down)
                .with(PortalFrameStateProperties.NORTH, north)
                .with(PortalFrameStateProperties.SOUTH, south)
                .with(PortalFrameStateProperties.EAST, east)
                .with(PortalFrameStateProperties.WEST, west)
        );
    }

    private static boolean isStateInvalidPotentialFrame(BlockState state)
    {
        if (state.isAir())
            return false;

        return VoidHeartBlocks.PORTAL_FRAME_TAG.contains(state.getBlock()) || state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS;
    }

    public static void createCoreState(BlockState state, World world, BlockPos pos, Direction facing)
    {
        BlockState coreState = VoidHeartBlocks.PORTAL_FRAME_CORE.getDefaultState();

        coreState = coreState.with(Properties.FACING, facing);

        if (state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS)
        {
            world.setBlockState(pos, coreState);
            return;
        }

        for (Direction direction : Direction.values())
        {
            coreState = switch (direction)
                    {
                        case DOWN -> coreState.with(PortalFrameStateProperties.DOWN, state.get(PortalFrameStateProperties.DOWN));
                        case UP -> coreState.with(PortalFrameStateProperties.UP, state.get(PortalFrameStateProperties.UP));
                        case NORTH -> coreState.with(PortalFrameStateProperties.NORTH, state.get(PortalFrameStateProperties.NORTH));
                        case SOUTH -> coreState.with(PortalFrameStateProperties.SOUTH, state.get(PortalFrameStateProperties.SOUTH));
                        case WEST -> coreState.with(PortalFrameStateProperties.WEST, state.get(PortalFrameStateProperties.WEST));
                        case EAST -> coreState.with(PortalFrameStateProperties.EAST, state.get(PortalFrameStateProperties.EAST));
                    };
        }

        world.setBlockState(pos, coreState);
    }

    public static boolean canUseBlock(World world, BlockPos ownPos, BlockPos pos, BlockState state)
    {
        if (pos.equals(ownPos))
            return true;

        // Allow raw voidstone bricks and already formed portals
        if (state.getBlock() != VoidHeartBlocks.PORTAL_FRAME)
            return state.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS;

        PortalFrameTile tile = (PortalFrameTile) world.getBlockEntity(pos);

        return tile != null && !tile.isCore();
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
        Mutable offset = origin.mutableCopy().move(direction);

        while (contentChecker.test(offset) && origin.getManhattanDistance(offset) < maxLength)
            offset.move(direction);

        int offsetDist = origin.getManhattanDistance(offset);
        if (offsetDist >= maxLength || offsetDist == 1)
            return Pair.of(origin, origin);

        if (!borderChecker.test(offset))
            return Pair.of(origin, origin);

        int maxInvertedAxis;
        int minInvertedAxis;

        Mutable originWidth = origin.mutableCopy().move(direction);
        Mutable offsetWidth = offset.mutableCopy().move(direction.getOpposite());
        while (isLineValid(originWidth, offsetWidth, contentChecker)
                && borderChecker.test(originWidth.offset(direction.getOpposite()))
                && borderChecker.test(offsetWidth.offset(direction))
        )
        {
            Direction toMove;
            if (perpendicular.getAxis() == Y)
                toMove = direction.getAxis() == X ? SOUTH : EAST;
            else if (direction.getAxis().isHorizontal())
                toMove = UP;
            else if (perpendicular.getAxis() == X)
                toMove = SOUTH;
            else
                toMove = EAST;

            originWidth.move(toMove);
            offsetWidth.move(toMove);
        }
        if (perpendicular.getAxis() == Y)
            maxInvertedAxis = direction.getAxis() == X ? originWidth.getZ() - 1 : originWidth.getX() - 1;
        else if (direction.getAxis().isHorizontal())
            maxInvertedAxis = originWidth.getY() - 1;
        else if (perpendicular.getAxis() == X)
            maxInvertedAxis = originWidth.getZ() - 1;
        else
            maxInvertedAxis = originWidth.getX() - 1;

        if (!isLineValid(originWidth, offsetWidth, borderChecker))
            return Pair.of(origin, origin);

        originWidth.set(origin).move(direction);
        offsetWidth.set(offset).move(direction.getOpposite());
        while (isLineValid(originWidth, offsetWidth, contentChecker)
                && borderChecker.test(originWidth.offset(direction.getOpposite()))
                && borderChecker.test(offsetWidth.offset(direction))
        )
        {
            Direction toMove;
            if (perpendicular.getAxis() == Y)
                toMove = direction.getAxis() == X ? NORTH : WEST;
            else if (direction.getAxis().isHorizontal())
                toMove = DOWN;
            else if (perpendicular.getAxis() == X)
                toMove = NORTH;
            else
                toMove = WEST;

            originWidth.move(toMove);
            offsetWidth.move(toMove);
        }
        if (perpendicular.getAxis() == Y)
            minInvertedAxis = direction.getAxis() == X ? originWidth.getZ() + 1 : originWidth.getX() + 1;
        else if (direction.getAxis().isHorizontal())
            minInvertedAxis = originWidth.getY() + 1;
        else if (perpendicular.getAxis() == X)
            minInvertedAxis = originWidth.getZ() + 1;
        else
            minInvertedAxis = originWidth.getX() + 1;

        if (!isLineValid(originWidth, offsetWidth, borderChecker))
            return Pair.of(origin, origin);

        originWidth.move(direction.getOpposite());
        offsetWidth.move(direction);
        minInvertedAxis--;
        maxInvertedAxis++;

        if (perpendicular.getAxis() == Y)
        {
            if (direction.getAxis() == X)
            {
                int minX = min(originWidth.getX(), offsetWidth.getX());
                int maxX = max(originWidth.getX(), offsetWidth.getX());

                int minY = min(originWidth.getY(), offsetWidth.getY());
                int maxY = max(originWidth.getY(), offsetWidth.getY());
                return Pair.of(new BlockPos(minX, minY, minInvertedAxis), new BlockPos(maxX, maxY, maxInvertedAxis));
            }
            else
            {
                int minY = min(originWidth.getY(), offsetWidth.getY());
                int maxY = max(originWidth.getY(), offsetWidth.getY());

                int minZ = min(originWidth.getZ(), offsetWidth.getZ());
                int maxZ = max(originWidth.getZ(), offsetWidth.getZ());
                return Pair.of(new BlockPos(minInvertedAxis, minY, minZ), new BlockPos(maxInvertedAxis, maxY, maxZ));
            }
        }
        else if (direction.getAxis().isHorizontal())
        {
            int minX = min(originWidth.getX(), offsetWidth.getX());
            int maxX = max(originWidth.getX(), offsetWidth.getX());

            int minZ = min(originWidth.getZ(), offsetWidth.getZ());
            int maxZ = max(originWidth.getZ(), offsetWidth.getZ());
            return Pair.of(new BlockPos(minX, minInvertedAxis, minZ), new BlockPos(maxX, maxInvertedAxis, maxZ));
        }
        else if (perpendicular.getAxis() == X)
        {
            int minX = min(originWidth.getX(), offsetWidth.getX());
            int maxX = max(originWidth.getX(), offsetWidth.getX());

            int minY = min(originWidth.getY(), offsetWidth.getY());
            int maxY = max(originWidth.getY(), offsetWidth.getY());
            return Pair.of(new BlockPos(minX, minY, minInvertedAxis), new BlockPos(maxX, maxY, maxInvertedAxis));
        }
        else
        {
            int minY = min(originWidth.getY(), offsetWidth.getY());
            int maxY = max(originWidth.getY(), offsetWidth.getY());

            int minZ = min(originWidth.getZ(), offsetWidth.getZ());
            int maxZ = max(originWidth.getZ(), offsetWidth.getZ());
            return Pair.of(new BlockPos(minInvertedAxis, minY, minZ), new BlockPos(maxInvertedAxis, maxY, maxZ));
        }
    }

    private static boolean isLineValid(BlockPos from, BlockPos to, Predicate<BlockPos> checker)
    {
        return BlockPos.stream(from, to).allMatch(checker);
    }
}
