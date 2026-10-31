package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.core.BlockPos;
import net.voxelindustry.voidheart.common.content.oculus.OculusFootprint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.voxelindustry.voidheart.client.render.oculus.OculusProjector.FADE_RESOLUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class OculusEdgeFadeTest
{
    private static float at(float[] fade, OculusFootprint footprint, float x, float z)
    {
        int texelX = (int) (x * FADE_RESOLUTION);
        int texelZ = (int) (z * FADE_RESOLUTION);
        return fade[texelX + texelZ * footprint.width() * FADE_RESOLUTION];
    }

    @Test
    void fadesTowardTheOuterEdgeButNotAcrossJoinedBlocks()
    {
        var footprint = OculusFootprint.of(List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        var fade = OculusProjector.edgeFade(footprint);

        assertThat(at(fade, footprint, 0.03F, 0.5F)).isLessThan(0.25F);
        assertThat(at(fade, footprint, 1.0F, 0.5F)).isEqualTo(1F);
        assertThat(at(fade, footprint, 0.5F, 0.5F)).isEqualTo(1F);
    }

    @Test
    void innerCornerOfAnLShapeFades()
    {
        var footprint = OculusFootprint.of(List.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0), new BlockPos(0, 64, 1)));
        var fade = OculusProjector.edgeFade(footprint);

        assertThat(at(fade, footprint, 1.5F, 1.5F)).as("missing cell").isZero();
        assertThat(at(fade, footprint, 0.97F, 0.97F)).as("next to the notch").isLessThan(0.35F);
        assertThat(at(fade, footprint, 0.5F, 0.5F)).isCloseTo(1F, within(1E-6F));
    }
}
