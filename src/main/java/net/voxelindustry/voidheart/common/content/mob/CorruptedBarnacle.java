package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

/**
 * Starts small and quick, grows every two minutes it spends loaded, and in its last stage cements itself where it stands and feeds with a long limb.
 * One burst out of a player runs from them and turns into a nest instead of fighting.
 */
public class CorruptedBarnacle extends CrawlerMonster
{
    public static final int GROWN = 2;
    public static final int GROWTH_TICKS = 2 * 60 * 20;

    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(CorruptedBarnacle.class, EntityDataSerializers.INT);

    private static final float[] WIDTHS = { 0.5F, 0.75F, 1 };
    private static final float[] HEIGHTS = { 0.35F, 0.5F, 0.7F };
    private static final double[] HEALTH = { 6, 16, 30 };
    private static final double[] DAMAGE = { 2, 4, 6 };
    private static final double[] SPEEDS = { 0.33, 0.24, 0 };
    private static final float[] INFECTION_CHANCES = { 0.1F, 0.2F, 0.35F };
    private static final int[] EXPERIENCE = { 2, 4, 7 };

    private static final double LIMB_REACH = 3;
    private static final int LIMB_COOLDOWN = 35;
    private static final int SCATTER_TICKS = 30 * 20;
    private static final double SCATTER_MIN = 16;
    private static final double SCATTER_MAX = 32;
    // Paths may end a node beside their target and the mob stops short of that node, so it aims past where it will nest
    private static final double SCATTER_OVERSHOOT = 3;
    private static final double SCATTER_RUN_SPEED = 1.25;
    private static final int PATH_INTERVAL = 20;
    private static final double STUCK_DISTANCE = 0.5;
    private static final int NEST_SEARCH_RADIUS = 2;
    private static final int NESTS_NEARBY_LIMIT = 8;
    private static final int NESTS_NEARBY_RANGE = 64;

    private int growthTicks;
    private int scatterTicks;
    private Vec3 scatterFrom = Vec3.ZERO;
    private Vec3 scatterTarget = Vec3.ZERO;
    private double scatterDistance;

    public CorruptedBarnacle(EntityType<? extends CorruptedBarnacle> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE[0];
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(HEALTH[0], DAMAGE[0], SPEEDS[0]);
    }

    public int stage()
    {
        return entityData.get(DATA_STAGE);
    }

    public void setStage(int stage)
    {
        growthTicks = stage * GROWTH_TICKS;
        applyStage(stage);
    }

