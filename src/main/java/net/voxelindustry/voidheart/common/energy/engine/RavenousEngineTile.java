package net.voxelindustry.voidheart.common.energy.engine;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.content.essence.MultiEssenceStorage;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The maw of a Ravenous Engine. Relays link to it as they do to an athanor's intake and fill one tank per essence. Each tick it burns one essence into
 * void energy through {@link VoidEnergy#offer}, and only as much as the network took, so an engine nothing draws from burns nothing.
 * <p>
 * It gives no essence back, so no Shatter Forge mistakes it for a storage.
 */
public class RavenousEngineTile extends TileBase implements IEssenceReceiver
{
    /**
     * Ticks without any output before the engine counts as idle, so a network taking in fits doesn't make the maw snap shut.
     */
    public static final int IDLE_GRACE_TICKS = 10;
    /**
     * Output steps shown to clients: 0 is idle, the last is a full burn.
     */
    public static final int ACTIVITY_STEPS   = 4;
    // How quickly the shown output follows the real one, per tick
    private static final double OUTPUT_SMOOTHING   = 0.1;
    private static final int    TARGET_SCAN_TICKS  = 40;
    // The largest field a pylon projects, a large azurium bundle's

    private MultiEssenceStorage tank = new MultiEssenceStorage(RavenousEngineBalance.TANK, EngineFuel.values().length);
    @Nullable
    private EngineFuel burning;
    private double credit;

    // Server only
    private int    ticksSinceOutput = IDLE_GRACE_TICKS;
    private long   lastOutput;
    private double averageOutput;
    private long   targetScannedAt  = -TARGET_SCAN_TICKS;

    // Synced to clients
    @Nullable
    private EngineFuel shownFuel;
    private int        activity;
    @Nullable
    private BlockPos   outputTarget;

    ////////////
    // CLIENT //
    ////////////

    public static final int OPEN_TICKS = 10;

    private final EngineAnimation animation = new EngineAnimation();

    public RavenousEngineTile(BlockPos pos, BlockState state)
    {
        super(RavenousEngineContent.RAVENOUS_ENGINE_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RavenousEngineTile engine)
    {
        if (level instanceof ServerLevel serverLevel)
            engine.burn(serverLevel);
        else
            engine.animate(level);
    }

    private void burn(ServerLevel level)
    {
        var fuel = RavenousBurn.choose(burning, amounts());
        if (fuel != null && fuel != burning)
        {
            burning = fuel;
            setChanged();
        }

        var stored = fuel == null ? 0 : tank.amountOf(essenceOf(fuel));
        // The density only matters with essence to burn, so an empty tank may still hand over its credit as any fuel
        var step = RavenousBurn.step(fuel == null ? EngineFuel.STILLNESS : fuel, stored, credit, amount -> VoidEnergy.offer(level, worldPosition, amount));
        if (step.essence() > 0)
            tank.drain(essenceOf(fuel), step.essence());
        if (step.essence() > 0 || step.credit() != credit)
            setChanged();
        credit = step.credit();
        lastOutput = step.energy();
        averageOutput += (lastOutput - averageOutput) * OUTPUT_SMOOTHING;
        ticksSinceOutput = lastOutput > 0 ? 0 : Math.min(IDLE_GRACE_TICKS, ticksSinceOutput + 1);

        updateShown(level, fuel);
    }

    /**
     * What clients draw: the fuel burning while energy flows, how much of a full burn flows, and where it goes. Synced only when one of them changes.
     */
    private void updateShown(ServerLevel level, @Nullable EngineFuel fuel)
    {
        var active = ticksSinceOutput < IDLE_GRACE_TICKS && fuel != null;
        var newFuel = active ? fuel : null;
        var newActivity = active ? Math.clamp(Math.round(averageOutput / fuel.fullOutput() * ACTIVITY_STEPS), 1, ACTIVITY_STEPS) : 0;

        var newTarget = outputTarget;
        if (!active)
            newTarget = null;
        else if (outputTarget == null || level.getGameTime() - targetScannedAt >= TARGET_SCAN_TICKS)
        {
            targetScannedAt = level.getGameTime();
            newTarget = findTarget(level, worldPosition).orElse(null);
        }

        if (newFuel == shownFuel && newActivity == activity && Objects.equals(newTarget, outputTarget))
            return;
        shownFuel = newFuel;
        activity = newActivity;
        outputTarget = newTarget;
        setChanged();
        sync();
    }

    /**
     * Where the output arc lands: a creative well whose field covers the engine, since those answer first, or else the bundle of the nearest pylon whose
     * field covers the engine, which is where the grid takes what it accepts.
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

    private void animate(Level level)
    {
        if (animation.tick(level, shownFuel, activity / (float) ACTIVITY_STEPS, outputTarget))
            playPulseSound(level);
        else if (shownFuel == EngineFuel.STILLNESS && animation.humDue(level.getGameTime(), worldPosition))
            level.playLocalSound(worldPosition, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.12F, 0.6F, false);
    }

    private void playPulseSound(Level level)
    {
        var fuel = animation.displayedFuel();
        if (fuel == EngineFuel.FIERY && level.getRandom().nextInt(3) == 0)
            level.playLocalSound(worldPosition, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.25F, 1.2F + level.getRandom().nextFloat() * 0.3F, false);
        else if (fuel == EngineFuel.SUFFERING)
            level.playLocalSound(worldPosition, SoundEvents.GENERIC_DRINK.value(), SoundSource.BLOCKS, 0.3F, 0.45F, false);
    }

    /**
     * Essence held, indexed by {@link EngineFuel#ordinal()}.
     */
    public int[] amounts()
    {
        var amounts = new int[EngineFuel.values().length];
        for (var fuel : EngineFuel.values())
            amounts[fuel.ordinal()] = tank.amountOf(essenceOf(fuel));
        return amounts;
    }

    @Nullable
    public static EngineFuel fuelOf(EssenceType type)
    {
        if (type == EssenceRegistry.STILLNESS)
            return EngineFuel.STILLNESS;
        if (type == EssenceRegistry.FIERY)
            return EngineFuel.FIERY;
        if (type == EssenceRegistry.SUFFERING)
            return EngineFuel.SUFFERING;
        return null;
    }

    public static EssenceType essenceOf(EngineFuel fuel)
    {
        return switch (fuel)
        {
            case STILLNESS -> EssenceRegistry.STILLNESS;
            case FIERY -> EssenceRegistry.FIERY;
            case SUFFERING -> EssenceRegistry.SUFFERING;
        };
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        return fuelOf(type) == null ? 0 : tank.spaceFor(type);
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        if (fuelOf(type) == null || amount <= 0)
            return 0;
        var received = tank.fill(type, amount);
        if (received > 0)
            setChanged();
        return received;
    }

    /**
     * None of it can be drawn back out.
     */
    @Override
    public int amountOf(EssenceType type)
    {
        return 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        return 0;
    }

    public void restoreTank(MultiEssenceStorage source)
    {
        if (source.storageCount() != EngineFuel.values().length)
            return;
        tank = new MultiEssenceStorage(RavenousEngineBalance.TANK, EngineFuel.values().length);
        for (int i = 0; i < source.storageCount(); i++)
        {
            var type = source.types()[i];
            if (type != null && fuelOf(type) != null)
                tank.fill(type, source.amounts()[i]);
        }
        setChanged();
    }

    public MultiEssenceStorage tank()
    {
        return tank;
    }

    @Nullable
    public EngineFuel burning()
    {
        return burning;
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

    public boolean isIdle()
    {
        return shownFuel == null;
    }

    @Nullable
    public EngineFuel shownFuel()
    {
        return shownFuel;
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

    public EngineAnimation animation()
    {
        return animation;
    }

    private static final Codec<EngineFuel> FUEL_CODEC = Codec.STRING.xmap(EngineFuel::valueOf, EngineFuel::name);

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        tank = nbt.read("tank", MultiEssenceStorage.CODEC)
                .filter(read -> read.storageCount() == EngineFuel.values().length)
                .orElseGet(() -> new MultiEssenceStorage(RavenousEngineBalance.TANK, EngineFuel.values().length));
        burning = readFuel(nbt, "burning");
        credit = Math.max(0, nbt.getDoubleOr("credit", 0));

        shownFuel = readFuel(nbt, "shownFuel");
        activity = Math.clamp(nbt.getIntOr("activity", 0), 0, ACTIVITY_STEPS);
        outputTarget = nbt.read("outputTarget", BlockPos.CODEC).orElse(null);
    }

    @Nullable
    private static EngineFuel readFuel(ValueInput nbt, String key)
    {
        try
        {
            return nbt.read(key, FUEL_CODEC).orElse(null);
        } catch (IllegalArgumentException unknown)
        {
            return null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("tank", MultiEssenceStorage.CODEC, tank);
        if (burning != null)
            nbt.store("burning", FUEL_CODEC, burning);
        nbt.putDouble("credit", credit);

        if (shownFuel != null)
            nbt.store("shownFuel", FUEL_CODEC, shownFuel);
        nbt.putInt("activity", activity);
        if (outputTarget != null)
            nbt.store("outputTarget", BlockPos.CODEC, outputTarget);
    }
}
