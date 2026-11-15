package net.voxelindustry.voidheart.client.secondheart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MeshView;
import net.fabricmc.fabric.api.client.rendering.v1.SubmitRenderPhase;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Passes a player's submits on, with every textured model, model part, item and custom geometry swapped to the second heart's dissolve for the
 * texture it draws. What has no texture to dissolve, glints and outlines, is left out while the body is coming apart.
 */
class DissolvingCollector implements SubmitNodeCollector
{
    private final SubmitNodeCollector        root;
    private final OrderedSubmitNodeCollector delegate;
    private final int                        slot;

    DissolvingCollector(SubmitNodeCollector root, int slot)
    {
        this(root, root, slot);
    }

    private DissolvingCollector(SubmitNodeCollector root, OrderedSubmitNodeCollector delegate, int slot)
    {
        this.root = root;
        this.delegate = delegate;
        this.slot = slot;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order)
    {
        return new DissolvingCollector(root, root.order(order), slot);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
                                int tintedColor, @Nullable UvMapping uvMapping, int outlineColor)
    {
        var dissolve = HeartDissolve.dissolveType(renderType, slot);
        if (dissolve != null)
            delegate.submitModel(model, state, poseStack, dissolve, lightCoords, overlayCoords, tintedColor, uvMapping, 0);
    }

    @Override
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer renderer)
    {
        var dissolve = HeartDissolve.dissolveType(renderType, slot);
        if (dissolve != null)
            delegate.submitCustomGeometry(poseStack, dissolve, renderer);
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
                           int[] tintLayers, ItemQuads quads, ItemStackRenderState.FoilType foilType)
    {
        var byType = new LinkedHashMap<RenderType, List<BakedQuad>>();
        for (var quad : quads.all())
        {
            var dissolve = HeartDissolve.dissolveType(quad.materialInfo().itemRenderType(), slot);
            if (dissolve != null)
                byType.computeIfAbsent(dissolve, key -> new ArrayList<>()).add(quad);
        }
        for (var entry : byType.entrySet())
        {
            var group = entry.getValue();
            delegate.submitCustomGeometry(poseStack, entry.getKey(), (pose, buffer) ->
            {
                var instance = new QuadInstance();
                instance.setLightCoords(lightCoords);
                instance.setOverlayCoords(overlayCoords);
                for (var quad : group)
                {
                    var material = quad.materialInfo();
                    int tint = material.isTinted() && material.tintIndex() >= 0 && material.tintIndex() < tintLayers.length ? tintLayers[material.tintIndex()] : -1;
                    instance.setColor(tint);
                    buffer.putBakedQuad(pose, quad, instance);
                }
            });
        }
    }

    @Override
    public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords, int outlineColor,
                           int[] tintLayers, ItemQuads quads, MeshView mesh, ItemStackRenderState.FoilType foilType)
    {
        submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
    }

    @Override
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
                                           int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay)
    {
        delegate.submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, crumblingOverlay);
    }

    @Override
    public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces)
    {
        delegate.submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack poseStack, @Nullable Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords,
                              CameraRenderState camera)
    {
        delegate.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
    }

    @Override
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
                           int lightCoords, int color, int backgroundColor, int outlineColor)
    {
        delegate.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
    }

    @Override
    public void submitTextBackground(PoseStack poseStack, float x0, float y0, float x1, float y1, int color, Font.DisplayMode displayMode, int lightCoords)
    {
        delegate.submitTextBackground(poseStack, x0, y0, x1, y1, color, displayMode, lightCoords);
    }

    @Override
    public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation)
    {
        delegate.submitFlame(poseStack, renderState, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState)
    {
        delegate.submitLeash(poseStack, leashState);
    }

    @Override
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor)
    {
        delegate.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
    }

    @Override
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords,
                                 int overlayCoords, int outlineColor)
    {
        delegate.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent)
    {
        delegate.submitBreakingBlockModel(poseStack, parts, progress, isBlockTranslucent);
    }

    @Override
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain)
    {
        delegate.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles)
    {
        delegate.submitQuadParticleGroup(particles);
    }

    @Override
    public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop)
    {
        delegate.submitGizmoPrimitives(group, camera, onTop);
    }

    @Override
    public <T extends SubmitNode> void submitCustom(SubmitRenderPhase<T> phase, T node)
    {
        delegate.submitCustom(phase, node);
    }
}
