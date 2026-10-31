package net.voxelindustry.voidheart.common.setup;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfigurePayload;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierWavePayload;
import net.voxelindustry.voidheart.common.content.permeablebarrier.PermeableBarrierMenu;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidHeartMenus
{
    public static ExtendedMenuType<PermeableBarrierMenu, BlockPos> PERMEABLE_BARRIER;

    public static void registerMenus()
    {
        PERMEABLE_BARRIER = Registry.register(BuiltInRegistries.MENU, Identifier.fromNamespaceAndPath(MODID, "permeable_barrier"),
                new ExtendedMenuType<>(PermeableBarrierMenu::new, BlockPos.STREAM_CODEC));

        PayloadTypeRegistry.clientboundPlay().register(BarrierWavePayload.TYPE, BarrierWavePayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(BarrierConfigurePayload.TYPE, BarrierConfigurePayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BarrierConfigurePayload.TYPE, BarrierConfigurePayload::handle);
    }
}
