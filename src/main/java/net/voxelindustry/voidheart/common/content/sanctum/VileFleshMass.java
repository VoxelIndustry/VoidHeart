package net.voxelindustry.voidheart.common.content.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.common.content.sanctum.SanctumBalance.*;

/**
 * What sits at the end of a sanctum: a heap of flesh grown over the place where its pillar stood, a toothed gash down its front and eyes all
 * over it. It never moves. Woken, it raises tentacles out of the floor under whoever is near, sweeps round itself, grabs those who keep their
 * distance to drag them to its maw, and spits bile at the rest. Its shoggoths come back to it to be eaten when hurt.
 * <p>
 * Dead, it comes apart where it sat while the pillar rises out of it.
 */
public class VileFleshMass extends VoidMonster
{
    public static final ResourceKey<DamageType> ERUPTION = damage("flesh_eruption");
    public static final ResourceKey<DamageType> BITE = damage("flesh_bite");

    private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(VileFleshMass.class, EntityDataSerializers.BOOLEAN);

    public static final byte EVENT_SWEEP = 71;
    public static final byte EVENT_GRAB = 72;
    public static final byte EVENT_SPIT = 73;
    public static final byte EVENT_BITE = 74;
    public static final byte EVENT_FEED = 75;

    public static final int DEATH_TICKS = 70;
    private static final int[] PILLAR_TICKS = { 24, 40, 56 };

