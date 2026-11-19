package net.voxelindustry.voidheart.client.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.client.model.armor.VoidArmorModel;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Draws the Void Armor frame: on its wearer in place of their body, open on its base and parked where it was left. Its plates take world light in entity
 * cutout; its lacing, slit, lenses and charged tubes are the same boxes again in {@code eyes}, scaled by how lit the frame is, breathing slowly. The glow
 * stays modest so the lacing doesn't become a net of light at night, and the slit is its brightest point.
 */
public final class VoidArmorRender
{
    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_armor.png");
    public static final Identifier GLOW    = Identifier.fromNamespaceAndPath(MODID, "textures/entity/void_armor_glow.png");

    private static final RenderType BODY_TYPE = RenderTypes.entityCutout(TEXTURE);
    private static final RenderType GLOW_TYPE = RenderTypes.eyes(GLOW);

    private static final float BREATH_DEPTH  = 0.18F;
    private static final float BREATH_MILLIS = 3200;
    /** A living thing's renderer stands its model this far down from its pivot, so its feet meet the ground. */
    private static final float MODEL_DROP    = 1.501F;

    private static @Nullable VoidArmorModel firstPerson;

    private VoidArmorRender()
    {
    }

    public static VoidArmorModel model(EntityModelSet models, boolean glow)
    {
        return new VoidArmorModel(models.bakeLayer(VoidArmorModel.LAYER), glow);
    }

    /**
     * The frame's arm a wearer sees in first person, kept from the first wearer layer made.
     */
    public static void firstPersonModel(VoidArmorModel model)
    {
        if (firstPerson == null)
            firstPerson = model;
    }

    public static @Nullable VoidArmorModel firstPersonModel()
    {
        return firstPerson;
    }

    public static RenderType bodyType()
    {
        return BODY_TYPE;
    }

    /**
     * Submits the frame and its glow in the pose stack as a humanoid model stands in it.
     */
    public static void submit(VoidArmorModel model, VoidArmorModel glowModel, AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                              int lightCoords, int overlayCoords, int outlineColor)
    {
        var look = ((FabricRenderState) state).getDataOrDefault(VoidArmorLook.KEY, VoidArmorLook.NONE);
        collector.submitModel(model, state, poseStack, BODY_TYPE, lightCoords, overlayCoords, -1, null, outlineColor);
        if (look.glow() <= 0)
            return;
        // Eyes adds its texture, so the tint scales the glow
        float breath = 1 - BREATH_DEPTH * (0.5F + 0.5F * Mth.sin(Util.getMillis() / BREATH_MILLIS * Mth.TWO_PI));
        collector.submitModel(glowModel, state, poseStack, GLOW_TYPE, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ARGB.colorFromFloat(1, look.glow() * breath, look.glow() * breath, look.glow() * breath), null, 0);
    }

    /**
     * Stands a frame with no one in it at the pose stack's origin, turned to the yaw, at its worn size.
     */
    public static void standing(VoidArmorModel model, VoidArmorModel glowModel, AvatarRenderState state, float yaw, PoseStack poseStack,
                                SubmitNodeCollector collector, int lightCoords)
    {
        poseStack.pushPose();
        poseStack.rotate(Axis.YP.rotationDegrees(180 - yaw));
        poseStack.scale(ArmorBalance.SCALE, ArmorBalance.SCALE, ArmorBalance.SCALE);
        poseStack.scale(-1, -1, 1);
        poseStack.translate(0, -MODEL_DROP, 0);
        submit(model, glowModel, state, poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * The frame's arm in first person, where the wearer's own would be, plated and gauntleted around what it holds.
     */
    public static void submitArm(ModelPart arm, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords)
    {
        collector.submitModelPart(arm, poseStack, BODY_TYPE, lightCoords, OverlayTexture.NO_OVERLAY, null);
    }
}
