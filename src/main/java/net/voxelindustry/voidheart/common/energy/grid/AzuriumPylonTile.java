package net.voxelindustry.voidheart.common.energy.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Joins its level's {@link VoidGrid} while loaded. The grid hands it what it passed, averaged, which it syncs so clients can draw its glow, arcs and threads.
 */
public class AzuriumPylonTile extends TileBase
{
    /**
     * A pylon or machine at the other end, and how much flows toward it per tick; negative when it flows in from there.
     */
    public record Flow(BlockPos pos, long rate)
    {
        public static final Codec<Flow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Flow::pos),
                Codec.LONG.fieldOf("rate").forGetter(Flow::rate)
        ).apply(instance, Flow::new));
    }

    /**
     * Pylons loaded on the client, for the renderer.
     */
    public static final Set<AzuriumPylonTile> CLIENT_PYLONS = ConcurrentHashMap.newKeySet();

    private static final int GLOW_EASE_TICKS = 10;

    private long       throughput;
    private List<Flow> arcs = List.of();
    private List<Flow> fed  = List.of();
    // The middles of the portals a Deep field reaches on through
    private List<Vec3> deepReaches = List.of();

    // Client only: load eased toward the synced one, current then previous tick
    private float glow;
    private float previousGlow;

    public AzuriumPylonTile(BlockPos pos, BlockState state)
    {
        super(VoidGridContent.AZURIUM_PYLON_TILE, pos, state);
    }

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        join();
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        join();
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (getLevel() instanceof ServerLevel serverLevel)
            VoidGrid.leave(serverLevel, this);
        else
            CLIENT_PYLONS.remove(this);
    }

    private void join()
    {
        if (getLevel() instanceof ServerLevel serverLevel)
            VoidGrid.join(serverLevel, this);
        else if (getLevel() != null)
            CLIENT_PYLONS.add(this);
    }

    public static void tickClient(Level level, BlockPos pos, BlockState state, AzuriumPylonTile pylon)
    {
        pylon.previousGlow = pylon.glow;
        float target = pylon.load();
        float step = 1F / GLOW_EASE_TICKS;
        pylon.glow = pylon.glow < target ? Math.min(target, pylon.glow + step) : Math.max(target, pylon.glow - step);
    }

    public PylonBundle bundle()
    {
        return getLevel() == null ? PylonBundle.NONE : PylonBundle.held(getLevel(), getBlockPos());
    }

    public boolean hanging()
    {
        return AzuriumPylonBlock.claws(getBlockState()) == Direction.DOWN;
    }

    public BlockPos bundlePos()
    {
        return AzuriumPylonBlock.bundlePos(getBlockState(), getBlockPos());
    }

    /**
     * The point of the bundle's crystals, where arcs leave and land: their top, or their bottom when the pylon hangs.
     */
    public Vec3 tip()
    {
        return alongBundle(bundle().height() / 16D);
    }

    public Vec3 bundleCenter()
    {
        return alongBundle(bundle().height() / 32D);
    }

    private Vec3 alongBundle(double reach)
    {
        var pos = getBlockPos();
        double y = hanging() ? pos.getY() - reach : pos.getY() + 1 + reach;
        return new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
    }

    /**
     * Throughput over cap, 0 to 1.
     */
    public float load()
    {
        long cap = bundle().cap();
        return cap <= 0 ? 0 : Math.clamp(throughput / (float) cap, 0, 1);
    }

    public float glow(float partialTicks)
    {
        return previousGlow + (glow - previousGlow) * partialTicks;
    }

    public long throughput()
    {
        return throughput;
    }

    public List<Flow> arcs()
    {
        return arcs;
    }

    public List<Flow> fed()
    {
        return fed;
    }

    public List<Vec3> deepReaches()
    {
        return deepReaches;
    }

    void setDeepReaches(List<Vec3> reaches)
    {
        if (reaches.equals(deepReaches))
            return;
        deepReaches = List.copyOf(reaches);
        sync();
    }

    void setDisplay(VoidGridLedger.PylonDisplay display)
    {
        var newArcs = sorted(display.arcs());
        var newFed = sorted(display.fed());
        if (throughput == display.throughput() && arcs.equals(newArcs) && fed.equals(newFed))
            return;

        throughput = display.throughput();
        arcs = newArcs;
        fed = newFed;
        sync();
    }

    private static List<Flow> sorted(Map<BlockPos, Long> flows)
    {
        var list = new ArrayList<Flow>();
        flows.forEach((pos, rate) -> list.add(new Flow(pos, rate)));
        list.sort(Comparator.comparingLong(flow -> flow.pos().asLong()));
        return List.copyOf(list);
    }

    @Override
    protected void loadAdditional(ValueInput input)
    {
        super.loadAdditional(input);
        throughput = input.getLongOr("throughput", 0);
        arcs = input.read("arcs", Flow.CODEC.listOf()).orElse(List.of());
        fed = input.read("fed", Flow.CODEC.listOf()).orElse(List.of());
        deepReaches = input.read("deep_reaches", Vec3.CODEC.listOf()).orElse(List.of());
    }

    @Override
    protected void saveAdditional(ValueOutput output)
    {
        super.saveAdditional(output);
        output.putLong("throughput", throughput);
        output.store("arcs", Flow.CODEC.listOf(), arcs);
        output.store("fed", Flow.CODEC.listOf(), fed);
        output.store("deep_reaches", Vec3.CODEC.listOf(), deepReaches);
    }
}
