package net.voxelindustry.voidheart.common.content.blight;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;
import java.util.function.Predicate;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The fourth void blight: a crossbow that loads faster and turns the arrows it fires into bolts of darkness, which stitch what they strike together.
 * See {@link EclipseBolt} and {@link EclipseLinks}. It takes arrows only. Its quicker load is set where the crossbow's is worked out, see
 * {@code MixinCrossbowItemCharge}, and the hand poses vanilla keeps for its own crossbow are given to it by the client mixins on them.
 */
public class BlightCrossbowItem extends CrossbowItem
{
    /** A crossbow's power for arrows, which its bolt damage is shared over. */
    private static final float ARROW_POWER = 3.15F;

    public BlightCrossbowItem(Properties properties)
    {
        super(properties.stacksTo(1)
                .durability(930)
                .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                .enchantable(10)
                .rarity(Rarity.EPIC)
                .fireResistant());
    }

    /** A crossbow's load time scaled to this one's, so quick charge still shortens it alike. */
    public static int chargeDuration(int crossbowTicks)
    {
        return Math.max(1, Math.round(crossbowTicks * BlightBalance.CROSSBOW_CHARGE_TICKS / 25F));
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles()
    {
        return ARROW_ONLY;
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles()
    {
        return ARROW_ONLY;
    }

    @Override
    protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit)
    {
        var bolt = new EclipseBolt(level, shooter, ammo.copyWithCount(1), weapon);
        bolt.setBaseDamage(BlightBalance.BOLT_DAMAGE / ARROW_POWER);
        bolt.setSoundEvent(SoundEvents.CROSSBOW_HIT);
        if (isCrit)
            bolt.setCritArrow(true);
        return bolt;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".void_blight_crossbow.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".void_blight_crossbow.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
