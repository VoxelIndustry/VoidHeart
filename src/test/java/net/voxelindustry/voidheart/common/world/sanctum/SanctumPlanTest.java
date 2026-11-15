package net.voxelindustry.voidheart.common.world.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.voxelindustry.voidheart.common.world.underway.MosaicDoor;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static net.voxelindustry.voidheart.common.world.sanctum.SanctumPlan.*;
import static org.assertj.core.api.Assertions.assertThat;

class SanctumPlanTest
{
    private static final long[] SEEDS = { 0, 1, 42, -7, 123456789L };

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 42, -7, 123456789L })
    void samePlanFromSameSeed(long seed)
    {
        var a = SanctumPlan.generate(seed);
        var b = SanctumPlan.generate(seed);
        for (int y = 0; y < HEIGHT; y++)
            for (int z = 0; z < LENGTH; z++)
                for (int x = 0; x < WIDTH; x++)
                    assertThat(a.material(x, y, z)).isEqualTo(b.material(x, y, z));
        assertThat(a.props()).isEqualTo(b.props());
        assertThat(a.posts()).isEqualTo(b.posts());
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 42, -7, 123456789L })
    void sealedAllRound(long seed)
    {
        var plan = SanctumPlan.generate(seed);
        for (int y = 0; y < HEIGHT; y++)
        {
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    boolean edge = x == 0 || z == 0 || y == 0 || x == WIDTH - 1 || z == LENGTH - 1 || y == HEIGHT - 1;
                    if (edge)
                        assertThat(plan.material(x, y, z).solid()).as("shell at %d %d %d", x, y, z).isTrue();
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 42, -7, 123456789L })
    void gateStandsInAWallFacingTheRoom(long seed)
    {
        var plan = SanctumPlan.generate(seed);
        for (int x = GATE_X0; x < GATE_X0 + GATE_COLUMNS; x++)
        {
            for (int y = FLOOR_Y; y < FLOOR_Y + GATE_ROWS; y++)
            {
                assertThat(plan.material(x, y, GATE_Z)).isEqualTo(SanctumMaterial.WALL);
                assertThat(plan.material(x, y, GATE_Z + GATE_FACING.getStepZ())).isEqualTo(SanctumMaterial.AIR);
            }
        }
        assertThat(plan.material(ARRIVAL_X, FLOOR_Y - 1, ARRIVAL_Z).solid()).isTrue();
        assertThat(plan.material(ARRIVAL_X, FLOOR_Y, ARRIVAL_Z)).isEqualTo(SanctumMaterial.AIR);
        assertThat(plan.material(ARRIVAL_X, FLOOR_Y + 1, ARRIVAL_Z)).isEqualTo(SanctumMaterial.AIR);
        assertThat(plan.props()).noneMatch(prop -> Math.abs(prop.x() - ARRIVAL_X) <= 1 && prop.z() <= ARRIVAL_Z + 1 && prop.y() < FLOOR_Y + GATE_ROWS);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 42, -7, 123456789L })
    void massSitsClearOnTheSanctuary(long seed)
    {
        var plan = SanctumPlan.generate(seed);
        var mass = plan.posts().stream().filter(post -> post.kind() == PostKind.MASS).findFirst().orElseThrow();
        assertThat(mass.y()).isEqualTo(MASS_Y);
        assertThat(plan.material(mass.x(), mass.y() - 1, mass.z()).solid()).isTrue();
        // Its body is about four blocks across and three tall, and the pillar three stones
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 0; dy < 4; dy++)
                    assertThat(plan.material(mass.x() + dx, mass.y() + dy, mass.z() + dz)).as("room at %d %d %d", dx, dy, dz).isEqualTo(SanctumMaterial.AIR);
        assertThat(plan.props()).noneMatch(prop -> Math.abs(prop.x() - mass.x()) <= 2 && Math.abs(prop.z() - mass.z()) <= 2 && prop.y() >= mass.y() && prop.y() < mass.y() + 4);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 42, -7, 123456789L })
    void shoggothsStandOnTheFloorWithRoom(long seed)
    {
        var plan = SanctumPlan.generate(seed);
        var shoggoths = plan.posts().stream().filter(post -> post.kind() == PostKind.SHOGGOTH).toList();
        assertThat(shoggoths).hasSize(8);
        for (var post : shoggoths)
        {
            assertThat(plan.material(post.x(), post.y() - 1, post.z()).solid()).isTrue();
            assertThat(plan.material(post.x(), post.y(), post.z())).isEqualTo(SanctumMaterial.AIR);
            assertThat(plan.material(post.x(), post.y() + 1, post.z())).isEqualTo(SanctumMaterial.AIR);
            assertThat(post.z()).isGreaterThan(DOOR_Z1);
        }
    }

    /** Someone walking from the gate must reach the mass, stepping up at most a block and around everything the plan put on the floor. */
    @ParameterizedTest
    @ValueSource(longs = { 0, 1, 42, -7, 123456789L })
    void massCanBeReachedOnFoot(long seed)
    {
        var plan = SanctumPlan.generate(seed);
        Map<Long, UnderwayProp> props = new HashMap<>();
        for (var prop : plan.props())
            props.put(BlockPos.asLong(prop.x(), prop.y(), prop.z()), prop.prop());

        var start = new BlockPos(ARRIVAL_X, FLOOR_Y, ARRIVAL_Z);
        Set<BlockPos> seen = new HashSet<>();
        var queue = new ArrayDeque<BlockPos>();
        queue.add(start);
        seen.add(start);
        boolean reached = false;
        while (!queue.isEmpty() && !reached)
        {
            var at = queue.poll();
            for (var side : Direction.Plane.HORIZONTAL)
            {
                for (int dy = 1; dy >= -2; dy--)
                {
                    var next = at.offset(side.getStepX(), dy, side.getStepZ());
                    if (!standable(plan, props, next) || dy > 0 && !open(plan, props, at.above(2)))
                        continue;
                    if (seen.add(next))
                        queue.add(next);
                    if (Math.abs(next.getX() - MASS_X) <= 3 && Math.abs(next.getZ() - MASS_Z) <= 3)
                        reached = true;
                    break;
                }
            }
        }
        assertThat(reached).isTrue();
    }

    private static boolean standable(SanctumPlan plan, Map<Long, UnderwayProp> props, BlockPos feet)
    {
        return plan.material(feet.getX(), feet.getY() - 1, feet.getZ()).solid() && open(plan, props, feet) && open(plan, props, feet.above());
    }

    private static boolean open(SanctumPlan plan, Map<Long, UnderwayProp> props, BlockPos pos)
    {
        return SanctumPlan.inside(pos.getX(), pos.getY(), pos.getZ()) && plan.material(pos.getX(), pos.getY(), pos.getZ()) == SanctumMaterial.AIR
                && !props.containsKey(pos.asLong());
    }

    @Test
    void fleshGrowsFromTheSanctuary()
    {
        for (long seed : SEEDS)
        {
            var plan = SanctumPlan.generate(seed);
            int near = 0, far = 0;
            for (int x = NAOS_X0; x <= NAOS_X1; x++)
            {
                if (isFlesh(plan.material(x, FLOOR_Y - 1, NAOS_Z0 + 1)))
                    far++;
                if (isFlesh(plan.material(x, plan.standY(x, MASS_Z - 3) - 1, MASS_Z - 3)))
                    near++;
            }
            assertThat(near).isGreaterThan(far);
            assertThat(plan.props()).anyMatch(prop -> prop.prop() == UnderwayProp.TENDRIL_COIL);
            assertThat(plan.props()).anyMatch(prop -> prop.prop() == UnderwayProp.TENDRIL_TIP);
        }
    }

    private static boolean isFlesh(SanctumMaterial material)
    {
        return material == SanctumMaterial.FLESH || material == SanctumMaterial.FLESH_BRUISED;
    }

    @Test
    void bothKindsOfFleshShow()
    {
        for (long seed : SEEDS)
        {
            var plan = SanctumPlan.generate(seed);
            int plain = 0, bruised = 0;
            for (int z = 0; z < LENGTH; z++)
            {
                for (int x = 0; x < WIDTH; x++)
                {
                    var material = plan.material(x, plan.standY(x, z) - 1, z);
                    plain += material == SanctumMaterial.FLESH ? 1 : 0;
                    bruised += material == SanctumMaterial.FLESH_BRUISED ? 1 : 0;
                }
            }
            assertThat(bruised).as("bruised flesh in sanctum %d", seed).isBetween(plain / 6, plain * 2);
        }
    }

    @Test
    void tendrilsLeaveHeadroom()
    {
        for (long seed : SEEDS)
        {
            var plan = SanctumPlan.generate(seed);
            for (var prop : plan.props())
            {
                if (prop.prop() != UnderwayProp.TENDRIL_TIP)
                    continue;
                assertThat(prop.y() - plan.standY(prop.x(), prop.z())).as("tip over %d %d", prop.x(), prop.z()).isGreaterThanOrEqualTo(3);
            }
        }
    }

    @Test
    void addressesRoundTrip()
    {
        for (int index : new int[] { 0, 1, 2, 17, 900, SanctumAddress.MAX_INDEX })
        {
            var origin = SanctumAddress.origin(index);
            assertThat(SanctumAddress.at(origin.getX(), origin.getZ())).isEqualTo(index);
            assertThat(SanctumAddress.at(origin.getX() + WIDTH - 1, origin.getZ() + LENGTH - 1)).isEqualTo(index);
        }
        assertThat(SanctumAddress.at(0, -1024)).isEqualTo(-1);
        assertThat(SanctumAddress.at(-1024, 0)).isEqualTo(-1);
        assertThat(SanctumAddress.at(1024, 1024)).isEqualTo(-1);
    }

    @Test
    void mosaicDoorSitsUnderTheWorldAndOverTheDais()
    {
        for (var facing : Direction.Plane.HORIZONTAL)
        {
            var door = new MosaicDoor(new BlockPos(100, 50, 200), facing);
            int inside = 0;
            for (int column = 0; column < 15; column++)
            {
                for (int row = 0; row <= 8; row++)
                {
                    if (door.contains(door.tile(column, row)))
                    {
                        inside++;
                        assertThat(column).isBetween(MosaicDoor.FIRST_COLUMN, MosaicDoor.FIRST_COLUMN + MosaicDoor.COLUMNS - 1);
                        assertThat(row).isBetween(MosaicDoor.TOP_ROW, MosaicDoor.BOTTOM_ROW);
                    }
                }
            }
            assertThat(inside).isEqualTo(MosaicDoor.COLUMNS * MosaicDoor.ROWS);
            // The way back out stands in front of the door's foot, on the temple's side
            var threshold = BlockPos.containing(door.threshold());
            assertThat(threshold).isEqualTo(door.tile(MosaicDoor.FIRST_COLUMN + 1, MosaicDoor.BOTTOM_ROW).relative(facing));
            assertThat(door.mouth(0.4).contains(door.center().add(facing.getUnitVec3().scale(0.2)))).isTrue();
        }
    }
}
