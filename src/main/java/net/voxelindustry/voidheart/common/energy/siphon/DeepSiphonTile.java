package net.voxelindustry.voidheart.common.energy.siphon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRiftTile;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.armillary.DaedalusConnections;
import net.voxelindustry.voidheart.common.content.mob.Legionary;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusMob;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.SplittableRandom;

/**
 * A Deep Siphon on a bridge of the Daedalus. While the armillary holding the way onto its bridge is loaded, it draws {@link DeepSiphonBalance#OUTPUT}
 * every tick and hands it to that armillary through {@link ArmillaryTile#receiveFromDaedalus}, which pays the hold first and offers the rest to the
 * network around it. It only draws what is taken, and everything taken adds to the Daedalus' notice of it, spent as waves of mobs climbing onto the bridge
 * once a player comes near.
 * <p>
 * It takes up the way its placer came in by, and while unbound or severed, the newest open way onto its bridge, with or without anyone inside.
 * While the way holds, it keeps its own chunk ticking.
 */
public class DeepSiphonTile extends TileBase
{
    /** Tag on every mob a wave brought, so they can be told from the district's own. */
    public static final String WAVE_TAG = "voidheart_siphon_wave";

    /** Draw steps shown to clients: 0 is not drawing, the last is the full output. */
    public static final int ACTIVITY_STEPS = 4;

    private static final int    LINK_CHECK_TICKS = 20;
    // How quickly the shown output follows the real one, per tick
    private static final double OUTPUT_SMOOTHING = 0.1;
    private static final int    IDLE_GRACE_TICKS = 10;
    // Client: how fast the threads and crown ease in and out, per tick
    private static final float  FADE_IN_STEP     = 1 / 20F;
    private static final float  FADE_OUT_STEP    = 1 / 30F;

    private int                link   = ArmillaryRiftTile.NONE;
    private final SiphonNotice notice = new SiphonNotice();
    private long               waves;

    // Server only
    private SiphonBridge.@Nullable Footing footing;
    private boolean                        footingKnown;
    private @Nullable Boolean              chunkHeld;
    private long                           lastDrawn;
    private double                         averageOutput;
    private int                            ticksSinceDrawn = IDLE_GRACE_TICKS;

    // Synced to clients
    private SiphonStatus status   = SiphonStatus.OFF_BRIDGE;
    private int          activity;

    // Client only
    private float shown;
    private float previousShown;

