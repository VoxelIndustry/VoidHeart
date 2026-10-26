package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Links every collector, storage and relay in range and line of sight. Linked relays form a network routing essence
 * from collectors into storages, each relay carrying a single aspect at a time.
 */
public class RavenousRelayTile extends TileBase
{
    public static final int RANGE = 8;

    private static final int SCAN_INTERVAL_TICKS = 40;

    private final List<BlockPos> linkedRelays     = new ArrayList<>();
    private final List<BlockPos> linkedCollectors = new ArrayList<>();
    private final List<BlockPos> linkedReceivers  = new ArrayList<>();
    private long lastScan = -SCAN_INTERVAL_TICKS;

    private EssenceType    transmitted = EssenceRegistry.EMPTY;
    /**
     * Collectors this relay draws from.
     */
    private List<BlockPos> flowSources = List.of();
    /**
     * Relays or storages this relay sends essence to.
     */
    private List<BlockPos> flowTargets = List.of();

    ////////////
    // CLIENT //
    ////////////

    private static final int STRAND_TICKS = 20;

    /**
     * Strand growth ticks, current then previous, keyed by the other end of the strand.
     */
    private final Map<BlockPos, int[]> incomingStrands = new HashMap<>();
    private final Map<BlockPos, int[]> outgoingStrands = new HashMap<>();
    private EssenceType displayedAspect = EssenceRegistry.EMPTY;

    public RavenousRelayTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_RELAY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, RavenousRelayTile relay)
    {
        if (world.isClientSide())
        {
            relay.tickStrands();
            return;
        }

        if (world.getGameTime() - relay.lastScan >= SCAN_INTERVAL_TICKS)
            relay.scan();

        // Spread across relays so they do not all route on the same tick
        if (Math.floorMod(world.getGameTime() + pos.asLong(), RelayNetwork.ROUTE_INTERVAL_TICKS) == 0)
            RelayNetwork.route(relay);
    }

    private void scan()
    {
        lastScan = level.getGameTime();
        linkedRelays.clear();
        linkedCollectors.clear();
        linkedReceivers.clear();

        var center = Vec3.atCenterOf(worldPosition);
        for (var chunkX = (worldPosition.getX() - RANGE) >> 4; chunkX <= (worldPosition.getX() + RANGE) >> 4; chunkX++)
        {
            for (var chunkZ = (worldPosition.getZ() - RANGE) >> 4; chunkZ <= (worldPosition.getZ() + RANGE) >> 4; chunkZ++)
            {
                var chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                    continue;

                for (var tile : chunk.getBlockEntities().values())
                {
                    var pos = tile.getBlockPos();
                    if (tile == this || tile.isRemoved() || Vec3.atCenterOf(pos).distanceTo(center) > RANGE)
                        continue;

                    List<BlockPos> links;
                    if (tile instanceof RavenousRelayTile)
                        links = linkedRelays;
                    else if (tile instanceof RavenousCollectorTile)
                        links = linkedCollectors;
                    else if (tile instanceof IEssenceReceiver)
                        links = linkedReceivers;
                    else
                        continue;

                    if (hasLineOfSight(pos))
                        links.add(pos.immutable());
                }
            }
        }
    }

    /**
     * Only blocks with a collision shape break the link, the two ends excluded.
     */
    private boolean hasLineOfSight(BlockPos target)
    {
        var from = Vec3.atCenterOf(worldPosition);
        var to = Vec3.atCenterOf(target);
        var steps = Mth.ceil(from.distanceTo(to) * 4);
        var cursor = new BlockPos.MutableBlockPos();
        for (var i = 1; i < steps; i++)
        {
            var point = from.lerp(to, i / (double) steps);
            cursor.set(point.x, point.y, point.z);
            if (cursor.equals(worldPosition) || cursor.equals(target))
                continue;
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Strands grow toward the end they feed while essence flows through them, then retract once it stops.
     */
    private void tickStrands()
    {
        if (transmitted != EssenceRegistry.EMPTY)
            displayedAspect = transmitted;

        tickStrands(incomingStrands, flowSources);
        tickStrands(outgoingStrands, flowTargets);
    }

    private static void tickStrands(Map<BlockPos, int[]> strands, List<BlockPos> active)
    {
        for (var pos : active)
            strands.computeIfAbsent(pos, key -> new int[2]);

        var iterator = strands.entrySet().iterator();
        while (iterator.hasNext())
        {
            var entry = iterator.next();
            var ticks = entry.getValue();
            ticks[1] = ticks[0];
            if (active.contains(entry.getKey()))
                ticks[0] = Math.min(STRAND_TICKS, ticks[0] + 1);
            else if (ticks[0] > 0)
                ticks[0]--;
            else
                iterator.remove();
        }
    }

    /**
     * @return growth of the strands from each collector drained, 0 when retracted and 1 when they reach this relay
     */
    public Map<BlockPos, Float> getIncomingStrands(float partialTicks)
    {
        return strandGrowths(incomingStrands, partialTicks);
    }

    /**
     * @return growth of the strands toward each relay or storage fed, 0 when retracted and 1 when they reach it
     */
    public Map<BlockPos, Float> getOutgoingStrands(float partialTicks)
    {
        return strandGrowths(outgoingStrands, partialTicks);
    }

    private static Map<BlockPos, Float> strandGrowths(Map<BlockPos, int[]> strands, float partialTicks)
    {
        var growths = new HashMap<BlockPos, Float>();
        strands.forEach((pos, ticks) -> growths.put(pos, Mth.lerp(partialTicks, ticks[1], ticks[0]) / STRAND_TICKS));
        return growths;
    }

    /**
     * Aspect of the strands, kept while they retract after the flow stopped.
     */
    public EssenceType getDisplayedAspect()
    {
        return displayedAspect;
    }

    void updateFlow(EssenceType transmitted, List<BlockPos> sources, List<BlockPos> targets)
    {
        if (this.transmitted == transmitted && flowSources.equals(sources) && flowTargets.equals(targets))
            return;

        this.transmitted = transmitted;
        flowSources = List.copyOf(sources);
        flowTargets = List.copyOf(targets);
        setChanged();
        sync();
    }

    List<BlockPos> linkedRelays()
    {
        return linkedRelays;
    }

    List<BlockPos> linkedCollectors()
    {
        return linkedCollectors;
    }

    List<BlockPos> linkedReceivers()
    {
        return linkedReceivers;
    }

    List<BlockPos> flowSources()
    {
        return flowSources;
    }

    public EssenceType transmitted()
    {
        return transmitted;
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        transmitted = nbt.read("transmitted", EssenceRegistry.TYPE_CODEC).map(EssenceRegistry.ESSENCE_REGISTRY::getValue).orElse(EssenceRegistry.EMPTY);
        flowSources = nbt.read("flowSources", BlockPos.CODEC.listOf()).orElse(List.of());
        flowTargets = nbt.read("flowTargets", BlockPos.CODEC.listOf()).orElse(List.of());
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("transmitted", EssenceRegistry.TYPE_CODEC, EssenceRegistry.ESSENCE_REGISTRY.getResourceKey(transmitted).get());
        nbt.store("flowSources", BlockPos.CODEC.listOf(), flowSources);
        nbt.store("flowTargets", BlockPos.CODEC.listOf(), flowTargets);
    }
}
