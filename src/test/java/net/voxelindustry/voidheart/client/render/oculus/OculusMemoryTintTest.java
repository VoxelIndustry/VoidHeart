package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OculusMemoryTintTest
{
    @Test
    void aMemoryKeepsLightnessAndLosesHue()
    {
        int grass = OculusSnapshot.remembered(ARGB.color(255, 0x5DA03A));
        int sand = OculusSnapshot.remembered(ARGB.color(255, 0xDBD3A0));
        int coal = OculusSnapshot.remembered(ARGB.color(255, 0x202020));

        assertThat(lightness(sand)).isGreaterThan(lightness(grass));
        assertThat(lightness(grass)).isGreaterThan(lightness(coal));
        // Every block of a memory leans the same cold violet, whatever its own hue
        for (int color : new int[] { grass, sand, coal })
            assertThat(ARGB.blue(color)).isGreaterThanOrEqualTo(ARGB.green(color));
    }

    @Test
    void aMemoryKeepsTheVoxelsAlpha()
    {
        assertThat(ARGB.alpha(OculusSnapshot.remembered(ARGB.color(0x80, 0x5DA03A)))).isEqualTo(0x80);
    }

    private static float lightness(int color)
    {
        return 0.299F * ARGB.red(color) + 0.587F * ARGB.green(color) + 0.114F * ARGB.blue(color);
    }
}
