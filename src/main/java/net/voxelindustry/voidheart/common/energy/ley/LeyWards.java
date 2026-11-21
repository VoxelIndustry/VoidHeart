package net.voxelindustry.voidheart.common.energy.ley;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.curse.Corruption;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * How far around a mob its path search looks for wards that keep it out.
     */
    private static final double KEEP_GATHER = 48;

    /**
     * @param keepsOut the creature kind an imprinted memory set in it keeps out, or null
     */
    private record Ward(float strength, @Nullable EntityType<?> keepsOut)
    {
    }

    /**
     * A ward's reach that a kind may not enter, as a sphere.
     */
    public record Keep(Vec3 center, double radiusSq)
    {
        public boolean contains(double x, double y, double z)
        {
            return center.distanceToSqr(x, y, z) <= radiusSq;
        }
    }

    private static final Map<ServerLevel, Map<BlockPos, Ward>> BY_LEVEL  = new WeakHashMap<>();
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

    static void update(ServerLevel level, BlockPos ward, float strength, @Nullable EntityType<?> keepsOut)
    {
        BY_LEVEL.computeIfAbsent(level, key -> new HashMap<>()).put(ward.immutable(), new Ward(strength, keepsOut));
    }

    /**
     * The reach of every holding ward near the mob that keeps its kind out, but those it already stands in, which it must be free to leave.
     */
    public static List<Keep> keepingOut(Mob mob)
    {
        if (!(mob.level() instanceof ServerLevel level))
            return List.of();
        var wards = BY_LEVEL.get(level);
        if (wards == null || wards.isEmpty())
            return List.of();
        List<Keep> keeps = null;
        double radiusSq = (double) LeyStoneBalance.WARD_RADIUS * LeyStoneBalance.WARD_RADIUS;
        double gatherSq = (LeyStoneBalance.WARD_RADIUS + KEEP_GATHER) * (LeyStoneBalance.WARD_RADIUS + KEEP_GATHER);
        for (var entry : wards.entrySet())
        {
            var ward = entry.getValue();
            if (ward.keepsOut() != mob.getType() || !(ward.strength() > 0))
                continue;
            var center = Vec3.atCenterOf(entry.getKey());
            double distanceSq = center.distanceToSqr(mob.position());
            if (distanceSq <= radiusSq || distanceSq > gatherSq)
                continue;
            if (keeps == null)
                keeps = new ArrayList<>();
            keeps.add(new Keep(center, radiusSq));
        }
        return keeps == null ? List.of() : keeps;
    }

    /**
     * The center of a holding ward that keeps the mob's kind out and that it stands in, or null.
     */
    public static @Nullable Vec3 keptOutOf(Mob mob)
    {
        if (!(mob.level() instanceof ServerLevel level))
            return null;
        var wards = BY_LEVEL.get(level);
        if (wards == null || wards.isEmpty())
            return null;
        double radiusSq = (double) LeyStoneBalance.WARD_RADIUS * LeyStoneBalance.WARD_RADIUS;
        for (var entry : wards.entrySet())
        {
            var ward = entry.getValue();
            if (ward.keepsOut() == mob.getType() && ward.strength() > 0 && Vec3.atCenterOf(entry.getKey()).distanceToSqr(mob.position()) <= radiusSq)
                return Vec3.atCenterOf(entry.getKey());
        }
        return null;
    }

    /**
     * Whether a holding ward keeps that kind out of the spot.
     */
    public static boolean keepsOut(ServerLevel level, EntityType<?> type, double x, double y, double z)
    {
        var wards = BY_LEVEL.get(level);
        if (wards == null || wards.isEmpty())
            return false;
        double radiusSq = (double) LeyStoneBalance.WARD_RADIUS * LeyStoneBalance.WARD_RADIUS;
        for (var entry : wards.entrySet())
        {
            var ward = entry.getValue();
            if (ward.keepsOut() == type && ward.strength() > 0 && Vec3.atCenterOf(entry.getKey()).distanceToSqr(x, y, z) <= radiusSq)
                return true;
        }
        return false;
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
            float strength = ward.getValue().strength();
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
