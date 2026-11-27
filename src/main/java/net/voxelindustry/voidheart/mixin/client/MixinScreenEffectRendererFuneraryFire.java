package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.voxelindustry.voidheart.client.render.mausoleum.FuneraryFlames;
import net.voxelindustry.voidheart.common.content.mausoleum.FuneraryFire;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Burning with a wisp's fire, the player sees green flames licking up the screen rather than vanilla's.
 */
@Mixin(ScreenEffectRenderer.class)
public class MixinScreenEffectRendererFuneraryFire
{
    @ModifyArg(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"),
            index = 2)
    private TextureAtlasSprite voidheart$greenFire(TextureAtlasSprite sprite)
    {
        var player = Minecraft.getInstance().player;
        return player != null && FuneraryFire.burnsGreen(player) ? FuneraryFlames.sprite(FuneraryFlames.FIRE_1) : sprite;
    }
}
