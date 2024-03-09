package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer.TextLayerType;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
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
                MinecraftClient.getInstance().textRenderer.draw(String.valueOf(output.getCount()), 6, -6, 0xFFFFFF, true, matrices.peek().getPositionMatrix(), vertexConsumers, TextLayerType.NORMAL, 0, light);
                matrices.pop();
            }
        }

        matrices.push();
        matrices.translate(0.5, 1.75, 0.5);
        matrices.scale(2 / 16F, 2 / 16F, 2 / 16F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation());

        if (!output.isEmpty())
        {
            matrices.translate(0, 2, 0);
            matrices.scale(2.5F, 2.5F, 2.5F);

            var model = MinecraftClient.getInstance().getItemRenderer().getModel(output, null, null, 0);

            MinecraftClient.getInstance().getItemRenderer().renderItem(output, ModelTransformationMode.GUI, false, matrices, vertexConsumers, 15728880, OverlayTexture.DEFAULT_UV, model);
            matrices.scale(0.6F, 0.6F, 0.6F);
            matrices.translate(0, -2, 0);
        }

        List<ItemStack> stackLefts = altar.getClientRecipeToConsume();
        long stackLeft = stackLefts.stream().filter(stack -> !stack.isEmpty()).count();
        matrices.translate(-(stackLeft - 1) / 2F - 0.5F, 0, 0);

        boolean isFirst = true;
        for (var stack : stackLefts)
        {
            if (isFirst)
            {
                isFirst = false;
                continue;
            }
            if (stack.isEmpty())
                continue;
            matrices.translate(1, 0, 0);

            var model = MinecraftClient.getInstance().getItemRenderer().getModel(stack, null, null, 0);
            MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.GUI, false, matrices, vertexConsumers, 15728880, OverlayTexture.DEFAULT_UV, model);
        }

        matrices.pop();
    }
}
