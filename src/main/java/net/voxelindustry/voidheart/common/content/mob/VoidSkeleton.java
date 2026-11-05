package net.voxelindustry.voidheart.common.content.mob;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

public class VoidSkeleton extends VoidMonster
{
    private static final int EXPERIENCE = 6;

    public VoidSkeleton(EntityType<? extends VoidSkeleton> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(26, 6, 0.26);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.VOID_SKELETON;
    }
}
