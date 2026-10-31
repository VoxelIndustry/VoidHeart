package net.voxelindustry.voidheart.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierItem;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierStructure;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jspecify.annotations.Nullable;

/**
 * Shows where the held weaver will place barriers: green where it can, red where another owner's structure forbids it.
 */
public class WeaverPreviewRender
{
    private static final int MAX_PREVIEWED = 4096;

    private static final int PLACEABLE_COLOR = ARGB.color(110, PermeableBarrierRender.SIGIL_GLOW);
    private static final int REFUSED_COLOR   = ARGB.color(110, 255, 70, 70);
    private static final int POINT_COLOR     = ARGB.color(170, 200, 150, 255);

    public static void collectSubmits(LevelRenderContext context)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var level = minecraft.level;
        if (player == null || level == null)
            return;

        var stack = getHeldWeaver(player.getItemInHand(InteractionHand.MAIN_HAND), player.getItemInHand(InteractionHand.OFF_HAND));
        if (stack == null)
            return;

        var blockHit = getBlockHit(minecraft.hitResult);
        BlockPos first = stack.get(VoidHeartDataComponents.BARRIER_FIRST_POINT);
        var target = first != null
                ? PermeableBarrierItem.getSecondPoint(first, player, blockHit)
                : blockHit != null ? blockHit.getBlockPos().relative(blockHit.getDirection()) : null;
        if (first == null && target == null)
            return;

        var camera = context.levelState().cameraRenderState.pos;
        var origin = first != null ? first : target;
        var poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);

        // Writes depth, so translucent barriers drawn later behind the preview cannot paint over it
        context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(PermeableBarrierRender.WAVE_TEXTURE), (pose, buffer) ->
        {
            GlowCube.cube(pose, buffer, 0, 0, 0, 0.01F, POINT_COLOR);
            if (first == null || target == null)
                return;

            int remaining = player.hasInfiniteMaterials() ? Integer.MAX_VALUE : stack.getMaxDamage() - stack.getDamageValue();
            int visited = 0;
            for (var pos : BlockPos.betweenClosed(first, target))
            {
                if (remaining <= 0 || ++visited > MAX_PREVIEWED)
                    break;
                if (!level.getBlockState(pos).isAir())
                    continue;

                if (PermeableBarrierStructure.canExtend(level, pos, player))
                {
                    cubeAt(pose, buffer, origin, pos, PLACEABLE_COLOR);
                    remaining--;
                }
                else
                    cubeAt(pose, buffer, origin, pos, REFUSED_COLOR);
            }
        });
        poseStack.popPose();
    }

    private static void cubeAt(PoseStack.Pose pose, VertexConsumer buffer, BlockPos origin, BlockPos pos, int color)
    {
        GlowCube.cube(pose, buffer, pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ(), 0.002F, color);
    }

    private static @Nullable ItemStack getHeldWeaver(ItemStack mainHand, ItemStack offHand)
    {
        if (mainHand.getItem() instanceof PermeableBarrierItem)
            return mainHand;
        return offHand.getItem() instanceof PermeableBarrierItem ? offHand : null;
    }

    private static @Nullable BlockHitResult getBlockHit(@Nullable HitResult hitResult)
    {
        return hitResult instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }
}
