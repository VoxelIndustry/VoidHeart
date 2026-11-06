package net.voxelindustry.voidheart.common.content.oil;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Oils coat melee weapons and arrows. A coated hit counts as the oil's kind of damage and carries the oil's effect. Each hit landed on a living thing
 * uses a charge of a weapon's coat, and an arrow's coat lasts one hit.
 */
public final class OilCoating
{
    /**
     * How long a searing hit sets its target alight.
     */
    public static final float SEARING_FIRE_SECONDS = 4;
    public static final float STRONG_FIRE_SECONDS  = 8;
    /**
     * Extra knockback of a concussive hit, on top of the hit's own. A Knockback I sword adds 0.5.
     */
    public static final float CONCUSSIVE_KNOCKBACK = 0.8F;
    public static final float STRONG_KNOCKBACK     = 1.4F;
    /**
     * Blessings an arcane hit strips, plain and from a stronger brew.
     */
    public static final int   ARCANE_STRIPS        = 1;
    public static final int   STRONG_STRIPS        = 2;
    /**
     * Durability a seething steel coat costs its weapon on each hit, on top of the hit's own.
     */
    public static final int   SEETHING_WEAR        = 1;
    /**
     * Arrows one flask coats.
     */
    public static final int   ARROWS_PER_FLASK     = 16;

    private OilCoating()
    {
    }

    public static boolean canCoat(ItemStack stack)
    {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT);
    }

    public static boolean isArrow(ItemStack stack)
    {
        return stack.is(ItemTags.ARROWS);
    }

    /**
     * Replaces whatever coated the weapon with a fresh coat.
     */
    public static void coat(ItemStack weapon, OilType type)
    {
        coat(weapon, type, Brew.PLAIN);
    }

    public static void coat(ItemStack weapon, OilType type, Brew brew)
    {
        weapon.set(VoidHeartDataComponents.WEAPON_OIL, WeaponOil.fresh(type, brew));
    }

    /**
     * Coats each arrow of the stack for a single hit, replacing any other oil.
     */
    public static void coatArrows(ItemStack arrows, OilType type)
    {
        coatArrows(arrows, type, Brew.PLAIN);
    }

    /**
     * An arrow's coat is always one hit, so only a stronger brew changes it.
     */
    public static void coatArrows(ItemStack arrows, OilType type, Brew brew)
    {
        arrows.set(VoidHeartDataComponents.WEAPON_OIL, new WeaponOil(type, 1, 1, brew.empowered(), false));
    }

    @Nullable
    public static WeaponOil coatingOf(ItemStack stack)
    {
        return stack.get(VoidHeartDataComponents.WEAPON_OIL);
    }

    /**
     * The coated item a hit was dealt with: the weapon in hand for a hit struck by hand, or the arrow or thrown trident itself. Null for anything else.
     */
    @Nullable
    public static ItemStack coatedStackOf(DamageSource source)
    {
        if (!(source.getEntity() instanceof LivingEntity attacker))
            return null;

        ItemStack stack;
        if (source.getDirectEntity() instanceof AbstractArrow projectile)
            stack = projectile.getPickupItemStackOrigin();
        else if (source.getDirectEntity() == attacker)
            stack = attacker.getMainHandItem();
        else
            return null;
        return coatingOf(stack) == null ? null : stack;
    }

    /**
     * @return the kind the hit counts as because of its weapon's coating, or null when it has none
     */
    @Nullable
    public static DragoonAdaptation.Kind kindOf(DamageSource source)
    {
        var stack = coatedStackOf(source);
        return stack == null ? null : coatingOf(stack).type().kind();
    }

    /**
     * A coated hit landed: the target takes the oil's effect and the coating loses a charge.
     */
    public static void onHitLanded(LivingEntity target, DamageSource source)
    {
        var stack = coatedStackOf(source);
        if (stack == null)
            return;

        var coating = coatingOf(stack);
        // The Dragoon answers oils only by what kind they count as. Burn ticks would also teach it fire again, using up what the coating is for
        if (!(target instanceof KernacianDragoon))
            applyEffect(coating, target, source);
        if (coating.wearing() && source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker)
            stack.hurtAndBreak(SEETHING_WEAR, attacker, EquipmentSlot.MAINHAND);

        var spent = coating.spend();
        if (spent == null)
            stack.remove(VoidHeartDataComponents.WEAPON_OIL);
        else
            stack.set(VoidHeartDataComponents.WEAPON_OIL, spent);
    }

    private static void applyEffect(WeaponOil coating, LivingEntity target, DamageSource source)
    {
        boolean strong = coating.strong();
        switch (coating.type())
        {
            case SEARING -> target.igniteForSeconds(strong ? STRONG_FIRE_SECONDS : SEARING_FIRE_SECONDS);
            case CONCUSSIVE -> concuss(target, source, strong ? STRONG_KNOCKBACK : CONCUSSIVE_KNOCKBACK);
            case ARCANE -> dispel(target, strong ? STRONG_STRIPS : ARCANE_STRIPS);
        }
    }

    /**
     * Knocks the target further away from the blow, and breaks off a Bishop's lance before it is cast.
     */
    private static void concuss(LivingEntity target, DamageSource source, float strength)
    {
        double awayX;
        double awayZ;
        if (source.getDirectEntity() instanceof AbstractArrow projectile)
        {
            var motion = projectile.getDeltaMovement();
            awayX = -motion.x;
            awayZ = -motion.z;
        }
        else
        {
            awayX = source.getEntity().getX() - target.getX();
            awayZ = source.getEntity().getZ() - target.getZ();
        }
        target.knockback(strength, awayX, awayZ, source, 0);
        if (target instanceof FallenBishop bishop)
            bishop.breakLance();
    }

    /**
     * Strips some of the target's beneficial effects, picked at random.
     */
    private static void dispel(LivingEntity target, int count)
    {
        var beneficial = new ArrayList<>(target.getActiveEffects().stream().filter(effect -> effect.getEffect().value().isBeneficial()).toList());
        if (beneficial.isEmpty())
            return;

        for (int i = 0; i < count && !beneficial.isEmpty(); i++)
            target.removeEffect(beneficial.remove(target.getRandom().nextInt(beneficial.size())).getEffect());
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.NEUTRAL, 0.6F, 1.6F);
    }
}
