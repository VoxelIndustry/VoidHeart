package net.voxelindustry.voidheart.client.model.armor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Void Armor's boxes each take their own patch of its skin, inside it. With {@code DUNGEON_PREVIEW=true} the layout is written to
 * {@code build/void-armor-skin/layout.json}, for {@code .claude/tools/void_armor_skin.lua} to paint the skin from.
 */
class VoidArmorSkinTest
{
    @Test
    void everyBoxHasItsOwnPatchInsideTheSkin() throws IOException
    {
        var boxes = VoidArmorModel.build().skin().boxes();
        assertThat(boxes).hasSizeBetween(60, 90);

        for (int i = 0; i < boxes.size(); i++)
        {
            var box = boxes.get(i);
            assertThat(box.u() + box.footprintWidth()).as("right edge of box %d", i).isLessThanOrEqualTo(ArmorSkin.WIDTH);
            assertThat(box.v() + box.footprintHeight()).as("bottom edge of box %d", i).isLessThanOrEqualTo(ArmorSkin.HEIGHT);
            for (int j = 0; j < i; j++)
            {
                var other = boxes.get(j);
                boolean apart = box.u() >= other.u() + other.footprintWidth() || other.u() >= box.u() + box.footprintWidth()
                                || box.v() >= other.v() + other.footprintHeight() || other.v() >= box.v() + box.footprintHeight();
                assertThat(apart).as("boxes %d and %d", j, i).isTrue();
            }
        }

        if ("true".equals(System.getenv("DUNGEON_PREVIEW")))
        {
            var out = Path.of("build/void-armor-skin/layout.json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, VoidArmorModel.build().skin().toJson());
        }
    }
}