    public DeepSiphonTile(BlockPos pos, BlockState state)
    {
        super(DeepSiphonContent.DEEP_SIPHON_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DeepSiphonTile siphon)
    {
        if (level instanceof ServerLevel serverLevel)
            siphon.tickServer(serverLevel);
        else
            siphon.tickClient();
    }

    private void tickServer(ServerLevel level)
    {
        long time = level.getGameTime();
        var connection = connection(level);
        if (footing() != null && (connection == null || connection.isSevered()) && (time + worldPosition.hashCode()) % LINK_CHECK_TICKS == 0)
        {
            relink(level);
            connection = connection(level);
        }

        var newStatus = draw(level, connection);
        holdChunk(level, connection != null && !connection.isSevered());
        callWaves(level, time);
        updateShown(newStatus);
    }

    private SiphonStatus draw(ServerLevel level, DaedalusConnections.@Nullable Connection connection)
    {
        lastDrawn = 0;
        SiphonStatus newStatus;
        if (footing() == null)
            newStatus = SiphonStatus.OFF_BRIDGE;
        else if (connection == null)
            newStatus = SiphonStatus.NO_LINK;
        else if (connection.isSevered())
            newStatus = SiphonStatus.SEVERED;
        else
        {
            var armillary = ArmillaryTile.holding(level.getServer(), link);
            if (armillary == null)
                newStatus = SiphonStatus.UNANSWERED;
            else
            {
                lastDrawn = armillary.receiveFromDaedalus(DeepSiphonBalance.OUTPUT);
                newStatus = lastDrawn > 0 ? SiphonStatus.DRAWING : SiphonStatus.FULL;
                if (lastDrawn > 0)
                {
                    notice.add(lastDrawn);
                    setChanged();
                }
            }
        }

        averageOutput += (lastDrawn - averageOutput) * OUTPUT_SMOOTHING;
        ticksSinceDrawn = lastDrawn > 0 ? 0 : Math.min(IDLE_GRACE_TICKS, ticksSinceDrawn + 1);
        // A network taking in fits shouldn't make the threads flicker out between takes
        if (newStatus == SiphonStatus.FULL && ticksSinceDrawn < IDLE_GRACE_TICKS)
            newStatus = SiphonStatus.DRAWING;
        return newStatus;
    }

    private DaedalusConnections.@Nullable Connection connection(ServerLevel level)
    {
        return link == ArmillaryRiftTile.NONE ? null : DaedalusConnections.get(level.getServer()).get(link);
    }

    /**
     * Takes up the newest way onto its bridge that is open and not severed, whether or not anyone came into the Daedalus by it.
     */
    public void relink(ServerLevel level)
    {
        var footing = footing();
        if (footing == null)
            return;
        DaedalusConnections.get(level.getServer()).openConnections().stream()
                .filter(connection -> isWayOntoBridge(level, connection, footing))
                .max(Comparator.comparingInt(DaedalusConnections.Connection::id))
                .ifPresent(connection -> bind(connection.id()));
    }

    /**
     * Takes up the way the player came in by, if it leads onto this bridge.
     */
    public void relinkFrom(ServerPlayer player)
    {
        if (!(level instanceof ServerLevel serverLevel) || footing() == null)
            return;
        var connections = DaedalusConnections.get(serverLevel.getServer());
        var entry = connections.entryOf(player.getUUID());
        if (entry != null && isWayOntoBridge(serverLevel, connections.get(entry), footing()))
            bind(entry);
    }

    private void bind(int id)
    {
        if (link == id)
            return;
        link = id;
        setChanged();
    }

    private static boolean isWayOntoBridge(ServerLevel level, DaedalusConnections.@Nullable Connection connection, SiphonBridge.Footing footing)
    {
        return connection != null && !connection.isSevered() && connection.bridgeIn(level.getSeed()).equals(footing.bridge());
    }

    /**
     * The bridge it stands on, worked out once, since a block never moves.
     */
    public SiphonBridge.@Nullable Footing footing()
    {
        if (!footingKnown && level instanceof ServerLevel serverLevel)
        {
            footingKnown = true;
            footing = serverLevel.dimension() == VoidDaedalus.WORLD_KEY
                    ? SiphonBridge.at(serverLevel.getSeed(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ())
                    : null;
        }
        return footing;
    }

    /**
     * Keeps its chunk ticking while its way holds. The first tick after loading also lets go of a ticket left from a way that closed while the world was off.
     */
    private void holdChunk(ServerLevel level, boolean wanted)
    {
        if (chunkHeld != null && chunkHeld == wanted)
            return;
        if (wanted)
            DeepSiphonChunkHold.hold(level, worldPosition);
        else
            DeepSiphonChunkHold.release(level, worldPosition);
        chunkHeld = wanted;
    }

    boolean holdsChunk()
    {
        return Boolean.TRUE.equals(chunkHeld);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel)
            DeepSiphonChunkHold.release(serverLevel, pos);
    }

    ///////////
    // WAVES //
    ///////////

    private void callWaves(ServerLevel level, long time)
    {
        var footing = footing();
        if (footing == null || notice.mobs() == 0)
            return;

        int count = notice.wave(time, isPlayerNear(level));
        if (count == 0)
            return;
        var bridge = footing.bridge();
        var random = new SplittableRandom(level.getSeed() ^ worldPosition.asLong() * 31 + waves++);
        var mobs = SiphonWaves.compose(bridge.from(), SiphonBridge.to(bridge), count, random);
        notice.spend(spawn(level, mobs, SiphonBridge.waveSpots(bridge)));
        setChanged();
    }

    private boolean isPlayerNear(ServerLevel level)
    {
        var center = Vec3.atCenterOf(worldPosition);
        double range = DeepSiphonBalance.PLAYER_RANGE * DeepSiphonBalance.PLAYER_RANGE;
        for (var player : level.players())
        {
            if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(center) <= range)
                return true;
        }
        return false;
    }

    /**
     * Each mob climbs out at the first free spot, a Legionary's partner beside it.
     *
     * @return how many came, a Legionary pair counting as two
     */
    private int spawn(ServerLevel level, java.util.List<DaedalusMob> mobs, java.util.List<SiphonBridge.Spot> spots)
    {
        int came = 0;
        int spot = 0;
        Legionary waiting = null;
        for (var kind : mobs)
        {
            var type = typeOf(kind);
            Mob mob = null;
            while (mob == null && spot < spots.size())
            {
                var at = spots.get(spot++);
                double y = at.y() + (kind.placing == DaedalusMob.Placing.AIR ? 1.5 : 0);
                if (level.noCollision(type.getSpawnAABB(at.x() + 0.5, y, at.z() + 0.5)))
                    mob = create(level, type, at.x() + 0.5, y, at.z() + 0.5);
            }
            if (mob == null)
                break;

            if (mob instanceof Legionary legionary)
            {
                if (waiting == null)
                {
                    waiting = legionary;
                    continue;
                }
                legionary.pairWith(waiting);
                arrive(level, waiting);
                arrive(level, legionary);
                waiting = null;
                came += 2;
                continue;
            }
            arrive(level, mob);
            came++;
        }
        return came;
    }

