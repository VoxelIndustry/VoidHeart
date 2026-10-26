package net.voxelindustry.voidheart.client.render;

import com.geckolib.cache.BakedModelCache;
import com.geckolib.cache.GeckoLibResources;
import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Draws a GeckoLib block model in its rest pose with a tint, for translucent placement hints.
 * Bones are posed from their own pivot and base rotation only: the baked bones are shared with the live renderer
 * and still hold the last animation frame.
 */
public class StaticGeoGhostRender
{
    private static final int FULL_BRIGHT = 15728880;

    private final Identifier modelName;
    private final Identifier texture;

    private @Nullable BakedModelCache modelCache;
    private @Nullable BakedGeoModel   model;

    public StaticGeoGhostRender(Identifier modelName, Identifier texture)
    {
        this.modelName = modelName;
        this.texture = texture;
    }

    private @Nullable BakedGeoModel model()
    {
        var baked = GeckoLibResources.getBakedModels();
        // A resource reload swaps the whole cache. Keys depend on how GeckoLib strips the resource path, match on the model file name
        if (baked != modelCache)
        {
            modelCache = baked;
            model = baked.cache().entrySet().stream()
                    .filter(entry -> entry.getKey().getNamespace().equals(modelName.getNamespace()) && entry.getKey().getPath().endsWith(modelName.getPath()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
        return model;
    }

    /**
     * Pose stack origin is the block corner, the model is drawn standing on the block floor like GeoBlockRenderer.
     */
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int color)
    {
        var bakedModel = model();
        if (bakedModel == null)
            return;

        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture), (pose, buffer) ->
        {
            var bonePoses = new PoseStack();
            bonePoses.last().set(pose);
            for (var bone : bakedModel.topLevelBones())
                renderBone(bone, bonePoses, buffer, color);
        });
        poseStack.popPose();
    }

    private static void renderBone(GeoBone bone, PoseStack poseStack, VertexConsumer buffer, int color)
    {
        poseStack.pushPose();
        bone.translateToPivotPoint(poseStack);
        RenderUtil.optionalRotateZYX(poseStack, bone.baseRotZ(), bone.baseRotY(), bone.baseRotX());
        bone.translateAwayFromPivotPoint(poseStack);

        if (bone instanceof CuboidGeoBone cuboid)
        {
            for (var cube : cuboid.cubes)
            {
                poseStack.pushPose();
                cube.render(poseStack, buffer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
                poseStack.popPose();
            }
        }

        for (var child : bone.children())
            renderBone(child, poseStack, buffer, color);
        poseStack.popPose();
    }
}
