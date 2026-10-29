package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Sprint dust and footsteps on a barrier are those of the block it pretends to be.
 */
@Mixin(Entity.class)
public class MixinEntityBarrierDisguise
{
    @ModifyVariable(method = "spawnSprintParticle", at = @At("STORE"))
    private BlockState disguiseSprintParticle(BlockState state, @Local BlockPos pos)
    {
        return PermeableBarrierBlock.disguise(((Entity) (Object) this).level(), pos, state);
    }

    @ModifyVariable(method = "walkingStepSound", at = @At("HEAD"), argsOnly = true)
    private BlockState disguiseStepSound(BlockState state, @Local(argsOnly = true) BlockPos pos)
    {
        return PermeableBarrierBlock.disguise(((Entity) (Object) this).level(), pos, state);
    }
}
