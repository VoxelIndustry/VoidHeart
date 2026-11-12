package net.voxelindustry.voidheart.common.world.underway;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class UnderwayPropTest
{
    private static final File TEXTURES = new File("src/main/resources/assets/voidheart/textures/block/underway");

    @Test
    void everyBoxFitsInsideItsBlock()
    {
        for (var prop : UnderwayProp.values())
        {
            for (var box : prop.boxes())
            {
                assertThat(Math.min(box.x0(), Math.min(box.y0(), box.z0()))).as("%s %s", prop, box).isNotNegative();
                assertThat(Math.max(box.x1(), Math.max(box.y1(), box.z1()))).as("%s %s", prop, box).isLessThanOrEqualTo(16);
                assertThat(box.x1() > box.x0() && box.y1() > box.y0() && box.z1() > box.z0()).as("%s %s", prop, box).isTrue();
            }
        }
    }

    /** Two boxes sharing space draw faces on the same plane, which flicker. */
    @Test
    void boxesOfAPropNeverShareSpace()
    {
        for (var prop : UnderwayProp.values())
        {
            var boxes = prop.boxes();
            for (int first = 0; first < boxes.size(); first++)
            {
                for (int second = first + 1; second < boxes.size(); second++)
                    assertThat(boxes.get(first).overlaps(boxes.get(second))).as("%s: %s and %s", prop, boxes.get(first), boxes.get(second)).isFalse();
            }
        }
    }

    @Test
    void propsTouchWhatTheyAreMountedOn()
    {
        for (var prop : UnderwayProp.values())
        {
            int[] bounds = prop.bounds();
            switch (prop.mount())
            {
                case FLOOR -> assertThat(bounds[1]).as("%s stands on the floor", prop).isZero();
                case WALL -> assertThat(bounds[5]).as("%s hangs on the wall to its south", prop).isEqualTo(16);
                case CEILING -> assertThat(bounds[4]).as("%s hangs from the ceiling", prop).isEqualTo(16);
            }
        }
    }

    @Test
    void everyTextureExistsAndCroppedFacesStayOnTheirs()
    {
        var names = new HashSet<String>();
        for (var prop : UnderwayProp.values())
        {
            for (var box : prop.boxes())
            {
                names.add(box.texture());
                for (var face : box.faces())
                {
                    names.add(face.texture());
                    assertThat("nsewud").as("%s %s", prop, face).contains(String.valueOf(face.side()));
                    assertThat(face.u() + box.faceWidth(face.side())).as("%s %s", prop, face).isLessThanOrEqualTo(16);
                    assertThat(face.v() + box.faceHeight(face.side())).as("%s %s", prop, face).isLessThanOrEqualTo(16);
                }
            }
        }
        for (var name : names)
            assertThat(new File(TEXTURES, name + ".png")).as("texture %s", name).exists();
    }
}
