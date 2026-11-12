package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayPropMountTest
{
    private static final long[] SEEDS = { 1L, 1234L, 987654321L, 42L, -7L };
    private static final int RANGE = 6;
    // North, east, south, west, as the chunk writer turns props
    private static final int[][] FACING = { { 0, -1 }, { 1, 0 }, { 0, 1 }, { -1, 0 } };

    /** A wall prop with nothing behind it, or a ceiling prop with nothing above, hangs in thin air. */
    @Test
    void hungPropsHangOnSomething()
    {
        var floating = new ArrayList<String>();
        var causes = new TreeMap<String, Integer>();
        for (long seed : SEEDS)
        {
            var canvas = new UnderwayCanvas(DistrictPos.PITCH * RANGE, DistrictPos.PITCH * RANGE);
            canvas.reset(0, 0);
            UnderwayPlans.paint(seed, canvas);
            for (int y = UnderwayLevels.MIN_Y; y <= UnderwayLevels.TOP_Y; y++)
            {
                for (int z = canvas.minZ(); z <= canvas.maxZ(); z++)
                {
                    for (int x = canvas.minX(); x <= canvas.maxX(); x++)
                    {
                        var prop = canvas.propAt(x, y, z);
                        if (prop == null || prop.mount() == UnderwayProp.Mount.FLOOR)
                            continue;
                        int bx = x, by = y, bz = z;
                        if (prop.mount() == UnderwayProp.Mount.WALL)
                        {
                            int[] facing = FACING[canvas.propFacing(x, y, z)];
                            bx -= facing[0];
                            bz -= facing[1];
                        }
                        else
                            by++;
                        if (bx < canvas.minX() || bx > canvas.maxX() || bz < canvas.minZ() || bz > canvas.maxZ())
                            continue;
                        if (UnderwayPropMount.holds(canvas, bx, by, bz))
                            continue;
                        var behind = canvas.get(bx, by, bz);
                        var behindProp = canvas.propAt(bx, by, bz);
                        String cause = prop.mount() + " on " + (behindProp != null ? "prop " + behindProp : behind.name());
                        causes.merge(cause, 1, Integer::sum);
                        if (floating.size() < 40)
                            floating.add(prop + " at " + x + " " + y + " " + z + " facing " + canvas.propFacing(x, y, z) + ", seed " + seed + ": " + cause);
                    }
                }
            }
        }
        assertThat(floating).as("causes %s", causes).isEmpty();
    }
}
