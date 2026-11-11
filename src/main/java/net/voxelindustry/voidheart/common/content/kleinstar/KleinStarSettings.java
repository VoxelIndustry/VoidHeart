package net.voxelindustry.voidheart.common.content.kleinstar;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Klein Star's settings that a client has to agree with its server on: what a star costs, which decides whether the staff answers a click and
 * what its tooltip says, and how far its blast reaches, which is the ring its bearer is shown. The server's own config decides both. It tells each
 * player on joining and everyone again when they change, and a client goes by what it was told for as long as it is connected.
 */
public final class KleinStarSettings
{
    private static final int RECHECK_TICKS = 40;

    /**
     * @param cost   Contravoid each star burns
     * @param radius blocks its blast reaches
     */
    public record Told(int cost, float radius) implements CustomPacketPayload
    {
        public static final Type<Told> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MODID, "klein_star_settings"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Told> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Told::cost,
                ByteBufCodecs.FLOAT, Told::radius,
                Told::new);

        @Override
        public Type<Told> type()
        {
            return TYPE;
        }
    }

    // What the server last said, on a client
    private static volatile @Nullable Told told;
    // What this server last said
    private static @Nullable Told         sent;

    private KleinStarSettings()
    {
    }

    public static void register()
    {
        PayloadTypeRegistry.clientboundPlay().register(Told.TYPE, Told.CODEC);
    }

    private static Told own()
    {
        return new Told(VoidHeartConfig.get().kleinStarContravoidCost, VoidHeartConfig.get().kleinStarRadius);
    }

    /**
     * @return what a star costs, as the server of this level has it
     */
    public static int cost(Level level)
    {
        var known = told;
        return level.isClientSide() && known != null ? known.cost() : VoidHeartConfig.get().kleinStarContravoidCost;
    }

    /**
     * @return what a star costs as far as this side knows, where there is no level to ask: for a tooltip
     */
    public static int shownCost()
    {
        var known = told;
        return known != null ? known.cost() : VoidHeartConfig.get().kleinStarContravoidCost;
    }

    /**
     * @return how far a blast reaches, as the server of this level has it
     */
    public static float radius(Level level)
    {
        var known = told;
        return level.isClientSide() && known != null ? known.radius() : VoidHeartConfig.get().kleinStarRadius;
    }

    public static void onJoin(ServerPlayer player)
    {
        ServerPlayNetworking.send(player, own());
    }

    public static void tickServer(MinecraftServer server)
    {
        if (server.getTickCount() % RECHECK_TICKS != 0)
            return;
        var now = own();
        if (now.equals(sent))
            return;
        sent = now;
        server.getPlayerList().getPlayers().forEach(player -> ServerPlayNetworking.send(player, now));
    }

    /** On a client, the server's word. */
    public static void accept(Told fromServer)
    {
        told = fromServer;
    }

    /** On a client, on leaving a server. */
    public static void forget()
    {
        told = null;
    }

    /** On a server, on stopping. */
    public static void clear()
    {
        sent = null;
    }
}
