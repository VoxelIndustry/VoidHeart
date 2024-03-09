package net.voxelindustry.voidheart.client.render;

import net.minecraft.block.SkullBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.gui.hud.InGameHud.HeartType;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.client.CustomRenderLayers;
import net.voxelindustry.voidheart.client.util.ClientConstants;
import net.voxelindustry.voidheart.client.util.MathUtil;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.compat.immportal.ImmersivePortalCompat;

import static java.lang.Math.*;

public class VoidHeartRender implements BlockEntityRenderer<VoidHeartTile>
{
    private final ItemStack voidHeartStack = new ItemStack(VoidHeartItems.VOID_HEART);

    @Override
    public void render(VoidHeartTile voidHeart, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        matrices.push();

        matrices.translate(0.5, 0.3, 0.5);
        matrices.multiply(MathUtil.quatFromAngleDegrees(voidHeart.getWorld().getTime() + tickDelta, MathUtil.NEGATIVE_Y));

        float sinHeight = 6;
        float amplitude = 5;
        float scale = (float) (2 - abs(sin((voidHeart.getWorld().getTime() + tickDelta) / amplitude)) / sinHeight);
        matrices.scale(scale, scale, scale);

        var model = MinecraftClient.getInstance().getItemRenderer().getModel(voidHeartStack, null, null, 0);
        MinecraftClient.getInstance().getItemRenderer().renderItem(voidHeartStack, ModelTransformationMode.GROUND, false, matrices, vertexConsumers, 15728880, OverlayTexture.DEFAULT_UV, model);

        matrices.pop();

        var playerProfile = voidHeart.getPlayerProfile();

        if (playerProfile == null || ImmersivePortalCompat.areWeRenderedByPortal())
            return;

        matrices.push();
        matrices.translate(0.5, 1.3, 0.5);
        matrices.scale(1.25F / 64F, 1.25F / 64F, 1.25F / 64F);

        matrices.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        matrices.multiply(MathUtil.quatFromAngleDegrees(180, MathUtil.POSITIVE_Z));

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        var nameSize = textRenderer.getWidth(playerProfile.getName()) / 2F;
        textRenderer.draw(playerProfile.getName(), -nameSize, 0, 0xFFFFFF, true, matrices.peek().getPositionMatrix(), vertexConsumers, TextLayerType.NORMAL, 0, 15728880);

        var headRenderLayer = SkullBlockEntityRenderer.getRenderLayer(SkullBlock.Type.PLAYER, playerProfile);
        renderHeadSkin(matrices, vertexConsumers.getBuffer(headRenderLayer), -16 - nameSize - 8, -2, 0, 20, 20);

        PlayerEntity playerEntity = voidHeart.getWorld().getPlayerByUuid(voidHeart.getPlayerID());
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

        matrices.pop();
    }

    private void renderHearts(float health, float maxHealth, MatrixStack matrixStack, VertexConsumerProvider vertexConsumers, float posX, float posY, float posZ, float width, float height)
    {
        var heartRenderBuffer = vertexConsumers.getBuffer(CustomRenderLayers.getColorTextureTranslucent(ClientConstants.GUI_ICONS_TEXTURE));

        var containerMinU = HeartType.CONTAINER.getU(false, false) / 256F;
        var containerMaxU = containerMinU + (9 / 256F);

        var minV = 0;
        var maxV = 9 / 256F;

        for (int i = 0; i < maxHealth; i += 2)
        {
            renderHeart(matrixStack, heartRenderBuffer, posX + width / 2 * i, posY, posZ, width, height, containerMinU, minV, containerMaxU, maxV);
        }

        var normalMinU = HeartType.NORMAL.getU(false, false) / 256F;
        var normalMaxU = normalMinU + (9 / 256F);

        for (int i = 0; i < health - 1; i += 2)
        {
            renderHeart(matrixStack, heartRenderBuffer, posX + width / 2 * i, posY, posZ, width, height, normalMinU, minV, normalMaxU, maxV);
        }

        if (health % 2 != 0)
        {
            var halfHeartMinU = HeartType.NORMAL.getU(true, false) / 256F;
            var halfHeartMaxU = halfHeartMinU + (9 / 256F);
            renderHeart(matrixStack, heartRenderBuffer, posX + width * (float) floor(health / 2F), posY, posZ, width, height, halfHeartMinU, minV, halfHeartMaxU, maxV);
        }
    }

    private void renderHeart(MatrixStack matrixStack,
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
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(minU, minV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(minU, maxV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(maxU, maxV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(1, 1, 1, 0.95F)
                .texture(maxU, minV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
    }

    private void renderHeadSkin(MatrixStack matrixStack, VertexConsumer buffer, float posX, float posY, float posZ, float width, float height)
    {
        var minU = 0.125F;
        var minV = 0.125F;
        var maxU = 0.25F;
        var maxV = 0.25F;

        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY, posZ)
                .color(1, 1, 1, 0.95f)
                .texture(minU, minV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX, posY + height, posZ)
                .color(1, 1, 1, 0.95f)
                .texture(minU, maxV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY + height, posZ)
                .color(1, 1, 1, 0.95f)
                .texture(maxU, maxV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
        buffer.vertex(matrixStack.peek().getPositionMatrix(), posX + width, posY, posZ)
                .color(1, 1, 1, 0.95f)
                .texture(maxU, minV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(240, 240)
                .normal(-1, 0, 0)
                .next();
    }
}