    private @Nullable Mob create(ServerLevel level, EntityType<? extends Mob> type, double x, double y, double z)
    {
        var mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (mob == null)
            return null;
        // Facing the siphon, as it is what drew them
        float yaw = (float) (Math.toDegrees(Math.atan2(worldPosition.getZ() + 0.5 - z, worldPosition.getX() + 0.5 - x)) - 90);
        mob.snapTo(x, y, z, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        return mob;
    }

    private static void arrive(ServerLevel level, Mob mob)
    {
        mob.setPersistenceRequired();
        mob.addTag(WAVE_TAG);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
        level.addFreshEntity(mob);
        // Climbing up out of the chasm, from below the deck
        level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() - 0.6, mob.getZ(), 8, 0.35, 0.4, 0.35, 0.03);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 0.35F, 1.4F + level.getRandom().nextFloat() * 0.2F);
    }

    static EntityType<? extends Mob> typeOf(DaedalusMob mob)
    {
        return switch (mob)
        {
            case VOID_SKELETON -> VoidHeartEntities.VOID_SKELETON;
            case VOID_GHOUL -> VoidHeartEntities.VOID_GHOUL;
            case WRAITH -> VoidHeartEntities.WRAITH;
            case STALKING_WRAITH -> VoidHeartEntities.STALKING_WRAITH;
            case FALLEN_BISHOP -> VoidHeartEntities.FALLEN_BISHOP;
            case MALIGNANT_BOULDER -> VoidHeartEntities.MALIGNANT_BOULDER;
            case LEGIONARY -> VoidHeartEntities.LEGIONARY;
            case CENTURION -> VoidHeartEntities.CENTURION;
            case KERNACIAN_DRAGOON -> VoidHeartEntities.KERNACIAN_DRAGOON;
            case FALLEN_NOVICE -> VoidHeartEntities.FALLEN_NOVICE;
            case FALLEN_ADEPT -> VoidHeartEntities.FALLEN_ADEPT;
            case FALLEN_ELDER -> VoidHeartEntities.FALLEN_ELDER;
            case CRAB_SMALL -> VoidHeartEntities.CORRUPTED_CRAB_SMALL;
            case CRAB_MEDIUM -> VoidHeartEntities.CORRUPTED_CRAB_MEDIUM;
            case CRAB_LARGE -> VoidHeartEntities.CORRUPTED_CRAB_LARGE;
            case BARNACLE -> VoidHeartEntities.CORRUPTED_BARNACLE;
            case EYE_MAW -> VoidHeartEntities.EYE_MAW;
            case ARCANE_EYE -> VoidHeartEntities.ARCANE_EYE;
            case BARNACLE_NEST -> throw new IllegalArgumentException("A nest is a block");
        };
    }

    ////////////
    // CLIENT //
    ////////////

    /**
     * What clients draw: why it draws or doesn't, and how much of its output flows. Synced only when either changes.
     */
    private void updateShown(SiphonStatus newStatus)
    {
        int newActivity = newStatus == SiphonStatus.DRAWING
                ? Math.clamp(Math.round(averageOutput / DeepSiphonBalance.OUTPUT * ACTIVITY_STEPS), 1, ACTIVITY_STEPS)
                : 0;
        if (newStatus == status && newActivity == activity)
            return;
        status = newStatus;
        activity = newActivity;
        setChanged();
        sync();
    }

    private void tickClient()
    {
        previousShown = shown;
        float target = activity / (float) ACTIVITY_STEPS;
        shown = shown < target ? Math.min(target, shown + FADE_IN_STEP) : Math.max(target, shown - FADE_OUT_STEP);
    }

    /**
     * How strongly the threads and crown show, from 0 when it doesn't draw to 1 at its full output, eased.
     */
    public float shown(float partialTicks)
    {
        return previousShown + (shown - previousShown) * partialTicks;
    }

    public SiphonStatus status()
    {
        return status;
    }

    public int activity()
    {
        return activity;
    }

    public long lastDrawn()
    {
        return lastDrawn;
    }

    public double averageOutput()
    {
        return averageOutput;
    }

    public int link()
    {
        return link;
    }

    public SiphonNotice notice()
    {
        return notice;
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        link = nbt.getIntOr("link", ArmillaryRiftTile.NONE);
        notice.setBanked(nbt.getLongOr("notice", 0));
        waves = nbt.getLongOr("waves", 0);
        status = SiphonStatus.byOrdinal(nbt.getIntOr("status", 0));
        activity = Math.clamp(nbt.getIntOr("activity", 0), 0, ACTIVITY_STEPS);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.putInt("link", link);
        nbt.putLong("notice", notice.banked());
        nbt.putLong("waves", waves);
        nbt.putInt("status", status.ordinal());
        nbt.putInt("activity", activity);
    }
}
