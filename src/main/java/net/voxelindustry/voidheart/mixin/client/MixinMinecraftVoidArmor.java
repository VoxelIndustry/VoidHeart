package net.voxelindustry.voidheart.mixin.client;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorPayloads;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Worn, the Void Armor's keys after its racks' switch its sights instead of selecting a slot, and the drop key lets nothing go
@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MixinMinecraftVoidArmor
{
    @WrapOperation(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void voidheart$capabilityKeys(Inventory inventory, int slot, Operation<Void> original)
    {
        if (slot >= ArmorHotbar.RACKS && ArmorWear.wearing(inventory.player))
            ClientPlayNetworking.send(new ArmorPayloads.Capability(slot - ArmorHotbar.RACKS));
        else
            original.call(inventory, slot);
    }

    @WrapWithCondition(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;dropItem(Lnet/minecraft/client/player/LocalPlayer;Z)V"))
    private boolean voidheart$keepRack(MultiPlayerGameMode gameMode, LocalPlayer player, boolean all)
    {
        return !ArmorWear.wearing(player);
    }
}
