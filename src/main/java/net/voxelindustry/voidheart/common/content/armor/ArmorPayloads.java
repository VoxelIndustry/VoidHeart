package net.voxelindustry.voidheart.common.content.armor;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class ArmorPayloads
{
    /**
     * A wearer pressed the key of one of the frame's sights, counted from the first key after the racks'.
     */
    public record Capability(int index) implements CustomPacketPayload
    {
        public static final Type<Capability> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "void_armor_capability"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Capability> STREAM_CODEC = ByteBufCodecs.VAR_INT.<RegistryFriendlyByteBuf>cast()
                .map(Capability::new, Capability::index);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }

        public static void handle(Capability capability, ServerPlayNetworking.Context context)
        {
            ArmorHotbar.useCapability(context.player(), capability.index());
        }
    }

    private ArmorPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.serverboundPlay().register(Capability.TYPE, Capability.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Capability.TYPE, Capability::handle);
    }
}
