package net.voxelindustry.voidheart.client.render.oculus;

import net.voxelindustry.voidheart.client.render.oculus.OculusMesher.Volume;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static net.voxelindustry.voidheart.client.render.oculus.OculusMesher.INTS_PER_QUAD;
import static net.voxelindustry.voidheart.client.render.oculus.OculusMesher.INTS_PER_VERTEX;
import static org.assertj.core.api.Assertions.assertThat;

class OculusMesherTest
{
    private static final int STONE = 0xFF808080;
    private static final int GRASS = 0xFF40A040;

    private static final Vector3f[] NORMALS = {
            new Vector3f(0, -1, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, -1),
            new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(1, 0, 0)
    };

    private static int quads(int[] mesh)
    {
        return mesh.length / INTS_PER_QUAD;
    }

    private static Vector3f corner(int[] mesh, int quad, int vertex)
    {
        int packed = mesh[quad * INTS_PER_QUAD + vertex * INTS_PER_VERTEX];
        return new Vector3f(packed & 0xFF, packed >> 8 & 0xFF, packed >> 16 & 0xFF);
    }

    private static int face(int[] mesh, int quad)
    {
        return mesh[quad * INTS_PER_QUAD] >>> 24;
    }

    @Test
    void lonelyVoxelHasSixFaces()
    {
        var volume = Volume.empty(4);
        volume.set(3, 1, 5, STONE);

        var mesh = OculusMesher.mesh(volume);

        assertThat(quads(mesh)).isEqualTo(6);
        assertThat(mesh[INTS_PER_VERTEX - 1]).as("color stored as ABGR").isEqualTo(0xFF808080);
    }

    @Test
    void quadsWindCounterClockwiseSeenFromOutside()
    {
        var volume = Volume.empty(4);
        volume.set(3, 1, 5, STONE);

        var mesh = OculusMesher.mesh(volume);

        for (int quad = 0; quad < quads(mesh); quad++)
        {
            var origin = corner(mesh, quad, 0);
            var normal = corner(mesh, quad, 1).sub(origin).cross(corner(mesh, quad, 2).sub(origin));
            assertThat(normal.normalize()).as("face %s", face(mesh, quad)).isEqualTo(NORMALS[face(mesh, quad)]);
        }
    }

    @Test
    void touchingVoxelsOfOneColorMergeAndHideTheirSharedFace()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(1, 0, 0, STONE);

        assertThat(quads(OculusMesher.mesh(volume))).isEqualTo(6);
    }

    @Test
    void touchingVoxelsOfDifferentColorsHideTheirSharedFaceWithoutMerging()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(1, 0, 0, GRASS);

        assertThat(quads(OculusMesher.mesh(volume))).isEqualTo(10);
    }

    @Test
    void wholeLayerBecomesSixQuads()
    {
        var volume = Volume.empty(4);
        for (int x = 0; x < 16; x++)
        {
            for (int z = 0; z < 16; z++)
                volume.set(x, 2, z, GRASS);
        }

        var mesh = OculusMesher.mesh(volume);

        assertThat(quads(mesh)).isEqualTo(6);
        for (int quad = 0; quad < 6; quad++)
        {
            if (face(mesh, quad) == 1)
                assertThat(corner(mesh, quad, 2)).isEqualTo(new Vector3f(16, 3, 16));
        }
    }

    @Test
    void neighborChunkBorderHidesFacesWithoutAddingAny()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(-1, 0, 0, STONE);
        volume.set(16, 0, 7, STONE);

        assertThat(quads(OculusMesher.mesh(volume))).isEqualTo(5);
    }

    @Test
    void windowCutClosesTheSlice()
    {
        var volume = Volume.empty(2);
        volume.set(0, 0, 0, STONE);
        volume.set(0, 1, 0, STONE);

        var mesh = OculusMesher.mesh(volume);

        assertThat(quads(mesh)).isEqualTo(6);
        for (int quad = 0; quad < quads(mesh); quad++)
        {
            if (face(mesh, quad) == 1)
                assertThat(corner(mesh, quad, 0).y).isEqualTo(2);
        }
    }
}
