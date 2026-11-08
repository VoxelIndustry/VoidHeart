package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Breaking a knowledge column leaves the others of its space standing, and their block entity with them.
 */
@Mixin(ServerPlayerGameMode.class)
public class MixinServerPlayerGameModeColumns
{
    @Shadow
    @Final
    protected ServerPlayer player;

    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean voidheart$breakOneColumn(ServerLevel level, BlockPos pos, boolean movedByPiston, Operation<Boolean> original)
    {
        var remaining = KnowledgeColumnBlock.remainingAfterBreak(level.getBlockState(pos), pos, player);
        return remaining != null ? level.setBlock(pos, remaining, Block.UPDATE_ALL) : original.call(level, pos, movedByPiston);
    }
}
