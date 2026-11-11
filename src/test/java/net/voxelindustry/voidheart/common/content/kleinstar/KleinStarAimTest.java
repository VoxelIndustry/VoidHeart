package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarAim.Gaze;
import net.voxelindustry.voidheart.common.content.kleinstar.KleinStarAim.Mark;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KleinStarAimTest
{
    private static final double RADIUS = 6;
    private static final Vec3   EYES   = new Vec3(0, 64, -10);

    private static Mark mark(double x, double z, double health)
    {
        return new Mark(new Vec3(x, 64, z), health);
    }

    private static Vec3 aim(List<Mark> marks)
    {
        return KleinStarAim.aim(marks, RADIUS, null).at();
    }

    private static Gaze lookingAt(Mark mark)
    {
        return new Gaze(EYES, mark.at().subtract(EYES).normalize());
    }

    @Test
    void nothingToAimAt()
    {
        assertNull(KleinStarAim.aim(List.of(), RADIUS, null));
    }

    @Test
    void aloneItIsStruckDeadCenter()
    {
        assertEquals(new Vec3(10, 64, 4), aim(List.of(mark(10, 4, 20))));
    }

    @Test
    void twoAlikeAreStruckHalfway()
    {
        var at = aim(List.of(mark(0, 0, 20), mark(4, 0, 20)));
        assertEquals(2, at.x, 0.05);
        assertEquals(0, at.z, 0.05);
    }

    @Test
    void theHealthierPullsHarder()
    {
        assertEquals(1, aim(List.of(mark(0, 0, 60), mark(4, 0, 20))).x, 0.05);
    }

    @Test
    void whatTheBlastWouldMissDoesNotPull()
    {
        var alone = aim(List.of(mark(0, 0, 60), mark(4, 0, 20)));
        var withFar = aim(List.of(mark(0, 0, 60), mark(4, 0, 20), mark(40, 0, 59)));
        assertEquals(alone.x, withFar.x, 1e-9);
    }

    @Test
    void slidingBringsMoreIntoTheBlast()
    {
        // The third is out of reach of the healthiest, but within reach of the middle of the first two
        var marks = List.of(mark(0, 0, 40), mark(5, 0, 40), mark(8, 0, 10));
        assertEquals(3, KleinStarAim.reached(marks, aim(marks), RADIUS));
    }

    @Test
    void theHealthiestIsNeverLetOut()
    {
        // Each slide brings in a heavier crowd farther along the row, which would drag the aim more than a blast away from the healthiest at its end
        var marks = List.of(mark(0, 0, 30), mark(5, 0, 29), mark(5.5, 0, 29), mark(6, 0, 29), mark(10, 0, 29), mark(10.5, 0, 29), mark(11, 0, 29));
        var at = aim(marks);
        assertTrue(at.distanceTo(new Vec3(0, 64, 0)) <= RADIUS, "aim " + at);
        assertTrue(at.x > 5, "aim " + at);
    }

    @Test
    void theHealthiestCrowdIsNotChosenOverTheHealthiestOne()
    {
        var found = KleinStarAim.aim(List.of(mark(0, 0, 100), mark(30, 0, 60), mark(31, 0, 60), mark(32, 0, 60)), RADIUS, null);
        assertEquals(0, found.at().x, 1e-9);
        assertEquals(0, found.anchor());
    }

    @Test
    void lookingAtTheWeakerSteersTheStarToIt()
    {
        var strong = mark(-12, 0, 100);
        var weak = mark(12, 0, 20);
        var marks = List.of(strong, weak);
        assertEquals(0, KleinStarAim.aim(marks, RADIUS, null).anchor());
        assertEquals(1, KleinStarAim.aim(marks, RADIUS, lookingAt(weak)).anchor());
        assertEquals(0, KleinStarAim.aim(marks, RADIUS, lookingAt(strong)).anchor());
    }

    @Test
    void aGlanceIsOnlyAWeight()
    {
        // Twenty times healthier, the other still wins against a direct look
        var weak = mark(12, 0, 10);
        var marks = List.of(mark(-12, 0, 200), weak);
        assertEquals(0, KleinStarAim.aim(marks, RADIUS, lookingAt(weak)).anchor());
    }

    @Test
    void lookingElsewhereChangesNothing()
    {
        var marks = List.of(mark(-12, 0, 30), mark(12, 0, 20));
        var away = new Gaze(EYES, new Vec3(0, 0, -1));
        assertEquals(0, KleinStarAim.aim(marks, RADIUS, away).anchor());
        assertEquals(KleinStarAim.weight(marks.get(0), null), KleinStarAim.weight(marks.get(0), away), 1e-9);
    }

    @Test
    void withinAGroupTheLookPullsTheBurstTowardWhatIsLookedAt()
    {
        var left = mark(-2, 0, 20);
        var right = mark(2, 0, 20);
        var marks = List.of(left, right);
        assertEquals(0, aim(marks).x, 0.05);
        assertTrue(KleinStarAim.aim(marks, RADIUS, lookingAt(right)).at().x > 0.3);
        assertEquals(2, KleinStarAim.reached(marks, KleinStarAim.aim(marks, RADIUS, lookingAt(right)).at(), RADIUS));
    }
}
