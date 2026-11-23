package net.voxelindustry.voidheart.common.ley.anchor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.ley.LeyHash;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Wrought anchors of a level: built by players beside a ley line, they reach every other wrought anchor in the level for void energy, and the
 * Old Anchors around them as if they were one. Old Anchors list the wrought anchors that reach them.
 */
public class WroughtAnchors
{
    private static final long SALT_NAME = 31;

    private final OldAnchors anchors;
    private final WroughtAnchorState state;
    private final Map<Long, List<OldAnchor>> linkedById = new HashMap<>();

    WroughtAnchors(OldAnchors anchors, WroughtAnchorState state)
    {
        this.anchors = anchors;
        this.state = state;
    }

    public Optional<WroughtAnchor> get(long id)
    {
        return state.get(id);
    }

    public boolean canStandAt(BlockPos pos)
    {
        return anchors.network().nearLine(pos.getX(), pos.getZ(), WroughtAnchorBalance.LINE_REACH);
    }

    /**
     * Records a wrought anchor just built, or one standing in the world that the level has no record of, such as a copied one.
     */
    public WroughtAnchor register(String name, BlockPos pos)
    {
        var anchor = state.add(name.isEmpty() ? defaultName(pos) : name, pos.getX(), pos.getY(), pos.getZ());
        changed();
        return anchor;
    }

    public void unregister(long id)
    {
        if (state.remove(id))
            changed();
    }

    /**
     * Forgets the anchor only if it stands at pos, so taking down a copy that wasn't recorded yet leaves the original alone.
     */
    public void unregisterAt(long id, BlockPos pos)
    {
        if (state.get(id).filter(anchor -> anchor.pos().equals(pos)).isPresent())
            unregister(id);
    }

    public String defaultName(BlockPos pos)
    {
        return LeyAnchorNames.name(anchors.level().getSeed(), LeyHash.hash(pos.getX(), pos.getY(), pos.getZ(), SALT_NAME));
    }

    private void changed()
    {
        linkedById.clear();
        anchors.forgetTerrain();
        AnchorWaypoints.invalidate();
    }

    /**
     * The Old Anchors this wrought anchor reaches without leaving the lines around it.
     */
    public List<OldAnchor> linked(WroughtAnchor anchor)
    {
        return linkedById.computeIfAbsent(anchor.id(), key -> anchors.network().linkedAt(anchor.x(), anchor.z()));
    }

    /**
     * The wrought anchors that reach this Old Anchor through the lines.
     */
    public List<WroughtAnchor> linkedTo(OldAnchor old)
    {
        return state.all().stream()
                .filter(anchor -> anchor.distanceTo(old.x(), old.z()) <= LeyAnchorNetwork.SEED_RADIUS)
                .filter(anchor -> linked(anchor).stream().anyMatch(linked -> linked.nodeId() == old.nodeId()))
                .toList();
    }

    /**
     * Every other wrought anchor in the level, closest first.
     */
    public List<WroughtAnchor> others(WroughtAnchor anchor)
    {
        return state.all().stream()
                .filter(other -> other.id() != anchor.id())
                .sorted(Comparator.comparingDouble(other -> other.distanceTo(anchor.x(), anchor.z())))
                .toList();
    }

    public List<AnchorView> waypoints(WroughtAnchor anchor)
    {
        return Stream.concat(linked(anchor).stream().map(anchors::view), others(anchor).stream().map(WroughtAnchor::view)).toList();
    }

    public Optional<WroughtAnchor> near(Vec3 position)
    {
        return state.all().stream()
                .filter(anchor -> Math.abs(position.y - anchor.y()) <= OldAnchors.NEAR_HEIGHT)
                .filter(anchor -> horizontalDistanceSq(anchor, position) <= OldAnchors.NEAR_RADIUS * OldAnchors.NEAR_RADIUS)
                .min(Comparator.comparingDouble(anchor -> horizontalDistanceSq(anchor, position)));
    }

