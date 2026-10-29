package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mining cracks and hit sounds of a barrier are those of the block it pretends to be.
 */
@Mixin(ClientLevel.class)
public class MixinBarrierBreakingParticles
{
    @ModifyVariable(method = "addBreakingBlockEffects", at = @At("STORE"))
    private BlockState disguiseBarrier(BlockState state, @Local(argsOnly = true) BlockPos pos)
    {
        return PermeableBarrierBlock.disguise((ClientLevel) (Object) this, pos, state);
    }
}
