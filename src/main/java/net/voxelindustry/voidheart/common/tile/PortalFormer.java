package net.voxelindustry.voidheart.common.tile;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static net.minecraft.util.math.Direction.Axis.X;
import static net.minecraft.util.math.Direction.Axis.Y;
import static net.minecraft.util.math.Direction.*;

public class PortalFormer
{
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
