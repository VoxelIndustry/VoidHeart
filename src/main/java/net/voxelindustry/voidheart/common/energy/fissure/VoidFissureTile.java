package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.ley.Ley;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A Void Fissure or Void Wound. A fissure offers void energy through {@link VoidEnergy#offer} by its width, and only what the network takes; a wound
 * offers none. Both seep: the seep spreads from the crack a block a minute up to its width's radius, weakens wards and hastens corruption inside it,
 * lets weak void creatures through, and taints the node of a ley line close by.
 * <p>
 * Relays link to it as to any receiver and pour Contravoid in. Once its cost is paid it draws shut over {@link VoidFissureBalance#CLOSE_TICKS}, its seep
 * thinning with it, and is gone.
 */
public class VoidFissureTile extends TileBase implements IEssenceReceiver
{
    public static final Set<VoidFissureTile> CLIENT_LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    public static final int ACTIVITY_STEPS = 4;

    private static final int    IDLE_GRACE_TICKS   = 10;
    private static final double OUTPUT_SMOOTHING   = 0.1;
    private static final int    TARGET_SCAN_TICKS  = 40;
    private static final int    TAINT_TICKS        = 20;

    // Saved and synced
    private float width        = 1;
    private float seepFrom;
    private long  seepSince    = -1;
    private long  tornAt       = Long.MIN_VALUE;
    private long  closingSince = -1;
    private int   paid;
    @Nullable
    private BlockPos outputTarget;
    private int      activity;
    // A wound opened with full faces on every side, as inside a failed reactor's shell, keeps its crack to its own block
    private boolean  confined;

    // Saved, server only
    private long nextSpawn = -1;

    // Server only
    private long   lastOutput;
    private double averageOutput;
    private int    ticksSinceOutput = IDLE_GRACE_TICKS;
    private long   targetScannedAt  = -TARGET_SCAN_TICKS;

    // Client only
    private final FissureAnimation animation = new FissureAnimation();

    public VoidFissureTile(BlockPos pos, BlockState state)
    {
        super(VoidFissureContent.VOID_FISSURE_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidFissureTile fissure)
    {
        if (level instanceof ServerLevel serverLevel)
            fissure.serverTick(serverLevel);
        else
            fissure.animation.tick(level, fissure);
    }

    private void serverTick(ServerLevel level)
    {
        long now = level.getGameTime();
        if (seepSince < 0)
            open(level, width, now);

        if (closingSince >= 0)
        {
            if (now - closingSince >= VoidFissureBalance.CLOSE_TICKS)
            {
                level.removeBlock(worldPosition, false);
                return;
            }
            updateShown(level, false);
            return;
        }

        if (!isWound())
        {
            lastOutput = VoidEnergy.offer(level, worldPosition, SeepMath.output(width()));
            averageOutput += (lastOutput - averageOutput) * OUTPUT_SMOOTHING;
            ticksSinceOutput = lastOutput > 0 ? 0 : Math.min(IDLE_GRACE_TICKS, ticksSinceOutput + 1);
        }
        updateShown(level, ticksSinceOutput < IDLE_GRACE_TICKS && !isWound());

        if (Math.floorMod(now + worldPosition.hashCode(), TAINT_TICKS) == 0)
            taintNearbyLine(level);

        if (nextSpawn < 0 || nextSpawn > now + SeepMath.spawnIntervalTicks(width))
            nextSpawn = now + SeepMath.spawnIntervalTicks(width);
        else if (now >= nextSpawn)
        {
            nextSpawn = now + SeepMath.spawnIntervalTicks(width);
            SeepCreatures.tryBringThrough(level, this, now);
            setChanged();
        }
    }

    /**
     * Starts the seep and tears the crack open, for a seed just placed or a wound just opened.
     */
    void open(ServerLevel level, float width, long now)
    {
        this.width = SeepMath.clampWidth(width);
        seepFrom = 0;
        seepSince = now;
        tornAt = now;
        nextSpawn = now + SeepMath.spawnIntervalTicks(this.width);
        level.playSound(null, worldPosition, SoundEvents.WARDEN_DIG, SoundSource.BLOCKS, 0.8F, 0.55F);
        setChanged();
        sync();
    }

    /**
     * One step wider: more output, a larger seep spreading on from where it stood, and a dearer closing. Contravoid already paid is kept.
     */
    void widen(ServerLevel level)
    {
        long now = level.getGameTime();
        seepFrom = seepRadius(now);
        seepSince = now;
        width = Math.min(VoidFissureBalance.MAX_WIDTH, width() + 1);
        tornAt = now;
        level.playSound(null, worldPosition, SoundEvents.WARDEN_DIG, SoundSource.BLOCKS, 1F, 0.45F);
        level.sendParticles(ParticleTypes.SQUID_INK, worldPosition.getX() + 0.5, worldPosition.getY() + 0.1, worldPosition.getZ() + 0.5, 12, width * 0.4, 0.05, 0.2, 0.01);
        setChanged();
        sync();
    }

    /**
     * For review and tests: sets the seep's radius now, held below its width's radius.
     */
    public void setSeepRadius(ServerLevel level, float radius)
    {
        seepFrom = Math.clamp(radius, 0, SeepMath.targetRadius(width));
        seepSince = level.getGameTime();
        setChanged();
        sync();
    }

    /**
     * For review and tests: sets a fissure's width at once, the seep spreading on from where it stood.
     */
    public void setWidth(ServerLevel level, int width)
    {
        long now = level.getGameTime();
        seepFrom = Math.min(seepRadius(now), SeepMath.targetRadius(width));
        seepSince = now;
        this.width = SeepMath.clampWidth(width);
        tornAt = now;
        setChanged();
        sync();
    }

    private void taintNearbyLine(ServerLevel level)
    {
        var line = Ley.lineNear(level, worldPosition, VoidFissureBalance.LINE_REACH);
        if (line.isEmpty())
            return;
        var distance = Math.sqrt(line.get().distanceSq(worldPosition.getX() + 0.5, worldPosition.getZ() + 0.5));
        var perMinute = SeepMath.taintPerMinute(width, distance);
        if (perMinute > 0)
            Ley.addTaint(level, line.get().from(), perMinute * TAINT_TICKS / SeepMath.TICKS_PER_MINUTE);
    }

    private void updateShown(ServerLevel level, boolean active)
    {
        var newActivity = active ? Math.clamp(Math.round(averageOutput / SeepMath.output(width()) * ACTIVITY_STEPS), 1, ACTIVITY_STEPS) : 0;
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
     * Where the output arc lands: a creative well covering the fissure, since those answer first, or else the bundle of the nearest pylon covering it.
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

    /**
     * How strongly this one seeps at a point: 1 at the crack falling to 0 at the seep's edge, thinning out while it closes.
     */
    public float seepStrength(double x, double y, double z, long now)
    {
        var radius = seepRadius(now);
        if (radius <= 0)
            return 0;
        var distance = Math.sqrt(worldPosition.distToCenterSqr(x, y, z));
        return SeepMath.strength(distance, radius) * (1 - SeepMath.closing(closingSince, now));
    }

    public float seepRadius(long now)
    {
        if (seepSince < 0)
            return 0;
        return SeepMath.radiusAt(seepFrom, seepSince, now, SeepMath.targetRadius(width));
    }

    public boolean isWound()
    {
        return getBlockState().getBlock() instanceof VoidFissureBlock block && block.isWound();
    }

    /**
     * Whether it opened closed in on every side, so its crack stays inside its own block. Its seep is as wide as ever.
     */
    public boolean isConfined()
    {
        return confined;
    }

    void confine(boolean confined)
    {
        this.confined = confined;
        setChanged();
    }

    public int width()
    {
        return Math.round(width);
    }

    /**
     * The width it seeps at, fractional for a wound.
     */
    public float size()
    {
        return width;
    }

    public int cost()
    {
        return SeepMath.closeCost(width);
    }

    public int paid()
    {
        return paid;
    }

    public boolean isClosing()
    {
        return closingSince >= 0;
    }

    public long closingSince()
    {
        return closingSince;
    }

    public long tornAt()
    {
        return tornAt;
    }

    public long lastOutput()
    {
        return lastOutput;
    }

    public double averageOutput()
    {
        return averageOutput;
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

    public FissureAnimation animation()
    {
        return animation;
    }

    /**
     * Stable per crack, so its shape survives reloads and looks the same to everyone.
     */
    public long seed()
    {
        return worldPosition.asLong() * 0x9E3779B97F4A7C15L;
    }

    /**
     * Contravoid paid in, by a relay or a command. Once the cost is met it starts to close.
     */
    public int pay(int amount)
    {
        if (isClosing() || amount <= 0 || !(level instanceof ServerLevel server))
            return 0;
        var taken = Math.min(amount, cost() - paid);
        if (taken <= 0)
            return 0;
        paid += taken;
        if (paid >= cost())
            startClosing(server);
        setChanged();
        return taken;
    }

    private void startClosing(ServerLevel level)
    {
        closingSince = level.getGameTime();
        activity = 0;
        outputTarget = null;
        level.playSound(null, worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.9F, 0.6F);
        setChanged();
        sync();
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        return type == EssenceRegistry.CONTRAVOID && !isClosing() ? Math.max(0, cost() - paid) : 0;
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        return type == EssenceRegistry.CONTRAVOID ? pay(amount) : 0;
    }

    /**
     * Nothing paid can be drawn back out.
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

    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);
        track(level);
    }

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        if (getLevel() != null)
            track(getLevel());
    }

    private void track(Level level)
    {
        if (level instanceof ServerLevel server)
            VoidFissures.add(server, this);
        else if (level.isClientSide())
            CLIENT_LOADED.add(this);
    }

    @Override
    public void setRemoved()
    {
        super.setRemoved();
        if (getLevel() instanceof ServerLevel server)
            VoidFissures.remove(server, this);
        else if (getLevel() != null)
        {
            CLIENT_LOADED.remove(this);
            if (isClosing())
                FissureScars.leave(this);
        }
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);
        width = SeepMath.clampWidth(nbt.getFloatOr("width", 1));
        var from = nbt.getFloatOr("seepFrom", 0);
        seepFrom = Float.isFinite(from) ? Math.max(0, from) : 0;
        seepSince = nbt.getLongOr("seepSince", -1);
        tornAt = nbt.getLongOr("tornAt", Long.MIN_VALUE);
        closingSince = Math.max(-1, nbt.getLongOr("closingSince", -1));
        paid = Math.clamp(nbt.getIntOr("paid", 0), 0, cost());
        nextSpawn = nbt.getLongOr("nextSpawn", -1);
        outputTarget = nbt.read("outputTarget", BlockPos.CODEC).orElse(null);
        activity = Math.clamp(nbt.getIntOr("activity", 0), 0, ACTIVITY_STEPS);
        confined = nbt.getBooleanOr("confined", false);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);
        nbt.putFloat("width", width);
        nbt.putFloat("seepFrom", seepFrom);
        nbt.putLong("seepSince", seepSince);
        nbt.putLong("tornAt", tornAt);
        nbt.putLong("closingSince", closingSince);
        nbt.putInt("paid", paid);
        nbt.putLong("nextSpawn", nextSpawn);
        if (outputTarget != null)
            nbt.store("outputTarget", BlockPos.CODEC, outputTarget);
        nbt.putInt("activity", activity);
        if (confined)
            nbt.putBoolean("confined", true);
    }
}
