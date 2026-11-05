package net.voxelindustry.voidheart.client.render.mob;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.mob.BarnacleInfection;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Shells growing on an infected player, one more for each point of infection. Each hangs from a limb so that it moves with it.
 */
public class BarnacleGrowthLayer extends RenderLayer<AvatarRenderState, PlayerModel>
{
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(MODID, "textures/entity/barnacle_growth.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);

    private final ModelPart growth;

    public BarnacleGrowthLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ModelPart growthRoot)
    {
        super(parent);
        growth = growthRoot.getChild("growth");
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot)
    {
        var level = Minecraft.getInstance().level;
        if (level == null || state.isInvisible || !(level.getEntity(state.id) instanceof Player player))
            return;

        int amount = BarnacleInfection.amount(player);
        if (amount <= 0)
            return;

        var model = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        draw(poseStack, collector, lightCoords, overlay, model.rightArm, -3, 0.5F, 0, Axis.ZP, -Mth.HALF_PI);
        if (amount > 1)
            draw(poseStack, collector, lightCoords, overlay, model.body, 1.5F, 4, -2, Axis.XP, Mth.HALF_PI);
        if (amount > 2)
            draw(poseStack, collector, lightCoords, overlay, model.body, -1, 7.5F, 2, Axis.XP, -Mth.HALF_PI);
        if (amount > 3)
            draw(poseStack, collector, lightCoords, overlay, model.leftArm, 3, 3, 0, Axis.ZP, Mth.HALF_PI);
        if (amount > 4)
            draw(poseStack, collector, lightCoords, overlay, model.rightLeg, 0, 4, -2, Axis.XP, Mth.HALF_PI);
    }

    private void draw(PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, ModelPart limb, float x, float y, float z, Axis axis, float angle)
    {
        poseStack.pushPose();
        limb.translateAndRotate(poseStack);
        poseStack.translate(x / 16, y / 16, z / 16);
        poseStack.rotate(axis.rotation(angle));
        collector.submitModelPart(growth, poseStack, RENDER_TYPE, light, overlay, null);
        poseStack.popPose();
    }
}
