package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.voxelindustry.voidheart.client.render.state.PermeableBarrierRenderState;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Opening and closing animation. Closing, the barrier assembles itself: mosaic tiles snap in one after the other,
 * spreading from a random seed point on each block until the fronts merge, each set in a vibrant green gasket
 * that fades once laid. Opening plays it backward, tiles flaring their gasket before falling away toward the seeds.
 */
public class BarrierMosaicRender
{
    private static final Identifier GASKET_TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/block/permeable_barrier_gasket.png");

    private static final int   TILES           = 8;
    private static final float SPREAD_SPAN     = 0.7F;
    private static final float JITTER          = 0.08F;
    private static final float FADE_IN         = 0.04F;
    private static final float GASKET_DURATION = 0.25F;
    private static final int   GASKET_COLOR    = ARGB.color(170, 60, 255, 110);
    private static final float TILE_INFLATE    = 0.001F;
    private static final float GASKET_INFLATE  = 0.003F;
    // Farthest two points on the surface of a unit cube are opposite corners
    private static final float MAX_DISTANCE    = (float) Math.sqrt(3);

    /**
     * Picks a random exposed spot on this barrier and each barrier around it, re-rolled on every flip.
     * Spreading from all of them lets the fronts of neighboring blocks meet and merge across block edges.
     */
    public static void collectSeedPoints(BlockGetter level, BlockPos pos, PermeableBarrierRenderState state)
    {
        state.seedPoints.clear();
        long ownFlip = BarrierTransitionTracker.flipTick(pos).orElse(0);

        for (var neighbor : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)))
        {
            if (!level.getBlockState(neighbor).is(VoidHeartBlocks.PERMEABLE_BARRIER))
                continue;

            long seed = neighbor.asLong() * 31 + BarrierTransitionTracker.flipTick(neighbor).orElse(ownFlip);
            var exposed = new ArrayList<Direction>(6);
            for (var direction : Direction.values())
            {
                if (!level.getBlockState(neighbor.relative(direction)).is(VoidHeartBlocks.PERMEABLE_BARRIER))
                    exposed.add(direction);
            }
            if (exposed.isEmpty())
                continue;

            var face = exposed.get((int) (hash(seed, Direction.DOWN, 7, 7) * exposed.size()));
            var point = surfacePoint(face, hash(seed, Direction.UP, 3, 1), hash(seed, Direction.NORTH, 1, 3));
            point[0] += neighbor.getX() - pos.getX();
            point[1] += neighbor.getY() - pos.getY();
            point[2] += neighbor.getZ() - pos.getZ();
            state.seedPoints.add(point);
        }
    }

    public static void submitMosaic(PermeableBarrierRenderState state, PoseStack poseStack, SubmitNodeCollector collector, boolean closing)
    {
        var atlasSprite = firstSprite(state);
        if (atlasSprite == null || state.seedPoints.isEmpty())
            return;

        var seedPoints = state.seedPoints;

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(atlasSprite.atlasLocation()), (pose, buffer) ->
                forEachTile(state, seedPoints, closing, (direction, u0, v0, u1, v1, opacity, gasket) ->
                        tile(pose, buffer, state, direction, u0, v0, u1, v1, opacity)));

        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(GASKET_TEXTURE), (pose, buffer) ->
                forEachTile(state, seedPoints, closing, (direction, u0, v0, u1, v1, opacity, gasket) ->
                {
                    int alpha = (int) (gasket * ARGB.alpha(GASKET_COLOR));
                    if (alpha > 0)
                        gasket(pose, buffer, direction, u0, v0, u1, v1, ARGB.color(alpha, GASKET_COLOR));
                }));
    }

    @FunctionalInterface
    private interface TileConsumer
    {
        void accept(Direction direction, float u0, float v0, float u1, float v1, float opacity, float gasket);
    }

    /**
     * Walks every laid tile of the visible faces, with its opacity and gasket glow from 0 to 1.
     */
    private static void forEachTile(PermeableBarrierRenderState state, List<float[]> seedPoints, boolean closing, TileConsumer consumer)
    {
        float cell = 1F / TILES;
        float progress = state.transition;
        for (var direction : Direction.values())
        {
            if (state.culledFaces[direction.ordinal()] || state.faceSprites[direction.ordinal()] == null)
                continue;

            for (int i = 0; i < TILES; i++)
            {
                for (int j = 0; j < TILES; j++)
                {
                    float u0 = i * cell;
                    float v0 = j * cell;
                    float distance = Math.min(1, nearestDistance(seedPoints, surfacePoint(direction, u0 + cell / 2, v0 + cell / 2)) / MAX_DISTANCE);
                    float spread = Mth.clamp(distance + (hash(state.seed, direction, i, j) - 0.5F) * JITTER, 0, 1) * SPREAD_SPAN;

                    float opacity;
                    float gasket;
                    if (closing)
                    {
                        // Laid when the spread reaches it, the gasket fading afterward
                        float sinceLaid = progress - spread;
                        if (sinceLaid < 0)
                            continue;
                        opacity = Math.min(1, sinceLaid / FADE_IN);
                        gasket = Math.max(0, 1 - sinceLaid / GASKET_DURATION);
                    }
                    else
                    {
                        // Farthest tiles fall first, flaring their gasket just before
                        float removedAt = SPREAD_SPAN - spread + FADE_IN;
                        float untilRemoved = removedAt - progress;
                        if (untilRemoved <= 0)
                            continue;
                        opacity = Math.min(1, untilRemoved / FADE_IN);
                        gasket = Math.max(0, 1 - untilRemoved / GASKET_DURATION);
                    }
                    consumer.accept(direction, u0, v0, u0 + cell, v0 + cell, opacity, gasket);
                }
            }
        }
    }

    private static float nearestDistance(List<float[]> seedPoints, float[] point)
    {
        float nearest = Float.MAX_VALUE;
        for (var seedPoint : seedPoints)
            nearest = Math.min(nearest, distance(seedPoint, point));
        return nearest;
    }

    private static float[] surfacePoint(Direction direction, float u, float v)
    {
        var corners = GlowCube.faceCorners(direction, 0, 0, 0, 0);
        float[] point = new float[3];
        for (int axis = 0; axis < 3; axis++)
            point[axis] = corners[0][axis] + u * (corners[3][axis] - corners[0][axis]) + v * (corners[1][axis] - corners[0][axis]);
        return point;
    }

    private static float distance(float[] a, float[] b)
    {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        float dz = a[2] - b[2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * @return a stable pseudo-random value from 0 to 1
     */
    private static float hash(long seed, Direction direction, int i, int j)
    {
        long hash = (seed * 31 + direction.ordinal()) * 31 + i * TILES + j;
        hash *= 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 29;
        hash *= 0xBF58476D1CE4E5B9L;
        return (hash >>> 40 & 0xFFFF) / 65536F;
    }

    private static void tile(PoseStack.Pose pose, VertexConsumer buffer, PermeableBarrierRenderState state, Direction direction, float u0, float v0, float u1, float v1, float opacity)
    {
        var sprite = state.faceSprites[direction.ordinal()];
        int brightness = (int) (state.faceShade[direction.ordinal()] * 255);
        int color = ARGB.color((int) (opacity * 255), brightness, brightness, brightness);
        int light = state.faceLight[direction.ordinal()];

        quad(pose, buffer, direction, TILE_INFLATE, u0, v0, u1, v1, (u, v) -> sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u,
                (u, v) -> sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v, color, light);
    }

    private static void gasket(PoseStack.Pose pose, VertexConsumer buffer, Direction direction, float u0, float v0, float u1, float v1, int color)
    {
        // The gasket texture spans the whole tile, its one-texel edge becoming a thin line around it
        quad(pose, buffer, direction, GASKET_INFLATE, u0, v0, u1, v1, (u, v) -> (u - u0) / (u1 - u0), (u, v) -> (v - v0) / (v1 - v0), color, GlowCube.FULL_BRIGHT);
    }

    @FunctionalInterface
    private interface TexCoord
    {
        float apply(float u, float v);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Direction direction, float inflate, float u0, float v0, float u1, float v1,
                             TexCoord texU, TexCoord texV, int color, int light)
    {
        var corners = GlowCube.faceCorners(direction, 0, 0, 0, inflate);
        var normal = direction.getUnitVec3i();
        float[][] faceUVs = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};

        for (var faceUV : faceUVs)
        {
            float u = faceUV[0];
            float v = faceUV[1];
            // Bilinear position on the face: corner 0 is (0,0), corner 3 is (1,0) and corner 1 is (0,1)
            float x = corners[0][0] + u * (corners[3][0] - corners[0][0]) + v * (corners[1][0] - corners[0][0]);
            float y = corners[0][1] + u * (corners[3][1] - corners[0][1]) + v * (corners[1][1] - corners[0][1]);
            float z = corners[0][2] + u * (corners[3][2] - corners[0][2]) + v * (corners[1][2] - corners[0][2]);
            GlowCube.vertex(pose, buffer, x, y, z, texU.apply(u, v), texV.apply(u, v), color, light, normal.getX(), normal.getY(), normal.getZ());
        }
    }

    private static @Nullable TextureAtlasSprite firstSprite(PermeableBarrierRenderState state)
    {
        for (var sprite : state.faceSprites)
        {
            if (sprite != null)
                return sprite;
        }
        return null;
    }
}
