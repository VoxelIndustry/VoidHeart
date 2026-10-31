package net.voxelindustry.voidheart.common.content.permeablebarrier;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.permeablebarrier.BarrierConfig.CoOwner;

import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Sent by the barrier screen to edit the config of the structure it was opened on.
 */
public record BarrierConfigurePayload(Action action, int flag, Optional<CoOwner> coOwner) implements CustomPacketPayload
{
    public static final Type<BarrierConfigurePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "barrier_configure"));

    private static final StreamCodec<ByteBuf, CoOwner> CO_OWNER_STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CoOwner::id,
            ByteBufCodecs.stringUtf8(16), CoOwner::name,
            CoOwner::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, BarrierConfigurePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> Action.values()[index], Action::ordinal), BarrierConfigurePayload::action,
            ByteBufCodecs.VAR_INT, BarrierConfigurePayload::flag,
            ByteBufCodecs.optional(CO_OWNER_STREAM_CODEC), BarrierConfigurePayload::coOwner,
            BarrierConfigurePayload::new);

    public enum Action
    {
        TOGGLE_FLAG,
        SET_APPEARANCE,
        CLEAR_APPEARANCE,
        TOGGLE_TEAM,
        TOGGLE_CO_OWNER
    }

    public static BarrierConfigurePayload of(Action action)
    {
        return new BarrierConfigurePayload(action, 0, Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }

    public static void handle(BarrierConfigurePayload payload, ServerPlayNetworking.Context context)
    {
        var player = context.player();
        if (!(player.containerMenu instanceof PermeableBarrierMenu menu) || !menu.stillValid(player))
            return;

        var level = player.level();
        var tile = PermeableBarrierStructure.getTile(level, menu.getPos());
        if (tile == null || !tile.getConfig().isAlly(player))
            return;

        switch (payload.action())
        {
            case TOGGLE_FLAG ->
            {
                var flags = BarrierConfig.Flag.values();
                if (payload.flag() >= 0 && payload.flag() < flags.length)
                    PermeableBarrierStructure.updateConfig(level, menu.getPos(), config -> config.toggle(flags[payload.flag()]));
            }
            case SET_APPEARANCE -> menu.trySetAppearance(player, menu.getCarried());
            case CLEAR_APPEARANCE -> PermeableBarrierStructure.updateConfig(level, menu.getPos(), config -> config.withAppearance(Optional.empty()));
            case TOGGLE_TEAM ->
            {
                var team = player.getTeam();
                PermeableBarrierStructure.updateConfig(level, menu.getPos(), config ->
                        config.withOwnerTeam(config.ownerTeam().isPresent() || team == null ? Optional.empty() : Optional.of(team.getName())));
            }
            case TOGGLE_CO_OWNER -> payload.coOwner()
                    .filter(coOwner -> !coOwner.id().equals(player.getUUID()))
                    .ifPresent(coOwner -> PermeableBarrierStructure.updateConfig(level, menu.getPos(), config -> config.toggleCoOwner(coOwner)));
        }
    }
}
