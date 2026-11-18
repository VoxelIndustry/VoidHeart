package net.voxelindustry.voidheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A trident thrown from a Void Armor rack remembers it, and comes back to it when its loyalty brings it home
@Mixin(ThrownTrident.class)
public abstract class MixinThrownTridentVoidArmor
{
    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
    private void voidheart$rememberRack(Level level, LivingEntity owner, ItemStack stack, CallbackInfo info)
    {
        if (!level.isClientSide() && owner instanceof Player player)
            ArmorHotbar.thrown((ThrownTrident) (Object) this, player);
    }

    @WrapOperation(method = "tryPickup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean voidheart$backToRack(Inventory inventory, ItemStack stack, Operation<Boolean> original)
    {
        return ArmorHotbar.pickUp((ThrownTrident) (Object) this, inventory.player, stack) || original.call(inventory, stack);
    }
}
