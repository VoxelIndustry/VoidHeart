package net.voxelindustry.voidheart.client.render;

import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.ClientConstants;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import static java.lang.Math.*;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.gui.Gui.HeartType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SkullBlock;

public class VoidHeartRender implements BlockEntityRenderer<VoidHeartTile>
{
    private final Identifier PORTAL_ICONS = Identifier.fromNamespaceAndPath(MODID, "textures/hud/portal_icons.png");

    private final ItemStack voidHeartStack = new ItemStack(VoidHeartItems.VOID_HEART);

    @Override
    public void render(VoidHeartTile voidHeart, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay)
    {
        matrices.pushPose();

        matrices.translate(0.5, 0.3, 0.5);
        matrices.mulPose(MathUtil.quatFromAngleDegrees(voidHeart.getLevel().getGameTime() + tickDelta, MathUtil.NEGATIVE_Y));

        float sinHeight = 6;
        float amplitude = 5;
        float scale = (float) (2 - abs(sin((voidHeart.getLevel().getGameTime() + tickDelta) / amplitude)) / sinHeight);
        matrices.scale(scale, scale, scale);

        var model = Minecraft.getInstance().getItemRenderer().getModel(voidHeartStack, null, null, 0);
        Minecraft.getInstance().getItemRenderer().render(voidHeartStack, ItemDisplayContext.GROUND, false, matrices, vertexConsumers, 15728880, OverlayTexture.NO_OVERLAY, model);

        matrices.popPose();

        var playerProfile = voidHeart.getPlayerProfile();

        if (playerProfile == null)
            return;

        matrices.pushPose();
        matrices.translate(0.5, 1.3, 0.5);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        matrices.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
        matrices.mulPose(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));

        var textRenderer = Minecraft.getInstance().font;
        var nameSize = textRenderer.width(playerProfile.getName()) / 2F;
        textRenderer.drawInBatch(playerProfile.getName(), -nameSize + 1, 1, 0x3c3c3c, false, matrices.last().pose(), vertexConsumers, DisplayMode.NORMAL, 0, 15728880);
        textRenderer.drawInBatch(playerProfile.getName(), -nameSize, 0, 0xFFFFFF, false, matrices.last().pose(), vertexConsumers, DisplayMode.SEE_THROUGH, 0, 15728880);

        var headRenderLayer = SkullBlockRenderer.getRenderType(SkullBlock.Types.PLAYER, playerProfile);
        renderHeadSkin(matrices, vertexConsumers.getBuffer(headRenderLayer), -16 - nameSize - 8, -2, 0, 20, 20);

        Player playerEntity = voidHeart.getLevel().getPlayerByUUID(voidHeart.getPlayerID());
        if (playerEntity != null)
            renderHearts(playerEntity.getHealth(),
                    playerEntity.getMaxHealth(),
                    matrices,
                    vertexConsumers,
                    -18,
                    8,
                    0,
                    6,
                    6);

        if (voidHeart.heartData() != null)
        {
            renderHeartData(voidHeart.heartData(), matrices, vertexConsumers, -50, 20, 0);
        }

        matrices.popPose();
    }

    private void renderHeartData(VoidHeartData data, PoseStack matrixStack, MultiBufferSource vertexConsumers, float posX, float posY, float posZ)
    {
        var heartRenderBuffer = vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(PORTAL_ICONS));

        for (int i = 0; i < data.currentFlexionCount(); i++)
        {
            renderSprite(matrixStack, heartRenderBuffer, posX + 10 * (i % 2), posY + 10 * (i / 2), posZ, 8, 8, 0.25F, 4 / 48F, 0.75F, 12 / 48F);
        }

        for (int i = data.currentFlexionCount(); i < data.maxFlexion(); i++)
        {
            renderSprite(matrixStack, heartRenderBuffer, posX + 10 * (i % 2), posY + 10 * (i / 2), posZ, 8, 8, 0.25F, 20 / 48F, 0.75F, 28 / 48F);
        }
    }

    private void renderHearts(float health, float maxHealth, PoseStack matrixStack, MultiBufferSource vertexConsumers, float posX, float posY, float posZ, float width, float height)
    {
        var heartRenderBuffer = vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(ClientConstants.GUI_ICONS_TEXTURE));

        var containerMinU = HeartType.CONTAINER.getX(false, false) / 256F;
        var containerMaxU = containerMinU + (9 / 256F);

        var minV = 0;
        var maxV = 9 / 256F;

        for (int i = 0; i < maxHealth; i += 2)
        {
            renderSprite(matrixStack, heartRenderBuffer, posX + width / 2 * i, posY, posZ, width, height, containerMinU, minV, containerMaxU, maxV);
        }

        var normalMinU = HeartType.NORMAL.getX(false, false) / 256F;
        var normalMaxU = normalMinU + (9 / 256F);

        for (int i = 0; i < health - 1; i += 2)
        {
            renderSprite(matrixStack, heartRenderBuffer, posX + width / 2 * i, posY, posZ, width, height, normalMinU, minV, normalMaxU, maxV);
        }

        if (health % 2 != 0)
        {
            var halfHeartMinU = HeartType.NORMAL.getX(true, false) / 256F;
            var halfHeartMaxU = halfHeartMinU + (9 / 256F);
            renderSprite(matrixStack, heartRenderBuffer, posX + width * (float) floor(health / 2F), posY, posZ, width, height, halfHeartMinU, minV, halfHeartMaxU, maxV);
        }
    }

    private void renderSprite(PoseStack matrixStack,
                              VertexConsumer buffer,
                              float posX,
                              float posY,
                              float posZ,
                              float width,
                              float height,
                              float minU,
                              float minV,
                              float maxU,
                              float maxV)
    {
        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(minU, minV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(minU, maxV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(maxU, maxV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .uv(maxU, minV)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
    }

    private void renderHeadSkin(PoseStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height)
    {
        var minU = 0.125F;
        var minV = 0.125F;
        var maxU = 0.25F;
        var maxV = 0.25F;

        buffer.vertex(matrixStack.last().pose(), posX, posY, posZ)
                .color(1, 1, 1, 0.95f)
                .uv(minU, minV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX, posY + height, posZ)
                .color(1, 1, 1, 0.95f)
                .uv(minU, maxV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY + height, posZ)
                .color(1, 1, 1, 0.95f)
                .uv(maxU, maxV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
        buffer.vertex(matrixStack.last().pose(), posX + width, posY, posZ)
                .color(1, 1, 1, 0.95f)
                .uv(maxU, minV)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(240, 240)
                .normal(-1, 0, 0)
                .endVertex();
    }
}
