package net.voxelindustry.voidheart.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The blocks of an open Wraith Veil tunnel have no collision for its bearer, and only for them. Movement, the server's move checks and the pose
 * all ask through here, so walking the tunnel is ordinary walking.
 */
@Mixin(EntityCollisionContext.class)
public abstract class MixinEntityCollisionContextWallPassage
{
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void voidheart$openTunnel(BlockState state, CollisionGetter collisionGetter, BlockPos pos, CallbackInfoReturnable<VoxelShape> info)
    {
        if (WallPassages.isPassable(((EntityCollisionContext) (Object) this).getEntity(), pos))
            info.setReturnValue(Shapes.empty());
    }
}
