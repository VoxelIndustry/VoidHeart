package net.voxelindustry.voidheart.client.curse;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * Flesh crawls in from every edge of the afflicted player's screen, all of it on their HUD alone: a lumpy rim, jagged teeth along its lip,
 * tentacles reaching past them and odd eyes set in it, all wriggling, while a heart pounds faster and faster inside their head. When it ends
 * everything whips back out of sight.
 * <p>
 * Drawn as one GUI element of plain colored quads, rebuilt each frame into a buffer kept between frames, see {@link HymnMesh}.
 */
public final class HymnCurse extends ClientCurse
{
    private static final SoundSource HEART_SOURCE = SoundSource.HOSTILE;

    private @Nullable HymnLayout layout;
    private int                  layoutWidth;
    private int                  layoutHeight;

    private final QuadBuffer buffer = new QuadBuffer();
    private int              drawnLastFrame;

    public HymnCurse(long seed, int duration)
    {
        super(seed, duration);
    }

    @Override
    protected void tick(Minecraft minecraft, LocalPlayer player)
    {
        int beat = HymnTimeline.beatAt(age);
        if (beat >= 0)
            minecraft.getSoundManager().play(new SimpleSoundInstance(SoundEvents.WARDEN_HEARTBEAT.location(), HEART_SOURCE,
                    HymnTimeline.volume(beat), HymnTimeline.pitch(beat), random, false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    /**
     * Quads drawn on the last frame, for tests.
     */
    public int drawnLastFrame()
    {
        return drawnLastFrame;
    }

    @Override
    public void extractHud(GuiGraphicsExtractor graphics, float partialTicks)
    {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (layout == null || width != layoutWidth || height != layoutHeight)
        {
            layout = HymnLayout.of(seed, width, height);
            layoutWidth = width;
            layoutHeight = height;
        }

        buffer.clear();
        drawnLastFrame = HymnMesh.build(layout, width, height, age + partialTicks, buffer);
        if (drawnLastFrame == 0)
            return;
        // Above the hotbar and everything drawn before it
        graphics.nextStratum();
        graphics.guiRenderState.addGuiElement(new Flesh(buffer, new Matrix3x2f(graphics.pose()), new ScreenRectangle(0, 0, width, height)));
    }

    private record Flesh(QuadBuffer buffer, Matrix3x2fc pose, ScreenRectangle bounds) implements GuiElementRenderState
    {
        @Override
        public void buildVertices(VertexConsumer consumer)
        {
            buffer.emit(consumer, pose);
        }

        @Override
        public RenderPipeline pipeline()
        {
            return RenderPipelines.GUI;
        }

        @Override
        public TextureSetup textureSetup()
        {
            return TextureSetup.noTexture();
        }

        @Override
        public @Nullable ScreenRectangle scissorArea()
        {
            return null;
        }
    }

    /**
     * Kept and refilled each frame, as the element built from it is drawn within the same frame.
     */
    private static final class QuadBuffer implements HymnMesh.Quads
    {
        private float[] positions = new float[4096 * 8];
        private int[]   colors    = new int[4096 * 4];
        private int     vertices;

        void clear()
        {
            vertices = 0;
        }

        @Override
        public void quad(float x0, float y0, int c0, float x1, float y1, int c1, float x2, float y2, int c2, float x3, float y3, int c3)
        {
            if (colors.length < vertices + 4)
            {
                positions = Arrays.copyOf(positions, positions.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            put(x0, y0, c0);
            put(x1, y1, c1);
            put(x2, y2, c2);
            put(x3, y3, c3);
        }

        private void put(float x, float y, int color)
        {
            positions[vertices * 2] = x;
            positions[vertices * 2 + 1] = y;
            colors[vertices] = color;
            vertices++;
        }

        void emit(VertexConsumer consumer, Matrix3x2fc pose)
        {
            for (int vertex = 0; vertex < vertices; vertex++)
                consumer.addVertexWith2DPose(pose, positions[vertex * 2], positions[vertex * 2 + 1]).setColor(colors[vertex]);
        }
    }
}
