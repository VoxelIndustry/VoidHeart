package net.voxelindustry.voidheart.common.tile;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PortalFormerTest
{
    @Test
    void floodFill_givenHorizontalOneHeightPortal_thenShouldFlood()
    {
        String[] map = new String[]{
                "XXXX",
                "X__X",
                "XXXX"};

        Pair<BlockPos, BlockPos> area = PortalFormer.floodFill(new BlockPos(0, 1, 0),
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(area.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(area.getValue()).isEqualTo(new BlockPos(2, 1, 0));

        String[] longerMap = new String[]{
                "XXXXXX",
                "X____X",
                "XXXXXX"};

        Pair<BlockPos, BlockPos> longerArea = PortalFormer.floodFill(new BlockPos(0, 1, 0),
                16,
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(longerArea.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(longerArea.getValue()).isEqualTo(new BlockPos(4, 1, 0));
    }

    @Test
    void floodFill_givenRectangularPortal_thenShouldFlood()
    {
        String[] map = new String[]{
                "XXXXX",
                "X___X",
                "X___X",
                "XXXXX"};

        Pair<BlockPos, BlockPos> area = PortalFormer.floodFill(new BlockPos(0, 1, 0),
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(area.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(area.getValue()).isEqualTo(new BlockPos(3, 2, 0));

        String[] longerMap = new String[]{
                "XXXX",
                "X__X",
                "X__X",
                "X__X",
                "XXXX"};

        Pair<BlockPos, BlockPos> longerArea = PortalFormer.floodFill(new BlockPos(0, 1, 0),
                16,
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(longerArea.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(longerArea.getValue()).isEqualTo(new BlockPos(2, 3, 0));
    }

    @Test
    void floodFill_givenOpenPortalAtOppositeEdge_thenShouldNotFlood()
    {
        String[] map = new String[]{
                "XXXXX",
                "X____",
                "X___X",
                "XXXXX"};

        BlockPos origin = new BlockPos(0, 1, 0);
        Pair<BlockPos, BlockPos> area = PortalFormer.floodFill(origin,
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(area.getKey()).isEqualTo(origin);
        assertThat(area.getValue()).isEqualTo(origin);

        String[] longerMap = new String[]{
                "X_XX",
                "X__X",
                "X__X",
                "X__X",
                "XXXX"};

        Pair<BlockPos, BlockPos> longerArea = PortalFormer.floodFill(origin,
                16,
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(longerArea.getKey()).isEqualTo(origin);
        assertThat(longerArea.getValue()).isEqualTo(origin);
    }

    @Test
    void floodFill_givenOpenPortal_thenShouldNotFlood()
    {
        String[] map = new String[]{
                "XXXXX",
                "____X",
                "X___X",
                "XXXXX"};

        BlockPos origin = new BlockPos(0, 1, 0);
        Pair<BlockPos, BlockPos> area = PortalFormer.floodFill(origin,
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(area.getKey()).isEqualTo(origin);
        assertThat(area.getValue()).isEqualTo(origin);

        String[] longerMap = new String[]{
                "XXXX",
                "X__X",
                "X__X",
                "X__X",
                "X_XX"};

        Pair<BlockPos, BlockPos> longerArea = PortalFormer.floodFill(origin,
                16,
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> longerMap[longerMap.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.EAST,
                Direction.NORTH);

        assertThat(longerArea.getKey()).isEqualTo(origin);
        assertThat(longerArea.getValue()).isEqualTo(origin);
    }

    @Test
    void tryFloodFill_givenFirstGoodPortal_withSecondOpenPortal_thenShouldFloodFirst()
    {
        String[] map = new String[]{
                "X_XXXXXXX",
                "X___X___X",
                "X___X___X",
                "XXXXXXXXX"};

        BlockPos origin = new BlockPos(4, 1, 0);
        Pair<BlockPos, BlockPos> area = PortalFormer.tryFloodFill(origin,
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.NORTH, Direction.EAST, Direction.WEST);

        assertThat(area.getKey()).isEqualTo(new BlockPos(5, 1, 0));
        assertThat(area.getValue()).isEqualTo(new BlockPos(7, 2, 0));
    }

    @Test
    void tryFloodFill_givenFirstOpenPortal_withSecondGoodPortal_thenShouldFloodFirst()
    {
        String[] map = new String[]{
                "XXXXXXXXX",
                "X___X____",
                "X___X___X",
                "XXXXXXXXX"};

        Pair<BlockPos, BlockPos> area = PortalFormer.tryFloodFill(new BlockPos(4, 1, 0),
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.NORTH, Direction.EAST, Direction.WEST);

        assertThat(area.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(area.getValue()).isEqualTo(new BlockPos(3, 2, 0));
    }

    @Test
    void tryFloodFill_givenVertical_withFirstOpenPortal_withSecondGoodPortal_thenShouldFloodFirst()
    {
        String[] map = new String[]{
                "XXXXX",
                "X____",
                "X___X",
                "XXXXX",
                "X___X",
                "X___X",
                "XXXXX"};

        Pair<BlockPos, BlockPos> area = PortalFormer.tryFloodFill(new BlockPos(2, 3, 0),
                16,
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X',
                pos -> map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_',
                Direction.NORTH, Direction.UP, Direction.DOWN);

        assertThat(area.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(area.getValue()).isEqualTo(new BlockPos(3, 2, 0));
    }

    @Test
    void tryFloodFill_givenRectangularGoodPortal_thenShouldFlood()
    {
        String[] map = new String[]{
                "XXXX",
                "X__X",
                "X__X",
                "X__X",
                "XXXX"};

        Pair<BlockPos, BlockPos> area = PortalFormer.tryFloodFill(new BlockPos(3, 3, 0),
                16,
                pos -> mapBorderChecker(pos, map),
                pos -> mapEmptyChecker(pos, map),
                Direction.NORTH, Direction.EAST, Direction.WEST);

        assertThat(area.getKey()).isEqualTo(new BlockPos(1, 1, 0));
        assertThat(area.getValue()).isEqualTo(new BlockPos(2, 3, 0));
    }

    @Test
    void tryFloodFill_givenHorizontalRectangularGoodPortal_thenShouldFlood()
    {
        String[] map = new String[]{
                "XXXX",
                "X__X",
                "X__X",
                "X__X",
                "XXXX"};

        Pair<BlockPos, BlockPos> area = PortalFormer.tryFloodFill(new BlockPos(3, 1, 3),
                16,
                pos -> mapBorderCheckerHorizontal(pos, map),
                pos -> mapEmptyCheckerHorizontal(pos, map),
                Direction.UP, Direction.EAST, Direction.WEST);

        assertThat(area.getKey()).isEqualTo(new BlockPos(1, 1, 1));
        assertThat(area.getValue()).isEqualTo(new BlockPos(2, 1, 3));
    }

    private boolean mapBorderChecker(BlockPos pos, String[] map)
    {
        if (map[0].length() <= pos.getX())
            return false;
        if (map.length <= pos.getY())
            return false;

        return map[map.length - pos.getY() - 1].charAt(pos.getX()) == 'X';
    }

    private boolean mapEmptyChecker(BlockPos pos, String[] map)
    {
        if (map[0].length() <= pos.getX())
            return true;
        if (map.length <= pos.getY())
            return true;

        return map[map.length - pos.getY() - 1].charAt(pos.getX()) == '_';
    }

    private boolean mapBorderCheckerHorizontal(BlockPos pos, String[] map)
    {
        if (map[0].length() <= pos.getX())
            return false;
        if (map.length <= pos.getZ())
            return false;

        return map[map.length - pos.getZ() - 1].charAt(pos.getX()) == 'X';
    }

    private boolean mapEmptyCheckerHorizontal(BlockPos pos, String[] map)
    {
        if (map[0].length() <= pos.getX())
            return true;
        if (map.length <= pos.getZ())
            return true;

        return map[map.length - pos.getZ() - 1].charAt(pos.getX()) == '_';
    }
}
