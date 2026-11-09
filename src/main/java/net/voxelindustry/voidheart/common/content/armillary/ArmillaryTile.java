package net.voxelindustry.voidheart.common.content.armillary;

import lombok.Getter;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.machine.DrawCredit;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.machine.PowerMeter;
import net.minecraft.server.MinecraftServer;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTeleportLock;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance.*;

/**
 * The instrument itself: it checks its own structure, keeps the chart turning, and follows the connection it opened, which outlives it if it must.
 */
public class ArmillaryTile extends TileBase
{
    private static final int STRUCTURE_INTERVAL = 10;
    private static final int DIAL_INTERVAL = 100;
    private static final int VIEW_INTERVAL      = 20;
    private static final int PREVIEW_TICKS      = 20 * 30;
    private static final int TICKS_PER_MINUTE   = 20 * 60;
    private static final int RESHAPE_PARTICLES  = 40;

    public static final int RINGS = 3;

    private static final float[] RING_SPEEDS       = { 0.012F, -0.021F, 0.034F };
    private static final float   SPEED_EASE        = 0.02F;
    private static final float   FORM_STEP         = 1 / 60F;
    private static final float   SEARCHING_SPEED   = 3;
    private static final float   REACHED_SPEED     = 0.5F;
    private static final float   FALTER_SPEED_DROP = 0.6F;
    /** How fast the rings still turn with nothing to draw on. */
    private static final float   UNPOWERED_SPIN    = 0.1F;
    /** How long the siphon counts as flowing after it last delivered. */
    private static final int     SIPHON_FLOW_TICKS = 20;

    /** What a player must have studied to make out the strata, and reach for it. */
    public static final Identifier DAEDALUS_RESEARCH = Identifier.fromNamespaceAndPath(MODID, "daedalus");

    private ArmillaryChart chart = new ArmillaryChart();

    @Getter
    private boolean                  formed;
    private @Nullable DungeonAddress connection;
    /** The way into the Daedalus held open, if that is what the rings hold rather than a dungeon. */
    private           int            daedalus = ArmillaryRiftTile.NONE;
    /** Who has already watched the strata come into focus on this chart since studying it. */
    private final     Set<UUID>      sawStrata = new HashSet<>();
    @Getter
    private ConnectionTimeline.Stage stage = ConnectionTimeline.Stage.STABLE;
    private long                     previewUntil;
    /** How much of the connection is left, from 1 down to 0. */
    private float                    connectionLeft;

    private final DrawCredit   searchCredit = new DrawCredit();
    private final PowerMeter   meter        = new PowerMeter();
    /** How well what the rings draw is met, synced so they slow down without it. */
    @Getter
    private       MachinePower power        = MachinePower.IDLE;
    /** Void energy gathered toward an opening. Kept when a path is abandoned, for the next one. */
    @Getter
    private       long         charge;
    /** How far the hold of the Daedalus has fallen behind its draw. It is severed once that reaches HOLD_STARVED_TICKS of draw. */
    @Getter
    private       long         holdDebt;
    /** What the deep siphon put aside here for the hold. */
    private       long         siphonStore;
    private       long         siphonUntil;
    /** Whether the deep siphon delivered here in the last second, synced for its look at the armillary. */
    @Getter
    private       boolean      siphonFlowing;
    // Whether this armillary's chunk hold is placed, unknown until its first tick after loading
    private @Nullable Boolean      chunksHeld;

    private final float[] ringAngles        = new float[RINGS];
    private final float[] previousRingAngles = new float[RINGS];
    private       float   spin               = 1;
    private       float   formation;
    private       float   previousFormation;
    /** Client only, when the portal started to tear open. */
    @Getter
    private       long    portalOpenedAt     = Long.MIN_VALUE / 2;
    /** Client only. */
    @Getter
    private final ArmillaryDisplay display   = new ArmillaryDisplay();

