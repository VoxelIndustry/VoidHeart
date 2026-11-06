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

    private final float[] ringAngles         = new float[RINGS];
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

        if (formed)
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
    }

    private boolean dialIsMoving()
    {
        return chart.getPhase() == ArmillaryChart.Phase.SEARCHING || chart.getPhase() == ArmillaryChart.Phase.CONNECTED;
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
        ServerPlayNetworking.send(player, ArmillaryPayloads.View.of(chart, rules(), stage, pearls, left, total, strata));
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
        if (target == null || !pay(player, target.openCost()))
            return false;

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
        if (point == null || !knowsDaedalus(player) || !pay(player, daedalusCost()))
            return false;

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
        if (level instanceof ServerLevel serverLevel && daedalus != ArmillaryRiftTile.NONE)
            DaedalusConnections.get(serverLevel.getServer()).sever(daedalus, ArmillaryConnections.now(serverLevel.getServer()));
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

        float wantedSpin = spinOf(chart.getPhase(), stage);
        spin += (wantedSpin - spin) * SPEED_EASE;

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

        super.saveAdditional(tag);
    }
}
