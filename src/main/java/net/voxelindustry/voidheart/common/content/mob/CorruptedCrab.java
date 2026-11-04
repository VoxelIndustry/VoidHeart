package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

/**
 * Each size has its own entity type, so that it keeps its own hitbox, loot, spawn egg and attributes.
 */
public class CorruptedCrab extends CrawlerMonster
{
    public enum Size
    {
        SMALL(0.5F, 0.35F, 0.5F, 3, 8, 3, 0.40, 15),
        MEDIUM(1, 0.65F, 1, 6, 22, 5, 0.31, 20),
        LARGE(1.9F, 1.25F, 1.9F, 14, 50, 9, 0.24, 30);

        public final float width;
        public final float height;
        public final float modelScale;
        public final int experience;
        public final double health;
        public final double damage;
        public final double speed;
        public final int attackCooldown;

        Size(float width, float height, float modelScale, int experience, double health, double damage, double speed, int attackCooldown)
        {
            this.width = width;
            this.height = height;
            this.modelScale = modelScale;
            this.experience = experience;
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.attackCooldown = attackCooldown;
        }
    }

    private static final int RELEASED_ON_DEATH = 3;

    private final Size size;

    public CorruptedCrab(EntityType<? extends CorruptedCrab> type, Level level, Size size)
    {
        super(type, level);
        this.size = size;
        xpReward = size.experience;
    }

    public static AttributeSupplier.Builder createAttributes(Size size)
    {
        return attributes(size.health, size.damage, size.speed);
    }

    public Size size()
    {
        return size;
    }

    @Override
    public int attackCooldown()
    {
        return size.attackCooldown;
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(2, new CrawlerChaseGoal(this));
        goalSelector.addGoal(5, new CrawlerIdleGoal(this));
        goalSelector.addGoal(5, new StrollGoal(0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.CRAB;
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (size == Size.LARGE && level() instanceof ServerLevel level)
            releaseYoung(level);
    }

    private void releaseYoung(ServerLevel level)
    {
        for (int i = 0; i < RELEASED_ON_DEATH; i++)
        {
            var crab = VoidHeartEntities.CORRUPTED_CRAB_SMALL.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (crab == null)
                continue;

            double angle = i * Math.PI * 2 / RELEASED_ON_DEATH;
            crab.snapTo(getX() + Math.cos(angle) * 0.5, getY(), getZ() + Math.sin(angle) * 0.5, getYRot(), 0);
            crab.setTarget(getTarget());
            level.addFreshEntity(crab);
        }
    }
}
