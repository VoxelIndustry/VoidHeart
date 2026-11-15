package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BileArcTest
{
    /** Flies a glob by the rules its tick follows and checks it comes down on the spot it was aimed at, near or far, up or down. */
    @ParameterizedTest
    @CsvSource({ "8, 0, -2", "15, 4, -2", "30, 0, -2", "22, -9, 0", "12, 12, -3" })
    void landsWhereAimed(double x, double z, double dy)
    {
        var from = new Vec3(0, 2, 0);
        var to = new Vec3(x, 2 + dy, z);
        var velocity = BileArc.launch(from, to);
        var at = from;
        Vec3 landed = null;
        for (int tick = 0; tick < 400 && landed == null; tick++)
        {
            var next = at.add(velocity);
            velocity = BileArc.step(velocity);
            if (next.y <= to.y && velocity.y < 0)
            {
                double t = (at.y - to.y) / (at.y - next.y);
                landed = at.add(next.subtract(at).scale(t));
            }
            at = next;
        }
        assertThat(landed).isNotNull();
        assertThat(Math.hypot(landed.x - to.x, landed.z - to.z)).as("missed by").isLessThan(1.0);
    }
}
