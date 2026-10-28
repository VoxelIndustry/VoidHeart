package net.voxelindustry.voidheart.common.ley.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.VoidHeartTicker;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import static net.voxelindustry.voidheart.common.ley.LeyLatticeGenerator.REGION_SIZE;

/**
 * Old Anchors of a level: built into the world as their chunks load, awakened by players, travelled between.
 */
public class OldAnchors
{
    public static final int NEAR_RADIUS = 6;
    private static final int NEAR_HEIGHT = 4;
    private static final int MAP_MARGIN = 256;

    private static final Map<ServerLevel, OldAnchors> BY_LEVEL = new WeakHashMap<>();

    private final ServerLevel level;
    private final LeyLattice lattice;
    private final LeyAnchorNetwork network;
    private final LeyAnchorState state;
    private final AnchorTerrain terrain;

    private OldAnchors(ServerLevel level, LeyLattice lattice)
    {
        this.level = level;
        this.lattice = lattice;
        network = new LeyAnchorNetwork(level.getSeed(), lattice);
        state = LeyAnchorState.get(level);
        terrain = new AnchorTerrain(level);
    }

    public static Optional<OldAnchors> get(ServerLevel level)
    {
        return LeyLattice.get(level).map(lattice -> BY_LEVEL.computeIfAbsent(level, key -> new OldAnchors(level, lattice)));
    }

    public static void clear()
    {
        BY_LEVEL.clear();
    }

    public LeyAnchorNetwork network()
    {
        return network;
    }

    public boolean isActive(OldAnchor anchor)
    {
        return anchor.initiallyActive() || state.isAwakened(anchor.nodeId());
    }

    /**
     * Y of the lower block, estimated from the generated terrain until the anchor is built.
     */
    public int lowerY(OldAnchor anchor)
    {
        return state.placedY(anchor.nodeId()).orElseGet(() -> lattice.heights().groundHeight(anchor.x(), anchor.z()));
    }

    public AnchorView view(OldAnchor anchor)
    {
        return new AnchorView(anchor.nodeId(), anchor.name(), anchor.x(), lowerY(anchor), anchor.z(), isActive(anchor));
    }

    public List<AnchorView> linkedViews(OldAnchor anchor)
    {
        return network.linked(anchor).stream().map(this::view).toList();
    }

    public void onChunkLoad(LevelChunk chunk)
    {
        var pos = chunk.getPos();
        var missing = network.anchorsWithin(pos.getMinBlockX(), pos.getMinBlockZ(), pos.getMaxBlockX(), pos.getMaxBlockZ()).stream()
                .filter(anchor -> state.placedY(anchor.nodeId()).isEmpty())
                .toList();

        // Placing blocks from inside the chunk load callback is unsafe, wait a tick
        if (!missing.isEmpty())
            VoidHeartTicker.addDelayedTask(level.getServer(), 1, () -> missing.forEach(this::placeIfMissing));
    }

    public void placeIfMissing(OldAnchor anchor)
    {
        if (state.placedY(anchor.nodeId()).isPresent() || !level.hasChunk(anchor.x() >> 4, anchor.z() >> 4))
            return;

        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, anchor.x(), anchor.z());
        if (y + 1 >= level.getMaxY())
            return;

