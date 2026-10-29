package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Only the particles are disguised: fall behavior must stay the barrier's own.
 */
@Mixin(LivingEntity.class)
public class MixinBarrierFallParticles
{
    @ModifyArg(method = "checkFallDamage",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)V"),
               index = 1)
    private BlockState disguiseBarrier(BlockState state, @Local(argsOnly = true) BlockPos pos)
    {
        return PermeableBarrierBlock.disguise(((LivingEntity) (Object) this).level(), pos, state);
    }
}
