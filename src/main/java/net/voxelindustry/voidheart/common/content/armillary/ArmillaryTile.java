package net.voxelindustry.voidheart.common.content.armillary;

import lombok.Getter;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTeleportLock;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;
import org.jspecify.annotations.Nullable;

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

    private ArmillaryChart chart = new ArmillaryChart();

    @Getter
    private boolean                  formed;
    private @Nullable DungeonAddress connection;
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
                config.armillarySearchMinMinutes * TICKS_PER_MINUTE, Math.max(config.armillarySearchMinMinutes, config.armillarySearchMaxMinutes) * TICKS_PER_MINUTE);
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
            var wanted = VoidHeartBlocks.ARMILLARY_HUB.defaultBlockState().setValue(ArmillaryHubBlock.OPEN, connection != null);
            if (current != wanted)
                level.setBlockAndUpdate(hub, wanted);
        }
        else if (current.is(VoidHeartBlocks.ARMILLARY_HUB))
            level.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
    }

    private boolean followConnection(ServerLevel level)
    {
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
        var current = ConnectionTimeline.stage(left);
        if (current == stage)
            return false;

        stage = current;
        sync();
        return true;
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
        var open = connection == null ? null : ArmillaryConnections.get(server).get(connection);
        int left = open == null ? 0 : (int) Math.max(0, open.decayAt() - ArmillaryConnections.now(server));
        int total = open == null ? 0 : (int) open.duration();
        int pearls = player.isCreative() ? ArmillaryPayloads.View.FREE : pearlsHeld(player, Integer.MAX_VALUE);
        ServerPlayNetworking.send(player, ArmillaryPayloads.View.of(chart, rules(), stage, pearls, left, total));
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

        if (onHub && connection != null)
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
        if (ArmillaryConnections.get(player.level().getServer()).get(connection) == null)
            return;

        play(VoidHeartSounds.CONNECTION_TRAVEL);
        VoidDungeons.enter(player, connection);
        var arrival = player.position();
        player.level().playSound(null, arrival.x, arrival.y, arrival.z, VoidHeartSounds.CONNECTION_TRAVEL, SoundSource.PLAYERS, 1, 1);
    }

    void act(ServerPlayer player, ArmillaryPayloads.Action action)
    {
        boolean changed = switch (action.kind())
        {
            case TARGET -> chart.target(action.bubble(), rules());
            case ABANDON -> chart.abandon(rules());
            case OPEN -> open(player);
        };
        if (!changed)
            return;

        setChanged();
        sync();
        sendViews((ServerLevel) level);
    }

    private boolean open(ServerPlayer player)
    {
        var target = chart.target();
        if (target == null || chart.getPhase() != ArmillaryChart.Phase.REACHED || !formed)
            return false;

        if (!player.isCreative())
        {
            int cost = target.openCost();
            if (pearlsHeld(player, cost) < cost)
                return false;
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(VoidHeartItems.VOID_PEARL), false, cost, player.inventoryMenu.getCraftSlots());
        }

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
        if (connection == null)
            return false;

        var server = level.getServer();
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

    public Vec3 hubCenter()
    {
        return Vec3.atCenterOf(ArmillaryLayout.hub(worldPosition));
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        boolean wasConnected = isConnected();
        chart = tag.read("chart", ArmillaryChart.CODEC).orElseGet(ArmillaryChart::new);
        formed = tag.getBooleanOr("formed", false);
        connection = tag.read("connection", DungeonAddress.CODEC).orElse(null);
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
        tag.putBoolean("formed", formed);
        if (connection != null)
            tag.store("connection", DungeonAddress.CODEC, connection);
        tag.putInt("stage", stage.ordinal());
        tag.putLong("previewUntil", previewUntil);
        tag.putFloat("connectionLeft", connectionLeft);

        super.saveAdditional(tag);
    }
}
