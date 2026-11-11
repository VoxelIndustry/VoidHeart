package net.voxelindustry.voidheart.common.content.wraithveil;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Mark;
import net.voxelindustry.voidheart.common.content.wraithveil.WallPassagePlan.Plan;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Hands the passage the server chose to the client that walks it, so both follow the same path.
 */
public final class WallPassagePayloads
{
    private static final StreamCodec<RegistryFriendlyByteBuf, Mark> MARK_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, Mark::center,
            Direction.STREAM_CODEC, Mark::face,
            ByteBufCodecs.DOUBLE, Mark::halfU,
            ByteBufCodecs.DOUBLE, Mark::halfV,
            Mark::new);

    private static final StreamCodec<RegistryFriendlyByteBuf, Plan> PLAN_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, Plan::tubeMin,
            BlockPos.STREAM_CODEC, Plan::tubeMax,
            Direction.STREAM_CODEC, Plan::into,
            ByteBufCodecs.VAR_INT, Plan::depth,
            MARK_CODEC, Plan::entry,
            MARK_CODEC, Plan::exit,
            Vec3.STREAM_CODEC, Plan::entrySpot,
            Vec3.STREAM_CODEC, Plan::exitSpot,
            BlockPos.STREAM_CODEC, Plan::climbFrom,
            Plan::new);

    private WallPassagePayloads()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Begin.TYPE, Begin.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(End.TYPE, End.CODEC);
    }

    public record Begin(Plan plan) implements CustomPacketPayload
    {
        public static final Type<Begin> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "wall_passage_begin"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Begin> CODEC = PLAN_CODEC.map(Begin::new, Begin::plan);

        @Override
        public Type<Begin> type()
        {
            return TYPE;
        }
    }

    /**
     * The server cut the passage short and put the player where they belong.
     */
    public record End() implements CustomPacketPayload
    {
        public static final Type<End> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "wall_passage_end"));

        public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = StreamCodec.unit(new End());

        @Override
        public Type<End> type()
        {
            return TYPE;
        }
    }
}
