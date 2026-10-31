package net.voxelindustry.voidheart.client.render.oculus;

import it.unimi.dsi.fastutil.ints.IntArrayList;
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

    /**
     * The quads of the land itself, leaving out the void floor.
     */
    private static int[] land(int[] mesh)
    {
        return floorQuads(mesh, false);
    }

    private static int[] floorQuads(int[] mesh, boolean floor)
    {
        var kept = new IntArrayList();
        for (int quad = 0; quad < quads(mesh); quad++)
        {
            if ((face(mesh, quad) == OculusMesher.FLOOR_FACE) == floor)
                kept.addElements(kept.size(), mesh, quad * INTS_PER_QUAD, INTS_PER_QUAD);
        }
        return kept.toIntArray();
    }

    @Test
    void wholeWindowBottomGetsAnUpwardVoidFloor()
    {
        var volume = Volume.empty(4);
        for (int z = 0; z < 16; z++)
            volume.set(3, 0, z, STONE);

        var floor = floorQuads(OculusMesher.mesh(volume), true);

        assertThat(quads(floor)).as("under the land too, for when it dissolves").isEqualTo(1);
        for (int quad = 0; quad < quads(floor); quad++)
        {
            var origin = corner(floor, quad, 0);
            var normal = corner(floor, quad, 1).sub(origin).cross(corner(floor, quad, 2).sub(origin));
            assertThat(origin.y).isZero();
            assertThat(normal.normalize()).isEqualTo(new Vector3f(0, 1, 0));
        }
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

        var mesh = land(OculusMesher.mesh(volume));

        assertThat(quads(mesh)).isEqualTo(6);
        assertThat(mesh[INTS_PER_VERTEX - 1]).as("color stored as ABGR").isEqualTo(0xFF808080);
    }

    @Test
    void quadsWindCounterClockwiseSeenFromOutside()
    {
        var volume = Volume.empty(4);
        volume.set(3, 1, 5, STONE);

        var mesh = land(OculusMesher.mesh(volume));

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

        assertThat(quads(land(OculusMesher.mesh(volume)))).isEqualTo(6);
    }

    @Test
    void touchingVoxelsOfDifferentColorsHideTheirSharedFaceWithoutMerging()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(1, 0, 0, GRASS);

        assertThat(quads(land(OculusMesher.mesh(volume)))).isEqualTo(10);
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

        var mesh = land(OculusMesher.mesh(volume));

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

        assertThat(quads(land(OculusMesher.mesh(volume)))).isEqualTo(5);
    }

    @Test
    void hollowsAreNeverDrawnButMarkTheFacesTurnedToThem()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(1, 0, 0, OculusMesher.HOLLOW);
        volume.set(2, 0, 0, OculusMesher.HOLLOW);

        var mesh = land(OculusMesher.mesh(volume));

        assertThat(quads(mesh)).isEqualTo(6);
        int marked = 0;
        for (int quad = 0; quad < quads(mesh); quad++)
        {
            if ((face(mesh, quad) & OculusMesher.FACES_HOLLOW) != 0)
            {
                marked++;
                assertThat(face(mesh, quad) & 7).as("the east face, toward the hollow").isEqualTo(5);
            }
        }
        assertThat(marked).isEqualTo(1);
    }

    @Test
    void facesTowardOpenAirAndTowardHollowsDoNotMerge()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(0, 0, 1, STONE);
        volume.set(0, 1, 0, OculusMesher.HOLLOW);

        var tops = 0;
        var mesh = land(OculusMesher.mesh(volume));
        for (int quad = 0; quad < quads(mesh); quad++)
        {
            if ((face(mesh, quad) & 7) == 1)
                tops++;
        }
        assertThat(tops).isEqualTo(2);
    }

    @Test
    void hollowInTheNeighborBorderMarksTheFaceTurnedToIt()
    {
        var volume = Volume.empty(4);
        volume.set(0, 0, 0, STONE);
        volume.set(-1, 0, 0, OculusMesher.HOLLOW);

        var mesh = land(OculusMesher.mesh(volume));

        assertThat(quads(mesh)).isEqualTo(6);
        for (int quad = 0; quad < quads(mesh); quad++)
            assertThat((face(mesh, quad) & OculusMesher.FACES_HOLLOW) != 0).isEqualTo((face(mesh, quad) & 7) == 4);
    }

    @Test
    void windowCutClosesTheSlice()
    {
        var volume = Volume.empty(2);
        volume.set(0, 0, 0, STONE);
        volume.set(0, 1, 0, STONE);

        var mesh = land(OculusMesher.mesh(volume));

        assertThat(quads(mesh)).isEqualTo(6);
        for (int quad = 0; quad < quads(mesh); quad++)
        {
            if (face(mesh, quad) == 1)
                assertThat(corner(mesh, quad, 0).y).isEqualTo(2);
        }
    }
}
