package net.voxelindustry.voidheart.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.FlameFeatureRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.voxelindustry.voidheart.client.render.mausoleum.FuneraryFireLook;
import net.voxelindustry.voidheart.client.render.mausoleum.FuneraryFlames;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Draws the flames on an entity burning with a wisp's fire from the green fire sprites instead of vanilla's. Both come from the block atlas, so the
 * group's buffer serves either.
 */
@Mixin(FlameFeatureRenderer.class)
public class MixinFlameFeatureRendererFuneraryFire
{
    @ModifyVariable(method = "prepare", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private TextureAtlasSprite voidheart$greenFire0(TextureAtlasSprite sprite, FlameFeatureRenderer.Submit submit, VertexConsumer buffer)
    {
        return submit.entityRenderState().getDataOrDefault(FuneraryFireLook.GREEN, false) ? FuneraryFlames.sprite(FuneraryFlames.FIRE_0) : sprite;
    }

    @ModifyVariable(method = "prepare", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private TextureAtlasSprite voidheart$greenFire1(TextureAtlasSprite sprite, FlameFeatureRenderer.Submit submit, VertexConsumer buffer)
    {
        return submit.entityRenderState().getDataOrDefault(FuneraryFireLook.GREEN, false) ? FuneraryFlames.sprite(FuneraryFlames.FIRE_1) : sprite;
    }
}
