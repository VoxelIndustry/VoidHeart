package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import org.jspecify.annotations.Nullable;

/**
 * A bolt of darkness from the crossbow blight: an arrow in all but what it leaves behind, since a mob it wounds is stitched to the last its wielder
 * struck, see {@link EclipseLinks}. It gives back the arrow it was made from.
 */
public class EclipseBolt extends AbstractArrow
{
    public EclipseBolt(EntityType<? extends EclipseBolt> type, Level level)
    {
        super(type, level);
    }

    public EclipseBolt(Level level, LivingEntity owner, ItemStack ammo, @Nullable ItemStack weapon)
    {
        super(VoidHeartEntities.ECLIPSE_BOLT, owner, level, ammo, weapon);
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target)
    {
        super.doPostHurtEffects(target);
        if (getOwner() instanceof ServerPlayer wielder)
            EclipseLinks.struck(wielder, target);
    }

    @Override
    protected ItemStack getDefaultPickupItem()
    {
        return new ItemStack(Items.ARROW);
    }
}
