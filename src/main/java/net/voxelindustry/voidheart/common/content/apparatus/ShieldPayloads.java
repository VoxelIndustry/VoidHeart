package net.voxelindustry.voidheart.common.content.apparatus;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.joml.Vector3fc;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public final class ShieldPayloads
{
    /**
     * A hit the shield of an entity took, for everyone who sees it.
     *
     * @param from     where the hit came from, from the middle of the entity's box, in world axes
     * @param strength how much of the shield the hit took, from 0 to 1
     * @param broke    whether it emptied the shield
     */
    public record Hit(int entity, Vector3fc from, float strength, boolean broke) implements CustomPacketPayload
    {
        public static final Type<Hit> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "shield_hit"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Hit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hit::entity,
                ByteBufCodecs.VECTOR3F, Hit::from,
                ByteBufCodecs.FLOAT, Hit::strength,
                ByteBufCodecs.BOOL, Hit::broke,
                Hit::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    /**
     * A change made to the grid of the apparatus in hand from its screen.
     *
     * @param remove    true to take back the piece covering the cell, false to set a component from the inventory with its top left corner there
     * @param component what to set, ignored when removing
     */
    public record Edit(boolean remove, ApparatusComponent component, int x, int y, int rotation) implements CustomPacketPayload
    {
        public static final Type<Edit> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "apparatus_edit"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Edit::remove,
                ByteBufCodecs.idMapper(index -> ApparatusComponent.values()[index], ApparatusComponent::ordinal), Edit::component,
                ByteBufCodecs.VAR_INT, Edit::x,
                ByteBufCodecs.VAR_INT, Edit::y,
                ByteBufCodecs.VAR_INT, Edit::rotation,
                Edit::new);

        @Override
        public Type<? extends CustomPacketPayload> type()
        {
            return TYPE;
        }
    }

    private ShieldPayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Hit.TYPE, Hit.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE, Edit.STREAM_CODEC);
    }
}
