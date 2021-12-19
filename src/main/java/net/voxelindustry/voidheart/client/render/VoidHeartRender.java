package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3f;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static java.lang.Math.abs;
import static java.lang.Math.sin;

public class VoidHeartRender implements BlockEntityRenderer<VoidHeartTile>
{
    private final ItemStack voidHeartStack = new ItemStack(VoidHeartItems.VOID_HEART);

    @Override
    public void render(VoidHeartTile voidHeart, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        matrices.push();

        matrices.translate(0.5, 0.4, 0.5);
        matrices.multiply(Vec3f.NEGATIVE_Y.getDegreesQuaternion(voidHeart.getWorld().getTime() + tickDelta));

        float sinHeight = 6;
        float amplitude = 5;
        float scale = (float) (2 - abs(sin((voidHeart.getWorld().getTime() + tickDelta) / amplitude)) / sinHeight);
        matrices.scale(scale, scale, scale);

        MinecraftClient.getInstance().getItemRenderer().renderItem(voidHeartStack, Mode.GROUND, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, 0);

        matrices.pop();
    }
}