    public ArmillaryTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.ARMILLARY, pos, state);
    }

    public static ArmillaryChart.Rules rules()
    {
        var config = VoidHeartConfig.get();
        return new ArmillaryChart.Rules(TICKS_PER_MINUTE, config.armillaryScanMinutes * TICKS_PER_MINUTE, config.armillaryMaxBubbles, config.armillaryBubbleFadeMinutes * TICKS_PER_MINUTE,
                config.armillarySearchMinMinutes * TICKS_PER_MINUTE, Math.max(config.armillarySearchMinMinutes, config.armillarySearchMaxMinutes) * TICKS_PER_MINUTE,
                config.armillaryDaedalusSearchMinutes * TICKS_PER_MINUTE);
    }

    public static int daedalusCost()
    {
        return VoidHeartConfig.get().armillaryDaedalusPearls;
    }

    public static boolean knowsDaedalus(ServerPlayer player)
    {
        return KnowledgeState.get(player.level().getServer()).of(player.getUUID()).getCompleted().contains(DAEDALUS_RESEARCH);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, ArmillaryTile armillary)
    {
        if (world instanceof ServerLevel level)
            armillary.tickServer(level);
        else
            armillary.tickClient();
    }

    private void tickServer(ServerLevel level)
    {
        long time = level.getGameTime();
        boolean changed = false;

        if ((time + worldPosition.hashCode()) % STRUCTURE_INTERVAL == 0)
        {
            checkStructure(level);
            changed = followConnection(level);
        }

        if (drawPower(level) && formed)
        {
            var event = chart.tick(rules(), level.getRandom());
            switch (event)
            {
                case FOUND -> play(VoidHeartSounds.ARMILLARY_FOUND);
                case REACHED -> play(VoidHeartSounds.ARMILLARY_REACHED);
                default ->
                {
                }
            }
            if (event != ArmillaryChart.Event.NONE)
            {
                changed = true;
                sync();
            }
            else if (time % DIAL_INTERVAL == 0 && dialIsMoving())
                sync();
        }

        if (changed || time % VIEW_INTERVAL == 0)
        {
            setChanged();
            sendViews(level);
        }
        holdChunks(level);
    }

    /**
     * Keeps the base around it ticking while it holds the Daedalus, and lets it go once the way closes. The first tick after loading also lets go of a
     * ticket left from a way that closed while the world was off.
     */
    private void holdChunks(ServerLevel level)
    {
        boolean wanted = daedalus != ArmillaryRiftTile.NONE;
        if (chunksHeld != null && chunksHeld == wanted)
            return;
        if (wanted)
            ArmillaryChunkHold.hold(level, worldPosition);
        else
            ArmillaryChunkHold.release(level, worldPosition);
        chunksHeld = wanted;
    }

    private boolean dialIsMoving()
    {
        return chart.getPhase() == ArmillaryChart.Phase.SEARCHING || chart.getPhase() == ArmillaryChart.Phase.CONNECTED;
    }

    /**
     * Draws what the rings need this tick: a trickle while searching, a charge once a path holds, and a steady rate while the Daedalus is held open.
     *
     * @return whether the chart may move on, which a search only does while it is paid for
     */
    private boolean drawPower(ServerLevel level)
    {
        long want = 0;
        long got = 0;
        boolean chartRuns = true;

        if (heldDaedalus(level) != null)
        {
            want = DAEDALUS_HOLD;
            got = hold(level);
        }
        else if (formed && chart.getPhase() == ArmillaryChart.Phase.SEARCHING)
        {
            want = chart.targetsStrata() ? DAEDALUS_SEARCH : ARMILLARY_SEARCH;
            chartRuns = searchCredit.pay(want, amount -> VoidEnergy.draw(level, worldPosition, amount));
            got = searchCredit.lastDrawn();
        }
        else if (formed && chart.getPhase() == ArmillaryChart.Phase.REACHED && charge < openingCost())
        {
            want = Math.min(ARMILLARY_INTAKE, openingCost() - charge);
            got = VoidEnergy.draw(level, worldPosition, want);
            charge += got;
            if (got > 0)
                setChanged();
        }

        if (siphonFlowing && level.getGameTime() >= siphonUntil)
        {
            siphonFlowing = false;
            sync();
        }
        if (meter.record(want, got))
        {
            power = meter.state();
            setChanged();
            sync();
        }
        return chartRuns;
    }

    private DaedalusConnections.@Nullable Connection heldDaedalus(ServerLevel level)
    {
        if (daedalus == ArmillaryRiftTile.NONE)
            return null;
        var open = DaedalusConnections.get(level.getServer()).get(daedalus);
        return open == null || open.isSevered() ? null : open;
    }

    /**
     * Pays the hold, from what the siphon put aside first. A shortfall is caught up later at up to twice the rate, and severs the way once it has run for
     * HOLD_STARVED_TICKS of draw.
     *
     * @return what was paid this tick
     */
    private long hold(ServerLevel level)
    {
        long ask = DAEDALUS_HOLD + Math.min(holdDebt, DAEDALUS_HOLD);
        long fromSiphon = Math.min(siphonStore, ask);
        siphonStore -= fromSiphon;
        long got = fromSiphon + VoidEnergy.draw(level, worldPosition, ask - fromSiphon);

        long before = holdDebt;
        holdDebt = Math.max(0, holdDebt + DAEDALUS_HOLD - got);
        if (holdDebt != before)
            setChanged();
        if (holdDebt >= DAEDALUS_HOLD * HOLD_STARVED_TICKS)
        {
            holdDebt = 0;
            if (sever())
            {
                play(VoidHeartSounds.ARMILLARY_BREAK);
                setChanged();
                sync();
                sendViews(level);
            }
        }
        return got;
    }

    /**
     * Void energy an opening of the current path needs gathered first. Nothing without a path.
     */
    public long openingCost()
    {
        if (chart.targetsStrata())
            return DAEDALUS_OPEN;
        var target = chart.target();
        return target == null ? 0 : MachineEnergy.dungeonOpening(target.size());
    }

    /**
     * Whether a starved hold is on its way to being severed, and how many seconds are left if nothing comes, or -1 when the hold is paid.
     */
    public int secondsBeforeSevered()
    {
        if (holdDebt <= 0)
            return -1;
        return (int) Math.max(0, (DAEDALUS_HOLD * HOLD_STARVED_TICKS - holdDebt) / DAEDALUS_HOLD / 20);
    }

    /**
     * The deep siphon's way out of the Daedalus: energy it draws there arrives at the armillary holding the way open. It pays the hold first, and the rest
     * goes into the network around the armillary.
     *
     * @return how much was taken, which is nothing unless this armillary holds the Daedalus open
     */
    public long receiveFromDaedalus(long amount)
    {
        if (amount <= 0 || !(level instanceof ServerLevel serverLevel) || heldDaedalus(serverLevel) == null)
            return 0;

        long kept = Math.max(0, Math.min(amount, SIPHON_HOLD_STORE - siphonStore));
        siphonStore += kept;
        long taken = kept + VoidEnergy.offer(serverLevel, worldPosition, amount - kept);
        if (taken > 0)
        {
            siphonUntil = serverLevel.getGameTime() + SIPHON_FLOW_TICKS;
            if (!siphonFlowing)
            {
                siphonFlowing = true;
                sync();
            }
            setChanged();
        }
        return taken;
    }

    /**
     * The armillary holding a way into the Daedalus, for a deep siphon on that way's bridge. Only an armillary already loaded and ticking is found.
     */
    public static @Nullable ArmillaryTile holding(MinecraftServer server, int daedalusId)
    {
        var open = DaedalusConnections.get(server).get(daedalusId);
        if (open == null)
            return null;
        var level = server.getLevel(open.dimension());
        if (level == null || !level.shouldTickBlocksAt(open.armillary()))
            return null;
        return level.getBlockEntity(open.armillary()) instanceof ArmillaryTile armillary && armillary.daedalus == daedalusId ? armillary : null;
    }

    private void checkStructure(ServerLevel level)
    {
        boolean complete = ArmillaryLayout.isComplete(level, worldPosition);
        if (complete != formed)
        {
            formed = complete;
            play(complete ? VoidHeartSounds.ARMILLARY_FORM : VoidHeartSounds.ARMILLARY_BREAK);
            if (complete)
                previewUntil = 0;
            sync();
        }
        placeHub(level);
        shapeBase(level);
    }

    /**
     * Kept in line with the state of the armillary too, which also brings a world saved before the base had a model up to date.
     */
    private void shapeBase(ServerLevel level)
    {
        boolean reshaped = ArmillaryLayout.shapeBase(level, worldPosition, formed);
        if (getBlockState().getValue(ArmillaryCoreBlock.FORMED) != formed)
        {
            var state = getBlockState().setValue(ArmillaryCoreBlock.FORMED, formed);
            level.setBlockAndUpdate(worldPosition, state);
            setBlockState(state);
            reshaped = true;
        }
        if (reshaped)
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, RESHAPE_PARTICLES, 0.9, 0.2, 0.9, 0.02);
    }

    /**
     * Always kept in line with the state of the armillary, so that a hub removed by a stray command comes back.
     */
    private void placeHub(ServerLevel level)
    {
        var hub = ArmillaryLayout.hub(worldPosition);
        var current = level.getBlockState(hub);
        if (formed)
        {
            var wanted = VoidHeartBlocks.ARMILLARY_HUB.defaultBlockState().setValue(ArmillaryHubBlock.OPEN, holdsConnection());
            if (current != wanted)
                level.setBlockAndUpdate(hub, wanted);
        }
        else if (current.is(VoidHeartBlocks.ARMILLARY_HUB))
            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
    }

    private boolean holdsConnection()
    {
        return connection != null || daedalus != ArmillaryRiftTile.NONE;
    }

    private boolean followConnection(ServerLevel level)
    {
        if (daedalus != ArmillaryRiftTile.NONE)
            return followDaedalus(level);
        if (connection == null)
            return false;

        var server = level.getServer();
        var open = ArmillaryConnections.get(server).get(connection);
        if (open == null)
        {
            connection = null;
            connectionLeft = 0;
            stage = ConnectionTimeline.Stage.STABLE;
            chart.connectionClosed();
            play(VoidHeartSounds.ARMILLARY_CLOSE);
            placeHub(level);
            sync();
            return true;
        }

        long left = open.decayAt() - ArmillaryConnections.now(server);
        connectionLeft = open.duration() <= 0 ? 0 : (float) left / open.duration();
        return moveToStage(ConnectionTimeline.stage(left));
    }

    private boolean moveToStage(ConnectionTimeline.Stage current)
    {
        if (current == stage)
            return false;

        stage = current;
        sync();
        return true;
    }

    /**
     * Holds without wearing until it is severed, and from then on fails like any other connection's last minutes.
     */
    private boolean followDaedalus(ServerLevel level)
    {
        var server = level.getServer();
        var open = DaedalusConnections.get(server).get(daedalus);
        if (open == null)
        {
            daedalus = ArmillaryRiftTile.NONE;
            holdDebt = 0;
            siphonStore = 0;
            connectionLeft = 0;
            stage = ConnectionTimeline.Stage.STABLE;
            chart.connectionClosed();
            play(VoidHeartSounds.ARMILLARY_CLOSE);
            placeHub(level);
            sync();
            return true;
        }

        if (!open.isSevered())
        {
            connectionLeft = 1;
            return moveToStage(ConnectionTimeline.Stage.STABLE);
        }

        long left = open.decayAt() - ArmillaryConnections.now(server);
        connectionLeft = Mth.clamp((float) left / DaedalusConnections.SEVER_TICKS, 0, 1);
        return moveToStage(ConnectionTimeline.stage(left));
    }

    private void sendViews(ServerLevel level)
    {
        for (var player : level.players())
        {
            if (player.containerMenu instanceof ArmillaryMenu menu && menu.getPos().equals(worldPosition))
                sendView(player);
        }
    }

    private void sendView(ServerPlayer player)
    {
        var server = player.level().getServer();
        int left = 0;
        int total = 0;
        var open = connection == null ? null : ArmillaryConnections.get(server).get(connection);
        if (open != null)
        {
            left = (int) Math.max(0, open.decayAt() - ArmillaryConnections.now(server));
            total = (int) open.duration();
        }
        var daedalusOpen = daedalus == ArmillaryRiftTile.NONE ? null : DaedalusConnections.get(server).get(daedalus);
        if (daedalusOpen != null)
        {
            left = daedalusOpen.isSevered() ? (int) Math.max(0, daedalusOpen.decayAt() - ArmillaryConnections.now(server)) : 0;
            total = daedalusOpen.isSevered() ? DaedalusConnections.SEVER_TICKS : ArmillaryPayloads.View.STABLE;
        }
        int pearls = player.isCreative() ? ArmillaryPayloads.View.FREE : pearlsHeld(player, Integer.MAX_VALUE);
        boolean known = knowsDaedalus(player);
        boolean reveal = known && sawStrata.add(player.getUUID());
        if (reveal)
            setChanged();
        var strata = ArmillaryPayloads.StrataView.of(chart, rules(), known, reveal, daedalusCost(), server.overworld().getSeed());
        int charged = chart.getPhase() == ArmillaryChart.Phase.REACHED ? MachineEnergy.percent(charge, openingCost()) : ArmillaryPayloads.View.NO_CHARGE;
        ServerPlayNetworking.send(player, ArmillaryPayloads.View.of(chart, rules(), stage, pearls, left, total, strata, charged, power));
    }

    private static int pearlsHeld(ServerPlayer player, int upTo)
    {
        return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(VoidHeartItems.VOID_PEARL), true, upTo, player.inventoryMenu.getCraftSlots());
    }

    private void play(SoundEvent sound)
    {
        level.playSound(null, worldPosition.getX() + 0.5, worldPosition.getY() + 2.5, worldPosition.getZ() + 0.5, sound, SoundSource.BLOCKS, 1, 1);
    }

    /**
     * A right click on the core or the empty middle of the rings. The middle takes players through once a portal stands in it.
     */
    public void use(ServerPlayer player, boolean onHub)
    {
        // Built inside a dungeon it would be one more way of teleporting out of there
        if (DungeonTeleportLock.isLocked(player))
        {
            DungeonTeleportLock.refuse(player);
            return;
        }

        if (!formed)
        {
            previewUntil = level.getGameTime() + PREVIEW_TICKS;
            sync();
            boolean hasBase = ArmillaryLayout.isBaseComplete(level, worldPosition);
            player.sendOverlayMessage(Component.translatable(hasBase ? "voidheart.armillary.blocked" : "voidheart.armillary.incomplete"));
            return;
        }

        if (onHub && holdsConnection())
        {
            enter(player);
            return;
        }

        player.openMenu(new ExtendedMenuProvider<BlockPos>()
        {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer player)
            {
                return worldPosition;
            }

            @Override
            public Component getDisplayName()
            {
                return Component.translatable("block.voidheart.armillary_core");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
            {
                return new ArmillaryMenu(containerId, inventory, worldPosition);
            }
        });
        sendView(player);
    }

    private void enter(ServerPlayer player)
    {
        var server = player.level().getServer();
        if (daedalus != ArmillaryRiftTile.NONE)
        {
            if (DaedalusConnections.get(server).get(daedalus) == null)
                return;
            play(VoidHeartSounds.CONNECTION_TRAVEL);
            DaedalusConnections.enter(player, daedalus);
        }
        else
        {
            if (ArmillaryConnections.get(server).get(connection) == null)
                return;
            play(VoidHeartSounds.CONNECTION_TRAVEL);
            VoidDungeons.enter(player, connection);
        }
        var arrival = player.position();
        player.level().playSound(null, arrival.x, arrival.y, arrival.z, VoidHeartSounds.CONNECTION_TRAVEL, SoundSource.PLAYERS, 1, 1);
    }

    void act(ServerPlayer player, ArmillaryPayloads.Action action)
    {
        boolean changed = switch (action.kind())
        {
            case TARGET -> (action.bubble() != ArmillaryChart.STRATA || knowsDaedalus(player)) && chart.target(action.bubble(), rules(), level.getRandom());
            case ABANDON -> chart.abandon(rules());
            case OPEN -> open(player);
            case SEVER -> sever();
        };
        if (!changed)
            return;

        setChanged();
        sync();
        sendViews((ServerLevel) level);
    }

    private boolean open(ServerPlayer player)
    {
        if (chart.getPhase() != ArmillaryChart.Phase.REACHED || !formed)
            return false;
        if (chart.targetsStrata())
            return openDaedalus(player);

        var target = chart.target();
        if (target == null || !charged(player) || !pay(player, target.openCost()))
            return false;
        spendCharge();

        var server = player.level().getServer();
        var address = VoidDungeons.open(server, target.size(), target.theme(), target.difficulty());
        var config = VoidHeartConfig.get();
        int minTicks = config.armillaryConnectionMinMinutes * TICKS_PER_MINUTE;
        int maxTicks = Math.max(config.armillaryConnectionMinMinutes, config.armillaryConnectionMaxMinutes) * TICKS_PER_MINUTE;
        int duration = Mth.nextInt(level.getRandom(), minTicks, maxTicks);
        ArmillaryConnections.get(server).open(address, level.dimension(), worldPosition, ArmillaryConnections.now(server), duration);

        connection = address;
        connectionLeft = 1;
        chart.connect();
        placeHub((ServerLevel) level);
        play(VoidHeartSounds.ARMILLARY_OPEN);
        return true;
    }

    /**
     * An opening first gathers its void energy, which the action bar reports while it isn't there yet.
     */
    private boolean charged(ServerPlayer player)
    {
        long cost = openingCost();
        if (charge >= cost)
            return true;
        player.sendOverlayMessage(Component.translatable("voidheart.armillary.charging", MachineEnergy.percent(charge, cost)));
        return false;
    }

    private void spendCharge()
    {
        charge = Math.max(0, charge - openingCost());
    }

    private static boolean pay(ServerPlayer player, int cost)
    {
        if (player.isCreative())
            return true;
        if (pearlsHeld(player, cost) < cost)
            return false;
        player.getInventory().clearOrCountMatchingItems(stack -> stack.is(VoidHeartItems.VOID_PEARL), false, cost, player.inventoryMenu.getCraftSlots());
        return true;
    }

    private boolean openDaedalus(ServerPlayer player)
    {
        var point = chart.getStrata();
        if (point == null || !knowsDaedalus(player) || !charged(player) || !pay(player, daedalusCost()))
            return false;
        spendCharge();
        holdDebt = 0;

        daedalus = DaedalusConnections.get(player.level().getServer()).open(level.dimension(), worldPosition, point).id();
        connectionLeft = 1;
        stage = ConnectionTimeline.Stage.STABLE;
        chart.connect();
        placeHub((ServerLevel) level);
        play(VoidHeartSounds.ARMILLARY_OPEN);
        return true;
    }

    /**
     * Anyone at the chart may let go of the Daedalus. It is not let go of at once, for the sake of those still inside.
     */
    private boolean sever()
    {
        if (daedalus == ArmillaryRiftTile.NONE)
            return false;

        var server = level.getServer();
        if (!DaedalusConnections.get(server).sever(daedalus, ArmillaryConnections.now(server)))
            return false;
        followDaedalus((ServerLevel) level);
        return true;
    }

    /**
     * The Daedalus is only held by the armillary that opened the way, so it goes once the core does. A dungeon's connection outlives it, as it wears out on its own.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel)
        {
            if (daedalus != ArmillaryRiftTile.NONE)
                DaedalusConnections.get(serverLevel.getServer()).sever(daedalus, ArmillaryConnections.now(serverLevel.getServer()));
            ArmillaryChunkHold.release(serverLevel, pos);
        }
    }

    /**
     * Commands to review a chart without waiting for it.
     */
    public void revealBubble()
    {
        chart.find(rules(), level.getRandom());
        changedByCommand();
    }

    public boolean advanceSearch(float progress)
    {
        if (!chart.advanceSearch(progress))
            return false;

        if (chart.getPhase() == ArmillaryChart.Phase.REACHED)
            play(VoidHeartSounds.ARMILLARY_REACHED);
        changedByCommand();
        return true;
    }

    public boolean decayIn(int seconds)
    {
        var server = level.getServer();
        if (daedalus != ArmillaryRiftTile.NONE)
            return DaedalusConnections.get(server).decayAt(daedalus, ArmillaryConnections.now(server) + seconds * 20L);
        if (connection == null)
            return false;

        return ArmillaryConnections.get(server).decayIn(connection, ArmillaryConnections.now(server) + seconds * 20L);
    }

    private void changedByCommand()
    {
        setChanged();
        sync();
        sendViews((ServerLevel) level);
    }

    public ArmillaryChart getChart()
    {
        return chart;
    }

    public @Nullable DungeonAddress getConnection()
    {
        return connection;
    }

    public boolean isPreviewActive()
    {
        return !formed && level != null && level.getGameTime() < previewUntil;
    }

    public boolean isConnected()
    {
        return chart.getPhase() == ArmillaryChart.Phase.CONNECTED;
    }

    /**
     * The colour the portal takes. Null until a bubble has been reached, since its theme isn't known before.
     */
    public @Nullable DungeonTheme portalTheme()
    {
        var target = chart.target();
        return target != null && chart.getPhase().ordinal() >= ArmillaryChart.Phase.REACHED.ordinal() ? target.theme() : null;
    }

    /**
     * The colour of the district the portal opens into, once a root has reached the strata. Null otherwise.
     */
    public @Nullable Integer portalDaedalusRgb()
    {
        var point = chart.getStrata();
        if (point == null || chart.getPhase().ordinal() < ArmillaryChart.Phase.REACHED.ordinal())
            return null;
        return DaedalusTheme.at(new DistrictPos(point.districtX(), point.districtZ())).color();
    }

    public int getDaedalusConnection()
    {
        return daedalus;
    }

    private void tickClient()
    {
        float wanted = formed ? 1 : 0;
        previousFormation = formation;
        formation += Mth.clamp(wanted - formation, -FORM_STEP, FORM_STEP);

        float wantedSpin = spinOf(chart.getPhase(), stage) * (power == MachinePower.UNPOWERED ? UNPOWERED_SPIN : 1);
        spin +=(wantedSpin - spin) * SPEED_EASE;

        for (int ring = 0; ring < RINGS; ring++)
        {
            previousRingAngles[ring] = ringAngles[ring];
            ringAngles[ring] += RING_SPEEDS[ring] * spin * formation;
        }

        display.tick(ArmillaryDisplay.isShown(formed, chart.liveBubbles(), chart.getPhase()),
                ArmillaryDisplay.arcOf(chart.getPhase(), chart.getSearchTicks(), chart.getSearchDuration(), connectionLeft));
    }

    /**
     * How fast the rings want to turn, in their world and on the chart alike.
     */
    public static float spinOf(ArmillaryChart.Phase phase, ConnectionTimeline.Stage stage)
    {
        float spin = switch (phase)
        {
            case SCANNING, CONNECTED -> 1;
            case SEARCHING -> SEARCHING_SPEED;
            case REACHED -> REACHED_SPEED;
        };
        return spin * (1 - falterOf(stage) * FALTER_SPEED_DROP);
    }

    public static float ringSpeed(int ring)
    {
        return RING_SPEEDS[ring];
    }

    /**
     * How badly the rings stutter as a connection fails, from 0 to 1.
     */
    public static float falterOf(ConnectionTimeline.Stage stage)
    {
        return stage.ordinal() / (float) ConnectionTimeline.Stage.COLLAPSING.ordinal();
    }

    public float falter()
    {
        return falterOf(stage);
    }

    public float ringAngle(int ring, float partialTicks)
    {
        return Mth.lerp(partialTicks, previousRingAngles[ring], ringAngles[ring]);
    }

    public float formation(float partialTicks)
    {
        return Mth.lerp(partialTicks, previousFormation, formation);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        boolean wasConnected = isConnected();
        chart = tag.read("chart", ArmillaryChart.CODEC).orElseGet(ArmillaryChart::new);
        sawStrata.clear();
        tag.read("saw_strata", UUIDUtil.CODEC.listOf()).ifPresent(sawStrata::addAll);
        formed = tag.getBooleanOr("formed", false);
        connection = tag.read("connection", DungeonAddress.CODEC).orElse(null);
        daedalus = tag.getIntOr("daedalus", ArmillaryRiftTile.NONE);
        stage = ConnectionTimeline.Stage.values()[Mth.clamp(tag.getIntOr("stage", 0), 0, ConnectionTimeline.Stage.values().length - 1)];
        previewUntil = tag.getLongOr("previewUntil", 0);
        connectionLeft = tag.getFloatOr("connectionLeft", 0);
        power = MachinePower.byOrdinal(tag.getIntOr("power", 0));
        meter.restore(power);
        charge = tag.getLongOr("charge", 0);
        holdDebt = tag.getLongOr("holdDebt", 0);
        siphonStore = tag.getLongOr("siphonStore", 0);
        siphonFlowing = tag.getBooleanOr("siphonFlowing", false);

        if (level != null && level.isClientSide() && !wasConnected && isConnected())
            portalOpenedAt = level.getGameTime();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("chart", ArmillaryChart.CODEC, chart);
        tag.store("saw_strata", UUIDUtil.CODEC.listOf(), List.copyOf(sawStrata));
        tag.putBoolean("formed", formed);
        if (connection != null)
            tag.store("connection", DungeonAddress.CODEC, connection);
        if (daedalus != ArmillaryRiftTile.NONE)
            tag.putInt("daedalus", daedalus);
        tag.putInt("stage", stage.ordinal());
        tag.putLong("previewUntil", previewUntil);
        tag.putFloat("connectionLeft", connectionLeft);
        tag.putInt("power", power.ordinal());
        tag.putLong("charge", charge);
        tag.putLong("holdDebt", holdDebt);
        tag.putLong("siphonStore", siphonStore);
        tag.putBoolean("siphonFlowing", siphonFlowing);

        super.saveAdditional(tag);
    }
}
