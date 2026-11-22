package net.voxelindustry.voidheart.client.levitation;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Levitation Belt as worn: a thin dark band at the waist with a lead buckle in front, an azurium bead set in it. It follows the body through
 * {@link HumanoidModel#body}, so it bends with a sneak.
 * <p>
 * Drawn in entity cutout, which culls nothing since 26.3, so no two faces share a plane: the band stands proud of a chestplate and of leggings, the
 * buckle sinks into the band and the bead into the buckle. The same boxes are drawn again in {@link RenderTypes#eyes} with a glow skin black but for the
 * bead, so the bead shines in the dark. Per frame: three boxes, twice, for each wearer in view.
 */
public final class BeltWornRender implements TrinketRenderer
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "levitation_belt"), "main");

    private static final RenderType BODY_TYPE = RenderTypes.entityCutout(Identifier.fromNamespaceAndPath(MODID, "textures/entity/levitation_belt.png"));
    private static final RenderType GLOW_TYPE = RenderTypes.eyes(Identifier.fromNamespaceAndPath(MODID, "textures/entity/levitation_belt_glow.png"));

    /**
     * Past the body's faces at x ±4 and z ±2, a chestplate's at ±5 and ±3 and leggings' at ±4.5 and ±2.5, so the band shows over all of them.
     */
    private static final float BAND_GROWTH = 1.25F;

    private final ModelPart root;

    public BeltWornRender(EntityRendererProvider.Context context)
    {
        root = context.bakeLayer(LAYER);
    }

    /**
     * In model pixels from the neck, -z being the front. The body ends at y 12, where the legs begin, so the band sits just above the hips.
     */
    public static LayerDefinition createLayer()
    {
        var mesh = new MeshDefinition();
        var parts = mesh.getRoot();
        parts.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, 9.5F, -2, 8, 2, 4, new CubeDeformation(BAND_GROWTH, 0, BAND_GROWTH)), PartPose.ZERO);
        // The band's front is at z -3.25: the buckle sinks half a pixel into it, and the bead half a pixel into the buckle
        parts.addOrReplaceChild("buckle", CubeListBuilder.create().texOffs(0, 8).addBox(-1.5F, 9, -3.75F, 3, 3, 1), PartPose.ZERO);
        parts.addOrReplaceChild("bead", CubeListBuilder.create().texOffs(10, 8).addBox(-0.5F, 10, -4.25F, 1, 1, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void submit(ItemStack stack, TrinketSlotAccess slotReference, EntityModel<? extends LivingEntityRenderState> contextModel, PoseStack poseStack,
                       SubmitNodeCollector submit, int light, LivingEntityRenderState state, float limbAngle, float limbDistance)
    {
        // A Void Armor frame hides the body and leaves the belt asleep under it
        if (state.isInvisible || VoidArmorLook.framed(state) || !(contextModel instanceof HumanoidModel<?> humanoid))
            return;

        poseStack.pushPose();
        humanoid.root().translateAndRotate(poseStack);
        humanoid.body.translateAndRotate(poseStack);
        submit.submitModelPart(root, poseStack, BODY_TYPE, light, LivingEntityRenderer.getOverlayCoords(state, 0), null);
        submit.submitModelPart(root, poseStack, GLOW_TYPE, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
