package net.voxelindustry.voidheart.client.apparatus;

import com.mojang.blaze3d.vertex.PoseStack;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.client.TrinketRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusTier;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The apparatus as worn: an emitter plate on the chest with its lens in a raised bezel, a cell housing on the back, and straps rising from both to the
 * neck, trimmed in the metal of its tier. The skins are painted as worn metal, speckled and edge-lit like the mod's mobs, since flat fills read as plastic.
 * It follows the body through {@link HumanoidModel#body}, so it bends and turns with it.
 * <p>
 * Drawn in entity cutout, which culls nothing since 26.3, so no two faces share a plane: the straps sink into the plate and housing, the lens into the
 * bezel and the bezel into the plate, and nothing lies on the body's own faces. The lens and the housing's vents are the same boxes again in
 * {@link RenderTypes#eyes}, so they glow in the dark. Per frame: eight boxes, twice, for each wearer in view.
 */
public final class ApparatusWornRender implements TrinketRenderer
{
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(MODID, "shielding_apparatus"), "main");

    private static final Identifier GLOW = Identifier.fromNamespaceAndPath(MODID, "textures/entity/shielding_apparatus_glow.png");
    private static final RenderType GLOW_TYPE = RenderTypes.eyes(GLOW);
    private static final Map<ApparatusTier, RenderType> BODY_TYPES = new EnumMap<>(ApparatusTier.class);

    static
    {
        for (var tier : ApparatusTier.values())
            BODY_TYPES.put(tier, RenderTypes.entityCutout(
                    Identifier.fromNamespaceAndPath(MODID, "textures/entity/shielding_apparatus_" + tier.name().toLowerCase(Locale.ROOT) + ".png")));
    }

    private final ModelPart root;

    public ApparatusWornRender(EntityRendererProvider.Context context)
    {
        root = context.bakeLayer(LAYER);
    }

    /**
     * In model pixels from the neck, -z being the chest. The body's faces are at x ±4, z ±2 and y 0, and a chestplate's at x ±5, z ±3 and y -1, so the plate
     * and housing stand proud of both.
     */
    public static LayerDefinition createLayer()
    {
        var mesh = new MeshDefinition();
        var parts = mesh.getRoot();
        parts.addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0).addBox(-3, 2, -4.2F, 6, 5, 2), PartPose.ZERO);
        // The bezel sinks into the plate and the lens into the bezel, so no two faces meet in a plane
        parts.addOrReplaceChild("bezel", CubeListBuilder.create().texOffs(40, 0).addBox(-2, 2.5F, -4.8F, 4, 4, 1), PartPose.ZERO);
        parts.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(16, 0).addBox(-1, 3.5F, -5.3F, 2, 2, 1), PartPose.ZERO);
        parts.addOrReplaceChild("housing", CubeListBuilder.create().texOffs(0, 8).addBox(-3, 1.5F, 2.2F, 6, 7, 2), PartPose.ZERO);
        // The straps stop at the top of the body: above it they would run inside the head, and poke out of it once the head turns from the body
        for (int side = -1; side <= 1; side += 2)
        {
            float x = side > 0 ? 1.5F : -2.5F;
            String name = side > 0 ? "right" : "left";
            parts.addOrReplaceChild("front_strap_" + name, CubeListBuilder.create().texOffs(24, 0).addBox(x, 0, -3.5F, 1, 2.5F, 1), PartPose.ZERO);
            parts.addOrReplaceChild("back_strap_" + name, CubeListBuilder.create().texOffs(24, 0).addBox(x, 0, 2.5F, 1, 2, 1), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void submit(ItemStack stack, TrinketSlotAccess slotReference, EntityModel<? extends LivingEntityRenderState> contextModel, PoseStack poseStack,
                       SubmitNodeCollector submit, int light, LivingEntityRenderState state, float limbAngle, float limbDistance)
    {
        if (state.isInvisible || !(contextModel instanceof HumanoidModel<?> humanoid) || !(stack.getItem() instanceof ShieldingApparatusItem apparatus))
            return;

        poseStack.pushPose();
        humanoid.root().translateAndRotate(poseStack);
        humanoid.body.translateAndRotate(poseStack);
        submit.submitModelPart(root, poseStack, BODY_TYPES.get(apparatus.tier()), light, LivingEntityRenderer.getOverlayCoords(state, 0), null);
        submit.submitModelPart(root, poseStack, GLOW_TYPE, light, OverlayTexture.NO_OVERLAY, null);
        poseStack.popPose();
    }
}
