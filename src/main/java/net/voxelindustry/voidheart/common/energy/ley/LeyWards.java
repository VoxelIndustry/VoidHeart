package net.voxelindustry.voidheart.common.energy.ley;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.curse.Corruption;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The wards loaded in each level and how strongly each holds. Wards don't stack: a player is held by the strongest ward around them.
 * Registered as a {@link net.voxelindustry.voidheart.common.curse.CorruptionModifier}, which {@link Corruption#raise} only asks while corruption rises, so
 * being asked is also the moment to show the ward holding it back.
 */
public final class LeyWards
{
    /**
     * At most once every 2 seconds per player, from the looks.
     */
    private static final int HOLD_SHOWN_TICKS = 40;

    private static final Map<ServerLevel, Map<BlockPos, Float>> BY_LEVEL  = new WeakHashMap<>();
    private static final Map<UUID, Long>                        HOLD_SHOWN = new HashMap<>();

    private LeyWards()
    {
    }

    public record Hold(BlockPos ward, float strength)
    {
    }

    public static void register()
    {
        Corruption.addModifier(LeyWards::multiplier);
        ServerLifecycleEvents.SERVER_STOPPED.register(server ->
        {
            BY_LEVEL.clear();
            HOLD_SHOWN.clear();
        });
    }

    static void update(ServerLevel level, BlockPos ward, float strength)
    {
        BY_LEVEL.computeIfAbsent(level, key -> new HashMap<>()).put(ward.immutable(), strength);
    }

    static void remove(ServerLevel level, BlockPos ward)
    {
        var wards = BY_LEVEL.get(level);
        if (wards != null)
            wards.remove(ward);
    }

    /**
     * The strongest ward holding the air still around pos, if any holds at all.
     */
    public static @Nullable Hold strongest(ServerLevel level, Vec3 pos)
    {
        var wards = BY_LEVEL.get(level);
        if (wards == null)
            return null;

        Hold best = null;
        double radiusSq = (double) LeyStoneBalance.WARD_RADIUS * LeyStoneBalance.WARD_RADIUS;
        for (var ward : wards.entrySet())
        {
            float strength = ward.getValue();
            if (strength > 0 && (best == null || strength > best.strength()) && Vec3.atCenterOf(ward.getKey()).distanceToSqr(pos) <= radiusSq)
                best = new Hold(ward.getKey(), strength);
        }
        return best;
    }

    private static float multiplier(ServerPlayer player)
    {
        if (!(player.level() instanceof ServerLevel level))
            return 1;
        var hold = strongest(level, player.position());
        if (hold == null)
            return 1;

        showHold(level, player, hold.ward());
        return WardStrength.multiplier(hold.strength());
    }

    private static void showHold(ServerLevel level, ServerPlayer player, BlockPos ward)
    {
        long now = level.getGameTime();
        var last = HOLD_SHOWN.get(player.getUUID());
        if (last != null && now - last < HOLD_SHOWN_TICKS && now >= last)
            return;
        HOLD_SHOWN.put(player.getUUID(), now);

        var payload = new LeyStonePayloads.WardHold(ward, player.getId());
        var viewers = new HashSet<>(PlayerLookup.tracking(player));
        viewers.add(player);
        viewers.forEach(viewer -> ServerPlayNetworking.send(viewer, payload));
    }
}
