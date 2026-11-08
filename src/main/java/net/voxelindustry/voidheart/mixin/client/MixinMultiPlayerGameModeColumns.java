package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client predicts the same single column break as the server, so the others of the space never vanish and come back.
 */
@Mixin(MultiPlayerGameMode.class)
public class MixinMultiPlayerGameModeColumns
{
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean voidheart$breakOneColumn(Level level, BlockPos pos, BlockState replacement, int flags, Operation<Boolean> original)
    {
        var player = Minecraft.getInstance().player;
        var remaining = player == null ? null : KnowledgeColumnBlock.remainingAfterBreak(level.getBlockState(pos), pos, player);
        return original.call(level, pos, remaining != null ? remaining : replacement, flags);
    }
}
