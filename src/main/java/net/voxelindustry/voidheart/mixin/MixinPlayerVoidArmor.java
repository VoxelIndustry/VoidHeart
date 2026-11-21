package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A Void Armor wearer's sneak only counts toward stepping out, so it neither crouches nor holds them at an edge, and the frame can't glide or swim. Its
// racks never drop with its wearer's inventory
@Mixin(Player.class)
public abstract class MixinPlayerVoidArmor
{
    @ModifyReturnValue(method = "isStayingOnGroundSurface", at = @At("RETURN"))
    private boolean voidheart$noEdgeHold(boolean staying)
    {
        return staying && !ArmorWear.wearing((Player) (Object) this);
    }

    @ModifyReturnValue(method = "getDesiredPose", at = @At("RETURN"))
    private Pose voidheart$noCrouch(Pose pose)
    {
        return pose == Pose.CROUCHING && ArmorWear.wearing((Player) (Object) this) ? Pose.STANDING : pose;
    }

    @ModifyReturnValue(method = "canGlide", at = @At("RETURN"))
    private boolean voidheart$noGlide(boolean glide)
    {
        return glide && !ArmorWear.wearing((Player) (Object) this);
    }

    // Blinded players can't sprint, and the same check holds back a wearer who is stepping, limp or wading
    @ModifyReturnValue(method = "isMobilityRestricted", at = @At("RETURN"))
    private boolean voidheart$heldBack(boolean restricted)
    {
        return restricted || ArmorWear.holdsBackSprint((Player) (Object) this);
    }

    // The racks go back on the frame and the wearer's own hotbar comes back before their inventory drops, so the racks never spill
    @Inject(method = "dropEquipment", at = @At("HEAD"))
    private void voidheart$racksStayInFrame(ServerLevel level, CallbackInfo info)
    {
        if ((Object) this instanceof ServerPlayer player)
            ArmorWear.releaseHotbar(player);
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void voidheart$noSwimming(CallbackInfo info)
    {
        var self = (Player) (Object) this;
        if (ArmorWear.wearing(self))
        {
            self.setSwimming(false);
            info.cancel();
        }
    }
}
