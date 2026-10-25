package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;

public class CraftingOverlayRender
{
    static void renderCraftingOverlay(VoidAltarTile altar, PoseStack matrices, MultiBufferSource vertexConsumers, int light)
    {
        ItemStack output = altar.getClientRecipeOutput();

        if (!output.isEmpty())
        {
            // Render count text label
            if (output.getCount() > 1)
            {
                matrices.pushPose();
                matrices.translate(0.5, 1.85, 0.5);
                matrices.scale(1 / 64F, 1 / 64F, 1 / 64F);
                matrices.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                Minecraft.getInstance().font.drawInBatch(String.valueOf(output.getCount()), 6, -6, 0xFFFFFF, true, matrices.last().pose(), vertexConsumers, DisplayMode.NORMAL, 0, light);
                matrices.popPose();
            }
        }

        matrices.pushPose();
        matrices.translate(0.5, 1.75, 0.5);
        matrices.scale(2 / 16F, 2 / 16F, 2 / 16F);
        matrices.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());

        if (!output.isEmpty())
        {
            matrices.translate(0, 2, 0);
            matrices.scale(2.5F, 2.5F, 2.5F);

            var model = Minecraft.getInstance().getItemRenderer().getModel(output, null, null, 0);

            Minecraft.getInstance().getItemRenderer().render(output, ItemDisplayContext.GUI, false, matrices, vertexConsumers, 15728880, OverlayTexture.NO_OVERLAY, model);
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

            var model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);
            Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.GUI, false, matrices, vertexConsumers, 15728880, OverlayTexture.NO_OVERLAY, model);
        }

        matrices.popPose();
    }
}
