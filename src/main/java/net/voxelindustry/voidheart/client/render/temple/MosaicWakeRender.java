package net.voxelindustry.voidheart.client.render.temple;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.underway.UnderwayMosaicBlock;
import net.voxelindustry.voidheart.common.world.underway.UnderwayTemple;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws a temple's mosaic waking, on the client of the player who stares at it, over the wall's own blocks.
 * <p>
 * Everything is one {@link RenderTypes#entitySolid} pass over a texture holding the picture whole and chipped, so a lifted tessera shows exactly the pixels it
 * left. Solid and writing depth, it hides what is behind it as the wall does, and its faces are wound counter-clockwise from outside so culling keeps only those
 * facing the camera. Every normal points up, which the entity shader lights fully, and each face is shaded by hand as the world shades a block face turned the
 * same way, so a pixel laid flat matches the wall around it. Light comes from the air in front of each tile, and what glows lights itself through its light
 * coordinates.
 * <p>
 * Nothing lies in the wall's plane: flat pixels sit {@value #FLAT_LIFT} of a pixel off it. Per frame at full reach: about two thousand runs of tessera faces
 * and their cracks, five thousand short sides, a hundred and fifty tendril boxes, a hundred and fifty wheel boxes with thirty eye panels, and the hood's two
 * hundred drawn pixels, about twelve thousand quads, rebuilt only while a mosaic is awake and near.
 */
public final class MosaicWakeRender
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/misc/temple_mosaic.png");
    private static final RenderType TYPE = RenderTypes.entitySolid(TEXTURE);
    private static final float TEXTURE_SIZE = 256;
    /** A white texel beside the picture, which plain colours are drawn with. */
    private static final float WHITE_U = 248 / TEXTURE_SIZE;
    private static final float WHITE_V = 8 / TEXTURE_SIZE;

    /** How far off the wall flat pixels lie, in pixels. */
    private static final float FLAT_LIFT = 0.15F;
    private static final float SIDE_EPSILON = 0.01F;
    private static final double MAX_DISTANCE = 48;

    private static final int SEAM_DARK = 0x1C0A2A;
    private static final int SEAM_LIT = 0x9A4DFF;
    /** Tendril segments overlap by this share of their width, so bends show no gap. */
    private static final double TENDRIL_OVERLAP = 0.5;
    private static final double HOOK_LENGTH = 2;
    /** The wheels' gold, and how thick the eyes on them and the great eye at their hub are, in blocks. */
    private static final double RING_WIDTH = 2 / 16D;
    private static final double RING_EYE_DEPTH = 1 / 16D;
    private static final double HUB_EYE_DEPTH = 2 / 16D;
    /** The hood turns over the last of the reach, drawn out this many pixels, its hollow only this share of that. */
    private static final float HOOD_FROM = 0.85F;
    private static final float HOOD_DEPTH = 12F;
    private static final float HOOD_HOLLOW = 0.15F;

    private static final MosaicPicture picture = new MosaicPicture();

    private MosaicWakeRender()
    {
    }

    public static void collectSubmits(LevelRenderContext context)
    {
        var wall = MosaicStare.wall();
        var gaze = MosaicStare.gaze();
        var art = MosaicStare.art();
        var wake = MosaicStare.wake();
        var motion = MosaicStare.motion();
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        var player = minecraft.player;
        if (wall == null || gaze == null || art == null || level == null || player == null || !wake.active())
            return;
        var levelState = context.levelState();
        var camera = levelState.cameraRenderState.pos;
        if (wall.middle().distanceTo(camera) > MAX_DISTANCE)
            return;
        float partialTicks = levelState.worldPartialTicks;
        float time = levelState.gameTime % 240_000L + partialTicks;
        var viewer = wall.toPicture(player.getEyePosition(partialTicks));

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), TYPE, (pose, buffer) ->
        {
            picture.compute(art, wall.wear(), wake, gaze, motion, partialTicks, time, wall.seed());
            var out = new Out(pose, buffer, wall, camera, level.cardinalLighting());
            var light = new int[UnderwayMosaicBlock.TILES];
            for (int row = 0; row < UnderwayTemple.ROWS; row++)
            {
                for (int column = 0; column < UnderwayTemple.COLUMNS; column++)
                    light[row * UnderwayTemple.COLUMNS + column] = LightCoordsUtil.getLightCoords(level, wall.tile(column, row).relative(wall.facing()));
            }
            drawPicture(out, wall, light);
            float stir = wake.stir(partialTicks);
            float reach = wake.reach(partialTicks);
            if (stir > 0)
            {
                drawTendrils(out, art, wall, level, stir, reach, time, viewer);
                drawWheels(out, art, wall, level, stir, reach, motion.spin(partialTicks), motion.tumble(partialTicks), viewer);
            }
            if (reach > 0)
                drawHood(out, art, wall, reach, viewer, light);
        });
    }

    private static void drawPicture(Out out, MosaicWall wall, int[] light)
    {
        int[] wear = wall.wear();
        float seamGlow = picture.seamGlow();
        int seamColour = 0xFF000000 | lerpColour(SEAM_DARK, SEAM_LIT, seamGlow);
        int count = picture.drawnCount();
        for (int i = 0; i < count; i++)
        {
            int index = picture.drawn(i);
            int x = index % MosaicArt.WIDTH;
            int y = index / MosaicArt.WIDTH;
            int tile = MosaicArt.tileOf(index);
            byte kind = picture.kind(index);
            int own = picture.colour(index);
            float height = Math.max(picture.height(index), FLAT_LIFT);
            boolean raised = height > FLAT_LIFT;
            int tileLight = light[tile];
            float emission = picture.emission(index);
            int lit = emission > 0 ? glowing(tileLight, emission) : tileLight;

            if (kind == MosaicPicture.SEAM)
            {
                // A groove where something came away glows with the rest; a crack between tesserae brings its own colour
                int colour = own != 0 ? own : seamColour;
                int seamLight = glowing(tileLight, own != 0 ? emission : seamGlow);
                out.front(x, x + 1, y, height, WHITE_U, WHITE_V, WHITE_U, WHITE_V, colour, seamLight);
                if (raised)
                    sides(out, x, y, height, WHITE_U, WHITE_V, colour & 0xFFFFFF, seamLight);
                continue;
            }
            if (own != 0)
            {
                out.front(x, x + 1, y, height, WHITE_U, WHITE_V, WHITE_U, WHITE_V, shade(own, out.frontShade), lit);
                if (raised)
                    sides(out, x, y, height, WHITE_U, WHITE_V, own, lit);
                continue;
            }
            int from = picture.source(index);
            if (from != index)
            {
                // A pixel of the picture shown somewhere else, as a drop of brood sliding down
                float u = (from % MosaicArt.WIDTH + 0.5F) / TEXTURE_SIZE;
                float v = (from / MosaicArt.WIDTH + (wear[MosaicArt.tileOf(from)] == MosaicWall.CHIPPED ? MosaicArt.HEIGHT : 0) + 0.5F) / TEXTURE_SIZE;
                out.front(x, x + 1, y, height, u, v, u, v, shade(0xFFFFFF, out.frontShade), lit);
                continue;
            }
            // A run of pixels along a row at one height on one tile shares one face
            int end = x;
            while (i + 1 < count)
            {
                int next = picture.drawn(i + 1);
                if (next != index + end - x + 1 || (end + 1) % MosaicArt.TILE == 0 || picture.kind(next) != kind || picture.colour(next) != 0
                        || picture.source(next) != next || picture.emission(next) != emission || Math.max(picture.height(next), FLAT_LIFT) != height)
                    break;
                end++;
                i++;
            }
            float v = (y + (wear[tile] == MosaicWall.CHIPPED ? MosaicArt.HEIGHT : 0)) / TEXTURE_SIZE;
            out.front(x, end + 1, y, height, x / TEXTURE_SIZE, v, (end + 1) / TEXTURE_SIZE, v + 1 / TEXTURE_SIZE, shade(0xFFFFFF, out.frontShade), lit);
            if (raised)
            {
                for (int along = x; along <= end; along++)
                    sides(out, along, y, height, (along + 0.5F) / TEXTURE_SIZE, v + 0.5F / TEXTURE_SIZE, 0xFFFFFF, lit);
            }
        }
    }

    /** The sides of a lifted pixel, wherever its neighbour stands lower, in the colour of one texel. */
    private static void sides(Out out, int x, int y, float height, float u, float v, int colour, int light)
    {
        float left = neighbour(x - 1, y);
        if (left < height - SIDE_EPSILON)
            out.side(x, y, x, y + 1, left, height, out.right.getOpposite(), u, v, colour, light);
        float right = neighbour(x + 1, y);
        if (right < height - SIDE_EPSILON)
            out.side(x + 1, y, x + 1, y + 1, right, height, out.right, u, v, colour, light);
        float above = neighbour(x, y - 1);
        if (above < height - SIDE_EPSILON)
            out.side(x, y, x + 1, y, above, height, Direction.UP, u, v, colour, light);
        float below = neighbour(x, y + 1);
        if (below < height - SIDE_EPSILON)
            out.side(x, y + 1, x + 1, y + 1, below, height, Direction.DOWN, u, v, colour, light);
    }

    private static float neighbour(int x, int y)
    {
        if (x < 0 || y < 0 || x >= MosaicArt.WIDTH || y >= MosaicArt.HEIGHT)
            return 0;
        int index = MosaicArt.index(x, y);
        return picture.kind(index) == MosaicPicture.NONE ? 0 : Math.max(picture.height(index), FLAT_LIFT);
    }

    private static void drawTendrils(Out out, MosaicArt art, MosaicWall wall, ClientLevel level, float stir, float reach, float time, double[] viewer)
    {
        long seed = wall.seed();
        for (int tendril = 0; tendril < art.tendrils().size(); tendril++)
        {
            if (!MosaicTendrils.rooted(art, wall.wear(), tendril))
                continue;
            var path = art.tendrils().get(tendril);
            int above = MosaicArt.index(path.x()[0], Math.max(0, path.y()[0] - 2));
            float rootLift = picture.kind(above) == MosaicPicture.TESSERA ? picture.height(above) : 0;
            var points = MosaicTendrils.shape(art, tendril, stir, reach, time, seed, viewer[0], viewer[1], viewer[2], rootLift);
            var world = new Vec3[points.length];
            for (int s = 0; s < points.length; s++)
                world[s] = wall.toWorld(points[s][0], points[s][1], points[s][2]);
            for (int s = 0; s + 1 < points.length; s++)
            {
                var from = world[s];
                var segment = world[s + 1].subtract(from);
                double length = segment.length();
                if (length < 1E-4)
                    continue;
                var forward = segment.scale(1 / length);
                double width = (points[s][3] + points[s + 1][3]) / 2 / 16;
                int light = LightCoordsUtil.getLightCoords(level, BlockPos.containing(from));
                // Each face shows the painted row it came from, across the tendril's width, so it is the picture's own tendril pulled out of the wall
                int x = path.x()[s];
                int y = path.y()[s];
                int half = path.halfWidth()[s];
                out.box(from.subtract(forward.scale(width * TENDRIL_OVERLAP)), forward, length + width * TENDRIL_OVERLAP, width,
                        (x - half) / TEXTURE_SIZE, y / TEXTURE_SIZE, (x + half + 1) / TEXTURE_SIZE, (y + 1) / TEXTURE_SIZE, (x + 0.5F) / TEXTURE_SIZE, (y + 0.5F) / TEXTURE_SIZE, light);
            }
            if (points.length > 1)
            {
                var tip = world[points.length - 1];
                var forward = tip.subtract(world[points.length - 2]);
                // The bone hook the script paints a row under the tip
                float boneU = (path.x()[points.length - 1] + 0.5F) / TEXTURE_SIZE;
                float boneV = (path.y()[points.length - 1] + 1.5F) / TEXTURE_SIZE;
                if (forward.lengthSqr() > 1E-8)
                    out.box(tip, forward.normalize(), HOOK_LENGTH / 16, 1 / 16D, boneU, boneV, boneU, boneV, boneU, boneV,
                            LightCoordsUtil.getLightCoords(level, BlockPos.containing(tip)));
            }
        }
    }

    /**
     * The wheels as chains of gold boxes round their circles, their eyes as small panels showing the painted eyes. Lying on the wall, the eyes face out of it;
     * as the wheels come out they turn to face the player, and the great eye comes out at their hub, facing the player too.
     */
    private static void drawWheels(Out out, MosaicArt art, MosaicWall wall, ClientLevel level, float stir, float reach, float spin, float tumble, double[] viewer)
    {
        var shape = MosaicWheels.shape(art, stir, reach, spin, tumble);
        var viewerWorld = wall.toWorld(viewer[0], viewer[1], viewer[2]);
        var outward = new Vec3(wall.facing().getStepX(), 0, wall.facing().getStepZ());
        int hub = art.wheels().size();
        for (int k = 0; k < shape.rings().length; k++)
        {
            var ring = shape.rings()[k];
            int texel = ringTexel(art, k);
            float u = (texel % MosaicArt.WIDTH + 0.5F) / TEXTURE_SIZE;
            float v = (texel / MosaicArt.WIDTH + 0.5F) / TEXTURE_SIZE;
            for (int s = 0; s < ring.length; s++)
            {
                var from = wall.toWorld(ring[s][0], ring[s][1], ring[s][2]);
                var to = wall.toWorld(ring[(s + 1) % ring.length][0], ring[(s + 1) % ring.length][1], ring[(s + 1) % ring.length][2]);
                var segment = to.subtract(from);
                double length = segment.length();
                if (length < 1E-4)
                    continue;
                var forward = segment.scale(1 / length);
                out.box(from.subtract(forward.scale(RING_WIDTH * TENDRIL_OVERLAP)), forward, length + RING_WIDTH * TENDRIL_OVERLAP, RING_WIDTH, u, v, u, v, u, v,
                        LightCoordsUtil.getLightCoords(level, BlockPos.containing(from)));
            }
            int wheel = k;
            var painted = art.eyes().stream().filter(eye -> eye.figure() == MosaicArt.WHEEL_BODY && eye.part() == wheel).toList();
            var eyes = shape.eyes()[k];
            for (int n = 0; n < eyes.length && n < painted.size(); n++)
            {
                var at = wall.toWorld(eyes[n][0], eyes[n][1], eyes[n][2]);
                var facing = facing(outward, viewerWorld.subtract(at), reach);
                // Set on the gold rather than sunk in it
                eyePanel(out, level, painted.get(n), at.add(facing.scale((RING_WIDTH + RING_EYE_DEPTH) / 2)), facing, RING_EYE_DEPTH);
            }
        }
        if (reach > 0)
        {
            art.eyes().stream().filter(eye -> eye.figure() == MosaicArt.WHEEL_BODY && eye.part() == hub).findFirst().ifPresent(eye ->
            {
                var at = wall.toWorld(shape.hub()[0], shape.hub()[1], shape.hub()[2]);
                eyePanel(out, level, eye, at, facing(outward, viewerWorld.subtract(at), Math.min(1, reach * 2)), HUB_EYE_DEPTH);
            });
        }
    }

    /** Out of the wall while flat on it, turning toward the player as it comes out. */
    private static Vec3 facing(Vec3 outward, Vec3 toViewer, float turn)
    {
        var toward = toViewer.lengthSqr() > 1E-6 ? toViewer.normalize() : outward;
        var blend = outward.lerp(toward, turn);
        return blend.lengthSqr() > 1E-6 ? blend.normalize() : outward;
    }

    /** An eye of the picture as a panel, its face the painted eye's pixels, upright as the picture is. */
    private static void eyePanel(Out out, ClientLevel level, MosaicArt.Eye eye, Vec3 at, Vec3 facing, double depth)
    {
        int x0 = Mth.floor(eye.x() - eye.rx() + 0.5F);
        int x1 = Mth.floor(eye.x() + eye.rx() + 0.5F) + 1;
        int y0 = Mth.floor(eye.y() - eye.ry() + 0.5F);
        int y1 = Mth.floor(eye.y() + eye.ry() + 0.5F) + 1;
        float rimU = (x0 + 0.5F) / TEXTURE_SIZE;
        float rimV = (Mth.floor(eye.y() + 0.5F) + 0.5F) / TEXTURE_SIZE;
        out.panel(at, facing, (x1 - x0) / 16D, (y1 - y0) / 16D, depth, x0 / TEXTURE_SIZE, y0 / TEXTURE_SIZE, x1 / TEXTURE_SIZE, y1 / TEXTURE_SIZE, rimU, rimV,
                LightCoordsUtil.getLightCoords(level, BlockPos.containing(at)));
    }

    /** A pixel painted in a wheel's gold, for its colour. */
    private static int ringTexel(MosaicArt art, int wheel)
    {
        for (int index : art.wheelPixels(wheel))
        {
            if (art.region(index) == MosaicArt.RING)
                return index;
        }
        return MosaicArt.index(art.hubX(), art.hubY());
    }

    /**
     * The veiled one's hood, turned toward the player at the very end of the reach: each pixel of it drawn out of the wall along the way to the player's eyes,
     * the dark under it drawn out less, so the hood stands out round a hollow that looks back down the player's own line of sight.
     */
    private static void drawHood(Out out, MosaicArt art, MosaicWall wall, float reach, double[] viewer, int[] light)
    {
        float turn = MosaicWake.ease(reach, HOOD_FROM, 1);
        if (turn <= 0)
            return;
        double du = viewer[0] - (art.veiledX() + 0.5);
        double dv = viewer[1] - (art.veiledHead() + 0.5);
        double dh = viewer[2];
        double length = Math.sqrt(du * du + dv * dv + dh * dh);
        if (dh <= 0 || length < 1E-3)
            return;
        du /= length;
        dv /= length;
        dh /= length;

        int[] wear = wall.wear();
        var depth = new float[MosaicArt.WIDTH * MosaicArt.HEIGHT];
        for (int index : art.hoodPixels())
        {
            if (MosaicPicture.shows(art, wear, index))
                depth[index] = HOOD_DEPTH * turn * (art.part(index) == MosaicArt.HOOD_PART && art.region(index) == MosaicArt.VEIL ? HOOD_HOLLOW : 1);
        }
        for (int index : art.hoodPixels())
        {
            float own = depth[index];
            if (own <= 0)
                continue;
            int x = index % MosaicArt.WIDTH;
            int y = index / MosaicArt.WIDTH;
            int lit = light[MosaicArt.tileOf(index)];
            int tile = MosaicArt.tileOf(index);
            float u = (x + 0.5F) / TEXTURE_SIZE;
            float v = (y + (wear[tile] == MosaicWall.CHIPPED ? MosaicArt.HEIGHT : 0) + 0.5F) / TEXTURE_SIZE;
            out.drawnFront(x, y, own, du, dv, dh, FLAT_LIFT, x / TEXTURE_SIZE, v - 0.5F / TEXTURE_SIZE, (x + 1) / TEXTURE_SIZE, v + 0.5F / TEXTURE_SIZE, lit);
            int[][] around = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            Direction[] toward = {out.right.getOpposite(), out.right, Direction.UP, Direction.DOWN};
            for (int side = 0; side < 4; side++)
            {
                int nx = x + around[side][0];
                int ny = y + around[side][1];
                float other = nx < 0 || ny < 0 || nx >= MosaicArt.WIDTH || ny >= MosaicArt.HEIGHT ? 0 : depth[MosaicArt.index(nx, ny)];
                if (other < own - SIDE_EPSILON)
                    out.drawnSide(x, y, side, other, own, du, dv, dh, FLAT_LIFT, toward[side], u, v, lit);
            }
        }
    }

    /** Light coordinates raised so a thing lights itself by a share of full brightness, as when it glows. */
    private static int glowing(int light, float amount)
    {
        int own = Math.round(Mth.clamp(amount, 0, 1) * 15);
        return LightCoordsUtil.pack(Math.max(LightCoordsUtil.block(light), own), Math.max(LightCoordsUtil.sky(light), own));
    }

    private static int shade(int rgb, float shade)
    {
        return ARGB.color(255, Math.round((rgb >> 16 & 0xFF) * shade), Math.round((rgb >> 8 & 0xFF) * shade), Math.round((rgb & 0xFF) * shade));
    }

    private static int lerpColour(int from, int to, float t)
    {
        return Math.round(Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF)) << 16 | Math.round(Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF)) << 8
                | Math.round(Mth.lerp(t, from & 0xFF, to & 0xFF));
    }

    /** Lays quads given in the picture's pixels, or in the world, relative to the camera, through four reused corners. */
    private static final class Out
    {
        final PoseStack.Pose pose;
        final VertexConsumer buffer;
        final Direction facing;
        final Direction right;
        final Vec3 camera;
        final Vec3 corner;
        final CardinalLighting lighting;
        final float frontShade;
        private final double[][] corners = new double[4][3];

        Out(PoseStack.Pose pose, VertexConsumer buffer, MosaicWall wall, Vec3 camera, CardinalLighting lighting)
        {
            this.pose = pose;
            this.buffer = buffer;
            this.camera = camera;
            this.lighting = lighting;
            facing = wall.facing();
            right = wall.right();
            frontShade = lighting.byFace(facing);
            corner = wall.corner().subtract(camera);
        }

        private void picture(int corner, double u, double v, double h)
        {
            var at = corners[corner];
            at[0] = this.corner.x + (right.getStepX() * u + facing.getStepX() * h) / 16;
            at[1] = this.corner.y - v / 16;
            at[2] = this.corner.z + (right.getStepZ() * u + facing.getStepZ() * h) / 16;
        }

        private void world(int corner, Vec3 at)
        {
            corners[corner][0] = at.x;
            corners[corner][1] = at.y;
            corners[corner][2] = at.z;
        }

        /** The face of a run of pixels along a row, out of the wall by its height, its texture from one corner to the other. */
        void front(int fromX, int toX, int y, float height, float u0, float v0, float u1, float v1, int colour, int light)
        {
            picture(0, fromX, y, height);
            picture(1, fromX, y + 1, height);
            picture(2, toX, y + 1, height);
            picture(3, toX, y, height);
            quad(facing.getStepX(), 0, facing.getStepZ(), u0, v0, u1, v1, colour, light);
        }

        /** A side of a pixel between two of its corners, from one height out of the wall to another, in one texel's colour shaded as the world shades it. */
        void side(double u0, double v0, double u1, double v1, float low, float high, Direction toward, float u, float v, int colour, int light)
        {
            picture(0, u0, v0, low);
            picture(1, u1, v1, low);
            picture(2, u1, v1, high);
            picture(3, u0, v0, high);
            quad(toward.getStepX(), toward.getStepY(), toward.getStepZ(), u, v, u, v, shade(colour, lighting.byFace(toward)), light);
        }

        /**
         * A square box along a direction in the world. Its four long faces show the texture from one corner to the other, across its width and along its
         * length, and its ends one texel.
         */
        void box(Vec3 from, Vec3 forward, double length, double width, float u0, float v0, float u1, float v1, float capU, float capV, int light)
        {
            var reference = Math.abs(forward.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            var across = reference.cross(forward).normalize().scale(width / 2);
            var upward = forward.cross(across.normalize()).scale(width / 2);
            var start = from.subtract(camera);
            var end = start.add(forward.scale(length));
            for (int sign = -1; sign <= 1; sign += 2)
            {
                // The two sides across it, the two above and under it, and its two ends
                var side = across.scale(sign);
                boxFace(start.add(side).subtract(upward), end.add(side).subtract(upward), end.add(side).add(upward), start.add(side).add(upward), side, u0, v0, u1, v1, light);
                var lid = upward.scale(sign);
                boxFace(start.add(lid).subtract(across), end.add(lid).subtract(across), end.add(lid).add(across), start.add(lid).add(across), lid, u0, v0, u1, v1, light);
                var cap = sign > 0 ? end : start;
                boxFace(cap.subtract(across).subtract(upward), cap.add(across).subtract(upward), cap.add(across).add(upward), cap.subtract(across).add(upward),
                        forward.scale(sign), capU, capV, capU, capV, light);
            }
        }

        /**
         * An upright panel in the world facing a direction, its front showing the texture from one corner to the other as seen from where it faces, its back
         * and edges one texel.
         */
        void panel(Vec3 centre, Vec3 facing, double width, double height, double depth, float u0, float v0, float u1, float v1, float edgeU, float edgeV, int light)
        {
            var level = new Vec3(0, 1, 0).subtract(facing.scale(facing.y));
            var up = (level.lengthSqr() > 1E-6 ? level.normalize() : new Vec3(0, 0, 1)).scale(height / 2);
            var across = up.normalize().cross(facing).scale(width / 2);
            var front = centre.subtract(camera).add(facing.scale(depth / 2));
            var back = centre.subtract(camera).subtract(facing.scale(depth / 2));
            boxFace(front.subtract(across).add(up), front.subtract(across).subtract(up), front.add(across).subtract(up), front.add(across).add(up), facing, u0, v0, u1, v1,
                    light);
            boxFace(back.subtract(across).add(up), back.subtract(across).subtract(up), back.add(across).subtract(up), back.add(across).add(up), facing.reverse(), edgeU,
                    edgeV, edgeU, edgeV, light);
            for (int sign = -1; sign <= 1; sign += 2)
            {
                var side = across.scale(sign);
                boxFace(back.add(side).add(up), front.add(side).add(up), front.add(side).subtract(up), back.add(side).subtract(up), side, edgeU, edgeV, edgeU, edgeV, light);
                var lid = up.scale(sign);
                boxFace(back.add(lid).subtract(across), front.add(lid).subtract(across), front.add(lid).add(across), back.add(lid).add(across), lid, edgeU, edgeV, edgeU,
                        edgeV, light);
            }
        }

        /** The face of a pixel drawn out of the wall along a direction given in the picture's pixels, from a base a little off the wall. */
        void drawnFront(int x, int y, float length, double du, double dv, double dh, float base, float u0, float v0, float u1, float v1, int light)
        {
            picture(0, x + du * length, y + dv * length, base + dh * length);
            picture(1, x + du * length, y + 1 + dv * length, base + dh * length);
            picture(2, x + 1 + du * length, y + 1 + dv * length, base + dh * length);
            picture(3, x + 1 + du * length, y + dv * length, base + dh * length);
            quad(facing.getStepX(), 0, facing.getStepZ(), u0, v0, u1, v1, shade(0xFFFFFF, frontShade), light);
        }

        /** A side of a pixel drawn out of the wall, left, right, upper or lower by {@code side}, between two lengths along the direction. */
        void drawnSide(int x, int y, int side, float low, float high, double du, double dv, double dh, float base, Direction toward, float u, float v, int light)
        {
            double u0 = side == 1 ? x + 1 : x, v0 = side == 3 ? y + 1 : y;
            double u1 = side == 0 ? x : x + 1, v1 = side == 2 ? y : y + 1;
            picture(0, u0 + du * low, v0 + dv * low, base + dh * low);
            picture(1, u1 + du * low, v1 + dv * low, base + dh * low);
            picture(2, u1 + du * high, v1 + dv * high, base + dh * high);
            picture(3, u0 + du * high, v0 + dv * high, base + dh * high);
            quad(toward.getStepX(), toward.getStepY(), toward.getStepZ(), u, v, u, v, shade(0xFFFFFF, lighting.byFace(toward)), light);
        }

        /** A face of a box, its first two corners along the box so the texture's rows run along it. */
        private void boxFace(Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, float u0, float v0, float u1, float v1, int light)
        {
            world(0, a);
            world(1, b);
            world(2, c);
            world(3, d);
            quad(normal.x, normal.y, normal.z, u0, v0, u1, v1, shade(0xFFFFFF, shadeFor(normal.normalize())), light);
        }

        /** How the world would shade a face turned this way, blending the shades of the faces it lies between. */
        private float shadeFor(Vec3 normal)
        {
            double x = normal.x * normal.x, y = normal.y * normal.y, z = normal.z * normal.z;
            return (float) (x * lighting.byFace(normal.x > 0 ? Direction.EAST : Direction.WEST)
                    + y * lighting.byFace(normal.y > 0 ? Direction.UP : Direction.DOWN)
                    + z * lighting.byFace(normal.z > 0 ? Direction.SOUTH : Direction.NORTH));
        }

        /**
         * The four corners set last, laid counter-clockwise as seen from the side the normal points to, whichever way they were given. The first corner takes
         * the texture's first corner, the third its opposite one.
         */
        private void quad(double nx, double ny, double nz, float u0, float v0, float u1, float v1, int colour, int light)
        {
            var a = corners[0];
            var b = corners[1];
            var c = corners[2];
            double e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2];
            double e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
            double facing = (e1y * e2z - e1z * e2y) * nx + (e1z * e2x - e1x * e2z) * ny + (e1x * e2y - e1y * e2x) * nz;
            if (facing >= 0)
            {
                vertex(0, u0, v0, colour, light);
                vertex(1, u0, v1, colour, light);
                vertex(2, u1, v1, colour, light);
                vertex(3, u1, v0, colour, light);
            }
            else
            {
                vertex(3, u1, v0, colour, light);
                vertex(2, u1, v1, colour, light);
                vertex(1, u0, v1, colour, light);
                vertex(0, u0, v0, colour, light);
            }
        }

        private void vertex(int corner, float u, float v, int colour, int light)
        {
            var at = corners[corner];
            buffer.addVertex(pose, (float) at[0], (float) at[1], (float) at[2])
                    .setColor(colour)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0, 1, 0);
        }
    }
}
