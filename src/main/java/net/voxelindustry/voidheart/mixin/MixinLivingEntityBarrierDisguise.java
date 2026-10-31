package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Fall particles and landing sound on a barrier are those of the block it pretends to be.
 * Only these effects are disguised: fall behavior must stay the barrier's own.
 */
@Mixin(LivingEntity.class)
public class MixinLivingEntityBarrierDisguise
{
    @ModifyArg(method = "checkFallDamage",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)V"),
               index = 1)
    private BlockState disguiseFallParticles(BlockState state, @Local(argsOnly = true) BlockPos pos)
    {
        return PermeableBarrierBlock.disguise(((LivingEntity) (Object) this).level(), pos, state);
    }

    @ModifyVariable(method = "playBlockFallSound", at = @At("STORE"))
    private BlockState disguiseFallSound(BlockState state, @Local(ordinal = 0) int x, @Local(ordinal = 1) int y, @Local(ordinal = 2) int z)
    {
        return PermeableBarrierBlock.disguise(((LivingEntity) (Object) this).level(), new BlockPos(x, y, z), state);
    }
}
