package net.voxelindustry.voidheart.common.content.portalframe;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.apache.commons.lang3.tuple.Pair;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Math.*;
import static net.minecraft.util.math.Direction.Axis.X;
import static net.minecraft.util.math.Direction.Axis.Y;
import static net.minecraft.util.math.Direction.*;

public class PortalFormer
{
    public static boolean canUsePearlHere(ItemStack stack, boolean isInPocket)
    {
        if (isInPocket && !stack.getOrCreateTag().contains("pocketPos"))
            return true;
        if (!isInPocket && !stack.getOrCreateTag().contains("externalPos"))
            return true;
        return false;
    }

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

    public static boolean tryForm(World world, BlockState state, BlockPos brickPos, Direction direction)
    {
        Pair<BlockPos, BlockPos> portalPoints = PortalFormer.tryFloodFill(
                brickPos,
                16,
                pos -> PortalFormer.canUseBlock(world, brickPos, pos, world.getBlockState(pos)),
                world::isAir,
                direction,
                PortalFrameTile.getAdjacentDirection(direction));

        if (portalPoints.getLeft().equals(brickPos) && portalPoints.getRight().equals(brickPos))
            return false;

        createCoreState(state, world, brickPos, direction);

        PortalFrameTile portalFrameTile = (PortalFrameTile) world.getBlockEntity(brickPos);

        if (portalFrameTile == null)
            return false;

        BlockPos.stream(portalPoints.getLeft(), portalPoints.getRight()).forEach(pos ->
        {
            BlockState frameState = world.getBlockState(pos);

            if (frameState.getBlock() == VoidHeartBlocks.VOIDSTONE_BRICKS)
                world.setBlockState(pos, VoidHeartBlocks.PORTAL_FRAME.getDefaultState());

            PortalFrameTile wall = (PortalFrameTile) world.getBlockEntity(pos);

            // Core check for corners (it's valid to have a core as a corner but not a wall of the portal)
            if (wall == null || wall.isCore())
                return;

            wall.addCore(portalFrameTile);
            portalFrameTile.getLinkedFrames().add(pos.toImmutable());
        });

        portalFrameTile.setCore(true);
        portalFrameTile.setPortalPoints(portalPoints);
        portalFrameTile.markDirty();

        return true;
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
            switch (direction)
            {
                case DOWN:
                    if (state.get(PortalFrameBlock.DOWN))
                        coreState = coreState.with(PortalFrameBlock.DOWN, true);
                    break;
                case UP:
                    if (state.get(PortalFrameBlock.UP))
                        coreState = coreState.with(PortalFrameBlock.UP, true);
                    break;
                case NORTH:
                    if (state.get(PortalFrameBlock.NORTH))
                        coreState = coreState.with(PortalFrameBlock.NORTH, true);
                    break;
                case SOUTH:
                    if (state.get(PortalFrameBlock.SOUTH))
                        coreState = coreState.with(PortalFrameBlock.SOUTH, true);
                    break;
                case WEST:
                    if (state.get(PortalFrameBlock.WEST))
                        coreState = coreState.with(PortalFrameBlock.WEST, true);
                    break;
                case EAST:
                    if (state.get(PortalFrameBlock.EAST))
                        coreState = coreState.with(PortalFrameBlock.EAST, true);
                    break;
            }
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

    public static Stream<BlockPos> streamBorders(Pair<BlockPos, BlockPos> area)
    {
        return BlockPos.stream(area.getLeft(), area.getRight()).filter(pos ->
                (pos.getX() == area.getLeft().getX() || pos.getX() == area.getRight().getX() &&
                        pos.getY() == area.getLeft().getY() || pos.getY() == area.getRight().getY())
                        || (pos.getX() == area.getLeft().getX() || pos.getX() == area.getRight().getX() &&
                        pos.getZ() == area.getLeft().getZ() || pos.getZ() == area.getRight().getZ())
                        || (pos.getZ() == area.getLeft().getZ() || pos.getZ() == area.getRight().getZ() &&
                        pos.getY() == area.getLeft().getY() || pos.getY() == area.getRight().getY())
        );
    }

    public static Pair<BlockPos, BlockPos> excludeBorders(Pair<BlockPos, BlockPos> area)
    {
        BlockPos min = new BlockPos(
                min(area.getLeft().getX(), area.getRight().getX()) + (area.getLeft().getX() != area.getRight().getX() ? 1 : 0),
                min(area.getLeft().getY(), area.getRight().getY()) + (area.getLeft().getY() != area.getRight().getY() ? 1 : 0),
                min(area.getLeft().getZ(), area.getRight().getZ()) + (area.getLeft().getZ() != area.getRight().getZ() ? 1 : 0));

        BlockPos max = new BlockPos(
                max(area.getLeft().getX(), area.getRight().getX()) - (area.getLeft().getX() != area.getRight().getX() ? 1 : 0),
                max(area.getLeft().getY(), area.getRight().getY()) - (area.getLeft().getY() != area.getRight().getY() ? 1 : 0),
                max(area.getLeft().getZ(), area.getRight().getZ()) - (area.getLeft().getZ() != area.getRight().getZ() ? 1 : 0));

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
