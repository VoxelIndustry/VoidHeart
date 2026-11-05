package net.voxelindustry.voidheart.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.HitResult;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTeleportLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A pearl thrown before entering a dungeon, such as in a stasis chamber, can still land and pull its owner out
@Mixin(ThrownEnderpearl.class)
public abstract class MixinThrownEnderpearlDungeon
{
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void voidheart$refuseInDungeon(HitResult hit, CallbackInfo callback)
    {
        var pearl = (ThrownEnderpearl) (Object) this;
        if (pearl.getOwner() instanceof ServerPlayer owner && DungeonTeleportLock.refuse(owner))
        {
            pearl.discard();
            callback.cancel();
        }
    }
}
