package net.voxelindustry.voidheart.client.render;

import net.minecraft.block.BlockState;
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
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos.Mutable;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.tile.VoidAltarTile;

import java.util.List;

public class VoidAltarRender extends BlockEntityRenderer<VoidAltarTile>
{
    private BlockState voidPillarState;

    public VoidAltarRender(BlockEntityRenderDispatcher dispatcher)
    {
        super(dispatcher);
    }

    @Override
    public void render(VoidAltarTile altar, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay)
    {
        VertexConsumer buffer = vertexConsumers.getBuffer(RenderLayer.getTranslucent());

        if (!altar.getStack().isEmpty())
            renderPillarsPreview(altar, matrices, buffer);

        if (altar.getRecipeState() != null)
            renderCraftingOverlay(altar, matrices, vertexConsumers, light);

        matrices.push();

        double offset = Math.abs(Math.sin((altar.getWorld().getTime() + tickDelta) / 8.0) / 24.0) + 2 / 16D;

        matrices.translate(0.5, 1 + offset, 0.5);
        matrices.multiply(Vector3f.NEGATIVE_Y.getDegreesQuaternion(altar.getWorld().getTime() + tickDelta));

        MinecraftClient.getInstance().getItemRenderer().renderItem(altar.getStack(), Mode.GROUND, 15728880, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers);

        matrices.pop();
    }

    private void renderCraftingOverlay(VoidAltarTile altar, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        matrices.push();

        matrices.translate(0.5, 1.75, 0.5);
        matrices.scale(2 / 16F, 2 / 16F, 2 / 16F);

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

    private void renderPillarsPreview(VoidAltarTile altar, MatrixStack matrices, VertexConsumer buffer)
    {
        Mutable pos = altar.getPos().mutableCopy();
        matrices.push();

        matrices.translate(3, 0, -1);
        pos.move(3, 0, -1);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(0, 0, 2);
        pos.move(0, 0, 2);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(-2, 0, 2);
        pos.move(-2, 0, 2);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(-2, 0, 0);
        pos.move(-2, 0, 0);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(-2, 0, -2);
        pos.move(-2, 0, -2);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(0, 0, -2);
        pos.move(0, 0, -2);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(2, 0, -2);
        pos.move(2, 0, -2);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.translate(2, 0, 0);
        pos.move(2, 0, 0);
        renderPillarPreview(altar, pos, matrices, buffer);

        matrices.pop();
    }

    private void renderPillarPreview(VoidAltarTile altar, Mutable pos, MatrixStack matrices, VertexConsumer buffer)
    {
        if (!altar.getWorld().isAir(pos))
            return;

        MinecraftClient.getInstance().getBlockRenderManager().renderBlock(
                getVoidPillarState(),
                altar.getPos(),
                altar.getWorld(),
                matrices,
                buffer,
                true,
                altar.getWorld().getRandom());
    }

    private BlockState getVoidPillarState()
    {
        if (voidPillarState == null)
            voidPillarState = VoidHeartBlocks.VOID_PILLAR.getDefaultState();
        return voidPillarState;
    }
}
