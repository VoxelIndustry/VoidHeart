package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player dissolved by their second heart can't move until the reform is done, so the server never has to pull them back.
 */
@Mixin(KeyboardInput.class)
public abstract class MixinKeyboardInputSecondHeart extends ClientInput
{
    @Inject(method = "tick", at = @At("TAIL"))
    private void voidheart$holdWhileDissolved(CallbackInfo info)
    {
        var player = Minecraft.getInstance().player;
        if (player != null && SecondHeart.held(player))
        {
            keyPresses = Input.EMPTY;
            moveVector = Vec2.ZERO;
        }
    }
}
