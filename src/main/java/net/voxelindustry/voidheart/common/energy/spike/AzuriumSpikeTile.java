package net.voxelindustry.voidheart.common.energy.spike;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.flow.LeyConversion;
import net.voxelindustry.voidheart.common.ley.flow.LeyTopology;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * The base of an Azurium Spike. Each tick it reaches for its share of the ley where it stands, offers the void energy that ley would give through
 * {@link VoidEnergy#offer}, and converts through {@link LeyConversion} only the ley paying for what the network took. A spike nothing draws from
 * converts nothing, so it never taints a node for energy that goes nowhere.
 * <p>
 * Converting on a tainted node also gives Contravoid, in proportion to the taint, to the Contravoid collectors around it.
 */
public class AzuriumSpikeTile extends TileBase
{
    /**
     * Ticks without any output before the spike counts as idle, so a network taking in fits doesn't make its bands stutter.
     */
    public static final int IDLE_GRACE_TICKS = 10;
    /**
     * Output steps shown to clients: 0 is idle, the last is {@link LeySpikeBalance#MAX_LEY} converted.
     */
    public static final int ACTIVITY_STEPS   = 8;
    public static final float MAX_LEAN_DEGREES = 10;
    public static final float MIN_LEAN_DEGREES = 3;

    private static final double OUTPUT_SMOOTHING  = 0.1;
    private static final int    TARGET_SCAN_TICKS = 40;

    private int    setting = LeySpikeBalance.DEFAULT_SETTING;
    private double credit;
    private double contravoid;

    // Server only
    private int    ticksSinceOutput = IDLE_GRACE_TICKS;
    private double lastLey;
    private double lastFlow;
    private long   lastOutput;
    private double averageOutput;
    private double averageLey;
    private double averageContravoid;
    private long   targetScannedAt  = -TARGET_SCAN_TICKS;

    // Synced to clients
    private float      leanDegrees;
    private float      leanYaw;
    private int        activity;
    @Nullable
    private BlockPos   outputTarget;

    ////////////
    // CLIENT //
    ////////////

    private final SpikeAnimation animation = new SpikeAnimation();

    public AzuriumSpikeTile(BlockPos pos, BlockState state)
    {
        super(LeySpikeContent.AZURIUM_SPIKE_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, AzuriumSpikeTile spike)
    {
        if (level instanceof ServerLevel serverLevel)
            spike.convert(serverLevel);
        else
            spike.animation.tick(level, spike.activity / (float) ACTIVITY_STEPS, spike.outputTarget);
    }

    /**
     * Sets the lean it was driven in at, once, when placed.
     */
    public void driveIn(RandomSource random)
    {
        leanDegrees = MIN_LEAN_DEGREES + random.nextFloat() * (MAX_LEAN_DEGREES - MIN_LEAN_DEGREES);
        leanYaw = random.nextFloat() * 360;
        setChanged();
        sync();
    }

    private void convert(ServerLevel level)
    {
        lastFlow = Ley.lineFlowAt(level, worldPosition);
        var target = LeySpikeMath.targetLey(lastFlow, setting);
        // What the line would grant, upstream claims first. The conversion below replaces this claim with the ley actually converted.
        var granted = target > 0 ? Ley.claim(level, worldPosition, target) : 0;
        var step = LeySpikeMath.step(granted, credit, amount -> VoidEnergy.offer(level, worldPosition, amount));
        if (step.ley() > 0)
            LeyConversion.convert(level, worldPosition, step.ley());
        else if (granted > 0)
            // Ley.claim ignores a claim of nothing, which would leave the probe above held for others to wait on, so it is released at the provider
            Ley.provider().claim(level, worldPosition, 0);

        if (step.credit() != credit)
            setChanged();
        credit = step.credit();
        lastLey = step.ley();
        lastOutput = step.energy();
        averageOutput += (lastOutput - averageOutput) * OUTPUT_SMOOTHING;
        averageLey += (lastLey - averageLey) * OUTPUT_SMOOTHING;
        ticksSinceOutput = lastOutput > 0 ? 0 : Math.min(IDLE_GRACE_TICKS, ticksSinceOutput + 1);

        emitContravoid(level, step.ley());
        updateShown(level);
    }

    private void emitContravoid(ServerLevel level, double ley)
    {
        var node = node(level);
        var perTick = node.isPresent() ? LeySpikeMath.contravoidPerTick(ley, Ley.taint(level, node.getAsLong())) : 0;
        averageContravoid += (perTick - averageContravoid) * OUTPUT_SMOOTHING;
        if (perTick > 0)
        {
            contravoid += perTick;
            setChanged();
        }

        if (Math.floorMod(level.getGameTime() + worldPosition.hashCode(), LeySpikeBalance.EMIT_TICKS) != 0)
            return;
        var whole = (int) Math.floor(contravoid);
        if (whole <= 0)
            return;
        // What no collector takes is lost, as Suffering is when a mob dies far from any
        ContravoidHarvest.emit(level, worldPosition, whole);
        contravoid -= whole;
        setChanged();
    }

    /**
     * The node whose flow this spike bleeds: the node it stands on, or the upstream node of its line.
     */
    public OptionalLong node(ServerLevel level)
    {
        return LeyTopology.get(level)
                .map(topology -> topology.attachment(worldPosition.getX(), worldPosition.getZ()))
                .filter(attachment -> !attachment.isNone())
                .map(attachment -> OptionalLong.of(attachment.sourceNode()))
                .orElse(OptionalLong.empty());
    }

    /**
     * What clients draw: how fast the bands climb, and where the arc goes. Synced only when one of them changes.
     */
    private void updateShown(ServerLevel level)
    {
        var active = ticksSinceOutput < IDLE_GRACE_TICKS;
        var newActivity = active ? Math.clamp(Math.round(averageLey / LeySpikeBalance.MAX_LEY * ACTIVITY_STEPS), 1, ACTIVITY_STEPS) : 0;

        var newTarget = outputTarget;
        if (!active)
            newTarget = null;
        else if (outputTarget == null || level.getGameTime() - targetScannedAt >= TARGET_SCAN_TICKS)
        {
            targetScannedAt = level.getGameTime();
            newTarget = findTarget(level, worldPosition).orElse(null);
        }

        if (newActivity == activity && Objects.equals(newTarget, outputTarget))
            return;
        activity = newActivity;
        outputTarget = newTarget;
        setChanged();
        sync();
    }

    /**
     * Where the output arc lands: a creative well whose field covers the spike, since those answer first, or else the bundle of the nearest pylon whose
     * field covers it, which is where the grid takes what it accepts.
     */
    static Optional<BlockPos> findTarget(ServerLevel level, BlockPos pos)
    {
        var center = Vec3.atCenterOf(pos);
        var well = CreativeSources.wells(level).centers().stream()
                .filter(candidate -> candidate.distSqr(pos) <= (double) FoundationBalance.CREATIVE_WELL_RADIUS * FoundationBalance.CREATIVE_WELL_RADIUS)
                .min((a, b) -> Double.compare(a.distToCenterSqr(center), b.distToCenterSqr(center)));
        if (well.isPresent())
            return well;

        return VoidGrid.pylonsCovering(level, pos).stream()
                .map(GridLayout.Pylon::bundle)
                .min((a, b) -> Double.compare(a.distToCenterSqr(center), b.distToCenterSqr(center)));
    }

    public void cycleSetting(int by)
    {
        setSetting(Math.floorMod(setting + by, LeySpikeBalance.SHARES.length));
    }

    public void setSetting(int setting)
    {
        this.setting = LeySpikeMath.clampSetting(setting);
        setChanged();
    }

    public int setting()
    {
        return setting;
    }

    /**
     * Ley converted on the last tick.
     */
    public double lastLey()
    {
        return lastLey;
    }

    /**
     * The flow where the spike stands on the last tick.
     */
    public double lastFlow()
    {
        return lastFlow;
    }

    /**
     * Void energy the network took on the last tick.
     */
    public long lastOutput()
    {
        return lastOutput;
    }

    public double averageOutput()
    {
        return averageOutput;
    }

    public double averageLey()
    {
        return averageLey;
    }

    public double averageContravoid()
    {
        return averageContravoid;
    }

    public boolean isIdle()
    {
        return activity == 0;
    }

    public int activity()
    {
        return activity;
    }

    @Nullable
    public BlockPos outputTarget()
    {
        return outputTarget;
    }

    public float leanDegrees()
    {
        return leanDegrees;
    }

    public float leanYaw()
    {
        return leanYaw;
    }

    public SpikeAnimation animation()
    {
        return animation;
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        setting = LeySpikeMath.clampSetting(nbt.getIntOr("setting", LeySpikeBalance.DEFAULT_SETTING));
        credit = Math.clamp(nbt.getDoubleOr("credit", 0), 0, 1);
        contravoid = Math.clamp(nbt.getDoubleOr("contravoid", 0), 0, 1_000);

        leanDegrees = Math.clamp(nbt.getFloatOr("leanDegrees", 0), 0, MAX_LEAN_DEGREES);
        leanYaw = nbt.getFloatOr("leanYaw", 0);
        activity = Math.clamp(nbt.getIntOr("activity", 0), 0, ACTIVITY_STEPS);
        outputTarget = nbt.read("outputTarget", BlockPos.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.putInt("setting", setting);
        nbt.putDouble("credit", credit);
        nbt.putDouble("contravoid", contravoid);

        nbt.putFloat("leanDegrees", leanDegrees);
        nbt.putFloat("leanYaw", leanYaw);
        nbt.putInt("activity", activity);
        if (outputTarget != null)
            nbt.store("outputTarget", BlockPos.CODEC, outputTarget);
    }
}
