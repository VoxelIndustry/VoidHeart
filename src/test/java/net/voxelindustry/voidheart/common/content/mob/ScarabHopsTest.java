package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.ScarabHops.Kind;
import net.voxelindustry.voidheart.common.content.mob.ScarabHops.Spot;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ScarabHopsTest
{
    private static final double REACH = 1.1;

    /**
     * A corridor 3 wide and 3 high along x, solid all around, with an optional wall across it and salt cells.
     */
    private static final class Corridor implements ScarabHops.Ground
    {
        final Set<BlockPos> solid = new HashSet<>();
        final Set<BlockPos> unbreakable = new HashSet<>();
        final Set<BlockPos> salt = new HashSet<>();

        Corridor()
        {
            for (int x = -12; x <= 12; x++)
            {
                for (int y = -1; y <= 3; y++)
                {
                    for (int z = -2; z <= 2; z++)
                    {
                        if (y == -1 || y == 3 || z == -2 || z == 2)
                            solid.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        Corridor wallAt(int x)
        {
            for (int y = 0; y <= 2; y++)
                for (int z = -1; z <= 1; z++)
                    solid.add(new BlockPos(x, y, z));
            return this;
        }

        @Override
        public boolean fits(BlockPos cell, Direction surface)
        {
            // Only the corridor is open: what lies past its walls is rock
            return !solid.contains(cell) && cell.getY() >= 0 && cell.getY() <= 2 && Math.abs(cell.getZ()) <= 1;
        }

        @Override
        public boolean holds(BlockPos block, Direction face)
        {
            return solid.contains(block);
        }

        @Override
        public boolean passable(BlockPos block)
        {
            return !unbreakable.contains(block);
        }

        @Override
        public boolean warded(BlockPos block)
        {
            for (var near : BlockPos.betweenClosed(block.offset(-1, -4, -1), block.offset(1, 1, 1)))
            {
                if (salt.contains(near))
                    return true;
            }
            return false;
        }
    }

    // A player standing in the corridor
    private static AABB player(double x)
    {
        return new AABB(x - 0.3, 0, 0.2, x + 0.3, 1.8, 0.8);
    }

    private static Spot floor(int x, int z)
    {
        return new Spot(new BlockPos(x, 0, z), Direction.UP);
    }

    @Test
    void hopsAreShortAndLandOnAFaceItCanHold()
    {
        var ground = new Corridor();
        var random = RandomSource.create(1);
        for (int i = 0; i < 50; i++)
        {
            var from = floor(0, 0);
            var to = ScarabHops.plan(from, null, REACH, ground, random);
            assertThat(to).isNotNull();
            assertThat(to.center().distanceTo(from.center())).isBetween(ScarabHops.MIN_HOP, ScarabHops.MAX_HOP + 1);
            assertThat(ground.solid).contains(to.cell().relative(to.surface().getOpposite()));
            assertThat(ground.solid).doesNotContain(to.cell());
        }
    }

    @Test
    void closesInOnItsTargetAndKeepsChangingFaces()
    {
        var ground = new Corridor();
        var random = RandomSource.create(7);
        var target = player(10.5);
        var at = floor(-8, 0);
        int changes = 0;
        for (int hop = 0; hop < 12; hop++)
        {
            var next = ScarabHops.plan(at, target, REACH, ground, random);
            assertThat(next).as("hop %d", hop).isNotNull();
            assertThat(ScarabHops.distance(next.center(), target)).as("hop %d", hop)
                    .isLessThan(Math.max(REACH, ScarabHops.distance(at.center(), target)) + 1e-9);
            if (next.kind() != at.kind())
                changes++;
            at = next;
        }
        assertThat(ScarabHops.distance(at.center(), target)).isLessThanOrEqualTo(REACH);
        assertThat(changes).as("hops onto a different kind of face").isGreaterThanOrEqualTo(9);
    }

    @Test
    void usesWallsAndCeilingsAsWellAsTheFloor()
    {
        var ground = new Corridor();
        var random = RandomSource.create(3);
        var kinds = new HashSet<Kind>();
        var at = floor(0, 0);
        for (int hop = 0; hop < 20; hop++)
        {
            at = ScarabHops.plan(at, null, REACH, ground, random);
            kinds.add(at.kind());
        }
        assertThat(kinds).containsExactlyInAnyOrder(Kind.FLOOR, Kind.WALL, Kind.CEILING);
    }

    @Test
    void phasesThroughAWall()
    {
        var ground = new Corridor().wallAt(1);
        var to = ScarabHops.plan(floor(0, 0), player(3.5), REACH, ground, RandomSource.create(2));
        assertThat(to).isNotNull();
        assertThat(to.cell().getX()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void neverPhasesThroughWhatCannotBeBroken()
    {
        var ground = new Corridor().wallAt(1);
        for (int y = -1; y <= 3; y++)
            for (int z = -2; z <= 2; z++)
                ground.unbreakable.add(new BlockPos(1, y, z));
        var random = RandomSource.create(4);
        for (int i = 0; i < 30; i++)
        {
            var to = ScarabHops.plan(floor(0, 0), null, REACH, ground, random);
            assertThat(to).isNotNull();
            assertThat(to.cell().getX()).isLessThan(1);
        }
    }

    @Test
    void neverLandsNearOrHopsAcrossSalt()
    {
        var ground = new Corridor();
        // A line of salt across the whole corridor at x = 3
        for (int z = -1; z <= 1; z++)
            ground.salt.add(new BlockPos(3, 0, z));
        var random = RandomSource.create(5);
        var target = player(8.5);
        var at = floor(0, 0);
        for (int hop = 0; hop < 30; hop++)
        {
            var next = ScarabHops.plan(at, target, REACH, ground, random);
            if (next == null)
                break;
            assertThat(ground.warded(next.cell())).isFalse();
            assertThat(next.cell().getX()).as("%s from %s", next, at).isLessThan(3);
            at = next;
        }
    }

    @Test
    void cannotHopOutOfAWard()
    {
        var ground = new Corridor();
        ground.salt.add(new BlockPos(0, 0, 0));
        assertThat(ScarabHops.plan(floor(0, 0), null, REACH, ground, RandomSource.create(6))).isNull();
    }

    @Test
    void wardedPathIsNotClear()
    {
        var ground = new Corridor();
        ground.salt.add(new BlockPos(3, 0, 0));
        assertThat(ScarabHops.clear(new Vec3(0.5, 0.2, 0.5), new Vec3(6.5, 0.2, 0.5), ground)).isFalse();
        assertThat(ScarabHops.clear(new Vec3(-6.5, 0.2, 0.5), new Vec3(0.5, 0.2, 0.5), ground)).isTrue();
    }
}
