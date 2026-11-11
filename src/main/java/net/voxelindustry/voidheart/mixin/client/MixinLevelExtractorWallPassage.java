package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Inside a Wraith Veil tunnel the view is the tunnel's, not the inside of a block.
 */
@Mixin(LevelExtractor.class)
public abstract class MixinLevelExtractorWallPassage
{
    @Inject(method = "getViewBlockingState", at = @At("HEAD"), cancellable = true)
    private static void voidheart$tunnelView(LocalPlayer player, Frustum frustum, CallbackInfoReturnable<BlockState> info)
    {
        if (WallPassages.isInside(player))
            info.setReturnValue(null);
    }
}
