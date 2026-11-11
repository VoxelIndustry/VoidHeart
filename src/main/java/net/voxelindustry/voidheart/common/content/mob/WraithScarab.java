package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.PositionAndRotation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.InterpolationTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A beetle of the wraith family that never walks. It phases from face to face in short hops about half a second apart, floor, wall and ceiling in turn,
 * leaving a fading image of itself where it was, and bites whoever it lands beside. Salt wards it like any wraith: no hop starts, passes or ends near salt.
 * <p>
 * Each hop is told to clients as an entity event before the move, so they snap it to the new spot rather than gliding it there, and keep the old spot as
 * the start of a trail of afterimages drawn along the hop.
 */
public class WraithScarab extends CrawlerMonster
{
    public static final byte HOP_EVENT = 90;
    public static final int AFTERIMAGE_TICKS = 12;
    public static final int HOP_TICKS = 10;

    private static final int EXPERIENCE = 6;
    private static final double REACH = 1.1;
    private static final int BITE_COOLDOWN = 20;
    private static final int BITE_RECOVER_TICKS = 5;
    private static final int RETRY_TICKS = 5;
    private static final int IDLE_HOP_MIN = 40;
    private static final int IDLE_HOP_SPREAD = 60;
    // How long a client waits for the move that follows a hop event before it gives up on snapping
    private static final int HOP_SNAP_WINDOW = 10;
    private static final int HOP_PARTICLES = 6;
    private static final float HOP_VOLUME = 0.6F;
    private static final float BITE_VOLUME = 0.8F;
    private static final int SALT_CRACKLE_TICKS = 20;

    private int hopCooldown;
    private int biteCooldown;
    private int saltCrackleDelay;

    /**
     * @param born the tick it was left behind, on the client's count
     */
    public record Afterimage(Vec3 position, Direction surface, float bodyRot, int born)
    {
    }

    public final List<Afterimage> afterimages = new ArrayList<>();
    private @Nullable Afterimage pendingImage;
    public int landedTick = -100;

    public WraithScarab(EntityType<? extends WraithScarab> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(14, 4, 0.25);
    }

    @Override
    public boolean canCrawl()
    {
        return false;
    }

    @Override
    public boolean canChase()
    {
        return false;
    }

    @Override
    public double attackReach()
    {
        return REACH;
    }

    @Override
    public int attackCooldown()
    {
        return BITE_COOLDOWN;
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(2, new HuntGoal());
        goalSelector.addGoal(5, new WanderGoal());
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.WRAITH_SCARAB;
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        if (hopCooldown > 0)
            hopCooldown--;
        if (biteCooldown > 0)
            biteCooldown--;
        if (saltCrackleDelay > 0)
            saltCrackleDelay--;
    }

    public ScarabHops.Spot spot()
    {
        var surface = surface();
        // Its box sits snug against the face, so the cell is the one its middle lies in
        return new ScarabHops.Spot(BlockPos.containing(getBoundingBox().getCenter()), surface);
    }

    private ScarabHops.Ground ground()
    {
        var level = level();
        return new ScarabHops.Ground()
        {
            @Override
            public boolean fits(BlockPos cell, Direction surface)
            {
                if (!level.getFluidState(cell).isEmpty())
                    return false;
                var at = settledPosition(cell, surface, getBbWidth(), getBbHeight());
                return mayGoTo(at) && level.noCollision(WraithScarab.this, getDimensions(getPose()).makeBoundingBox(at));
            }

            @Override
            public boolean holds(BlockPos block, Direction face)
            {
                return level.getBlockState(block).isFaceSturdy(level, block, face);
            }

            @Override
            public boolean passable(BlockPos block)
            {
                var state = level.getBlockState(block);
                return !state.is(VoidHeartBlocks.POCKET_WALL) && state.getDestroySpeed(level, block) >= 0;
            }

            @Override
            public boolean warded(BlockPos block)
            {
                return SaltWard.isWardedBelow(level, block);
            }
        };
    }

    /**
     * @return whether it hopped
     */
    public boolean hop(@Nullable LivingEntity target)
    {
        var from = spot();
        var to = ScarabHops.plan(from, target == null ? null : target.getBoundingBox(), REACH, ground(), getRandom());
        if (to == null)
        {
            if (level() instanceof ServerLevel level && saltCrackleDelay == 0 && SaltWard.isWardedBelow(level, from.cell()))
            {
                // Held where it stands, the salt shows why
                var salt = SaltWard.wardBelow(level, from.cell());
                if (salt != null)
                    SaltWard.crackle(level, salt);
                saltCrackleDelay = SALT_CRACKLE_TICKS;
            }
            return false;
        }
        hopTo(to, target);
        return true;
    }

    public void hopTo(ScarabHops.Spot to, @Nullable LivingEntity facing)
    {
        if (!(level() instanceof ServerLevel level))
            return;

        var leaving = getBoundingBox().getCenter();
        level.broadcastEntityEvent(this, HOP_EVENT);
        getNavigation().stop();
        settleOn(to.cell(), to.surface());
        setDeltaMovement(Vec3.ZERO);
        resetFallDistance();
        if (facing != null)
            faceAlong(facing.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()));
        else
            faceAlong(getBoundingBox().getCenter().subtract(leaving));
        setOldPosAndRot();
        yBodyRotO = yBodyRot;
        needsSync = true;

