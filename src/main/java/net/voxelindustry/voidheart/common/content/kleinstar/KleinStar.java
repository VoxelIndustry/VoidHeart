package net.voxelindustry.voidheart.common.content.kleinstar;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A compressed star. It grows on the end of its caster's staff, going where the staff goes, while its caster steers where it will burst by looking,
 * see {@link KleinStarTargeting}. Grown, it leaves for whatever it settled on and follows it: what it saw while it grew it does not forget, so
 * something that steps behind a wall is still gone after, around the wall, see {@link KleinStarRoute}, and when the one it follows moves it finds
 * its way anew. Then it bursts: everything in its blast is hurt whatever shields it, left Radiant, and the blocks facing the burst keep burning
 * what comes near, see {@link RadiantCover}. It spares no one, its caster included.
 */
public class KleinStar extends Entity
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "klein_star"));

    private static final EntityDataAccessor<Boolean>   DATA_LAUNCHED = SynchedEntityData.defineId(KleinStar.class, EntityDataSerializers.BOOLEAN);
    // Who holds the staff it grows on and in which hand, so each client can keep it on the staff's end between ticks
    private static final EntityDataAccessor<Integer>   DATA_OWNER    = SynchedEntityData.defineId(KleinStar.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean>   DATA_RIGHT    = SynchedEntityData.defineId(KleinStar.class, EntityDataSerializers.BOOLEAN);
    // Where it would burst if it left now, as an offset from where it is, for its caster to see while they steer it
    private static final EntityDataAccessor<Vector3fc> DATA_AIM      = SynchedEntityData.defineId(KleinStar.class, EntityDataSerializers.VECTOR3);

    // In the open it leaves on a swinging curve, tried with less and less swing until nothing stands in its way
    private static final double[] SWINGS          = {1, 0.5, 0.25};
    private static final int      PATH_CHECKS     = 12;
    // Flying, it looks again at where the one it follows is every this many ticks, and finds a new way once that is this far from where it was headed
    private static final int      RETARGET_EVERY  = 2;
    private static final double   RETARGET_BEYOND = 0.75;

    private @Nullable LivingEntity   owner;
    // Everything it or its caster saw while it grew, hidden since or not
    private final Set<LivingEntity>  seen = new LinkedHashSet<>();
    private @Nullable LivingEntity   anchor;
    private @Nullable KleinStarPath  path;
    private int                      flyingTicks;
    // How far it had flown in all when it took its current path
    private double                   flownBefore;

    public KleinStar(EntityType<? extends KleinStar> type, Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /**
     * @param right whether the staff is in the caster's right hand
     */
    public KleinStar(Level level, LivingEntity owner, boolean right)
    {
        this(VoidHeartEntities.KLEIN_STAR, level);
        this.owner = owner;
        entityData.set(DATA_OWNER, owner.getId());
        entityData.set(DATA_RIGHT, right);
        moveCenterTo(tip(level, owner, right, 1));
    }

    /**
     * Just past the end of the staff its bearer holds, as others see it: beside and above them on the side of the hand that holds it, turning
     * with their body as the arm does, or as far that way as the room lets it.
     */
    public static Vec3 tip(Level level, LivingEntity bearer, boolean right, float partialTicks)
    {
        var eyes = bearer.getEyePosition(partialTicks);
        float yaw = bearer.getPreciseBodyRotation(partialTicks);
        var ahead = Vec3.directionFromRotation(0, yaw);
        var aside = Vec3.directionFromRotation(0, yaw + 90);
        var wanted = bearer.getPosition(partialTicks)
                .add(aside.scale(right ? KleinStarBalance.TIP_ASIDE : -KleinStarBalance.TIP_ASIDE))
                .add(0, bearer.getBbHeight() * KleinStarBalance.TIP_HEIGHT, 0)
                .add(ahead.scale(KleinStarBalance.TIP_AHEAD));

        var hit = level.clip(new ClipContext(eyes, wanted, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bearer));
        if (hit.getType() == HitResult.Type.MISS)
            return wanted;
        var back = eyes.subtract(wanted).normalize().scale(KleinStarBalance.BURST_BACKOFF);
        return hit.getLocation().add(back);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        builder.define(DATA_LAUNCHED, false);
        builder.define(DATA_OWNER, -1);
        builder.define(DATA_RIGHT, true);
        builder.define(DATA_AIM, new Vector3f());
    }

    public Vec3 center()
    {
        return position().add(0, getBbHeight() / 2, 0);
    }

    /**
     * Where to draw it between ticks: on the end of the staff while it grows, if its bearer is in sight, so it never trails behind them.
     */
    public Vec3 center(float partialTicks)
    {
        if (!launched() && level().getEntity(entityData.get(DATA_OWNER)) instanceof LivingEntity bearer && bearer.isAlive())
            return tip(level(), bearer, entityData.get(DATA_RIGHT), partialTicks);
        return getPosition(partialTicks).add(0, getBbHeight() / 2, 0);
    }

    /**
     * @return the id of whoever holds the staff it grows on, or -1
     */
    public int bearerId()
    {
        return entityData.get(DATA_OWNER);
    }

    public boolean inRightHand()
    {
        return entityData.get(DATA_RIGHT);
    }

    /**
     * @return whether it has left where it grew
     */
    public boolean launched()
    {
        return entityData.get(DATA_LAUNCHED);
    }

    /**
     * @return where it would burst if it left now, while it grows
     */
    public Vec3 aim()
    {
        return center().add(new Vec3(entityData.get(DATA_AIM)));
    }

    private void moveCenterTo(Vec3 center)
    {
        setPos(center.x, center.y - getBbHeight() / 2, center.z);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!(level() instanceof ServerLevel level))
            return;

        boolean carried = owner != null && owner.isAlive() && owner.level() == level;
        if (tickCount < KleinStarBalance.GROW_TICKS)
        {
            // Carried on the staff until it is grown, and steered by its bearer's gaze. Without its bearer it stays where it was
            if (carried)
            {
                moveCenterTo(tip(level, owner, entityData.get(DATA_RIGHT), 1));
                var target = lookFor(level);
                entityData.set(DATA_AIM, target.at().subtract(center()).toVector3f());
            }
            return;
        }

        if (path == null)
            launch(level, carried ? lookFor(level) : new KleinStarTargeting.Target(center(), null));
        fly(level);
    }

    /**
     * Adds what it sees now to what it saw, and settles on where to burst among all of it.
     */
    private KleinStarTargeting.Target lookFor(ServerLevel level)
    {
        seen.addAll(KleinStarTargeting.visible(level, center(), owner));
        seen.removeIf(living -> !living.isAlive() || living.level() != level);
        return KleinStarTargeting.target(level, center(), owner, seen, VoidHeartConfig.get().kleinStarRadius);
    }

    private void launch(ServerLevel level, KleinStarTargeting.Target target)
    {
        anchor = target.anchor();
        var from = center();
        var sight = sight(level);

        // In the open, the swinging curve
        double side = (random.nextBoolean() ? 1 : -1) * (0.6 + random.nextDouble() * 0.4);
        double twist = random.nextDouble() * 2 - 1;
        for (double swing : SWINGS)
        {
            var flight = KleinStarFlight.plan(from, target.at(), side, twist, swing);
            if (clear(flight, sight))
            {
                take(level, KleinStarPath.of(flight));
                break;
            }
        }
        if (path == null)
            take(level, findWay(level, from, target.at(), null));

        entityData.set(DATA_LAUNCHED, true);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.TRIDENT_RIPTIDE_3.value(), SoundSource.PLAYERS, 1.2F, 1.6F);
    }

    private static boolean clear(KleinStarFlight flight, KleinStarRoute.Sight sight)
    {
        var last = flight.from();
        for (int check = 1; check <= PATH_CHECKS; check++)
        {
            var point = flight.at(check / (double) PATH_CHECKS);
            if (!sight.clear(last, point))
                return false;
            last = point;
        }
        return true;
    }

    private KleinStarRoute.Sight sight(ServerLevel level)
    {
        return (from, to) -> KleinStarTargeting.sees(level, from, to, this);
    }

    /**
     * A way from one point to another: straight if nothing is between them, else around what is, else straight at it for want of better.
     *
     * @param heading which way it is already going
     */
    private KleinStarPath findWay(ServerLevel level, Vec3 from, Vec3 to, @Nullable Vec3 heading)
    {
        var sight = sight(level);
        if (sight.clear(from, to))
            return KleinStarPath.through(List.of(from, to), heading, sight);

        var open = new HashMap<BlockPos, Boolean>();
        var cursor = new BlockPos.MutableBlockPos();
        var corners = KleinStarRoute.find((x, y, z) ->
        {
            cursor.set(x, y, z);
            var known = open.get(cursor);
            if (known == null)
            {
                known = level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
                open.put(cursor.immutable(), known);
            }
            return known;
        }, sight, from, to);
        return corners == null
                ? new KleinStarPath(List.of(from, from, to, to))
                : KleinStarPath.through(corners, heading, sight);
    }

    /**
     * Takes a path from where it is, and tells everyone who sees it.
     */
    private void take(ServerLevel level, KleinStarPath taken)
    {
        path = taken;
        flownBefore = KleinStarBalance.flown(flyingTicks);
        var payload = new KleinStarPathPayload(getId(), taken.points(), flyingTicks);
        PlayerLookup.tracking(this).forEach(viewer -> ServerPlayNetworking.send(viewer, payload));
    }

    private void fly(ServerLevel level)
    {
        var from = center();
        if (flyingTicks > 0 && flyingTicks % RETARGET_EVERY == 0)
            follow(level, from);
        flyingTicks++;

        double along = KleinStarBalance.flown(flyingTicks) - flownBefore;
        var next = path.at(along);
        var hit = level.clip(new ClipContext(from, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() != HitResult.Type.MISS)
        {
            // Something closed its way since it was found: once more around it, or it bursts against it
            var step = next.subtract(from);
            var way = findWay(level, from, path.to(), step);
            var retry = way.at(KleinStarBalance.flown(flyingTicks) - KleinStarBalance.flown(flyingTicks - 1));
            if (level.clip(new ClipContext(from, retry, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS)
            {
                var back = step.lengthSqr() < 1e-8 ? Vec3.ZERO : step.normalize().scale(KleinStarBalance.BURST_BACKOFF);
                burst(level, hit.getLocation().subtract(back));
                return;
            }
            flyingTicks--;
            take(level, way);
            flyingTicks++;
            along = KleinStarBalance.flown(flyingTicks) - flownBefore;
            next = retry;
        }

        moveCenterTo(next);
        if (along >= path.length() || flyingTicks >= KleinStarBalance.MAX_FLIGHT_TICKS)
            burst(level, next);
    }

    /**
     * Whatever it went for may have moved: where to burst is settled again around it, and if that is no longer where the star is headed, a new
     * way is found from where the star is, turning from the way it is going.
     */
    private void follow(ServerLevel level, Vec3 from)
    {
        if (anchor == null || !anchor.isAlive() || anchor.level() != level)
            return;
        var wanted = KleinStarTargeting.around(level, anchor, VoidHeartConfig.get().kleinStarRadius);
        if (wanted.distanceTo(path.to()) <= RETARGET_BEYOND)
            return;
        double along = KleinStarBalance.flown(flyingTicks) - flownBefore;
        var heading = path.at(along + 0.5).subtract(path.at(along - 0.5));
        take(level, findWay(level, from, wanted, heading));
    }

    private void burst(ServerLevel level, Vec3 center)
    {
        double radius = VoidHeartConfig.get().kleinStarRadius;
        float damage = VoidHeartConfig.get().kleinStarDamage;
        var source = damageSources().source(DAMAGE, this, owner);

        for (var living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2).inflate(1), LivingEntity::isAlive))
        {
            double distance = Math.sqrt(living.getBoundingBox().distanceToSqr(center));
            if (distance > radius)
                continue;
            living.hurtServer(level, source, KleinStarBalance.damageAt(damage, distance, radius));
            if (living.isAlive())
                living.addEffect(new MobEffectInstance(VoidHeartEffects.RADIANT, KleinStarBalance.RADIANT_TICKS), owner);
        }

        RadiantCover.add(level, center, radius);

        var payload = new KleinStarBurstPayload(center, (float) radius, KleinStarBalance.COVER_TICKS);
        PlayerLookup.around(level, center, KleinStarBalance.SEEN_RANGE).forEach(viewer -> ServerPlayNetworking.send(viewer, payload));
        level.playSound(null, center.x, center.y, center.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 3, 0.6F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4, 1.4F);
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        return false;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        return distance < KleinStarBalance.SEEN_RANGE * KleinStarBalance.SEEN_RANGE;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
    }
}