        var lower = new BlockPos(anchor.x(), y, anchor.z());
        var blockState = VoidHeartBlocks.OLD_ANCHOR.defaultBlockState().setValue(OldAnchorBlock.ACTIVE, isActive(anchor));
        level.setBlock(lower, blockState.setValue(OldAnchorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(lower.above(), blockState.setValue(OldAnchorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        state.markPlaced(anchor.nodeId(), y);
    }

    public void use(ServerPlayer player, BlockPos lowerPos)
    {
        var anchor = network.anchorAt(lowerPos.getX(), lowerPos.getZ());
        if (anchor.isEmpty())
        {
            player.sendOverlayMessage(Component.translatable("voidheart.anchor.silent"));
            return;
        }

        if (!isActive(anchor.get()))
        {
            awaken(anchor.get(), lowerPos);
            player.sendOverlayMessage(Component.translatable("voidheart.anchor.awakened", anchor.get().name()));
            return;
        }

        var map = travelMap(anchor.get());
        ServerPlayNetworking.send(player, map);
        terrain.send(player, map);
    }

    private void awaken(OldAnchor anchor, BlockPos lowerPos)
    {
        state.awaken(anchor.nodeId());
        for (var pos : List.of(lowerPos, lowerPos.above()))
        {
            var blockState = level.getBlockState(pos);
            if (blockState.is(VoidHeartBlocks.OLD_ANCHOR))
                level.setBlock(pos, blockState.setValue(OldAnchorBlock.ACTIVE, true), Block.UPDATE_ALL);
        }

        var center = Vec3.atCenterOf(lowerPos.above());
        level.playSound(null, lowerPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1, 1.2F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 60, 0.4, 0.8, 0.4, 0.05);
        AnchorWaypoints.invalidate();
    }

    public AnchorPayloads.TravelMap travelMap(OldAnchor source)
    {
        var linked = linkedViews(source);

        int minX = source.x(), maxX = source.x(), minZ = source.z(), maxZ = source.z();
        for (var anchor : linked)
        {
            minX = Math.min(minX, anchor.x());
            maxX = Math.max(maxX, anchor.x());
            minZ = Math.min(minZ, anchor.z());
            maxZ = Math.max(maxZ, anchor.z());
        }
        minX -= MAP_MARGIN;
        minZ -= MAP_MARGIN;
        maxX += MAP_MARGIN;
        maxZ += MAP_MARGIN;

        var lines = new ArrayList<AnchorPayloads.Segment>();
        var seen = new HashSet<String>();
        for (int regionX = Math.floorDiv(minX, REGION_SIZE); regionX <= Math.floorDiv(maxX, REGION_SIZE); regionX++)
        {
            for (int regionZ = Math.floorDiv(minZ, REGION_SIZE); regionZ <= Math.floorDiv(maxZ, REGION_SIZE); regionZ++)
            {
                for (var edge : lattice.region(regionX, regionZ).edges())
                {
                    boolean inside = edge.fromX() >= minX && edge.fromX() <= maxX && edge.fromZ() >= minZ && edge.fromZ() <= maxZ
                            || edge.toX() >= minX && edge.toX() <= maxX && edge.toZ() >= minZ && edge.toZ() <= maxZ;
                    if (inside && seen.add(edge.from() + ":" + edge.to()))
                        lines.add(new AnchorPayloads.Segment(edge.fromX(), edge.fromZ(), edge.toX(), edge.toZ()));
                }
            }
        }
        return new AnchorPayloads.TravelMap(view(source), linked, lines);
    }

    /**
     * The closest anchor the player stands by, if any.
     */
    public Optional<OldAnchor> anchorNear(Vec3 position)
    {
        int x = (int) Math.floor(position.x);
        int z = (int) Math.floor(position.z);
        return network.anchorsWithin(x - NEAR_RADIUS, z - NEAR_RADIUS, x + NEAR_RADIUS, z + NEAR_RADIUS).stream()
                .filter(anchor -> Math.abs(position.y - lowerY(anchor)) <= NEAR_HEIGHT)
                .filter(anchor -> horizontalDistanceSq(anchor, position) <= NEAR_RADIUS * NEAR_RADIUS)
                .min(Comparator.comparingDouble(anchor -> horizontalDistanceSq(anchor, position)));
    }

    private static double horizontalDistanceSq(OldAnchor anchor, Vec3 position)
    {
        double dx = anchor.x() + 0.5 - position.x;
        double dz = anchor.z() + 0.5 - position.z;
        return dx * dx + dz * dz;
    }

    public ServerLevel level()
    {
        return level;
    }
}
