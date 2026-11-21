package net.voxelindustry.voidheart.mixin.client;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.voxelindustry.voidheart.client.armor.VoidArmorLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The Void Armor closes over its wearer, so their own body isn't drawn inside it. Every player shares this model, so the head is shown again for the rest.
@Mixin(PlayerModel.class)
public class MixinPlayerModelVoidArmor
{
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void voidheart$hideInFrame(AvatarRenderState state, CallbackInfo callback)
    {
        var model = (PlayerModel) (Object) this;
        boolean framed = ((FabricRenderState) state).getDataOrDefault(VoidArmorLook.KEY, VoidArmorLook.NONE).shown();
        model.head.visible = !framed;
        if (framed)
        {
            model.body.visible = false;
            model.rightArm.visible = false;
            model.leftArm.visible = false;
            model.rightLeg.visible = false;
            model.leftLeg.visible = false;
        }
    }
}
