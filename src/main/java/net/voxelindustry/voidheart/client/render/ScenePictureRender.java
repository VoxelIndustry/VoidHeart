package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;
import net.voxelindustry.voidheart.client.scene.Scene;
import net.voxelindustry.voidheart.client.scene.SceneFrame;
import net.voxelindustry.voidheart.client.scene.SceneView;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scene's stage: real block models and the forge's and altar's real effects, drawn to a texture without any world behind them.
 * <p>
 * Render types: settled blocks go through cutoutMovingBlock so they hide each other with depth, and only blocks arriving or ghosted are translucent.
 * Highlights use GLOW, whose additive overlaps cannot flicker. The stage is redrawn every frame, at most a few hundred models while a scene is open.
 */
public class ScenePictureRender extends PictureInPictureRenderer<ScenePictureRender.State>
{
    private static final int   FULL_BRIGHT    = 15728880;
    private static final int   GHOST_ALPHA    = 150;
    private static final int   GHOST_GLOW     = 60;
    private static final float DROP_HEIGHT    = 0.45F;
    private static final float HIGHLIGHT_LIFT = 0.012F;
    private static final float ITEM_SCALE     = 2.2F;
    // The texture's depth only reaches 1000 pixels either way, which a large screen's blocks could outgrow
    private static final float DEPTH_REACH    = 900;
    private static final float DEPTH_BLOCKS   = 22;

    private static final float SHADE_UP    = 1;
    private static final float SHADE_DOWN  = 0.5F;
    private static final float SHADE_Z     = 0.8F;
    private static final float SHADE_X     = 0.62F;

    /**
     * @param blockSize  how many screen units one block takes
     * @param worldTime  the time things that are only alive follow, such as the beam's twist
     * @param glowRgb    the color of highlights
     */
    public record State(Scene scene, SceneFrame frame, SceneView view, float blockSize, float worldTime, int glowRgb, int x0, int y0, int x1, int y1,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState
    {
        public State(Scene scene, SceneFrame frame, SceneView view, float blockSize, float worldTime, int glowRgb, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea)
        {
            this(scene, frame, view, blockSize, worldTime, glowRgb, x0, y0, x1, y1, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
        }

        @Override
        public float scale()
        {
            return blockSize;
        }
    }

    private final Map<ItemStack, ItemStackRenderState> items = new HashMap<>();

    private final ShatterForgeRenderState beam   = new ShatterForgeRenderState();
    private final VoidAltarRenderState    altar  = new VoidAltarRenderState();

    @Override
    public Class<State> getRenderStateClass()
    {
        return State.class;
    }

    @Override
    protected String getTextureLabel()
    {
        return "voidheart scene";
    }

    @Override
    protected float getTranslateY(int height, int guiScale)
    {
        return height / 2F;
    }

    @Override
    protected void renderToTexture(State state, PoseStack poseStack, SubmitNodeCollector collector)
    {
        var minecraft = Minecraft.getInstance();
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);

        float pixels = state.blockSize() * minecraft.getWindow().getGuiScale();
        poseStack.scale(1, 1, Math.min(1, DEPTH_REACH / (pixels * DEPTH_BLOCKS)));
        poseStack.mulPose(state.view().matrix());

        submitBlocks(poseStack, collector, state.scene().floor().stream().map(place -> new SceneFrame.ShownBlock(place.pos(), place.state(), 1)).toList());
        submitBlocks(poseStack, collector, state.frame().blocks());
        submitGhosts(poseStack, collector, state.frame().ghosts(), state.glowRgb());
        submitHighlights(poseStack, collector, state);
        submitItems(poseStack, collector, state);
        submitEffects(poseStack, collector, state);
    }

    private static int shaded(Direction direction, int alpha)
    {
        float shade = switch (direction)
        {
            case UP -> SHADE_UP;
            case DOWN -> SHADE_DOWN;
            case NORTH, SOUTH -> SHADE_Z;
            case EAST, WEST -> SHADE_X;
        };
        return ARGB.color(alpha, (int) (255 * shade), (int) (255 * shade), (int) (255 * shade));
    }

    private static void putModel(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, BlockState state, QuadInstance instance, int alpha)
    {
        for (var quad : ForgePreviewRender.quadsOf(state))
        {
            instance.setColor(shaded(quad.direction(), alpha));
            buffer.putBakedQuad(pose, quad, instance);
        }
    }

    private static QuadInstance instance()
    {
        var instance = new QuadInstance();
        instance.setLightCoords(FULL_BRIGHT);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        return instance;
    }

    private static void submitBlocks(PoseStack poseStack, SubmitNodeCollector collector, List<SceneFrame.ShownBlock> blocks)
    {
        var instance = instance();
        collector.submitCustomGeometry(poseStack, RenderTypes.cutoutMovingBlock(), (pose, buffer) ->
        {
            var local = new PoseStack();
            local.last().set(pose);
            for (var block : blocks)
            {
                if (block.appear() < 1)
                    continue;
                local.pushPose();
                local.translate(block.pos().getX(), block.pos().getY(), block.pos().getZ());
                putModel(local.last(), buffer, block.state(), instance, 255);
                local.popPose();
            }
        });
        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
        {
            var local = new PoseStack();
            local.last().set(pose);
            for (var block : blocks)
            {
                if (block.appear() >= 1)
                    continue;
                float eased = 1 - (1 - block.appear()) * (1 - block.appear()) * (1 - block.appear());
                local.pushPose();
                local.translate(block.pos().getX(), block.pos().getY() + DROP_HEIGHT * (1 - eased), block.pos().getZ());
                putModel(local.last(), buffer, block.state(), instance, (int) (255 * eased));
                local.popPose();
            }
        });
    }

