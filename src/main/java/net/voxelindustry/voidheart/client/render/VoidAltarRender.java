package net.voxelindustry.voidheart.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.model.json.ModelTransformation.Mode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.math.Vector3f;
import net.voxelindustry.voidheart.common.tile.VoidAltarTile;

public class VoidAltarRender extends BlockEntityRenderer<VoidAltarTile>
{

    public VoidAltarRender(BlockEntityRenderDispatcher dispatcher)
    {
        super(dispatcher);
    }

    @Override
    public void render(VoidAltarTile altar, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {

        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());

        if (!altar.getStack().isEmpty())
            PillarPlacementRender.renderPillarsPreview(this, altar, matrices, buffer);

        if (altar.getRecipeState() != null)
            CraftingOverlayRender.renderCraftingOverlay(altar, matrices, vertexConsumers, light);

        if (altar.isCrafting() && altar.getWarmProgress() > 0 || altar.getCoolProgress() > 0)
            CraftingVoidRender.renderVoidCube(altar, dispatcher, tickDelta, matrices, vertexConsumers);

        matrices.push();

        double offset = Math.abs(Math.sin((altar.getWorld().getTime() + tickDelta) / 8.0) / 24.0) + 2 / 16D;

        matrices.translate(0.5, 1 + offset, 0.5);
        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(altar.getWorld().getTime() + tickDelta));

        MinecraftClient.getInstance().getItemRenderer().renderItem(altar.getStack(), Mode.GROUND, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);

        matrices.pop();
    }
}
