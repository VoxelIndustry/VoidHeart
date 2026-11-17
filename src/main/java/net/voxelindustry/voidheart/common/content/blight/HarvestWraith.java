package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.WraithStain;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A mob slain by Night's Harvest, raised as a wraith for its wielder. It takes the dead mob's shape and size, rises out of a stain of ink under the
 * corpse, glides through blocks at whatever its wielder last struck or whatever struck its wielder, and sinks back into the ground when its time is
 * up. What it kills counts as its wielder's kill. Never kept with the world.
 */
public class HarvestWraith extends Mob
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "harvest_wraith"));
    /** Mobs that never rise, bosses above all. */
    public static final TagKey<EntityType<?>>   UNHARVESTABLE = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "unharvestable"));

    private static final EntityDataAccessor<String>  DATA_SOURCE = SynchedEntityData.defineId(HarvestWraith.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SINK   = SynchedEntityData.defineId(HarvestWraith.class, EntityDataSerializers.INT);

    /** How far beside its target it stops, past both their sides. */
    private static final double STAND_OFF    = 0.25;
    /** How close to its wielder it stays when there is nothing to fight. */
    private static final double HEEL         = 3.5;
    /** How much of the wanted speed it takes each tick, so it glides rather than snaps. */
    private static final double STEER        = 0.3;

    private @Nullable UUID         owner;
    private @Nullable LivingEntity target;
    private int                    attackCooldown;

    public HarvestWraith(EntityType<? extends HarvestWraith> type, Level level)
    {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, BlightBalance.WRAITH_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, BlightBalance.WRAITH_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, BlightBalance.WRAITH_REACH);
    }

    /** Raises the slain mob as its slayer's wraith, sending back that slayer's oldest if they already keep the most. */
    public static void raise(ServerLevel level, LivingEntity slain, Player slayer)
    {
        var mine = level.getEntities(VoidHeartEntities.HARVEST_WRAITH, wraith -> wraith.isAlive() && slayer.getUUID().equals(wraith.owner) && !wraith.isSinking());
        for (var old : Harvest.toRetire(mine, wraith -> wraith.tickCount))
            old.beginSinking();

        var wraith = new HarvestWraith(VoidHeartEntities.HARVEST_WRAITH, level);
        wraith.owner = slayer.getUUID();
        wraith.entityData.set(DATA_SOURCE, BuiltInRegistries.ENTITY_TYPE.getKey(slain.getType()).toString());
        wraith.refreshDimensions();
        wraith.snapTo(slain.getX(), slain.getY(), slain.getZ(), slain.getYRot(), 0);
        wraith.yBodyRot = slain.yBodyRot;
        wraith.yHeadRot = slain.yHeadRot;
        level.addFreshEntity(wraith);
        wraith.stain(BlightBalance.WRAITH_STAIN_TICKS + BlightBalance.WRAITH_RISE_TICKS);
        level.playSound(null, slain.getX(), slain.getY(), slain.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.6F);
    }

    /** The kind of mob it was raised from, which it is drawn and sized as. */
    public Optional<EntityType<?>> source()
    {
        var id = Identifier.tryParse(entityData.get(DATA_SOURCE));
        return id == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(id);
    }

    public int sinkStart()
    {
        return entityData.get(DATA_SINK);
    }

    public boolean isSinking()
    {
        return sinkStart() >= 0;
    }

    public Harvest.Phase phase()
    {
        return Harvest.phase(tickCount, sinkStart());
    }

    public @Nullable UUID owner()
    {
        return owner;
    }

    public void beginSinking()
    {
        if (isSinking())
            return;
        entityData.set(DATA_SINK, tickCount);
        setDeltaMovement(Vec3.ZERO);
        stain(BlightBalance.WRAITH_SINK_TICKS);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8F, 0.45F);
    }

    private void stain(int lingerTicks)
    {
        if (level() instanceof ServerLevel level)
            WraithStain.under(level, position(), Math.max(0.5F, getBbWidth() * 0.9F), BlightBalance.WRAITH_STAIN_TICKS, lingerTicks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_SOURCE, "");
        builder.define(DATA_SINK, -1);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_SOURCE.equals(accessor))
            refreshDimensions();
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose)
    {
        return source().map(EntityType::getDimensions).orElseGet(() -> super.getDefaultDimensions(pose));
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        if (attackCooldown > 0)
            attackCooldown--;

        switch (phase())
        {
            case GONE ->
            {
                discard();
                return;
            }
            case STAIN, RISING, SINKING ->
            {
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            default ->
            {
            }
        }

        var wielder = owner == null ? null : level.getPlayerByUUID(owner);
        if (wielder == null || !wielder.isAlive() || wielder.level() != level || Harvest.isSpent(tickCount))
        {
            beginSinking();
            return;
        }

        target = chooseTarget(wielder);
        if (target != null)
            fight(level, wielder, target);
        else
            heel(wielder);
    }

    /** Whatever its wielder last struck, otherwise whatever struck its wielder, otherwise what it was already after. */
    private @Nullable LivingEntity chooseTarget(Player wielder)
    {
        for (var candidate : new LivingEntity[] { wielder.getLastHurtMob(), wielder.getLastHurtByMob(), target })
        {
            if (isFoe(wielder, candidate))
                return candidate;
        }
        return null;
    }

    private boolean isFoe(Player wielder, @Nullable LivingEntity candidate)
    {
        return candidate != null && candidate.isAlive() && candidate != this && !(candidate instanceof Player) && !(candidate instanceof HarvestWraith)
               && candidate.level() == level() && candidate.distanceToSqr(wielder) < BlightBalance.WRAITH_REACH * BlightBalance.WRAITH_REACH;
    }

    private void fight(ServerLevel level, Player wielder, LivingEntity foe)
    {
        var away = new Vec3(getX() - foe.getX(), 0, getZ() - foe.getZ());
        if (away.lengthSqr() < 1.0E-4)
            away = new Vec3(1, 0, 0);
        double standOff = foe.getBbWidth() / 2 + getBbWidth() / 2 + STAND_OFF;
        glideTo(foe.position().add(away.normalize().scale(standOff)), foe.position());

        double reach = standOff + 0.6;
        if (attackCooldown <= 0 && distanceToSqr(foe) < reach * reach)
        {
            attackCooldown = BlightBalance.WRAITH_ATTACK_TICKS;
            swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
            foe.hurtServer(level, damageSources().source(DAMAGE, this, wielder), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
    }

    private void heel(Player wielder)
    {
        if (distanceToSqr(wielder) > HEEL * HEEL)
            glideTo(wielder.position().add(new Vec3(getX() - wielder.getX(), 0, getZ() - wielder.getZ()).normalize().scale(HEEL * 0.7)), wielder.position());
        else
            setDeltaMovement(getDeltaMovement().scale(0.6));
    }

    /** Straight at the point through whatever is in the way, slowing as it arrives, facing where it looks. */
    private void glideTo(Vec3 point, Vec3 look)
    {
        var to = point.subtract(position());
        double distance = to.length();
        var wanted = distance < 1.0E-3 ? Vec3.ZERO : to.scale(Math.min(BlightBalance.WRAITH_SPEED, distance * 0.5) / distance);
        setDeltaMovement(getDeltaMovement().lerp(wanted, STEER));

        var facing = look.subtract(position());
        if (facing.horizontalDistanceSqr() > 1.0E-4)
        {
            float yaw = (float) (Mth.atan2(facing.z, facing.x) * Mth.RAD_TO_DEG) - 90;
            setYRot(yaw);
            yBodyRot = yaw;
            yHeadRot = yaw;
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        // Glides through blocks: it moves itself, outside of the AI's walking
        if (!level().isClientSide() && phase() == Harvest.Phase.FIGHTING)
            move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public void travel(Vec3 input)
    {
        // Its glide is its own: no walking, falling or friction
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount)
    {
        if (phase() != Harvest.Phase.FIGHTING)
            return false;
        // Its own wielder's swings pass through it
        if (owner != null && source.getEntity() != null && owner.equals(source.getEntity().getUUID()))
            return false;
        if (amount >= getHealth())
        {
            // Struck down, it sinks back into the ground rather than lying dead
            playHurtSound(source);
            beginSinking();
            return true;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    protected void doPush(Entity entity)
    {
    }

    @Override
    public boolean canBeSeenAsEnemy()
    {
        return phase() == Harvest.Phase.FIGHTING && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean isPickable()
    {
        return phase() == Harvest.Phase.FIGHTING && super.isPickable();
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public boolean isAffectedByPotions()
    {
        return false;
    }
}
