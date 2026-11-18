package net.voxelindustry.voidheart.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// A Void Armor wearer can't drop a rack's weapon, swap it to the off hand, pick a block into the hotbar or set a hotbar slot from the creative screen
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerGamePacketListenerVoidArmor
{
    private static final String ON_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = ON_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void voidheart$frameHoldsRacks(ServerboundPlayerActionPacket packet, CallbackInfo info)
    {
        var action = packet.getAction();
        boolean handling = action == ServerboundPlayerActionPacket.Action.DROP_ITEM || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
                || action == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND;
        if (handling && ArmorWear.wearing(player))
        {
            player.containerMenu.sendAllDataToRemote();
            info.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target = ON_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void voidheart$noCreativeRacks(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo info)
    {
        int slot = packet.slotNum();
        boolean frames = slot >= InventoryMenu.USE_ROW_SLOT_START && slot < InventoryMenu.USE_ROW_SLOT_END || slot == InventoryMenu.SHIELD_SLOT;
        if (frames && ArmorWear.wearing(player))
        {
            player.inventoryMenu.sendAllDataToRemote();
            info.cancel();
        }
    }

    @Inject(method = "tryPickItem", at = @At("HEAD"), cancellable = true)
    private void voidheart$noPicking(ItemStack itemStack, CallbackInfo info)
    {
        if (ArmorWear.wearing(player))
            info.cancel();
    }
}
