package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.item.ItemStack;
import net.voxelindustry.voidheart.common.tile.VoidAltarTile;

import java.util.List;

public class CraftingOverlayRender
{
    static void renderCraftingOverlay(VoidAltarTile altar, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        matrices.push();

        matrices.translate(0.5, 1.75, 0.5);
        matrices.scale(2 / 16F, 2 / 16F, 2 / 16F);
        matrices.multiply(MinecraftClient.getInstance().getEntityRenderManager().getRotation());
        matrices.multiply(Vector3f.POSITIVE_Y.getDegreesQuaternion(180));

        ItemStack output = altar.getRecipeState().getOutputs(ItemStack.class).get(0).getRaw();

        matrices.translate(0, 2, 0);
        matrices.scale(2, 2, 2);
        MinecraftClient.getInstance().getItemRenderer().renderItem(output, Mode.GUI, light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);
        matrices.scale(0.5F, 0.5F, 0.5F);
        matrices.translate(0, -2, 0);

        List<ItemStack> stackLefts = altar.getRecipeState().getIngredientsLeft(ItemStack.class);
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
            MinecraftClient.getInstance().getItemRenderer().renderItem(stack, Mode.GUI, light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);
        }

        matrices.pop();
    }
}
