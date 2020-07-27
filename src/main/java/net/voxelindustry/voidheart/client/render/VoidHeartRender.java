package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

public class VoidHeartRender extends BlockEntityRenderer<VoidHeartTile>
{
    private final ItemStack voidHeartStack = new ItemStack(VoidHeartItems.VOID_HEART);

    public VoidHeartRender(BlockEntityRenderDispatcher dispatcher)
    {
        super(dispatcher);
    }

    @Override
    public void render(VoidHeartTile voidHeart, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        matrices.push();

        matrices.translate(0.5, 0.4, 0.5);
        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(voidHeart.getWorld().getTime() + tickDelta));
        matrices.scale(2, 2, 2);

        MinecraftClient.getInstance().getItemRenderer().renderItem(voidHeartStack, Mode.GROUND, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);

        matrices.pop();
    }
}
