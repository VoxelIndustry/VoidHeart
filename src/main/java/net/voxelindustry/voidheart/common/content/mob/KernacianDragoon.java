package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.DragoonClimb.Probe;
import net.voxelindustry.voidheart.common.content.mob.DragoonClimb.Step;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * An ancient war machine. It walks slowly toward whatever it fights, kicks away anything that comes too close, and from range opens its head to pour out a beam of
 * void fire that follows its target. Every hit teaches it: each kind of damage does less the more it has been used on it. Nothing it can't walk to is out of its
 * reach: it climbs pillars and walls after its target, edges out from under overhangs, and strikes from the wall at anything on the ledge above it.
 */
public class KernacianDragoon extends Automaton
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "dragoon_beam"));

    public static final int BEAM_CHARGE_TICKS = 30;
    public static final int BEAM_FIRE_TICKS = 40;
    public static final int BEAM_COOLDOWN_TICKS = 160;
    public static final int BEAM_HIT_INTERVAL = 5;
    public static final float BEAM_DAMAGE = 14;
    public static final double BEAM_RANGE = 24;
    public static final int KICK_WINDUP_TICKS = 8;
    public static final int KICK_FOLLOW_TICKS = 6;
    public static final float KICK_DAMAGE = 12;
    public static final double KICK_POWER = 4;
    public static final int CLOSE_TICKS = 8;

    private static final EntityDataAccessor<Integer> DATA_ADAPTATION = SynchedEntityData.defineId(KernacianDragoon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BEAM = SynchedEntityData.defineId(KernacianDragoon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_BEAM_END = SynchedEntityData.defineId(KernacianDragoon.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> DATA_KICK = SynchedEntityData.defineId(KernacianDragoon.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_CLIMBING = SynchedEntityData.defineId(KernacianDragoon.class, EntityDataSerializers.BOOLEAN);

    private static final int EXPERIENCE = 200;
    private static final double SPEED = 0.17;
    private static final double BEAM_MIN_RANGE = 4;
    // About as fast as a sprinting player circles it from a few blocks away, so running sideways only just stays ahead
    private static final double BEAM_TURN = Math.toRadians(2.5);
    private static final double BEAM_HIT_RADIUS = 0.35;
    private static final int BURN_SECONDS = 5;
    // Fire Protection counts double against the beam, as against fire, which the beam isn't tagged as so that Fire Resistance can't stop it
    private static final float FIRE_PROTECTION_PER_LEVEL = 2F / 25;
    private static final float FIRE_PROTECTION_CAP = 0.8F;
    private static final double CORE_HEIGHT = 2.2;
    private static final double CORE_FORWARD = 0.3;
    private static final double KICK_REACH = 1.4;
    private static final int KICK_COOLDOWN_TICKS = 40;
    private static final float GROW_PER_TICK = 0.25F;
    // Where its vents, joints and furnace are, as right, up and forward from its feet at its drawn scale
    private static final double[] BACK_VENT = { 0.12, 1.6, -0.4 };
    private static final double[][] JOINTS = { { 0.53, 2, 0 }, { -0.53, 2, 0 }, { 0.2, 1, 0 }, { -0.2, 1, 0 }, { 0.2, 0.55, -0.1 }, { -0.2, 0.55, -0.1 } };
    private static final double[] KICK_FOOT = { 0.2, 0.7, 0.75 };
    private static final int IDLE_STEAM_MIN_TICKS = 70;
    private static final int IDLE_STEAM_SPREAD_TICKS = 50;
    private static final int STEP_TICKS = 18;
    private static final float WALKING_SPEED = 0.15F;
    private static final int SPARKS_PER_HIT = 8;
    // A hand meets the face each time it climbs this far, alternating right and left
    private static final float CLIMB_CYCLE_PER_BLOCK = Mth.TWO_PI / 1.6F;
    private static final float CLIMB_POSE_PER_TICK = 0.2F;
    private static final double[] GRIP = { 0.6, 2.75, 0.5 };
    private static final int GRIP_CRUMBS = 5;
    // Within this of facing its target, a face it bumps into is one worth climbing
    private static final double CLIMB_FACING = 0.4;
    private static final int TARGET_MEMORY_TICKS = 600;
    // On guard it sees ahead of it, as its one eye slit would, and senses anyone this close whichever way they are
    private static final double GUARD_VIEW_COS = Math.cos(Math.toRadians(70));
    private static final double GUARD_SENSE_RANGE = 6;
    private static final double AT_POST = 1.2;
    private static final int POST_REPATH_TICKS = 20;
    // A post it can't find its way back to becomes wherever it gave up
    private static final int POST_GIVE_UP_TICKS = 600;

    private final ServerBossEvent bossEvent = new ServerBossEvent(getUUID(), getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);

    private Vec3 beamDirection = Vec3.ZERO;
    private int beamCooldown = BEAM_COOLDOWN_TICKS / 2;
    private int kickCooldown;
    private int climbCooldown;

    public int closingTicks = CLOSE_TICKS;
    private int lastBeamTicks;
    private int steamDelay = IDLE_STEAM_MIN_TICKS;
    private int stepDelay;

    /**
     * How far into its climbing pose it is, and how far its hands have gone hand over hand, on the client.
     */
    public float climbPose;
    public float climbPoseO;
    public float climbCycle;
    public float climbCycleO;
    private int lastGrip = Integer.MIN_VALUE;

    private @Nullable BlockPos post;
    private float postYaw;

    /**
     * How far each kind's parts have grown on the client, following the hit counts so that new parts grow in rather than pop.
     */
    public final float[] grown = new float[Kind.values().length];
    public final float[] grownO = new float[Kind.values().length];

    public KernacianDragoon(EntityType<? extends KernacianDragoon> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        bossEvent.setVisible(false);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(400, KICK_DAMAGE, SPEED)
                .add(Attributes.ARMOR, 20)
                .add(Attributes.ARMOR_TOUGHNESS, 8)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1)
                .add(Attributes.STEP_HEIGHT, 1.05)
                .add(Attributes.FOLLOW_RANGE, 32);
    }

    public int adaptation()
    {
        return entityData.get(DATA_ADAPTATION);
    }

    public void setAdaptation(int packed)
    {
        entityData.set(DATA_ADAPTATION, packed);
    }

    public int beamTicks()
    {
        return entityData.get(DATA_BEAM);
    }

    public int kickTicks()
    {
        return entityData.get(DATA_KICK);
    }

    public boolean isClimbing()
    {
        return entityData.get(DATA_CLIMBING);
    }

    /**
     * Shows it clinging to a wall, without climbing, to pose it.
     */
    public void showClimb(boolean climbing)
    {
        entityData.set(DATA_CLIMBING, climbing);
    }

    public boolean isFiring()
    {
        return beamTicks() > BEAM_CHARGE_TICKS;
    }

    public Vec3 beamEnd()
    {
        var end = entityData.get(DATA_BEAM_END);
        return new Vec3(end.x(), end.y(), end.z());
    }

    /**
     * Where the core inside its head is, which the model has to agree with.
     */
    public Vec3 beamOrigin(float partialTicks)
    {
        float yaw = Mth.rotLerp(partialTicks, yHeadRotO, yHeadRot) * Mth.DEG_TO_RAD;
        var forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return getPosition(partialTicks).add(0, CORE_HEIGHT, 0).add(forward.scale(CORE_FORWARD));
    }

    /**
     * Shows the head open and the beam toward a point, without firing, to pose it.
     */
    public void showBeam(Vec3 end, int ticks)
    {
        entityData.set(DATA_BEAM_END, new Vector3f((float) end.x, (float) end.y, (float) end.z));
        entityData.set(DATA_BEAM, ticks);
    }

    public void showKick(int ticks)
    {
        entityData.set(DATA_KICK, ticks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_ADAPTATION, 0);
        builder.define(DATA_BEAM, 0);
        builder.define(DATA_BEAM_END, new Vector3f());
        builder.define(DATA_KICK, 0);
        builder.define(DATA_CLIMBING, false);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new KickGoal());
        goalSelector.addGoal(2, new BeamGoal());
        goalSelector.addGoal(2, new ClimbGoal());
        goalSelector.addGoal(3, new AdvanceGoal());
        goalSelector.addGoal(4, new GuardGoal());
        addIdleGoals(6);
        // It keeps after a player who climbs or hides out of its sight, rather than forgetting them as most mobs do
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, (target, level) -> spots(target))
                .setUnseenMemoryTicks(TARGET_MEMORY_TICKS));
    }

    /**
     * Stands still at this spot, facing this way, until it spots a player or is hit, and walks back to it once it loses them.
     */
    public void guard(BlockPos post, float yaw)
    {
        this.post = post.immutable();
        postYaw = yaw;
    }

    public @Nullable BlockPos post()
    {
        return post;
    }

    public boolean isStandingGuard()
    {
        return post != null && getTarget() == null && position().distanceTo(Vec3.atBottomCenterOf(post)) <= AT_POST;
    }

    private boolean spots(LivingEntity target)
    {
        if (post == null || getTarget() != null)
            return true;
        var toward = target.position().subtract(position());
        if (toward.horizontalDistance() <= GUARD_SENSE_RANGE)
            return true;
        var flat = new Vec3(toward.x, 0, toward.z).normalize();
        return flat.dot(facing()) >= GUARD_VIEW_COS;
    }

    @Override
    public boolean isPushable()
    {
        return !isStandingGuard() && super.isPushable();
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.KERNACIAN_DRAGOON;
    }

    /**
     * A dragoon coming into view shows what it adapted to as already grown. Only hits seen happening grow in.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ADAPTATION.equals(accessor) && level().isClientSide() && tickCount > 0)
        {
            sparks(SPARKS_PER_HIT * 2);
            steam(6);
        }
        // Clamping onto a wall and letting go of it jolt its joints
        if (DATA_CLIMBING.equals(accessor) && level().isClientSide() && tickCount > 0)
        {
            sparks(SPARKS_PER_HIT / 2);
            steam(4);
        }
        if (DATA_ADAPTATION.equals(accessor) && level().isClientSide() && tickCount == 0)
        {
            int packed = adaptation();
            for (var kind : Kind.values())
            {
                grown[kind.ordinal()] = DragoonAdaptation.count(packed, kind);
                grownO[kind.ordinal()] = grown[kind.ordinal()];
            }
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        if (level().isClientSide())
        {
            int packed = adaptation();
            for (var kind : Kind.values())
            {
                int i = kind.ordinal();
                grownO[i] = grown[i];
                grown[i] = Mth.approach(grown[i], DragoonAdaptation.count(packed, kind), GROW_PER_TICK);
            }
            climbing();
            clientEffects();
        }
    }

    /**
     * On a wall it faces the wall, whatever its head follows.
     */
    @Override
    protected void tickHeadTurn(float yBodyRotT)
    {
        if (isClimbing())
            yBodyRot = getYRot();
        else
            super.tickHeadTurn(yBodyRotT);
    }

    private void climbing()
    {
        climbPoseO = climbPose;
        climbPose = Mth.approach(climbPose, isClimbing() ? 1 : 0, CLIMB_POSE_PER_TICK);
        climbCycleO = climbCycle;
        if (!isClimbing())
        {
            lastGrip = Integer.MIN_VALUE;
            return;
        }
        climbCycle += (float) position().distanceTo(new Vec3(xo, yo, zo)) * CLIMB_CYCLE_PER_BLOCK;
        // A hand grips each time it swings to the face
        int grip = Mth.floor((climbCycle - Mth.HALF_PI) / Mth.PI);
        if (grip != lastGrip && lastGrip != Integer.MIN_VALUE)
            grip((grip & 1) == 0 ? 1 : -1);
        lastGrip = grip;
    }

    /**
     * A hand bites into the face: it clanks, and what it grips crumbles from under its fingers.
     *
     * @param side 1 for its right hand, -1 for its left
     */
    private void grip(int side)
    {
        var hand = at(GRIP[0] * side, GRIP[1], GRIP[2]);
        var gripped = level().getBlockState(BlockPos.containing(hand.add(facing().scale(0.2))));
        if (!gripped.isAir())
        {
            var crumbs = new BlockParticleOption(ParticleTypes.BLOCK, gripped);
            for (int i = 0; i < GRIP_CRUMBS; i++)
                level().addParticle(crumbs, hand.x + spread(0.15), hand.y + spread(0.15), hand.z + spread(0.15), spread(0.05), -0.05, spread(0.05));
        }
        level().addParticle(ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, spread(0.15), 0.05, spread(0.15));
        level().playLocalSound(hand.x, hand.y, hand.z, sounds().step(), getSoundSource(), 0.8F, 0.55F + random.nextFloat() * 0.1F, false);
    }

    @Override
    public void handleDamageEvent(DamageSource source)
    {
        super.handleDamageEvent(source);
        sparks(SPARKS_PER_HIT);
    }

    private void clientEffects()
    {
        int beam = beamTicks();
        if (beam > 0)
        {
            closingTicks = 0;
            if (beam <= BEAM_CHARGE_TICKS)
                charging(beam);
            else
                firing(beam);
        }
        else if (lastBeamTicks > 0 || closingTicks < CLOSE_TICKS)
            closingTicks = Math.min(CLOSE_TICKS, closingTicks + 1);
        lastBeamTicks = beam;

        if (kickTicks() == KICK_WINDUP_TICKS)
        {
            var foot = at(KICK_FOOT);
            for (int i = 0; i < SPARKS_PER_HIT; i++)
                level().addParticle(ParticleTypes.ELECTRIC_SPARK, foot.x, foot.y, foot.z, spread(0.3), spread(0.3), spread(0.3));
            steam(4);
        }

        boolean walking = walkAnimation.speed() > WALKING_SPEED;
        if (walking && --stepDelay <= 0)
        {
            stepDelay = STEP_TICKS;
            steam(3);
        }
        if (--steamDelay <= 0)
        {
            steamDelay = IDLE_STEAM_MIN_TICKS + random.nextInt(IDLE_STEAM_SPREAD_TICKS);
            steam(5);
        }
    }

    /**
     * Heat smoke rises off it as it charges, and once the furnace is bare, void fire licks inside it and embers spill out over its chest.
     */
    private void charging(int ticks)
    {
        var core = beamOrigin(1);
        if (ticks == 1)
            steam(8);
        if (ticks % 2 == 0)
            level().addParticle(ParticleTypes.SMOKE, core.x + spread(0.3), core.y + 0.2, core.z + spread(0.3), 0, 0.04, 0);
        if (ticks < 8)
            return;
        var forward = facing();
        level().addParticle(VoidHeart.VOID_FLAME_PARTICLE, core.x + spread(0.15), core.y + spread(0.15), core.z + spread(0.15), 0, 0, 0);
        if (random.nextFloat() < ticks / (float) BEAM_CHARGE_TICKS)
            level().addParticle(VoidHeart.VOID_EMBER_PARTICLE, core.x, core.y - 0.1, core.z, forward.x * 0.08 + spread(0.05), 0.02, forward.z * 0.08 + spread(0.05));
    }

    /**
     * Everywhere the beam lands it splashes void fire, embers, smoke and the dust of what it burns, and it smoulders along its length.
     */
    private void firing(int ticks)
    {
        var from = beamOrigin(1);
        var to = beamEnd();
        var along = to.subtract(from);
        if (along.lengthSqr() < 1e-4)
            return;
        var direction = along.normalize();

        for (int i = 0; i < 4; i++)
            level().addParticle(VoidHeart.VOID_FLAME_PARTICLE, to.x + spread(0.4), to.y + spread(0.2), to.z + spread(0.4), spread(0.02), 0.02, spread(0.02));
        for (int i = 0; i < 3; i++)
            level().addParticle(VoidHeart.VOID_EMBER_PARTICLE, to.x, to.y + 0.05, to.z, spread(0.3) - direction.x * 0.1, 0.12 + random.nextFloat() * 0.15, spread(0.3) - direction.z * 0.1);
        if (ticks % 3 == 0)
            level().addParticle(ParticleTypes.LARGE_SMOKE, to.x, to.y + 0.2, to.z, 0, 0.05, 0);

        var hit = BlockPos.containing(to.add(direction.scale(0.1)));
        var block = level().getBlockState(hit);
        if (!block.isAir())
        {
            var dust = new BlockParticleOption(ParticleTypes.BLOCK, block);
            for (int i = 0; i < 2; i++)
                level().addParticle(dust, to.x + spread(0.3), to.y + 0.1, to.z + spread(0.3), 0, 0.1, 0);
        }

        var somewhere = from.add(along.scale(random.nextDouble()));
        level().addParticle(VoidHeart.VOID_FLAME_PARTICLE, somewhere.x, somewhere.y, somewhere.z, 0, 0, 0);
        if (ticks == BEAM_CHARGE_TICKS + 1)
        {
            steam(10);
            sparks(SPARKS_PER_HIT);
        }
    }

    private void steam(int puffs)
    {
        var back = facing().reverse();
        for (int side = -1; side <= 1; side += 2)
        {
            var vent = at(BACK_VENT[0] * side, BACK_VENT[1], BACK_VENT[2]);
            for (int i = 0; i < puffs; i++)
                level().addParticle(ParticleTypes.WHITE_SMOKE, vent.x, vent.y, vent.z, back.x * 0.06 + spread(0.02), 0.05 + random.nextFloat() * 0.03, back.z * 0.06 + spread(0.02));
        }
    }

    private void sparks(int count)
    {
        for (int i = 0; i < count; i++)
        {
            var joint = at(JOINTS[random.nextInt(JOINTS.length)]);
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, joint.x, joint.y, joint.z, spread(0.25), random.nextFloat() * 0.2, spread(0.25));
        }
    }

    private double spread(double amount)
    {
        return (random.nextDouble() - 0.5) * 2 * amount;
    }

    private Vec3 facing()
    {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }

    private Vec3 at(double[] local)
    {
        return at(local[0], local[1], local[2]);
    }

    /**
     * @return a point given as right, up and forward from its feet, turned with its body
     */
    private Vec3 at(double right, double up, double forward)
    {
        var ahead = facing();
        var toRight = new Vec3(-ahead.z, 0, ahead.x);
        return position().add(toRight.scale(right)).add(0, up, 0).add(ahead.scale(forward));
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);
        if (beamCooldown > 0)
            beamCooldown--;
        if (kickCooldown > 0)
            kickCooldown--;
        if (climbCooldown > 0)
            climbCooldown--;
        bossEvent.setProgress(getHealth() / getMaxHealth());
        bossEvent.setVisible(getTarget() != null && !isDormant());
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
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source)
    {
        return source.is(DamageTypeTags.IS_FALL) || super.isInvulnerableTo(level, source);
    }

    /**
     * Which of its adaptations a hit counts toward, or none for what no one dealt, like the void.
     */
    public static @Nullable Kind kindOf(DamageSource source)
    {
        if (source.is(MalignantBoulder.MAGIC))
            return Kind.MAGIC;
        if (source.is(DamageTypeTags.IS_EXPLOSION))
            return Kind.EXPLOSION;
        if (source.is(DamageTypeTags.IS_FIRE))
            return Kind.FIRE;
        if (source.is(DamageTypeTags.IS_PROJECTILE))
            return Kind.PROJECTILE;
        if (source.getEntity() == null)
            return null;

        var weapon = source.getWeaponItem();
        if (weapon != null && (weapon.is(ItemTags.SWORDS) || weapon.is(Items.TRIDENT)))
            return Kind.BLADE;
        return Kind.BLUNT;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage)
    {
        var kind = kindOf(source);
        int before = adaptation();
        if (kind != null)
            damage *= DragoonAdaptation.multiplier(before, kind);

        boolean hurt = super.hurtServer(level, source, damage);
        if (hurt && kind != null && isAlive())
            adapt(level, kind, before);
        return hurt;
    }

    private void adapt(ServerLevel level, Kind kind, int before)
    {
        int after = DragoonAdaptation.adapt(before, kind);
        if (after == before)
            return;
        setAdaptation(after);

        boolean grew = DragoonAdaptation.grewTier(DragoonAdaptation.count(before, kind), DragoonAdaptation.count(after, kind));
        playSound(VoidHeartSounds.DRAGOON_ADAPT, grew ? 1.6F : 0.8F, grew ? 0.8F : 1.1F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putInt("Adaptation", adaptation());
        if (post != null)
        {
            output.store("Post", BlockPos.CODEC, post);
            output.putFloat("PostYaw", postYaw);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        setAdaptation(input.getIntOr("Adaptation", 0));
        post = input.read("Post", BlockPos.CODEC).orElse(null);
        postYaw = input.getFloatOr("PostYaw", 0);
        if (hasCustomName())
            bossEvent.setName(getDisplayName());
    }

    /**
     * Fire Protection counts double, as it would against fire.
     */
    private static float againstFireProtection(LivingEntity target, float damage)
    {
        var fireProtection = target.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FIRE_PROTECTION);
        int levels = 0;
        for (var slot : EquipmentSlot.values())
        {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR)
                levels += EnchantmentHelper.getItemEnchantmentLevel(fireProtection, target.getItemBySlot(slot));
        }
        return damage * (1 - Math.min(FIRE_PROTECTION_CAP, levels * FIRE_PROTECTION_PER_LEVEL));
    }

    private Vec3 aimedEnd(Vec3 from, Vec3 direction)
    {
        var end = from.add(direction.scale(BEAM_RANGE));
        return level().clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getLocation();
    }

    private void burn(ServerLevel level, Vec3 from, Vec3 to)
    {
        for (var player : level.getEntitiesOfClass(Player.class, new AABB(from, to).inflate(1), EntitySelector.NO_CREATIVE_OR_SPECTATOR))
        {
            if (player.getBoundingBox().inflate(BEAM_HIT_RADIUS).clip(from, to).isEmpty())
                continue;
            if (player.hurtServer(level, damageSources().source(DAMAGE, this), againstFireProtection(player, BEAM_DAMAGE)))
                player.igniteForSeconds(BURN_SECONDS);
        }
    }

    private final class BeamGoal extends Goal
    {
        private BeamGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            if (target == null || !target.isAlive() || beamCooldown > 0)
                return false;
            double distance = distanceTo(target);
            return distance >= BEAM_MIN_RANGE && distance <= BEAM_RANGE && getSensing().hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return beamTicks() > 0 && beamTicks() <= BEAM_CHARGE_TICKS + BEAM_FIRE_TICKS;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            getNavigation().stop();
            entityData.set(DATA_BEAM, 1);
            playSound(VoidHeartSounds.DRAGOON_OPEN, 2, 1);
            playSound(VoidHeartSounds.DRAGOON_CHARGE, 2, 1);
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_BEAM, 0);
            beamCooldown = BEAM_COOLDOWN_TICKS;
        }

        @Override
        public void tick()
        {
            getNavigation().stop();
            var level = (ServerLevel) level();
            int ticks = beamTicks() + 1;
            entityData.set(DATA_BEAM, ticks);

            var target = getTarget();
            var from = beamOrigin(1);
            if (target != null)
            {
                getLookControl().setLookAt(target, 10, 10);
                var wanted = target.getBoundingBox().getCenter().subtract(from).normalize();
                beamDirection = ticks <= BEAM_CHARGE_TICKS || beamDirection.lengthSqr() < 1e-6 ? wanted : turnToward(beamDirection, wanted);
            }

            if (ticks <= BEAM_CHARGE_TICKS)
            {
                entityData.set(DATA_BEAM_END, toVector(from));
                return;
            }

            if (ticks == BEAM_CHARGE_TICKS + 1)
                playSound(VoidHeartSounds.DRAGOON_BEAM, 2.5F, 1);
            var end = aimedEnd(from, beamDirection);
            entityData.set(DATA_BEAM_END, toVector(end));
            if ((ticks - BEAM_CHARGE_TICKS) % BEAM_HIT_INTERVAL == 1)
                burn(level, from, end);
        }

        private static Vec3 turnToward(Vec3 current, Vec3 wanted)
        {
            double angle = Math.acos(Mth.clamp(current.dot(wanted), -1, 1));
            if (angle <= BEAM_TURN)
                return wanted;
            return current.lerp(wanted, BEAM_TURN / angle).normalize();
        }

        private static Vector3f toVector(Vec3 at)
        {
            return new Vector3f((float) at.x, (float) at.y, (float) at.z);
        }
    }

    private final class KickGoal extends Goal
    {
        private KickGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            return target != null && target.isAlive() && kickCooldown == 0 && beamTicks() == 0 && inKickReach(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return kickTicks() > 0;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            getNavigation().stop();
            entityData.set(DATA_KICK, 1);
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_KICK, 0);
            kickCooldown = KICK_COOLDOWN_TICKS;
        }

        @Override
        public void tick()
        {
            getNavigation().stop();
            advanceKick();
        }
    }

    private boolean inKickReach(LivingEntity target)
    {
        return getBoundingBox().inflate(KICK_REACH, 0, KICK_REACH).intersects(target.getBoundingBox());
    }

    /**
     * One tick of a kick, or of the strike it throws from a wall, which hit and throw alike.
     */
    private void advanceKick()
    {
        var target = getTarget();
        int ticks = kickTicks() + 1;
        if (target != null && ticks < KICK_WINDUP_TICKS)
            getLookControl().setLookAt(target, 30, 30);

        if (ticks == KICK_WINDUP_TICKS && target != null && inKickReach(target))
        {
            var level = (ServerLevel) level();
            playSound(VoidHeartSounds.DRAGOON_KICK, 2, 1);
            target.hurtServer(level, damageSources().mobAttack(this), KICK_DAMAGE);
            target.knockback(KICK_POWER, getX() - target.getX(), getZ() - target.getZ(), damageSources().mobAttack(this), KICK_DAMAGE);
        }
        if (ticks >= KICK_WINDUP_TICKS + KICK_FOLLOW_TICKS)
        {
            entityData.set(DATA_KICK, 0);
            kickCooldown = KICK_COOLDOWN_TICKS;
        }
        else
            entityData.set(DATA_KICK, ticks);
    }

    private boolean blocked(AABB box)
    {
        return !level().noCollision(this, box);
    }

    /**
     * The face in front of it on the side its target is, if there is one.
     */
    private @Nullable Direction faceToward(LivingEntity target)
    {
        var toward = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        if (toward.lengthSqr() < 1e-6)
            return null;
        toward = toward.normalize();
        Direction best = null;
        double bestFacing = CLIMB_FACING;
        for (var direction : Direction.Plane.HORIZONTAL)
        {
            double facing = direction.getStepX() * toward.x + direction.getStepZ() * toward.z;
            if (facing > bestFacing && blocked(getBoundingBox().move(direction.getStepX() * DragoonClimb.PROBE, 0, direction.getStepZ() * DragoonClimb.PROBE)))
            {
                best = direction;
                bestFacing = facing;
            }
        }
        return best;
    }

    private Probe probe(Direction face)
    {
        var box = getBoundingBox();
        double forwardX = face.getStepX();
        double forwardZ = face.getStepZ();
        double probe = DragoonClimb.PROBE;
        var footing = face.getAxis() == Direction.Axis.X ? box.deflate(DragoonClimb.FOOTING, 0, 0) : box.deflate(0, 0, DragoonClimb.FOOTING);
        return new Probe(
                blocked(box.move(forwardX * probe, 0, forwardZ * probe)),
                blocked(box.move(forwardX * probe, -DragoonClimb.LEDGE_PROBE, forwardZ * probe)),
                blocked(footing.move(0, -DragoonClimb.LEDGE_PROBE, 0)),
                !blocked(box.move(0, DragoonClimb.SPEED, 0)),
                !blocked(box.move(-forwardX * DragoonClimb.SPEED, 0, -forwardZ * DragoonClimb.SPEED)));
    }

    /**
     * Climbs after a target it can't walk to, so that no pillar, pile or ledge keeps a player out of its reach, and strikes from the wall at what is on the edge
     * above it. It holds on by moving itself each tick, so gravity never takes it while it climbs.
     */
    private final class ClimbGoal extends Goal
    {
        private @Nullable Direction face;
        private Step last = Step.RISE;
        private int lipTicks;
        private int ticks;

        private ClimbGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            var target = getTarget();
            if (target == null || !target.isAlive() || climbCooldown > 0 || beamTicks() > 0 || kickTicks() > 0 || !getNavigation().isDone())
                return false;
            var path = getNavigation().getPath();
            if (!DragoonClimb.wantsToClimb(target.getY() - getY(), path != null && path.canReach()))
                return false;
            face = faceToward(target);
            return face != null;
        }

        @Override
        public boolean canContinueToUse()
        {
            var target = getTarget();
            return face != null && target != null && target.isAlive() && DragoonClimb.keepsClimbing(target.getY() - getY(), ticks)
                    && last != Step.DONE && last != Step.LET_GO && last != Step.STUCK;
        }

        @Override
        public boolean isInterruptable()
        {
            return false;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            getNavigation().stop();
            last = Step.RISE;
            lipTicks = 0;
            ticks = 0;
            entityData.set(DATA_CLIMBING, true);
            playSound(sounds().step(), 1.6F, 0.4F);
        }

        @Override
        public void stop()
        {
            entityData.set(DATA_CLIMBING, false);
            if (kickTicks() > 0)
            {
                entityData.set(DATA_KICK, 0);
                kickCooldown = KICK_COOLDOWN_TICKS;
            }
            if (last != Step.DONE)
                climbCooldown = DragoonClimb.RETRY_TICKS;
            face = null;
        }

        @Override
        public void tick()
        {
            if (face == null)
                return;
            ticks++;
            getNavigation().stop();
            getMoveControl().setWait();
            resetFallDistance();
            setYRot(face.toYRot());
            setYBodyRot(face.toYRot());

            var target = getTarget();
            if (target != null)
                getLookControl().setLookAt(target, 30, 30);

            // It holds on to strike at a target on the edge above it
            if (kickTicks() > 0)
            {
                advanceKick();
                hold(0, 0);
                return;
            }
            if (target != null && kickCooldown == 0 && DragoonClimb.canSwipe(target.getY() - getY()) && inKickReach(target))
            {
                entityData.set(DATA_KICK, 1);
                hold(0, 0);
                return;
            }

            var step = DragoonClimb.next(probe(face), last, lipTicks);
            lipTicks = step == Step.LIP_OUT ? lipTicks + 1 : 0;
            last = step;
            var motion = DragoonClimb.motion(step);
            hold(motion[0], motion[1]);
        }

        /**
         * Moves it this tick, as forward toward the face and up, replacing whatever gravity left it with.
         */
        private void hold(double forward, double up)
        {
            if (face == null)
                return;
            if (forward == 0 && up == 0)
                forward = DragoonClimb.PUSH;
            setDeltaMovement(face.getStepX() * forward, up, face.getStepZ() * forward);
        }
    }

    private final class AdvanceGoal extends Goal
    {
        private int repathDelay;

        private AdvanceGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
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
        public void start()
        {
            setAggressive(true);
            repathDelay = 0;
        }

        @Override
        public void stop()
        {
            setAggressive(false);
            getNavigation().stop();
        }

        @Override
        public void tick()
        {
            var target = getTarget();
            if (target == null)
                return;
            getLookControl().setLookAt(target, 20, 20);
            if (--repathDelay <= 0)
            {
                repathDelay = 20;
                getNavigation().moveTo(target, 1);
            }
            var path = getNavigation().getPath();
            if (getNavigation().isDone() && (path == null || !path.canReach()) && target.getY() - getY() > DragoonClimb.MIN_RISE)
                getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1);
        }
    }

    /**
     * With no one to fight it walks back to its post, then stands there as still as the machine it is, not even turning its head.
     */
    private final class GuardGoal extends Goal
    {
        private int repathDelay;
        private int awayTicks;

        private GuardGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            return post != null && getTarget() == null && !isClimbing();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            repathDelay = 0;
            awayTicks = 0;
        }

        @Override
        public void stop()
        {
            getNavigation().stop();
        }

        @Override
        public void tick()
        {
            if (post == null)
                return;
            var spot = Vec3.atBottomCenterOf(post);
            if (position().distanceTo(spot) > AT_POST)
            {
                if (++awayTicks >= POST_GIVE_UP_TICKS)
                {
                    post = blockPosition();
                    return;
                }
                if (--repathDelay <= 0)
                {
                    repathDelay = POST_REPATH_TICKS;
                    getNavigation().moveTo(spot.x, spot.y, spot.z, 1);
                }
                return;
            }

            awayTicks = 0;
            getNavigation().stop();
            setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
            setYRot(postYaw);
            setYBodyRot(postYaw);
            setYHeadRot(postYaw);
            setXRot(0);
            var ahead = getEyePosition().add(facing());
            getLookControl().setLookAt(ahead.x, ahead.y, ahead.z);
        }
    }
}