    private enum Action
    {
        NONE,
        SWEEP,
        SPIT
    }

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);

    private Action action = Action.NONE;
    private int actionTicks;
    private int pause;
    private int eruptTicks = ERUPT_INTERVAL / 2;
    private int sweepCooldown;
    private int grabCooldown = GRAB_COOLDOWN / 3;
    private int spitCooldown;

    /** Client only: ticks since each kind of move began, or -1. */
    public int sweepAge = -1, grabAge = -1, spitAge = -1, biteAge = -1, feedAge = -1;

    public VileFleshMass(EntityType<? extends VileFleshMass> type, Level level)
    {
        super(type, level);
        bossEvent.setVisible(false);
        setPersistenceRequired();
    }

    private static ResourceKey<DamageType> damage(String name)
    {
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, name));
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(MASS_HEALTH, SWEEP_DAMAGE, 0)
                .add(Attributes.ARMOR, MASS_ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, MASS_TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.FOLLOW_RANGE, MASS_RANGE);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.VILE_FLESH_MASS;
    }

    @Override
    protected void registerGoals()
    {
    }

    // It never moves: nothing pushes it, and it pushes back like a wall
    @Override
    public void travel(Vec3 input)
    {
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(Entity entity)
    {
    }

    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity entity)
    {
        return isAlive();
    }

    @Override
    public boolean isAffectedByPotions()
    {
        return false;
    }

    public boolean isAwake()
    {
        return entityData.get(DATA_AWAKE);
    }

    private void setAwake(boolean awake)
    {
        entityData.set(DATA_AWAKE, awake);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_AWAKE, false);
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        bossEvent.setProgress(getHealth() / getMaxHealth());

        var target = pickTarget(level);
        setTarget(target);
        setAwake(target != null || (isAwake() && level.getNearestPlayer(this, MASS_RANGE) != null));
        bossEvent.setVisible(isAwake());
        if (!isAwake())
            return;
        if (target != null)
            getLookControl().setLookAt(target, 10, 10);

        if (sweepCooldown > 0)
            sweepCooldown--;
        if (grabCooldown > 0)
            grabCooldown--;
        if (spitCooldown > 0)
            spitCooldown--;

        if (--eruptTicks <= 0)
        {
            eruptTicks = enraged() ? ERUPT_INTERVAL_ENRAGED : ERUPT_INTERVAL;
            erupt(level);
        }

        if (action != Action.NONE)
        {
            actionTicks++;
            act(level, target);
            return;
        }
        if (pause > 0)
        {
            pause--;
            return;
        }
        choose(level, target);
    }

    private boolean enraged()
    {
        return getHealth() / getMaxHealth() < MASS_ENRAGED_BELOW;
    }

    private @Nullable Player pickTarget(ServerLevel level)
    {
        double range = isAwake() ? MASS_RANGE : MASS_WAKE_RANGE;
        return level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range), EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive)).stream()
                .filter(player -> distanceTo(player) <= range && canAttack(player))
                .min(Comparator.comparingDouble(this::distanceToSqr))
                .orElse(null);
    }

    private List<Player> playersWithin(ServerLevel level, double range)
    {
        var players = new ArrayList<Player>();
        for (var player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(range), EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive)))
        {
            if (edgeDistance(player) <= range)
                players.add(player);
        }
        return players;
    }

    /** @return how far an entity stands from the edge of its body */
    public double edgeDistance(Entity entity)
    {
        double dx = entity.getX() - getX(), dz = entity.getZ() - getZ();
        return Math.max(0, Math.sqrt(dx * dx + dz * dz) - getBbWidth() / 2 - entity.getBbWidth() / 2);
    }

    private void choose(ServerLevel level, @Nullable Player target)
    {
        if (sweepCooldown == 0 && !playersWithin(level, SWEEP_RANGE).isEmpty())
        {
            start(level, Action.SWEEP, EVENT_SWEEP);
            sweepCooldown = SWEEP_COOLDOWN;
            playSound(VoidHeartSounds.MASS_SWEEP, 2, 0.7F);
            return;
        }
        if (target == null)
            return;
        double distance = edgeDistance(target);
        if (grabCooldown == 0 && distance >= GRAB_MIN && distance <= GRAB_RANGE && hasLineOfSight(target) && !holding())
        {
            grabCooldown = GRAB_COOLDOWN;
            pause = MASS_PAUSE_TICKS;
            level.broadcastEntityEvent(this, EVENT_GRAB);
            playSound(VoidHeartSounds.MASS_GRAB, 2, 0.8F);
            FleshTentacle.grab(level, this, target);
            return;
        }
        if (spitCooldown == 0 && distance >= SPIT_MIN && hasLineOfSight(target))
        {
            start(level, Action.SPIT, EVENT_SPIT);
            spitCooldown = SPIT_COOLDOWN;
        }
    }

    private void start(ServerLevel level, Action action, byte event)
    {
        this.action = action;
        actionTicks = 0;
        level.broadcastEntityEvent(this, event);
    }

    private void act(ServerLevel level, @Nullable Player target)
    {
        switch (action)
        {
            case SWEEP ->
            {
                if (actionTicks == SWEEP_WINDUP)
                    sweep(level);
                if (actionTicks >= SWEEP_WINDUP + SWEEP_RECOVER)
                    finish();
            }
            case SPIT ->
            {
                if (actionTicks == SPIT_WINDUP && target != null)
                    spit(level, target);
                if (actionTicks >= SPIT_WINDUP + 6)
                    finish();
            }
            default -> finish();
        }
    }

    private void finish()
    {
        action = Action.NONE;
        pause = MASS_PAUSE_TICKS;
    }

    private void sweep(ServerLevel level)
    {
        playSound(VoidHeartSounds.MASS_SWEEP, 2.5F, 0.5F);
        for (var player : playersWithin(level, SWEEP_RANGE))
        {
            if (!player.hurtServer(level, damageSources().mobAttack(this), SWEEP_DAMAGE))
                continue;
            var away = new Vec3(player.getX() - getX(), 0, player.getZ() - getZ()).normalize();
            player.knockback(SWEEP_KNOCKBACK, -away.x, -away.z, damageSources().mobAttack(this), SWEEP_DAMAGE);
            SanctumPush.sync(player);
        }
    }

    private void spit(ServerLevel level, Player target)
    {
        var mouth = mouth();
        // Leads a moving target a little, then lobs high enough to come down on it
        var aim = target.position().add(target.getDeltaMovement().scale(8));
        level.addFreshEntity(new BileGlob(level, this, mouth, aim));
        playSound(VoidHeartSounds.MASS_SPIT, 2, 0.9F);
    }

    private void erupt(ServerLevel level)
    {
        int count = 0;
        for (var player : playersWithin(level, MASS_RANGE))
        {
            if (count >= ERUPT_MAX)
                break;
            if (hasLineOfSight(player) || edgeDistance(player) < 12)
            {
                FleshTentacle.erupt(level, this, floorUnder(player.position()));
                count++;
            }
        }
        // And one more at its own feet, for whoever comes too close
        double angle = random.nextDouble() * Math.PI * 2;
        double reach = getBbWidth() / 2 + 1.5 + random.nextDouble() * 2;
        FleshTentacle.erupt(level, this, floorUnder(position().add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach)));
    }

    private Vec3 floorUnder(Vec3 at)
    {
        var pos = BlockPos.containing(at);
        for (int down = 0; down < 6; down++)
        {
            var below = pos.below(down);
            if (!level().getBlockState(below.below()).getCollisionShape(level(), below.below()).isEmpty())
                return new Vec3(at.x, below.getY(), at.z);
        }
        return at;
    }

    /** Where its gash opens, low on its front. */
    public Vec3 mouth()
    {
        var look = Vec3.directionFromRotation(0, getYRot());
        return position().add(look.scale(getBbWidth() / 2)).add(0, getBbHeight() * 0.42, 0);
    }

    private boolean holding()
    {
        return !level().getEntitiesOfClass(FleshTentacle.class, getBoundingBox().inflate(GRAB_RANGE + 4), tentacle -> tentacle.isGrabOf(this)).isEmpty();
    }

    /** A tentacle has dragged someone to its gash. */
    public void bite(ServerLevel level, LivingEntity victim)
    {
        level.broadcastEntityEvent(this, EVENT_BITE);
        playSound(VoidHeartSounds.MASS_BITE, 2.5F, 0.6F);
        victim.hurtServer(level, damageSources().source(BITE, this), BITE_DAMAGE);
        var away = new Vec3(victim.getX() - getX(), 0, victim.getZ() - getZ()).normalize();
        victim.knockback(1.2, -away.x, -away.z, damageSources().source(BITE, this), BITE_DAMAGE);
        SanctumPush.sync(victim);
    }

    /** A shoggoth crawled back in. */
    public void feed(LesserShoggoth shoggoth)
    {
        if (!(level() instanceof ServerLevel level))
            return;
        heal(FEED_HEAL + FEED_HEAL_PER_HEALTH * shoggoth.getHealth());
        level.broadcastEntityEvent(this, EVENT_FEED);
        playSound(VoidHeartSounds.MASS_FEED, 2, 0.7F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VILE_FLESH.defaultBlockState()), shoggoth.getX(), shoggoth.getY() + 0.5, shoggoth.getZ(),
                30, 0.4, 0.4, 0.4, 0.1);
    }

    @Override
    public void handleEntityEvent(byte event)
    {
        switch (event)
        {
            case EVENT_SWEEP -> sweepAge = 0;
            case EVENT_GRAB -> grabAge = 0;
            case EVENT_SPIT -> spitAge = 0;
            case EVENT_BITE -> biteAge = 0;
            case EVENT_FEED -> feedAge = 0;
            default -> super.handleEntityEvent(event);
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
        {
            sweepAge = age(sweepAge, SWEEP_WINDUP + SWEEP_RECOVER);
            grabAge = age(grabAge, 30);
            spitAge = age(spitAge, SPIT_WINDUP + 10);
            biteAge = age(biteAge, 16);
            feedAge = age(feedAge, 20);
        }
    }

    private static int age(int age, int lasts)
    {
        return age < 0 || age + 1 > lasts ? -1 : age + 1;
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (level() instanceof ServerLevel level)
        {
            int index = Sanctums.indexAt(blockPosition());
            if (index >= 0)
                Sanctums.state(level.getServer()).markSlain(index);
            bossEvent.setVisible(false);
        }
    }

    /** It takes its time coming apart, and the pillar rises out of it as it goes. */
    @Override
    protected void tickDeath()
    {
        deathTime++;
        if (!(level() instanceof ServerLevel level))
            return;
        if (deathTime % 4 == 0)
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, VoidHeartBlocks.VILE_FLESH.defaultBlockState()), getX(), getY() + getBbHeight() * 0.5, getZ(),
                    16, getBbWidth() * 0.3, getBbHeight() * 0.3, getBbWidth() * 0.3, 0.1);
        int index = Sanctums.indexAt(blockPosition());
        for (int stone = 0; stone < PILLAR_TICKS.length; stone++)
        {
            if (deathTime == PILLAR_TICKS[stone] && index >= 0 && Sanctums.altar(index).closerToCenterThan(position(), 3))
                SanctumPillar.raise(level, index, stone);
        }
        if (deathTime >= DEATH_TICKS && !isRemoved())
        {
            level.broadcastEntityEvent(this, (byte) 60);
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt)
            setAwake(true);
        return hurt;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player)
    {
        super.startSeenByPlayer(player);
        bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    public boolean isBossBarShownTo(ServerPlayer player)
    {
        return bossEvent.isVisible() && bossEvent.getPlayers().contains(player);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putBoolean("Awake", isAwake());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        setAwake(input.getBooleanOr("Awake", false));
        if (hasCustomName())
            bossEvent.setName(getDisplayName());
    }
}
