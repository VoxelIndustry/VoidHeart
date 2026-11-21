package net.voxelindustry.voidheart.common.content.hoard;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The essence storages standing in pockets, which pour what they hold into their pocket's reserve and then show its fill. Each loaded storage is
 * looked at once a second: one newly in a pocket joins, one already joined shows the reserve again.
 */
public final class ReserveVessels
{
    private static final int REFRESH_TICKS = 20;

    /**
     * A storage that can serve as a vessel of its pocket's reserve.
     */
    public interface Vessel
    {
        boolean isRemoved();

        /**
         * Joins the reserve of the pocket it stands in, if it has not, and shows the reserve's fill.
         */
        void refreshVessel(ServerLevel level, PocketHoard.Hoard hoard);

        ServerLevel serverLevel();

        BlockPos vesselPos();
    }

    private static final Set<Vessel> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    private ReserveVessels()
    {
    }

    static void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(ReserveVessels::tick);
    }

    public static void track(Vessel vessel)
    {
        LOADED.add(vessel);
    }

    /**
     * @return the hoard of the pocket a position in the void lies in
     */
    public static Optional<PocketHoard.Hoard> pocketAt(ServerLevel level, BlockPos pos)
    {
        if (level.dimension() != VoidHeart.VOID_WORLD_KEY)
            return Optional.empty();
        return VoidPocketState.getVoidPocketState(level).ownerAt(pos).map(owner -> PocketHoard.get(level.getServer()).of(owner));
    }

    /**
     * @return the reserve slot of a storage's face, index 0 for a single storage
     */
    public static PocketReserve.SlotId slot(BlockPos pos, int index)
    {
        return new PocketReserve.SlotId(pos.asLong(), index);
    }

    private static void tick(MinecraftServer server)
    {
        if (server.getTickCount() % REFRESH_TICKS != 0 || LOADED.isEmpty())
            return;
        for (var vessel : List.copyOf(LOADED))
        {
            if (vessel.isRemoved() || vessel.serverLevel() == null)
            {
                LOADED.remove(vessel);
                continue;
            }
            var hoard = pocketAt(vessel.serverLevel(), vessel.vesselPos());
            if (hoard.isEmpty())
                LOADED.remove(vessel);
            else
                vessel.refreshVessel(vessel.serverLevel(), hoard.get());
        }
    }
}