    private void applyStage(int stage)
    {
        double previous = getMaxHealth();
        entityData.set(DATA_STAGE, stage);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH[stage]);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(DAMAGE[stage]);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEEDS[stage]);
        if (stage == GROWN)
            getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        setHealth((float) Math.min(getMaxHealth(), getHealth() + HEALTH[stage] - previous));
        xpReward = EXPERIENCE[stage];
        refreshDimensions();
    }

    public boolean isRooted()
    {
        return stage() == GROWN;
    }

    public boolean isScattering()
    {
        return scatterTicks > 0;
    }

    /**
     * Sends a barnacle that just burst out of a player running away from where they stand.
     */
    public void scatterFrom(Vec3 from, double angle)
    {
        scatterFrom = from;
        scatterTicks = SCATTER_TICKS;
        scatterDistance = SCATTER_MIN + random.nextDouble() * (SCATTER_MAX - SCATTER_MIN);
        chooseScatterTarget(angle);
    }

    private void chooseScatterTarget(double angle)
    {
        double reach = scatterDistance + SCATTER_OVERSHOOT;
        scatterTarget = scatterFrom.add(Math.cos(angle) * reach, 0, Math.sin(angle) * reach);
    }

    @Override
    public boolean canCrawl()
    {
        return !isRooted();
    }

    @Override
    public boolean canChase()
    {
        return !isScattering();
    }

    @Override
    public double attackReach()
    {
        return isRooted() ? LIMB_REACH : super.attackReach();
    }

    @Override
    public int attackCooldown()
    {
        return isRooted() ? LIMB_COOLDOWN : super.attackCooldown();
    }

    @Override
    public void setTarget(@Nullable LivingEntity target)
    {
        super.setTarget(isScattering() ? null : target);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder)
    {
        super.defineSynchedData(builder);
        builder.define(DATA_STAGE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor)
    {
        super.onSyncedDataUpdated(accessor);
        if (DATA_STAGE.equals(accessor))
            refreshDimensions();
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose)
    {
        int stage = stage();
        return EntityDimensions.scalable(WIDTHS[stage], HEIGHTS[stage]);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(1, new ScatterGoal());
        goalSelector.addGoal(2, new CrawlerChaseGoal(this));
        goalSelector.addGoal(5, new CrawlerIdleGoal(this));
        goalSelector.addGoal(5, new StrollGoal(0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.BARNACLE;
    }

    @Override
    public void travel(Vec3 input)
    {
        if (!isRooted())
        {
            super.travel(input);
            return;
        }

        setDeltaMovement(getDeltaMovement().multiply(0, 1, 0));
        if (touching(surface().getOpposite()))
            setDeltaMovement(Vec3.ZERO);
        else
        {
            if (isClinging())
                letGo();
            move(MoverType.SELF, new Vec3(0, -0.2, 0));
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level)
    {
        super.customServerAiStep(level);

        if (scatterTicks > 0)
            scatterTicks--;
        if (stage() < GROWN)
        {
            growthTicks++;
            if (growthTicks >= (stage() + 1) * GROWTH_TICKS)
                applyStage(stage() + 1);
        }
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target)
    {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof ServerPlayer player && random.nextFloat() < INFECTION_CHANCES[stage()])
            BarnacleInfection.infect(player);
        return hit;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output)
    {
        super.addAdditionalSaveData(output);
        output.putInt("GrowthTicks", growthTicks);
        output.putInt("ScatterTicks", scatterTicks);
        output.store("ScatterFrom", Vec3.CODEC, scatterFrom);
        output.store("ScatterTarget", Vec3.CODEC, scatterTarget);
        output.putDouble("ScatterDistance", scatterDistance);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input)
    {
        super.readAdditionalSaveData(input);
        growthTicks = input.getIntOr("GrowthTicks", 0);
        scatterTicks = input.getIntOr("ScatterTicks", 0);
        scatterFrom = input.read("ScatterFrom", Vec3.CODEC).orElse(Vec3.ZERO);
        scatterTarget = input.read("ScatterTarget", Vec3.CODEC).orElse(Vec3.ZERO);
        scatterDistance = input.getDoubleOr("ScatterDistance", 0);
        entityData.set(DATA_STAGE, Math.min(growthTicks / GROWTH_TICKS, GROWN));
        refreshDimensions();
    }

    private final class ScatterGoal extends Goal
    {
        private int pathDelay;
        private Vec3 lastCheckedAt = Vec3.ZERO;

        private ScatterGoal()
        {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            return isScattering();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void start()
        {
            pathDelay = 0;
            CorruptedBarnacle.super.setTarget(null);
            if (isClinging())
                letGo();
        }

        @Override
        public void tick()
        {
            if (position().distanceTo(scatterFrom) >= scatterDistance && (!((ServerLevel) level()).getGameRules().get(GameRules.MOB_GRIEFING) || tryNesting()))
                return;

            if (--pathDelay > 0)
                return;
            pathDelay = PATH_INTERVAL;

            // Nothing leads further that way: settle here if it is far enough, or else try another way
            double from = position().distanceTo(scatterFrom);
            if (position().distanceTo(lastCheckedAt) < STUCK_DISTANCE)
            {
                if (from >= SCATTER_MIN)
                    scatterDistance = from;
                else
                    chooseScatterTarget(random.nextDouble() * Math.PI * 2);
            }
            lastCheckedAt = position();
            getNavigation().moveTo(scatterTarget.x, scatterTarget.y, scatterTarget.z, SCATTER_RUN_SPEED);
        }

        private boolean tryNesting()
        {
            var level = (ServerLevel) level();
            var origin = blockPosition();
            if (BarnacleNestBlock.countNestsAround(level, origin, NESTS_NEARBY_RANGE) >= NESTS_NEARBY_LIMIT)
            {
                scatterTicks = 0;
                return true;
            }

            for (var pos : BlockPos.withinManhattan(origin, NEST_SEARCH_RADIUS))
            {
                if (!level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty())
                    continue;

                for (var facing : Direction.values())
                {
                    if (BarnacleNestBlock.canAttach(level, pos, facing))
                    {
                        level.setBlockAndUpdate(pos, VoidHeartBlocks.BARNACLE_NEST.defaultBlockState().setValue(BarnacleNestBlock.FACING, facing));
                        discard();
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
