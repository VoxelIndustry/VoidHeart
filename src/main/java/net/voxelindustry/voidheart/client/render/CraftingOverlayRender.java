package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;

import java.util.List;

public class CraftingOverlayRender
{
    static void renderCraftingOverlay(VoidAltarTile altar, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        ItemStack output = altar.getClientRecipeOutput();

        if (!output.isEmpty())
        {

            // Render count text label
            if (output.getCount() > 1)
            {
                matrices.push();
                matrices.translate(0.5, 1.85, 0.5);
                matrices.scale(1 / 64F, 1 / 64F, 1 / 64F);
                matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
                matrices.multiply(Vector3f.POSITIVE_Z.getDegreesQuaternion(180));
                MinecraftClient.getInstance().textRenderer.drawWithShadow(matrices, String.valueOf(output.getCount()), 6, -6, 0xFFFFFF);
                matrices.pop();
            }
        }

        matrices.push();
        matrices.translate(0.5, 1.75, 0.5);
        matrices.scale(2 / 16F, 2 / 16F, 2 / 16F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());
        matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(180));

        if (!output.isEmpty())
        {
            matrices.translate(0, 2, 0);
            matrices.scale(2.5F, 2.5F, 2.5F);
            MinecraftClient.getInstance().getItemRenderer().renderItem(output, Mode.GUI, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);
            matrices.scale(0.6F, 0.6F, 0.6F);
            matrices.translate(0, -2, 0);
        }

        List<ItemStack> stackLefts = altar.getClientRecipeToConsume();
        long stackLeft = stackLefts.stream().filter(stack -> !stack.isEmpty()).count();
        matrices.translate(-(stackLeft - 1) / 2F - 0.5F, 0, 0);

        boolean isFirst = true;
        for (ItemStack stack : stackLefts)
        {
            if (isFirst)
            {
                isFirst = false;
                continue;
            }
            if (stack.isEmpty())
                continue;
            matrices.translate(1, 0, 0);
            MinecraftClient.getInstance().getItemRenderer().renderItem(stack, Mode.GUI, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);
        }

        matrices.pop();
    }
}