        var arriving = getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, leaving.x, leaving.y, leaving.z, HOP_PARTICLES, 0.15, 0.15, 0.15, 0.02);
        level.sendParticles(ParticleTypes.SQUID_INK, arriving.x, arriving.y, arriving.z, HOP_PARTICLES / 2, 0.1, 0.1, 0.1, 0.01);
        level.playSound(null, arriving.x, arriving.y, arriving.z, VoidHeartSounds.WRAITH_SCARAB_HOP, SoundSource.HOSTILE, HOP_VOLUME, 0.9F + getRandom().nextFloat() * 0.3F);
    }

    private boolean canBite(LivingEntity target)
    {
        return getBoundingBox().inflate(REACH - getBbWidth() / 2).intersects(target.getBoundingBox()) && getSensing().hasLineOfSight(target);
    }

    private void bite(ServerLevel level, LivingEntity target)
    {
        faceAlong(target.getBoundingBox().getCenter().subtract(getBoundingBox().getCenter()));
        swingForAttack(InteractionHand.MAIN_HAND);
        playSound(VoidHeartSounds.WRAITH_SCARAB_BITE, BITE_VOLUME, 1);
        doHurtTarget(level, target);
        biteCooldown = BITE_COOLDOWN;
    }

    @Override
    public void handleEntityEvent(byte event)
    {
        if (event == HOP_EVENT)
        {
            pendingImage = new Afterimage(position(), surface(), yBodyRot, tickCount);
            return;
        }
        super.handleEntityEvent(event);
    }

    /**
     * Called by the interpolation on clients when a move arrives.
     *
     * @return whether that move was a hop, now taken in one step
     */
    private boolean takeHop(PositionPath path, float yRot, float xRot, boolean hasRotation)
    {
        if (pendingImage == null)
            return false;

        afterimages.add(new Afterimage(pendingImage.position(), pendingImage.surface(), pendingImage.bodyRot(), tickCount));
        pendingImage = null;
        float yaw = hasRotation ? yRot : getYRot();
        snapTo(path.endPosition(), yaw, hasRotation ? xRot : getXRot());
        yBodyRot = yaw;
        yBodyRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
        landedTick = tickCount;
        return true;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        // A hop lands on its new face at once rather than turning onto it
        if (level().isClientSide() && (pendingImage != null || tickCount - landedTick <= HOP_SNAP_WINDOW))
        {
            previousSurface = surface();
            surfaceBlend = SURFACE_BLEND_TICKS;
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!level().isClientSide())
            return;

        if (pendingImage != null && tickCount - pendingImage.born() > HOP_SNAP_WINDOW)
            pendingImage = null;
        afterimages.removeIf(image -> tickCount - image.born() >= AFTERIMAGE_TICKS);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler()
    {
        return new HopInterpolation(super.createInterpolationHandler());
    }

    /**
     * The usual interpolation, except that a hop is taken in one step.
     */
    private final class HopInterpolation implements InterpolationHandler
    {
        private final InterpolationHandler steps;

        private HopInterpolation(InterpolationHandler steps)
        {
            this.steps = steps;
        }

        @Override
        public InterpolationTracker interpolationTracker()
        {
            return steps.interpolationTracker();
        }

        @Override
        public @Nullable PositionAndRotation target()
        {
            return steps.target();
        }

        @Override
        public boolean interpolateTo(@Nullable PositionPath position, float yRot, float xRot, boolean hasRotation)
        {
            if (position != null && takeHop(position, yRot, xRot, hasRotation))
            {
                steps.cancel();
                return true;
            }
            return steps.interpolateTo(position, yRot, xRot, hasRotation);
        }

        @Override
        public void interpolate()
        {
            steps.interpolate();
        }

        @Override
        public void applyPredictedMovement(Vec3 delta)
        {
            steps.applyPredictedMovement(delta);
        }

        @Override
        public boolean hasActiveInterpolation()
        {
            return steps.hasActiveInterpolation();
        }

        @Override
        public void cancel()
        {
            steps.cancel();
        }
    }

    /**
     * Hops toward its target every half second, and bites it whenever it lands within reach.
     */
    private final class HuntGoal extends Goal
    {
        private HuntGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void stop()
        {
            setAggressive(false);
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;
            setAggressive(true);
            var level = (ServerLevel) level();

            if (biteCooldown == 0 && canBite(target))
            {
                bite(level, target);
                // Then on to another face beside it, once the bite has shown
                hopCooldown = Math.max(hopCooldown, BITE_RECOVER_TICKS);
                return;
            }
            if (hopCooldown > 0)
                return;
            hopCooldown = hop(target) ? HOP_TICKS : RETRY_TICKS;
        }
    }

    /**
     * With no one to hunt, it hops about now and then.
     */
    private final class WanderGoal extends Goal
    {
        private int wait;

        private WanderGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            if (getTarget() != null)
                return false;
            if (wait > 0)
            {
                wait--;
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse()
        {
            return false;
        }

        @Override
        public void start()
        {
            hop(null);
            wait = IDLE_HOP_MIN + getRandom().nextInt(IDLE_HOP_SPREAD);
        }
    }
}
