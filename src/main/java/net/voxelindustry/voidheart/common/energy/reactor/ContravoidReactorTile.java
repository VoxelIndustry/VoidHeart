package net.voxelindustry.voidheart.common.energy.reactor;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.energy.FoundationBalance;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.VoidWounds;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBalance;
import net.voxelindustry.voidheart.common.energy.creative.CreativeSources;
import net.voxelindustry.voidheart.common.energy.grid.GridLayout;
import net.voxelindustry.voidheart.common.energy.grid.VoidGrid;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Event;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Phase;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Refusal;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorCycle.Situation;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Part;
import net.voxelindustry.voidheart.common.energy.reactor.ReactorLayout.Slit;
import net.voxelindustry.voidheart.common.ley.Ley;
import net.voxelindustry.voidheart.common.ley.LeyLattice;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The core of a Contravoid reactor. Lit with void energy, it cancels Contravoid from one intake against an essence from the other and offers the
 * energy through {@link VoidEnergy#offer}, burning what the network takes and never less than the minimum.
 * <p>
 * Its mistakes and their countdown are in {@link ReactorCycle}. It only moves on while every chunk of its shell ticks, so an unloaded reactor, or one
 * whose shell is half loaded, freezes where it was. A failure opens a void wound where the core stood, taints the nearest ley node and loses the fuel.
 */
public class ContravoidReactorTile extends TileBase
{
    /**
     * Burn shown to clients, in steps: 0 is cold, the last is the fullest burn.
     */
    public static final int ACTIVITY_STEPS = 8;

    private static final int    CHECK_PERIOD     = 20;
    private static final int    TARGET_SCAN      = 40;
    private static final int    COUNTDOWN_SYNC   = 20;
    private static final double SMOOTHING        = 0.1;
    private static final int    MAX_ARC_TARGETS  = 6;
    private static final int    DRAWING_TICKS    = 10;

    /**
     * The client's sounds, installed by the client registrar so the server never loads them.
     */
    public static Consumer<ContravoidReactorTile> clientSounds = tile ->
    {
    };

    private final ReactorCycle cycle = new ReactorCycle();
    private Direction.Axis axis = Direction.Axis.X;
    private boolean complete;
    private boolean powered;
    private double  credit;
    private @Nullable ReactorEssence burning;
    /**
     * Index into {@link ReactorLayout#intakes} of the intake holding Contravoid, or -1 before either has held any.
     */
    private int     contravoidSide = -1;
    private boolean checkRequested = true;

    // Server only
    private long   lastOutput;
    private double lastBurned;
    private double averageOutput;
    private double averageBurn;
    private long   wasted;
    private int    ticksSinceDraw = DRAWING_TICKS;
    private long   targetsScannedAt = -TARGET_SCAN;
    private int    syncedCountdownAt;

    // Synced to clients
    private List<BlockPos> targets = List.of();
    private int     shownActivity;
    private int     shownCharge;
    private boolean shownDrawing;

    // Client only
    private final ReactorAnimation animation = new ReactorAnimation();

    public ContravoidReactorTile(BlockPos pos, BlockState state)
    {
        super(ContravoidReactorContent.CONTRAVOID_REACTOR_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ContravoidReactorTile tile)
    {
        if (level instanceof ServerLevel serverLevel)
            tile.serverTick(serverLevel);
        else
            tile.clientTick();
    }

    //
    // Server
    //

    private void serverTick(ServerLevel level)
    {
        if (!isShellTicking(level))
            return;

        var phase = cycle.phase();
        var active = phase != Phase.COLD && phase != Phase.FAILED;
        if (checkRequested || active || (level.getGameTime() + worldPosition.asLong()) % CHECK_PERIOD == 0)
        {
            checkRequested = false;
            checkShell(level);
            powered = isPowered(level);
        }

        var fuel = fuel(level);
        var situation = situation(fuel);
        long drawn = 0;
        if (phase == Phase.IGNITING && complete && !powered)
        {
            var wanted = Math.min(ReactorBalance.IGNITION_DRAW, cycle.ignitionWanted());
            if (wanted > 0)
                drawn = VoidEnergy.draw(level, worldPosition, wanted);
        }
        ticksSinceDraw = drawn > 0 ? 0 : Math.min(DRAWING_TICKS, ticksSinceDraw + 1);

        var event = cycle.tick(situation, drawn);
        if (drawn > 0)
            setChanged();
        if (event == Event.FAILED)
        {
            fail(level, fuel);
            return;
        }
        onEvent(level, event);

        if (cycle.burns())
            burn(level, fuel);
        else
        {
            lastOutput = 0;
            lastBurned = 0;
        }
        averageOutput += (lastOutput - averageOutput) * SMOOTHING;
        averageBurn += (lastBurned - averageBurn) * SMOOTHING;

        updateShown(level);
    }

    private void onEvent(ServerLevel level, Event event)
    {
        switch (event)
        {
            case NONE ->
            {
                return;
            }
            case LIT -> level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2F, 0.7F);
            case SHUT_DOWN ->
            {
                // A falling tone, calm and unlike failing
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.2F, 0.6F);
                credit = 0;
            }
            case RECOVERED -> level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8F, 0.7F);
            default ->
            {
            }
        }
        syncedCountdownAt = cycle.countdown();
        setChanged();
        sync();
    }

    private Situation situation(Fuel fuel)
    {
        return new Situation(complete, powered, fuel.contravoid() == null ? 0 : fuel.contravoid().amount(),
                fuel.essence() == null ? 0 : fuel.essence().amount(), credit);
    }

    private void burn(ServerLevel level, Fuel fuel)
    {
        var essence = fuel.essence() != null ? ReactorIntakeTile.essenceOf(fuel.essence().held()) : burning;
        if (essence == null)
            essence = ReactorEssence.FIERY;

        var step = ReactorBurn.step(essence, fuel.contravoid() == null ? 0 : fuel.contravoid().amount(), fuel.essence() == null ? 0 : fuel.essence().amount(),
                credit, amount -> VoidEnergy.offer(level, worldPosition, amount));
        if (step.pairs() > 0)
        {
            fuel.contravoid().burn(step.pairs());
            fuel.essence().burn(step.pairs());
        }
        if (step.pairs() > 0 || step.credit() != credit || essence != burning)
            setChanged();
        if (essence != burning)
            sync();
        burning = essence;
        credit = step.credit();
        lastOutput = step.energy();
        lastBurned = step.burned();
        wasted = Math.max(0, Math.round(step.burned() * essence.pairEnergy()) - step.energy());
    }

    private void fail(ServerLevel level, Fuel fuel)
    {
        var pos = worldPosition;
        VoidHeart.LOGGER.info("A Contravoid reactor failed at " + pos.toShortString() + " in " + level.dimension().identifier());

        if (fuel.contravoid() != null)
            fuel.contravoid().loseFuel();
        if (fuel.essence() != null)
            fuel.essence().loseFuel();
        for (var offset : ReactorLayout.intakes(axis))
        {
            if (level.getBlockEntity(pos.offset(offset)) instanceof ReactorIntakeTile intake)
                intake.loseFuel();
        }

        // No blast: a deep thump, and the core gone into the wound where it stood
        level.playSound(null, pos, SoundEvents.WARDEN_HEARTBEAT, SoundSource.BLOCKS, 4F, 0.5F);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 2F, 0.5F);
        setSlits(level, false);

        // The wound takes the core's place inside the shell, which stays standing. Should no wound open, the core still goes.
        VoidWounds.open(level, pos, VoidFissureBalance.WOUND_SIZE);
        if (level.getBlockState(pos).is(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        LeyLattice.get(level).flatMap(lattice -> lattice.nearestNode(pos.getX(), pos.getZ()))
                .ifPresent(node -> Ley.addTaint(level, node.id(), ReactorBalance.FAILURE_TAINT));
    }

    /**
     * Every chunk the shell touches must tick, or nothing moves on: unloading never makes a reactor fail.
     */
    private boolean isShellTicking(ServerLevel level)
    {
        for (var dx = -1; dx <= 1; dx += 2)
        {
            for (var dz = -1; dz <= 1; dz += 2)
            {
                var corner = worldPosition.offset(dx, 0, dz);
                if (!level.shouldTickBlocksAt(corner))
                    return false;
            }
        }
        return true;
    }

    private boolean isPowered(ServerLevel level)
    {
        if (level.hasNeighborSignal(worldPosition))
            return true;
        for (var expected : ReactorLayout.shell(axis))
        {
            if (level.hasNeighborSignal(worldPosition.offset(expected.offset())))
                return true;
        }
        return false;
    }

    //
    // Structure
    //

    public void requestCheck()
    {
        checkRequested = true;
    }

    public static @Nullable Part partAt(Level level, BlockPos pos)
    {
        var state = level.getBlockState(pos);
        if (state.is(ContravoidReactorContent.REACTOR_CONTAINMENT))
            return Part.CONTAINMENT;
        if (state.is(ContravoidReactorContent.REACTOR_INTAKE))
            return Part.INTAKE;
        return null;
    }

    /**
     * The axis its intakes stand along: locked while it runs, else read from the world.
     */
    public Direction.Axis axisIn(Level level)
    {
        if (cycle.phase() != Phase.COLD || level == null)
            return axis;
        return ReactorLayout.inferAxis(offset -> partAt(level, worldPosition.offset(offset)) == Part.INTAKE);
    }

    public List<ReactorLayout.Expected> missing(Level level)
    {
        var shellAxis = axisIn(level);
        return ReactorLayout.missing(shellAxis, offset -> partAt(level, worldPosition.offset(offset)));
    }

    private void checkShell(ServerLevel level)
    {
        var newAxis = axisIn(level);
        var nowComplete = missing(level).isEmpty();
        if (newAxis != axis || nowComplete != complete)
        {
            axis = newAxis;
            complete = nowComplete;
            setChanged();
            sync();
        }
        setSlits(level, complete || cycle.phase() != Phase.COLD);
    }

    /**
     * The slits open on the four face centers once the reactor stands whole, and stay open while it runs. Only blocks whose state changes are touched.
     */
    private void setSlits(Level level, boolean open)
    {
        for (var offset : faceCenters())
        {
            var pos = worldPosition.offset(offset);
            var state = level.getBlockState(pos);
            if (!state.is(ContravoidReactorContent.REACTOR_CONTAINMENT))
                continue;
            var slit = open ? ReactorLayout.slitAt(axis, offset) : Slit.NONE;
            if (state.getValue(ReactorContainmentBlock.SLIT) != slit)
                level.setBlock(pos, state.setValue(ReactorContainmentBlock.SLIT, slit), Block.UPDATE_CLIENTS);
        }
    }

    private static List<BlockPos> faceCenters()
    {
        var out = new ArrayList<BlockPos>(6);
        for (var direction : Direction.values())
            out.add(BlockPos.ZERO.relative(direction));
        return out;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && !level.isClientSide())
            setSlits(level, false);
        super.preRemoveSideEffects(pos, state);
    }

    /**
     * The core a shell block belongs to: the nearest within the 3x3x3 cube around it.
     */
    public static @Nullable ContravoidReactorTile coreAround(Level level, BlockPos shellPos)
    {
        ContravoidReactorTile best = null;
        var bestDistance = Integer.MAX_VALUE;
        for (var dx = -1; dx <= 1; dx++)
        {
            for (var dy = -1; dy <= 1; dy++)
            {
                for (var dz = -1; dz <= 1; dz++)
                {
                    var distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance == 0 || distance >= bestDistance)
                        continue;
                    if (level.getBlockEntity(shellPos.offset(dx, dy, dz)) instanceof ContravoidReactorTile core)
                    {
                        best = core;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    //
    // Fuel
    //

    record Fuel(@Nullable ReactorIntakeTile contravoid, @Nullable ReactorIntakeTile essence)
    {
    }

    private Fuel fuel(Level level)
    {
        ReactorIntakeTile contravoid = null;
        ReactorIntakeTile essence = null;
        var intakes = ReactorLayout.intakes(axis);
        for (var side = 0; side < intakes.size(); side++)
        {
            if (!(level.getBlockEntity(worldPosition.offset(intakes.get(side))) instanceof ReactorIntakeTile intake))
                continue;
            if (intake.holdsContravoid() && contravoid == null)
            {
                contravoid = intake;
                if (contravoidSide != side)
                {
                    contravoidSide = side;
                    setChanged();
                    sync();
                }
            }
            else if (intake.holdsEssence() && essence == null)
                essence = intake;
        }
        return new Fuel(contravoid, essence);
    }

    //
    // Use
    //

    /**
     * A use on any of its blocks: lights a cold reactor, or says what holds it back or what it is doing.
     */
    public void use(Player player)
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        checkShell(serverLevel);
        powered = isPowered(serverLevel);

        if (cycle.phase() != Phase.COLD)
        {
            player.sendOverlayMessage(statusLine());
            return;
        }

        var refusal = cycle.light(situation(fuel(serverLevel)));
        player.sendOverlayMessage(switch (refusal)
        {
            case NONE -> Component.translatable("voidheart.reactor.igniting");
            case INCOMPLETE -> Component.translatable("voidheart.reactor.refused.incomplete", missing(serverLevel).size());
            case POWERED -> Component.translatable("voidheart.reactor.refused.powered");
            case NO_FUEL -> Component.translatable("voidheart.reactor.refused.fuel");
            case RUNNING -> statusLine();
        });
        if (refusal == Refusal.NONE)
        {
            serverLevel.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1F, 0.5F);
            setChanged();
            sync();
        }
    }

    public Component statusLine()
    {
        return switch (cycle.phase())
        {
            case COLD -> Component.translatable(complete ? "voidheart.reactor.cold" : "voidheart.reactor.incomplete");
            case IGNITING -> Component.translatable("voidheart.reactor.igniting.progress", Math.round(cycle.charge() * 100F / ReactorBalance.IGNITION));
            case LIT -> Component.translatable("voidheart.reactor.lit", Math.round(averageOutput));
            case FAILING -> Component.translatable("voidheart.reactor.failing", Math.max(1, (cycle.countdown() + 19) / 20));
            case COLLAPSING, FAILED -> Component.translatable("voidheart.reactor.collapsing");
        };
    }

    //
    // Looks
    //

    private void updateShown(ServerLevel level)
    {
        var phase = cycle.phase();
        var running = phase.isRunning();
        var newActivity = running ? Math.clamp(Math.round(averageBurn / ReactorBalance.MAX_PAIRS * ACTIVITY_STEPS), 1, ACTIVITY_STEPS) : 0;
        var newCharge = phase == Phase.IGNITING ? (int) (cycle.charge() * 100 / ReactorBalance.IGNITION) : 0;
        var newDrawing = phase == Phase.IGNITING && ticksSinceDraw < DRAWING_TICKS;

        var newTargets = targets;
        if (!running && phase != Phase.IGNITING)
            newTargets = List.of();
        else if (level.getGameTime() - targetsScannedAt >= TARGET_SCAN || targets.isEmpty() && level.getGameTime() - targetsScannedAt >= 5)
        {
            targetsScannedAt = level.getGameTime();
            newTargets = findTargets(level, worldPosition);
        }

        var countdownDrift = phase == Phase.FAILING && Math.abs(syncedCountdownAt - cycle.countdown()) >= COUNTDOWN_SYNC;
        if (newActivity == shownActivity && newCharge == shownCharge && newDrawing == shownDrawing && Objects.equals(newTargets, targets) && !countdownDrift)
            return;
        shownActivity = newActivity;
        shownCharge = newCharge;
        shownDrawing = newDrawing;
        targets = newTargets;
        syncedCountdownAt = cycle.countdown();
        setChanged();
        sync();
    }

    /**
     * Where its arcs land: each creative well whose field covers it, and the bundle of each pylon whose field covers it, nearest first.
     */
    static List<BlockPos> findTargets(ServerLevel level, BlockPos pos)
    {
        var center = Vec3.atCenterOf(pos);
        var out = new ArrayList<BlockPos>();
        CreativeSources.wells(level).centers().stream()
                .filter(candidate -> candidate.distSqr(pos) <= (double) FoundationBalance.CREATIVE_WELL_RADIUS * FoundationBalance.CREATIVE_WELL_RADIUS)
                .forEach(well -> out.add(well.immutable()));
        VoidGrid.pylonsCovering(level, pos).stream().map(GridLayout.Pylon::bundle).forEach(out::add);
        out.sort(Comparator.comparingDouble(target -> target.distToCenterSqr(center)));
        return out.size() > MAX_ARC_TARGETS ? List.copyOf(out.subList(0, MAX_ARC_TARGETS)) : List.copyOf(out);
    }

    private void clientTick()
    {
        if (animation.tick(cycle.phase(), cycle.causes(), cycle.countdown(), shownActivity / (float) ACTIVITY_STEPS, shownCharge / 100F, shownDrawing, targets,
                worldPosition.asLong()))
            level.playLocalSound(worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 0.5F, false);
        clientSounds.accept(this);
    }

    //
    // Accessors
    //

    public ReactorCycle cycle()
    {
        return cycle;
    }

    public Phase phase()
    {
        return cycle.phase();
    }

    public Direction.Axis axis()
    {
        return axis;
    }

    public boolean isComplete()
    {
        return complete;
    }

    public boolean isPowered()
    {
        return powered;
    }

    public @Nullable ReactorEssence burning()
    {
        return burning;
    }

    public int contravoidSide()
    {
        return contravoidSide;
    }

    public long lastOutput()
    {
        return lastOutput;
    }

    public double averageOutput()
    {
        return averageOutput;
    }

    public double averageBurn()
    {
        return averageBurn;
    }

    public long wasted()
    {
        return wasted;
    }

    public double credit()
    {
        return credit;
    }

    public List<BlockPos> targets()
    {
        return targets;
    }

    public int shownActivity()
    {
        return shownActivity;
    }

    public ReactorAnimation animation()
    {
        return animation;
    }

    /**
     * Its two intakes, in {@link ReactorLayout#intakes} order, either missing.
     */
    public List<@Nullable ReactorIntakeTile> intakes()
    {
        var out = new ArrayList<@Nullable ReactorIntakeTile>(2);
        for (var offset : ReactorLayout.intakes(axisIn(level)))
            out.add(level != null && level.getBlockEntity(worldPosition.offset(offset)) instanceof ReactorIntakeTile intake ? intake : null);
        return out;
    }

    //
    // Saving
    //

    private static final Codec<Phase>          PHASE_CODEC   = Codec.STRING.xmap(name -> parse(Phase.class, name, Phase.COLD), Phase::name);
    private static final Codec<ReactorEssence> ESSENCE_CODEC = Codec.STRING.xmap(name -> parse(ReactorEssence.class, name, ReactorEssence.FIERY),
            ReactorEssence::name);

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback)
    {
        try
        {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown)
        {
            return fallback;
        }
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        cycle.restore(tag.read("phase", PHASE_CODEC).orElse(Phase.COLD), tag.getLongOr("charge", 0L), tag.getIntOr("countdown", 0),
                tag.getIntOr("causes", 0), tag.getIntOr("collapse", 0));
        axis = tag.getStringOr("axis", "x").equals("z") ? Direction.Axis.Z : Direction.Axis.X;
        complete = tag.getBooleanOr("complete", false);
        credit = Math.clamp(tag.getDoubleOr("credit", 0), 0, 0.999999);
        burning = tag.read("burning", ESSENCE_CODEC).orElse(null);
        contravoidSide = Math.clamp(tag.getIntOr("contravoidSide", -1), -1, 1);
        targets = List.copyOf(tag.read("targets", BlockPos.CODEC.listOf()).orElse(List.of()));
        shownActivity = Math.clamp(tag.getIntOr("activity", 0), 0, ACTIVITY_STEPS);
        shownCharge = Math.clamp(tag.getIntOr("chargeShown", 0), 0, 100);
        shownDrawing = tag.getBooleanOr("drawing", false);
        syncedCountdownAt = cycle.countdown();
        checkRequested = true;
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        tag.store("phase", PHASE_CODEC, cycle.phase());
        tag.putLong("charge", cycle.charge());
        tag.putInt("countdown", cycle.countdown());
        tag.putInt("causes", cycle.causes());
        tag.putInt("collapse", cycle.collapse());
        tag.putString("axis", axis == Direction.Axis.Z ? "z" : "x");
        tag.putBoolean("complete", complete);
        tag.putDouble("credit", credit);
        if (burning != null)
            tag.store("burning", ESSENCE_CODEC, burning);
        tag.putInt("contravoidSide", contravoidSide);
        tag.store("targets", BlockPos.CODEC.listOf(), targets);
        tag.putInt("activity", shownActivity);
        tag.putInt("chargeShown", shownCharge);
        tag.putBoolean("drawing", shownDrawing);
    }

    /**
     * Fills an intake straight away, for the review command and tests. Contravoid goes to the intake holding it or an empty one, an essence likewise.
     *
     * @return what went in
     */
    public int fill(net.voxelindustry.voidheart.common.content.essence.EssenceType type, int amount)
    {
        var filled = 0;
        for (var intake : intakes())
        {
            if (intake != null && filled < amount)
                filled += intake.receive(type, amount - filled);
        }
        return filled;
    }
}
