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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.client.render.state.ShatterForgeRenderState;
import net.voxelindustry.voidheart.client.render.state.VoidAltarRenderState;
import net.voxelindustry.voidheart.client.scene.Scene;
import net.voxelindustry.voidheart.client.scene.SceneFrame;
import net.voxelindustry.voidheart.client.scene.SceneView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A scene's stage: real block models and the forge's and altar's real effects, drawn to a texture without any world behind them.
 * <p>
 * Render types: settled blocks go through cutoutMovingBlock so they hide each other with depth, and only blocks arriving or ghosted are translucent.
 * Highlights use GLOW, whose additive overlaps cannot flicker. The stage is redrawn every frame: a face between two settled full blocks is never seen and is
 * left out, so a wide floor costs its top and its rim, a few thousand quads for the widest scene.
 */
public class ScenePictureRender extends PictureInPictureRenderer<ScenePictureRender.State>
{
    private static final int   FULL_BRIGHT    = 15728880;
    private static final int   GHOST_ALPHA    = 150;
    private static final int   GHOST_GLOW     = 60;
    private static final float DROP_HEIGHT    = 0.45F;
    private static final float HIGHLIGHT_LIFT = 0.012F;
    private static final float ITEM_SCALE     = 2.2F;
    private static final float BLOCK_SCALE    = 2.8F;
    // The texture's depth only reaches 1000 pixels either way, which a large screen's blocks could outgrow
    private static final float DEPTH_REACH    = 900;
    private static final float DEPTH_BLOCKS   = 22;
    // How far above what is under it an item still throws a shadow
    private static final float SHADOW_REACH   = 4;

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

    private final SceneActorsRender       actors = new SceneActorsRender();
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

        // A wide view holds more blocks in depth too, at fewer pixels each
        float pixels = state.blockSize() * minecraft.getWindow().getGuiScale();
        poseStack.scale(1, 1, Math.min(1, DEPTH_REACH / (pixels * Math.max(DEPTH_BLOCKS, state.view().span() * 1.5F))));
        poseStack.mulPose(state.view().matrix());

        var floor = state.scene().floor().stream().map(place -> new SceneFrame.ShownBlock(place.pos(), place.state(), 1)).toList();
        var solid = new HashSet<BlockPos>();
        for (var block : floor)
        {
            if (block.state().isSolidRender())
                solid.add(block.pos());
        }
        for (var block : state.frame().blocks())
        {
            if (block.appear() >= 1 && block.state().isSolidRender())
                solid.add(block.pos());
        }

        submitBlocks(poseStack, collector, floor, solid);
        submitBlocks(poseStack, collector, state.frame().blocks(), solid);
        submitGhosts(poseStack, collector, state.frame().ghosts(), state.glowRgb());
        submitHighlights(poseStack, collector, state);
        SceneMarksRender.submit(poseStack, collector, state.frame(), state.view(), shadows(state, floor), state.glowRgb(), state.worldTime());
        submitItems(poseStack, collector, state);
        actors.submit(poseStack, collector, state.frame().actors(), toViewer(state.view()), state.worldTime());
        submitEffects(poseStack, collector, state);
    }

    /**
     * The stage's depth runs negative towards the viewer.
     */
    static org.joml.Vector3f toViewer(SceneView view)
    {
        return new org.joml.Matrix4f(view.matrix()).invert().transformDirection(new org.joml.Vector3f(0, 0, -1)).normalize();
    }

    /**
     * Where each item's shadow falls: on the highest block under it, so that an item in the air can be told from one lying further back.
     */
    private static List<float[]> shadows(State state, List<SceneFrame.ShownBlock> floor)
    {
        if (state.frame().items().isEmpty())
            return List.of();

        var tops = new HashMap<Long, List<SceneFrame.ShownBlock>>();
        for (var blocks : List.of(floor, state.frame().blocks()))
        {
            for (var block : blocks)
                tops.computeIfAbsent(BlockPos.asLong(block.pos().getX(), 0, block.pos().getZ()), key -> new ArrayList<>()).add(block);
        }

        var shadows = new ArrayList<float[]>();
        for (var item : state.frame().items())
        {
            var under = tops.get(BlockPos.asLong((int) Math.floor(item.x()), 0, (int) Math.floor(item.z())));
            if (under == null)
                continue;

            double ground = Double.NEGATIVE_INFINITY;
            for (var block : under)
            {
                double top = block.pos().getY() + block.state().getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Direction.Axis.Y);
                if (top <= item.y() + 0.01 && top > ground)
                    ground = top;
            }
            if (item.y() - ground <= SHADOW_REACH)
                shadows.add(new float[] { (float) item.x(), (float) ground, (float) item.z() });
        }
        return shadows;
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
        putModel(pose, buffer, state, instance, alpha, 0);
    }

    /**
     * @param hidden the sides, as a mask of direction ordinals, where a full block stands against this full block
     */
    private static void putModel(PoseStack.Pose pose, com.mojang.blaze3d.vertex.VertexConsumer buffer, BlockState state, QuadInstance instance, int alpha, int hidden)
    {
        // What its own renderer draws has no model, and asking for one gives the missing model's checkered cube
        if (state.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL)
            return;

        for (var quad : ForgePreviewRender.quadsOf(state))
        {
            if ((hidden & 1 << quad.direction().ordinal()) != 0)
                continue;
            instance.setColor(shaded(quad.direction(), alpha));
            buffer.putBakedQuad(pose, quad, instance);
        }
    }

    private static int hiddenSides(SceneFrame.ShownBlock block, Set<BlockPos> solid)
    {
        if (!solid.contains(block.pos()))
            return 0;

        int hidden = 0;
        for (var direction : Direction.values())
        {
            if (solid.contains(block.pos().relative(direction)))
                hidden |= 1 << direction.ordinal();
        }
        return hidden;
    }

    private static QuadInstance instance()
    {
        var instance = new QuadInstance();
        instance.setLightCoords(FULL_BRIGHT);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        return instance;
    }

    private static void submitBlocks(PoseStack poseStack, SubmitNodeCollector collector, List<SceneFrame.ShownBlock> blocks, Set<BlockPos> solid)
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
                putModel(local.last(), buffer, block.state(), instance, 255, hiddenSides(block, solid));
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
            if (render.usesBlockLight())
            {
                // A block in the hand is a small block: it stands as it will once set down, where facing the viewer would flatten it to one of its sides
                poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
            }
            else
            {
                // Facing the viewer whichever way the camera turned, since a flat item seen edge on teaches nothing
                poseStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(-state.view().yaw()));
                poseStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(state.view().pitch()));
                poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            }
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
    }
}