    private static double horizontalDistanceSq(WroughtAnchor anchor, Vec3 position)
    {
        double dx = anchor.x() + 0.5 - position.x;
        double dz = anchor.z() + 0.5 - position.z;
        return dx * dx + dz * dz;
    }

    private Optional<WroughtAnchorTile> tile(WroughtAnchor anchor)
    {
        return anchors.level().getBlockEntity(anchor.pos(), VoidHeartTiles.WROUGHT_ANCHOR).filter(tile -> tile.getAnchorId() == anchor.id());
    }

    /**
     * Whether a standing anchor can still be reached, loading its chunk if needed. One whose block is gone is forgotten.
     */
    public boolean standing(WroughtAnchor anchor)
    {
        anchors.level().getChunk(anchor.x() >> 4, anchor.z() >> 4);
        if (tile(anchor).isPresent())
            return true;
        unregister(anchor.id());
        return false;
    }

    public void use(ServerPlayer player, WroughtAnchor anchor)
    {
        var map = anchors.travelMap(anchor.view(), linked(anchor).stream().map(anchors::view).toList(), others(anchor).stream().map(WroughtAnchor::view).toList());
        ServerPlayNetworking.send(player, map);
        anchors.terrain().send(player, map);
    }

    public void rename(ServerPlayer player, long id, String name)
    {
        var anchor = state.get(id);
        var cleaned = WroughtAnchorBalance.cleanName(name);
        if (anchor.isEmpty() || cleaned.isEmpty() || cleaned.equals(anchor.get().name()) || !inReach(player, anchor.get()))
            return;

        var renamed = anchor.get().named(cleaned);
        state.update(renamed);
        tile(renamed).ifPresent(tile -> tile.setName(cleaned));
        AnchorWaypoints.invalidate();
        player.sendOverlayMessage(Component.translatable("voidheart.wrought_anchor.renamed", cleaned));
    }

    boolean inReach(ServerPlayer player, WroughtAnchor anchor)
    {
        return player.level() == anchors.level() && player.position().distanceToSqr(Vec3.atCenterOf(anchor.pos())) <= AnchorTravel.REACH * AnchorTravel.REACH;
    }

    /**
     * Starts gathering the void energy a trip from this anchor costs. The trip leaves once it is gathered.
     */
    public void requestTravel(ServerPlayer player, long sourceId, long destinationId)
    {
        var source = state.get(sourceId);
        if (source.isEmpty() || sourceId == destinationId || !inReach(player, source.get()))
            return;

        String destinationName;
        int destinationX, destinationZ;
        if (WroughtAnchor.isWrought(destinationId))
        {
            var destination = state.get(destinationId);
            if (destination.isEmpty())
                return;
            destinationName = destination.get().name();
            destinationX = destination.get().x();
            destinationZ = destination.get().z();
        }
        else
        {
            var destination = linked(source.get()).stream().filter(anchor -> anchor.nodeId() == destinationId).findFirst();
            if (destination.isEmpty())
                return;
            if (!anchors.isActive(destination.get()))
            {
                player.sendOverlayMessage(Component.translatable("voidheart.anchor.dormant", destination.get().name()));
                return;
            }
            destinationName = destination.get().name();
            destinationX = destination.get().x();
            destinationZ = destination.get().z();
        }

        var tile = tile(source.get());
        if (tile.isEmpty())
        {
            unregister(sourceId);
            return;
        }
        if (tile.get().isGathering())
        {
            player.sendOverlayMessage(Component.translatable("voidheart.wrought_anchor.busy"));
            return;
        }
        if (VoidEnergy.available(anchors.level(), source.get().pos()) <= 0)
        {
            player.sendOverlayMessage(Component.translatable("voidheart.wrought_anchor.no_void"));
            return;
        }

        long cost = WroughtAnchorBalance.travelCost(source.get().distanceTo(destinationX, destinationZ));
        tile.get().startGathering(player, destinationId, destinationName, cost);
    }

    public ServerLevel level()
    {
        return anchors.level();
    }

    OldAnchors anchors()
    {
        return anchors;
    }
}
