package net.voxelindustry.voidheart.client.model.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mob models draw with vanilla's entity cutout, which since 26.3 culls no faces. A flat box then shows both of its faces in one plane, and two boxes showing
 * a face in the same plane both draw there, so either fights for depth. A see-through texel on a closed box shows its far faces from inside, which reads as
 * the box turned inside out.
 * <p>
 * Models are checked in their rest pose, so faces of parts that only meet while still count too: a mob standing idle stays near that pose.
 */
class MobModelGeometryTest
{
    private static final Path TEXTURES = Path.of("src/main/resources/assets/voidheart/textures/entity");
    // The cutout pipeline drops texels below this alpha
    private static final int CUTOUT_ALPHA = 26;
    // In blocks: a face nearer than this to another one fights it
    private static final float COPLANAR = 1e-4F;
    // In blocks: how far out from a face a point is tested for lying inside another box
    private static final float PROBE = 1e-3F;
    private static final int SAMPLES = 8;
    // Off the middle of each cell, so that no sample lands on another box's edge where it can't tell which side it is on
    private static final float CELL_OFFSET = 0.5137F;

    private record Model(String name, Supplier<LayerDefinition> layer, String texture) {}

    private static final List<Model> MODELS = List.of(
            new Model("arcane_eye", ArcaneEyeModel::createBodyLayer, "arcane_eye"),
            new Model("barnacle_growth", BarnacleGrowthModel::createBodyLayer, "barnacle_growth"),
            new Model("centurion", CenturionModel::createBodyLayer, "centurion"),
            new Model("corrupted_barnacle", CorruptedBarnacleModel::createBodyLayer, "corrupted_barnacle"),
            new Model("corrupted_crab", CorruptedCrabModel::createBodyLayer, "corrupted_crab"),
            new Model("eye_maw", EyeMawModel::createSmallLayer, "eye_maw"),
            new Model("large_eye_maw", EyeMawModel::createLargeLayer, "large_eye_maw"),
            new Model("fallen_bishop", FallenBishopModel::createBodyLayer, "fallen_bishop"),
            new Model("fallen_elder", FallenElderModel::createBodyLayer, "fallen_elder"),
            new Model("fallen_novice", FallenModel::createNoviceLayer, "fallen_novice"),
            new Model("fallen_adept", FallenModel::createAdeptLayer, "fallen_adept"),
            new Model("kernacian_dragoon", KernacianDragoonModel::createBodyLayer, "kernacian_dragoon"),
            new Model("legionary", LegionaryModel::createBodyLayer, "legionary"),
            new Model("malignant_boulder", MalignantBoulderModel::createBodyLayer, "malignant_boulder"),
            new Model("stalking_wraith", StalkingWraithModel::createBodyLayer, "stalking_wraith"),
            new Model("stalking_wraith_mouth", StalkingWraithMouthModel::createMouthLayer, "stalking_wraith"),
            new Model("treant", TreantModel::createBodyLayer, "treant"),
            new Model("void_ghoul", VoidGhoulModel::createBodyLayer, "void_ghoul"),
            new Model("void_skeleton", VoidSkeletonModel::createBodyLayer, "void_skeleton"),
            new Model("wraith", WraithModel::createBodyLayer, "wraith"),
            new Model("wraith_scarab", WraithScarabModel::createBodyLayer, "wraith_scarab"));

    // Openings drawn on purpose, where seeing the far side through a gap is the look: ragged edges, notched crests, a ribcage, open hoods and hems
    private static final Set<String> OPENINGS = Set.of(
            "centurion /head#5", "legionary /head#5",
            "fallen_novice /torso#2", "fallen_adept /torso#2", "fallen_adept /torso#3",
            "fallen_bishop /head#5", "fallen_elder /head#5", "fallen_bishop /left_arm#0", "fallen_bishop /right_arm#0",
            "void_skeleton /body#0", "void_skeleton /head#0",
            "wraith /head#1", "wraith /head#3", "wraith /skirt#0", "wraith /torso#1");

    private record Box(String name, Matrix4f toLocal, ModelPart.Cube cube, boolean flat) {}

    private record Face(Box box, Vector3f[] corners, float[][] uv, Vector3f normal)
    {
        Vector3f point(float s, float t)
        {
            var p = new Vector3f(corners[0]);
            p.add(new Vector3f(corners[1]).sub(corners[0]).mul(s));
            return p.add(new Vector3f(corners[3]).sub(corners[0]).mul(t));
        }

        float area()
        {
            return new Vector3f(corners[1]).sub(corners[0]).cross(new Vector3f(corners[3]).sub(corners[0])).length();
        }

