package net.voxelindustry.voidheart.mixin;

import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves a Wraith Veil tunnel along once the tick's own movement is done, so its collapse can carry the player out.
 */
@Mixin(Player.class)
public abstract class MixinPlayerWallPassage
{
    @Inject(method = "tick", at = @At("TAIL"))
    private void voidheart$followPassage(CallbackInfo info)
    {
        WallPassages.tick((Player) (Object) this);
    }
}
