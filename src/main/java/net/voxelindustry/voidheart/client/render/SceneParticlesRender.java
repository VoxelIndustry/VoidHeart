package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.particle.ParticleSprites;
import net.voxelindustry.voidheart.client.scene.Scene;
import net.voxelindustry.voidheart.client.scene.SceneEmitter;
import net.voxelindustry.voidheart.client.scene.SceneFrame;
import net.voxelindustry.voidheart.client.scene.SceneView;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scene's particles: the pictures the game's own particles use, on squares that face the stage's camera.
 * <p>
 * Render type: entityTranslucentEmissive on the atlas the picture is in, so the stage's light leaves them alone. It blends, tests depth without writing it and
 * sorts back to front, so blocks hide particles behind them, particles never hide what is drawn after them, and two that overlap cannot flicker. No square
 * lies in the plane of a block's face or of another square. A scene lets out a few hundred particles at its busiest, four vertices each, rebuilt every frame
 * like the rest of the stage. Pictures are looked up every frame too, a map read each, so a reload of resources cannot leave a stale one behind.
 */
final class SceneParticlesRender
{
    private static final int FULL_BRIGHT = 15728880;

    // The shorter side of the stage, in the units the least size is given in: 1 is about a pixel of a small window
    private static final float SPAN_UNITS = 170;
    // Half the side a particle keeps however far the camera stands, so a wide view does not lose them between pixels
    private static final float LEAST_SIZE = 1.4F;

    // Items made of several pictures always crumble into the same one, so a held scene stays still
    private static final long CRUMB_SEED = 42;

    private record Square(TextureAtlasSprite sprite, float u0, float v0, float u1, float v1, SceneFrame.ShownParticle particle)
    {
    }

    private SceneParticlesRender()
    {
    }

    /**
     * @param theme the color of what names none of its own
     */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, List<SceneFrame.ShownParticle> particles, SceneView view, int theme)
    {
        if (particles.isEmpty())
            return;

        var minecraft = Minecraft.getInstance();
        var atlas = minecraft.getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
        var crumbs = new IdentityHashMap<ItemStack, TextureAtlasSprite>();

        var squares = new LinkedHashMap<Identifier, List<Square>>();
        for (var particle : particles)
        {
            var square = switch (particle.sprite())
            {
                case SceneEmitter.Frames frames ->
                {
                    var sprite = atlas.getSprite(frames.frames().get(particle.frame()));
                    yield new Square(sprite, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), particle);
                }
                case SceneEmitter.Crumbs crumb ->
                {
                    int slices = SceneEmitter.CRUMB_SLICES;
                    boolean became = particle.frame() >= slices * slices;
                    var stack = became && crumb.becomes() != null ? crumb.becomes() : crumb.stack();
                    var sprite = crumbs.computeIfAbsent(stack, key -> ParticleSprites.itemSprite(key, minecraft.level, RandomSource.create(CRUMB_SEED)));

                    int slice = particle.frame() % (slices * slices);
                    float u = (sprite.getU1() - sprite.getU0()) / slices;
                    float v = (sprite.getV1() - sprite.getV0()) / slices;
                    float u0 = sprite.getU0() + u * (slice % slices);
                    float v0 = sprite.getV0() + v * (slice / slices);
                    yield new Square(sprite, u0, v0, u0 + u, v0 + v, particle);
                }
            };
            squares.computeIfAbsent(square.sprite().atlasLocation(), key -> new ArrayList<>()).add(square);
        }

        // The stage has +X to its right and +Y down it, and its depth runs negative towards the viewer
        var inverse = new Matrix4f(view.matrix()).invert();
        var right = inverse.transformDirection(new Vector3f(1, 0, 0)).normalize();
        var up = inverse.transformDirection(new Vector3f(0, -1, 0)).normalize();
        var toViewer = inverse.transformDirection(new Vector3f(0, 0, -1)).normalize();
        float least = LEAST_SIZE * view.span() / SPAN_UNITS;

        squares.forEach((texture, drawn) -> collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(texture), (pose, buffer) ->
        {
            for (var square : drawn)
            {
                var particle = square.particle();
                float size = Math.max(particle.size(), least);
                int rgb = (particle.rgb() == Scene.THEME ? theme : particle.rgb()) & 0xFFFFFF;
                int color = ARGB.color((int) (255 * Math.clamp(particle.alpha(), 0, 1)), rgb);

                corner(pose, buffer, particle, right, up, toViewer, -size, -size, square.u0(), square.v1(), color);
                corner(pose, buffer, particle, right, up, toViewer, -size, size, square.u0(), square.v0(), color);
                corner(pose, buffer, particle, right, up, toViewer, size, size, square.u1(), square.v0(), color);
                corner(pose, buffer, particle, right, up, toViewer, size, -size, square.u1(), square.v1(), color);
            }
        }));
    }

    private static void corner(PoseStack.Pose pose, VertexConsumer buffer, SceneFrame.ShownParticle particle, Vector3f right, Vector3f up, Vector3f toViewer,
                               float across, float along, float u, float v, int color)
    {
        buffer.addVertex(pose,
                        (float) particle.x() + right.x * across + up.x * along,
                        (float) particle.y() + right.y * across + up.y * along,
                        (float) particle.z() + right.z * across + up.z * along)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, toViewer.x, toViewer.y, toViewer.z);
    }
}