        /**
         * Where a point on the face's plane falls along its two edges, from 0 to 1 inside it.
         */
        float[] along(Vector3f point)
        {
            var e1 = new Vector3f(corners[1]).sub(corners[0]);
            var e3 = new Vector3f(corners[3]).sub(corners[0]);
            var d = new Vector3f(point).sub(corners[0]);
            return new float[] { d.dot(e1) / e1.lengthSquared(), d.dot(e3) / e3.lengthSquared() };
        }

        boolean opaqueAt(BufferedImage image, float s, float t)
        {
            float u = uv[0][0] + (uv[1][0] - uv[0][0]) * s + (uv[3][0] - uv[0][0]) * t;
            float v = uv[0][1] + (uv[1][1] - uv[0][1]) * s + (uv[3][1] - uv[0][1]) * t;
            int x = Math.min(image.getWidth() - 1, Math.max(0, (int) Math.floor(u * image.getWidth())));
            int y = Math.min(image.getHeight() - 1, Math.max(0, (int) Math.floor(v * image.getHeight())));
            return (image.getRGB(x, y) >>> 24) >= CUTOUT_ALPHA;
        }
    }

    private record Mesh(List<Box> boxes, List<Face> faces, BufferedImage image)
    {
        boolean covered(Vector3f point, Box... except)
        {
            for (var box : boxes)
            {
                if (box.flat || List.of(except).contains(box))
                    continue;
                var local = box.toLocal.transformPosition(new Vector3f(point)).mul(16);
                var c = box.cube;
                if (local.x > c.minX && local.x < c.maxX && local.y > c.minY && local.y < c.maxY && local.z > c.minZ && local.z < c.maxZ)
                    return true;
            }
            return false;
        }
    }

    @Test
    void noFlatBoxShowsTwoFacesInOnePlane() throws IOException
    {
        var problems = new ArrayList<String>();
        for (var model : MODELS)
        {
            var mesh = mesh(model);
            for (var box : mesh.boxes)
            {
                if (!box.flat)
                    continue;
                var faces = mesh.faces.stream().filter(f -> f.box == box && f.area() > 0).toList();
                if (faces.size() < 2)
                    continue;
                var a = faces.get(0);
                var b = faces.get(1);
                int fights = 0;
                for (int i = 0; i < SAMPLES; i++)
                {
                    for (int j = 0; j < SAMPLES; j++)
                    {
                        var p = a.point((i + CELL_OFFSET) / SAMPLES, (j + CELL_OFFSET) / SAMPLES);
                        var at = b.along(p);
                        boolean open = !mesh.covered(new Vector3f(a.normal).mul(PROBE).add(p)) || !mesh.covered(new Vector3f(a.normal).mul(-PROBE).add(p));
                        if (open && a.opaqueAt(mesh.image, (i + CELL_OFFSET) / SAMPLES, (j + CELL_OFFSET) / SAMPLES) && b.opaqueAt(mesh.image, at[0], at[1]))
                            fights++;
                    }
                }
                if (fights > 0)
                    problems.add("%s %s: both faces of a flat box drawn, %d%% fighting".formatted(model.name, box.name, fights * 100 / (SAMPLES * SAMPLES)));
            }
        }
        problems.forEach(System.out::println);
        assertThat(problems).isEmpty();
    }

    @Test
    void noTwoBoxesShowTheSameFace() throws IOException
    {
        var problems = new ArrayList<String>();
        for (var model : MODELS)
        {
            var mesh = mesh(model);
            var faces = mesh.faces.stream().filter(f -> f.area() > 0).toList();
            for (int i = 0; i < faces.size(); i++)
            {
                for (int j = i + 1; j < faces.size(); j++)
                {
                    var a = faces.get(i);
                    var b = faces.get(j);
                    if (a.box == b.box || Math.abs(a.normal.dot(b.normal)) < 0.999F)
                        continue;
                    if (Math.abs(a.normal.dot(new Vector3f(b.corners[0]).sub(a.corners[0]))) > COPLANAR)
                        continue;
                    int fights = fights(mesh, a, b);
                    if (fights > 0)
                        problems.add("%s %s / %s: %s faces fight at %d of %d points".formatted(model.name, a.box.name, b.box.name,
                                a.normal.dot(b.normal) > 0 ? "same-facing" : "opposite", fights, SAMPLES * SAMPLES));
                }
            }
        }
        problems.forEach(System.out::println);
        assertThat(problems).isEmpty();
    }

