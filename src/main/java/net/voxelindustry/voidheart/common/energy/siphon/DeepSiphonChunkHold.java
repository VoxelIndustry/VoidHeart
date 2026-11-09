package net.voxelindustry.voidheart.common.energy.siphon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps a siphon ticking in the Daedalus while its way holds, so it keeps drawing once every player is back at the base. Without it the siphon's chunk
 * unloads with the last player and the base goes unpaid. Saved with the level like the armillary's hold, until the siphon lets it go.
 */
public final class DeepSiphonChunkHold
{
    // A ticket's radius counts fully loaded chunks, and block entities tick one chunk further in, so 1 keeps just the siphon's own chunk ticking
    private static final int TICKET_RADIUS = 1;

    public static final TicketType TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    private DeepSiphonChunkHold()
    {
    }

    public static void register()
    {
        Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(MODID, "deep_siphon_hold"), TICKET);
    }

    /**
     * Adding the same hold twice keeps one ticket, so this is safe to call again after a reload.
     */
    public static void hold(ServerLevel level, BlockPos siphon)
    {
        level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(siphon), TICKET_RADIUS);
    }

    /**
     * Siphons in one chunk share its single ticket, so it is only let go once no other siphon there still holds it.
     */
    public static void release(ServerLevel level, BlockPos siphon)
    {
        if (level.hasChunkAt(siphon) && level.getChunkAt(siphon).getBlockEntities().values().stream()
                .anyMatch(tile -> tile instanceof DeepSiphonTile other && !other.getBlockPos().equals(siphon) && other.holdsChunk()))
            return;
        level.getChunkSource().removeTicketWithRadius(TICKET, ChunkPos.containing(siphon), TICKET_RADIUS);
    }
}
