package net.voxelindustry.voidheart.common.content.essence;

import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout.Box;
import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout.Part;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class GreatStorageLayoutTest
{
    @Test
    void cornersFillTheCubeOnce()
    {
        var offsets = new HashSet<String>();
        for (var part : Part.CORNERS)
            offsets.add(part.dx() + "," + part.dy() + "," + part.dz());
        assertThat(offsets).hasSize(GreatStorageLayout.BLOCKS);
        assertThat(Part.CORNERS.stream().filter(Part::isHolder)).containsExactly(Part.LOWER_NW);
    }

    @Test
    void turningMovesTheNorthWestPillarClockwise()
    {
        var pillar = new Box(1, 0, 1, 6, 4, 6, "pillar", "nw");
        assertThat(pillar.turned(1)).isEqualTo(new Box(10, 0, 1, 15, 4, 6, "pillar", "en"));
        assertThat(pillar.turned(2)).isEqualTo(new Box(10, 0, 10, 15, 4, 15, "pillar", "se"));
        assertThat(pillar.turned(3)).isEqualTo(new Box(1, 0, 10, 6, 4, 15, "pillar", "ws"));
        assertThat(pillar.turned(4)).isEqualTo(pillar);
    }

    @Test
    void everyCornerTurnsItsPillarToTheCubesOutside()
    {
        for (var part : Part.CORNERS)
        {
            if (part.isUpper())
                continue;
            var pillar = GreatStorageLayout.boxes(part, StorageFill.MAX_FILL).stream()
                    .filter(box -> box.texture().equals("pillar")).findFirst().orElseThrow();
            var outerX = part.dx() == 0 ? pillar.x1() < 8 : pillar.x0() > 8;
            var outerZ = part.dz() == 0 ? pillar.z1() < 8 : pillar.z0() > 8;
            assertThat(outerX && outerZ).as(part.name()).isTrue();
        }
    }

    @Test
    void pillarsRiseIntoTheUpperBlocksAsTheCubeFills()
    {
        assertThat(GreatStorageLayout.quarter(true, 0)).isEmpty();
        var full = GreatStorageLayout.quarter(true, StorageFill.MAX_FILL);
        assertThat(full.stream().mapToDouble(Box::y1).max().getAsDouble()).isEqualTo(16);
    }

    @Test
    void boxesStayInsideTheirBlock()
    {
        for (var part : Part.values())
        {
            for (var fill = 0; fill <= StorageFill.MAX_FILL; fill++)
            {
                for (var box : GreatStorageLayout.boxes(part, fill))
                {
                    assertThat(box.x0()).isBetween(0.0, 16.0);
                    assertThat(box.x1()).isBetween(0.0, 16.0);
                    assertThat(box.z0()).isBetween(0.0, 16.0);
                    assertThat(box.z1()).isBetween(0.0, 16.0);
                    assertThat(box.y0()).isGreaterThanOrEqualTo(0).isLessThan(box.y1());
                    assertThat(box.y1()).isLessThanOrEqualTo(16);
                }
            }
        }
    }

    @Test
    void plinthDrawsOnlyTheCubesOutside()
    {
        var frame = GreatStorageLayout.quarter(false, 0).getFirst();
        assertThat(frame.faces()).contains("n", "w").doesNotContain("e", "s");
    }
}