    @Test
    void closedBoxesShowNoInsideFaces() throws IOException
    {
        var problems = new ArrayList<String>();
        for (var model : MODELS)
        {
            var mesh = mesh(model);
            for (var face : mesh.faces)
            {
                if (face.box.flat || face.area() == 0 || OPENINGS.contains(model.name + " " + face.box.name))
                    continue;
                int holes = 0;
                for (int i = 0; i < SAMPLES; i++)
                {
                    for (int j = 0; j < SAMPLES; j++)
                    {
                        float s = (i + CELL_OFFSET) / SAMPLES;
                        float t = (j + CELL_OFFSET) / SAMPLES;
                        var p = face.point(s, t);
                        if (!face.opaqueAt(mesh.image, s, t) && !mesh.covered(new Vector3f(face.normal).mul(PROBE).add(p)) && insideShows(mesh, face, p))
                            holes++;
                    }
                }
                if (holes > 0)
                    problems.add("%s %s face %s: inside shows through %d of %d points".formatted(model.name, face.box.name, axis(face.normal), holes, SAMPLES * SAMPLES));
            }
        }
        problems.forEach(System.out::println);
        assertThat(problems).isEmpty();
    }

    /**
     * Whether looking straight in through a point on a face meets an opaque texel of the same box's far side.
     */
    private static boolean insideShows(Mesh mesh, Face face, Vector3f point)
    {
        for (var other : mesh.faces)
        {
            if (other.box != face.box || other == face || other.normal.dot(face.normal) > -0.999F)
                continue;
            float depth = face.normal.dot(new Vector3f(point).sub(other.corners[0]));
            var onOther = new Vector3f(face.normal).mul(-depth).add(point);
            var at = other.along(onOther);
            if (at[0] >= 0 && at[0] <= 1 && at[1] >= 0 && at[1] <= 1)
                return other.opaqueAt(mesh.image, at[0], at[1]);
        }
        return false;
    }

    /**
     * The points where both faces are opaque and some side of their shared plane is left open, so that both draw where the eye can reach.
     */
    private static int fights(Mesh mesh, Face a, Face b)
    {
        int fights = 0;
        for (int i = 0; i < SAMPLES; i++)
        {
            for (int j = 0; j < SAMPLES; j++)
            {
                float s = (i + CELL_OFFSET) / SAMPLES;
                float t = (j + CELL_OFFSET) / SAMPLES;
                var p = a.point(s, t);
                var at = b.along(p);
                if (at[0] < 0 || at[0] > 1 || at[1] < 0 || at[1] > 1)
                    continue;
                boolean front = !mesh.covered(new Vector3f(a.normal).mul(PROBE).add(p));
                boolean back = !mesh.covered(new Vector3f(a.normal).mul(-PROBE).add(p));
                if ((front || back) && a.opaqueAt(mesh.image, s, t) && b.opaqueAt(mesh.image, at[0], at[1]))
                    fights++;
            }
        }
        return fights;
    }

    private static Mesh mesh(Model model) throws IOException
    {
        BufferedImage image;
        try (var in = Files.newInputStream(TEXTURES.resolve(model.texture + ".png")))
        {
            image = ImageIO.read(in);
        }
        ModelPart root = model.layer.get().bakeRoot();
        var boxes = new ArrayList<Box>();
        var faces = new ArrayList<Face>();
        root.visit(new PoseStack(), (pose, path, index, cube) ->
        {
            boolean flat = cube.maxX == cube.minX || cube.maxY == cube.minY || cube.maxZ == cube.minZ;
            var box = new Box(path + "#" + index, new Matrix4f(pose.pose()).invert(), cube, flat);
            boxes.add(box);
            for (var polygon : cube.polygons)
            {
                var corners = new Vector3f[4];
                var uv = new float[4][];
                for (int v = 0; v < 4; v++)
                {
                    var vertex = polygon.vertices()[v];
                    corners[v] = pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), new Vector3f());
                    uv[v] = new float[] { vertex.u(), vertex.v() };
                }
                faces.add(new Face(box, corners, uv, pose.transformNormal(new Vector3f(polygon.normal()), new Vector3f())));
            }
        });
        return new Mesh(boxes, faces, image);
    }

    private static String axis(Vector3f normal)
    {
        float ax = Math.abs(normal.x), ay = Math.abs(normal.y), az = Math.abs(normal.z);
        if (ax >= ay && ax >= az)
            return normal.x > 0 ? "+x" : "-x";
        if (ay >= az)
            return normal.y > 0 ? "+y" : "-y";
        return normal.z > 0 ? "+z" : "-z";
    }
}
