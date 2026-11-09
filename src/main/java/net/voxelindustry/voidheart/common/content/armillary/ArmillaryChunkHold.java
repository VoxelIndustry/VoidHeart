package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps an armillary that holds the Daedalus open ticking, with the base around it, while every player is inside. Otherwise its chunk unloads, the hold
 * goes unpaid and a siphon can't find it. The ticket is saved with the level, so it survives a restart until the armillary lets it go.
 */
public final class ArmillaryChunkHold
{
    /**
     * Chunks each way that keep ticking, so pylons, engines and constructs built around the armillary keep paying its hold.
     */
    public static final int TICKING_RADIUS = 2;

    // A ticket's radius counts fully loaded chunks, and block entities tick one chunk further in than that
    private static final int TICKET_RADIUS = TICKING_RADIUS + 1;

    public static final TicketType TICKET = new TicketType(TicketType.NO_TIMEOUT,
            TicketType.FLAG_PERSIST | TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    private ArmillaryChunkHold()
    {
    }

    public static void register()
    {
        Registry.register(BuiltInRegistries.TICKET_TYPE, Identifier.fromNamespaceAndPath(MODID, "armillary_hold"), TICKET);
    }

    /**
     * Adding the same hold twice keeps one ticket, so this is safe to call again after a reload.
     */
    public static void hold(ServerLevel level, BlockPos armillary)
    {
        level.getChunkSource().addTicketWithRadius(TICKET, ChunkPos.containing(armillary), TICKET_RADIUS);
    }

    public static void release(ServerLevel level, BlockPos armillary)
    {
        level.getChunkSource().removeTicketWithRadius(TICKET, ChunkPos.containing(armillary), TICKET_RADIUS);
    }
}
