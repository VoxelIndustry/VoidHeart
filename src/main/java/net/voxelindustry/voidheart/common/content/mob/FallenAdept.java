package net.voxelindustry.voidheart.common.content.mob;

import net.voxelindustry.voidheart.common.content.salt.SaltWard;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds.MobSounds;

public class FallenAdept extends VoidMonster
{
    private static final int EXPERIENCE = 14;

    public FallenAdept(EntityType<? extends FallenAdept> type, Level level)
    {
        super(type, level);
        xpReward = EXPERIENCE;
        setPathfindingMalus(PathType.DAMAGE_CAUTIOUS, SaltWard.FALLEN_MALUS);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return attributes(44, 9, 0.23)
                .add(Attributes.ARMOR, 12)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Override
    protected void registerGoals()
    {
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        addIdleGoals(6);
        addPlayerTarget(true);
    }

    @Override
    protected MobSounds sounds()
    {
        return VoidHeartSounds.FALLEN_ADEPT;
    }
}
