package net.voxelindustry.voidheart.client.curse;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class RaptureMassMeshTest
{
    private static final int SEEDS = 40;

    private static final float MOST_DEGREES_OPEN  = 35;
    private static final float LEAST_DEGREES_OPEN = 15;
    private static final int   MOST_QUADS         = 40_000;
    private static final int   LEAST_QUADS        = 5_000;

    // The radius of the thing in the middle at its largest, in the plane above the player
    private static final float MIDDLE_CLEAR = 0.15F;

    @Test
    void sameSeedBuildsTheSameMesh()
    {
        assertThat(vertices(RaptureMassMesh.build(99))).isEqualTo(vertices(RaptureMassMesh.build(99)));
    }

    @Test
    void otherSeedsBuildOtherMeshes()
    {
        assertThat(vertices(RaptureMassMesh.build(1))).isNotEqualTo(vertices(RaptureMassMesh.build(2)));
    }

    @Test
    void quadsStayWithinWhatAFrameCanAfford()
    {
        var random = new Random(5);
        for (int i = 0; i < SEEDS; i++)
            assertThat(RaptureMassMesh.build(random.nextLong()).quads()).isBetween(LEAST_QUADS, MOST_QUADS);
    }

    @Test
    void opensUnevenlyWithinItsRange()
    {
        var random = new Random(6);
        for (int i = 0; i < SEEDS; i++)
        {
            var shape = new RaptureMassShape(random.nextLong());
            float narrowest = Float.MAX_VALUE;
            float widest = 0;
            for (float angle = 0; angle < Mth.TWO_PI; angle += 0.02F)
            {
                narrowest = Math.min(narrowest, shape.rim(angle));
                widest = Math.max(widest, shape.rim(angle));
            }
            assertThat(Math.toDegrees(Math.atan(widest))).isLessThanOrEqualTo(MOST_DEGREES_OPEN);
            assertThat(Math.toDegrees(Math.atan(narrowest))).isGreaterThanOrEqualTo(LEAST_DEGREES_OPEN);
            assertThat(widest / narrowest).isGreaterThan(1.25F);
        }
    }

    @Test
    void nothingStandsInFrontOfTheMiddle()
    {
        var random = new Random(7);
        for (int i = 0; i < SEEDS; i++)
        {
            var data = writeVertices(RaptureMassMesh.build(random.nextLong()));
            while (data.remaining() >= RaptureMassMesh.VERTEX_BYTES)
            {
                float x = data.getFloat();
                float y = data.getFloat();
                float z = data.getFloat();
                float offsetX = data.getFloat();
                float offsetY = data.getFloat();
                float offsetZ = data.getFloat();
                data.position(data.position() + RaptureMassMesh.VERTEX_BYTES - 6 * Float.BYTES);

                float planeX = (x + offsetX) / (y + offsetY);
                float planeZ = (z + offsetZ) / (y + offsetY);
                assertThat(planeX * planeX + planeZ * planeZ).isGreaterThan(MIDDLE_CLEAR * MIDDLE_CLEAR);
            }
        }
    }

    @Test
    void theTearOnlyOpensWithinItsOwnTimeAndLeavesTheOutsideShut()
    {
        var shape = new RaptureMassShape(11);
        var tear = shape.bakeTear();
        assertThat(tear).hasSize(RaptureMassShape.TEAR_SIZE * RaptureMassShape.TEAR_SIZE);

        int shut = 0;
        for (int texel : tear)
        {
            if ((texel >> 16 & 0xFF) == 255)
                shut++;
        }
        assertThat(shut).isGreaterThan(tear.length / 3);
        assertThat(shut).isLessThan(tear.length);
    }

    private static byte[] vertices(RaptureMassMesh mesh)
    {
        var data = writeVertices(mesh);
        var bytes = new byte[data.remaining()];
        data.get(bytes);
        return bytes;
    }

    private static ByteBuffer writeVertices(RaptureMassMesh mesh)
    {
        var rects = new float[RaptureMassMesh.SPRITES.length][];
        Arrays.fill(rects, new float[]{0, 0, 1, 1});
        var data = ByteBuffer.allocate(mesh.quads() * 4 * RaptureMassMesh.VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        mesh.write(data, rects);
        data.flip();
        return data;
    }
}