    /**
     * Shrunk the way the forge's own preview shrinks full blocks, so that what the scene shows is what the player will see.
     * The world's light lifts those ghosts off the ground in play. Here a glow does, since dark stone would otherwise vanish against a dark floor.
     */
    private static void submitGhosts(PoseStack poseStack, SubmitNodeCollector collector, List<SceneFrame.ShownBlock> ghosts, int glowRgb)
    {
        if (ghosts.isEmpty())
            return;

        var instance = instance();
        collector.submitCustomGeometry(poseStack, RenderTypes.translucentMovingBlock(), (pose, buffer) ->
        {
            var local = new PoseStack();
            local.last().set(pose);
            for (var ghost : ghosts)
            {
                local.pushPose();
                local.translate(ghost.pos().getX(), ghost.pos().getY(), ghost.pos().getZ());
                if (ghost.state().isSolidRender())
                {
                    local.translate(0.25F, 0.25F, 0.25F);
                    local.scale(0.5F, 0.5F, 0.5F);
                }
                putModel(local.last(), buffer, ghost.state(), instance, (int) (GHOST_ALPHA * ghost.appear()));
                local.popPose();
            }
        });
        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var ghost : ghosts)
            {
                int color = ARGB.color((int) (GHOST_GLOW * ghost.appear()), glowRgb);
                float inflate = ghost.state().isSolidRender() ? -0.24F : 0.01F;
                for (var direction : Direction.values())
                {
                    for (var corner : GlowCube.faceCorners(direction, ghost.pos().getX(), ghost.pos().getY(), ghost.pos().getZ(), inflate))
                        buffer.addVertex(pose, corner[0], corner[1], corner[2]).setColor(color);
                }
            }
        });
    }

    private static void submitHighlights(PoseStack poseStack, SubmitNodeCollector collector, State state)
    {
        if (state.frame().highlights().isEmpty())
            return;

        collector.submitCustomGeometry(poseStack, VoidHeartRenderTypes.GLOW, (pose, buffer) ->
        {
            for (var highlight : state.frame().highlights())
            {
                int color = ARGB.color((int) (90 * highlight.strength()), state.glowRgb());
                for (var direction : Direction.values())
                {
                    for (var corner : GlowCube.faceCorners(direction, highlight.pos().getX(), highlight.pos().getY(), highlight.pos().getZ(), HIGHLIGHT_LIFT))
                        buffer.addVertex(pose, corner[0], corner[1], corner[2]).setColor(color);
                }
            }
        });
    }

    private void submitItems(PoseStack poseStack, SubmitNodeCollector collector, State state)
    {
        var minecraft = Minecraft.getInstance();
        for (var item : state.frame().items())
        {
            var render = items.computeIfAbsent(item.stack(), stack ->
            {
                var created = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(created, stack, ItemDisplayContext.GROUND, minecraft.level, null, 0);
                return created;
            });

            poseStack.pushPose();
            poseStack.translate(item.x(), item.y(), item.z());
            // Facing the viewer whichever way the camera turned, since a flat item seen edge on teaches nothing
            poseStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(-state.view().yaw()));
            poseStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(state.view().pitch()));
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            render.submit(poseStack, collector, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }

    private void submitEffects(PoseStack poseStack, SubmitNodeCollector collector, State state)
    {
        var frame = state.frame();
        var scene = state.scene();

        if (scene.beam() != null && frame.beam() > 0)
        {
            beam.worldTime = state.worldTime();
            VoidBeamRender.extractScene(scene.beam().view(), scene.beam().segments(), frame.forming(), frame.beam(), beam);
            VoidBeamRender.submit(beam, poseStack, collector);
        }

        if (frame.warm() > 0)
        {
            altar.worldTime = state.worldTime();
            altar.warmProgress = frame.warm();
            altar.coolProgress = frame.cool();
            altar.cameraDistanceSqr = 0;
            CraftingVoidRender.submitVoidCube(altar, poseStack, collector);
        }

        var rings = frame.rings();
        if (rings != null && scene.rings() != null)
        {
            var core = scene.rings().core();
            // The viewer looks along the stage's +Z, so the camera lies back along -Z once the view's turn is undone
            var look = new org.joml.Matrix4f(state.view().matrix()).invert().transformDirection(new Vector3f(0, 0, -1));
            poseStack.pushPose();
            poseStack.translate(core.getX(), core.getY(), core.getZ());
            ArmillaryRender.submitRings(poseStack, collector, rings.formation(), rings.angles(), rings.falter(), rings.warmth(), state.worldTime(), FULL_BRIGHT,
                    rings.portal(), scene.rings().portal(), new Vec3(look.x, look.y, look.z));
            poseStack.popPose();
        }
    }
}
