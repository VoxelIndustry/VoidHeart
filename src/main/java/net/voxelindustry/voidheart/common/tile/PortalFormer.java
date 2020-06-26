package net.voxelindustry.voidheart.common.tile;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Predicate;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class PortalFormer
{
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
            if (direction.getAxis().isHorizontal())
                toMove = Direction.UP;
            else if (perpendicular.getAxis() == Axis.X)
                toMove = Direction.SOUTH;
            else
                toMove = Direction.EAST;

            originWidth.move(toMove);
            offsetWidth.move(toMove);
        }
        if (direction.getAxis().isHorizontal())
            maxInvertedAxis = originWidth.getY() - 1;
        else if (perpendicular.getAxis() == Axis.X)
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
            if (direction.getAxis().isHorizontal())
                toMove = Direction.DOWN;
            else if (perpendicular.getAxis() == Axis.X)
                toMove = Direction.NORTH;
            else
                toMove = Direction.WEST;

            originWidth.move(toMove);
            offsetWidth.move(toMove);
        }
        if (direction.getAxis().isHorizontal())
            minInvertedAxis = originWidth.getY() + 1;
        else if (perpendicular.getAxis() == Axis.X)
            minInvertedAxis = originWidth.getZ() + 1;
        else
            minInvertedAxis = originWidth.getX() + 1;

        if (!isLineValid(originWidth, offsetWidth, borderChecker))
            return Pair.of(origin, origin);

        if (direction.getAxis().isHorizontal())
        {
            int minX = min(originWidth.getX(), offsetWidth.getX());
            int maxX = max(originWidth.getX(), offsetWidth.getX());

            int minZ = min(originWidth.getZ(), offsetWidth.getZ());
            int maxZ = max(originWidth.getZ(), offsetWidth.getZ());
            return Pair.of(new BlockPos(minX, minInvertedAxis, minZ), new BlockPos(maxX, maxInvertedAxis, maxZ));
        }
        else if (direction.getAxis() == Axis.X)
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

    private static Direction rotateX(Direction direction, Direction perpendicular)
    {
        Direction[] adjacentDirection = getAdjacentDirection(perpendicular);

        int index = ArrayUtils.indexOf(adjacentDirection, direction);
        if (index == adjacentDirection.length - 1)
            return adjacentDirection[0];
        return adjacentDirection[index + 1];
    }

    private static Direction rotateXCCW(Direction direction, Direction perpendicular)
    {
        Direction[] adjacentDirection = getAdjacentDirection(perpendicular);

        int index = ArrayUtils.indexOf(adjacentDirection, direction);
        if (index == 0)
            return adjacentDirection[adjacentDirection.length - 1];
        return adjacentDirection[index - 1];
    }

    private static Direction[] getAdjacentDirection(Direction facing)
    {
        if (facing.getAxis() == Axis.X)
            return new Direction[]{Direction.NORTH, Direction.UP, Direction.SOUTH, Direction.DOWN};
        else if (facing.getAxis() == Axis.Z)
            return new Direction[]{Direction.WEST, Direction.UP, Direction.EAST, Direction.DOWN};
        return new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    }
}
