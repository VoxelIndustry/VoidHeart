package net.voxelindustry.voidheart.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.OilDecoration;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks coated weapons in every slot with their oil's drop and the charges left.
 */
@Mixin(GuiGraphicsExtractor.class)
public class MixinGuiGraphicsOil
{
    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    private void voidheart$oilDecoration(Font font, ItemStack itemStack, int x, int y, @Nullable String countText, CallbackInfo callback)
    {
        OilDecoration.draw((GuiGraphicsExtractor) (Object) this, itemStack, x, y);
    }
}
