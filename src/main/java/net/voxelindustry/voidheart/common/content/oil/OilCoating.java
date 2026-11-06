package net.voxelindustry.voidheart.common.content.oil;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jetbrains.annotations.Nullable;

/**
 * Oils coat melee weapons. A coated hit counts as the oil's kind of damage, and each hit landed on a living thing uses a charge.
 */
public final class OilCoating
{
    /**
     * How long a searing hit sets its target alight.
     */
    public static final float SEARING_FIRE_SECONDS = 4;

    private OilCoating()
    {
    }

    public static boolean canCoat(ItemStack stack)
    {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }

    /**
     * Replaces whatever coated the weapon with a fresh coat.
     */
    public static void coat(ItemStack weapon, OilType type)
    {
        weapon.set(VoidHeartDataComponents.WEAPON_OIL, WeaponOil.fresh(type));
    }

    @Nullable
    public static WeaponOil coatingOf(ItemStack stack)
    {
        return stack.get(VoidHeartDataComponents.WEAPON_OIL);
    }

    /**
     * The coated weapon of a hit struck by hand, or null for any other hit. A thrown trident or an arrow is not the weapon in hand.
     */
    @Nullable
    public static ItemStack coatedWeaponOf(DamageSource source)
    {
        if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker)
            return null;

        var weapon = attacker.getMainHandItem();
        return coatingOf(weapon) == null ? null : weapon;
    }

    /**
     * @return the kind the hit counts as because of its weapon's coating, or null when it has none
     */
    @Nullable
    public static DragoonAdaptation.Kind kindOf(DamageSource source)
    {
        var weapon = coatedWeaponOf(source);
        return weapon == null ? null : coatingOf(weapon).type().kind();
    }

    /**
     * A coated hit landed: the target takes the oil's effect and the coating loses a charge.
     */
    public static void onHitLanded(LivingEntity target, DamageSource source)
    {
        var weapon = coatedWeaponOf(source);
        if (weapon == null)
            return;

        var coating = coatingOf(weapon);
        // Each burn tick would teach a Dragoon fire again, using up what the coating is for
        if (coating.type() == OilType.SEARING && !(target instanceof KernacianDragoon))
            target.igniteForSeconds(SEARING_FIRE_SECONDS);

        var spent = coating.spend();
        if (spent == null)
            weapon.remove(VoidHeartDataComponents.WEAPON_OIL);
        else
            weapon.set(VoidHeartDataComponents.WEAPON_OIL, spent);
    }
}
