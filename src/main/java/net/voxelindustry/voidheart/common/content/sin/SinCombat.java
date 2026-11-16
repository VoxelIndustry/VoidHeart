package net.voxelindustry.voidheart.common.content.sin;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The game side of the sin temperaments: what a sin weapon adds to a hit, what the hit costs, and what the wielder learns from it.
 * <p>
 * The wielder decides: a weapon fights at the proficiency of whoever holds it.
 */
public final class SinCombat
{
    public static final Identifier SLOTH_DRAG = Identifier.fromNamespaceAndPath(MODID, "sloth_drag");

    private static final Map<UUID, Wielder> WIELDERS = new HashMap<>();

    private SinCombat()
    {
    }

    /**
     * What a player carries between hits. Lost on leaving, which only costs a few seconds of stacks or charge. Greed's debts are saved with the player instead,
     * see {@link GreedDebts}.
     */
    private static final class Wielder
    {
        long lastHurtTick;
        int  slothCharge;
        Vec3 lastPosition;
        long greedReadyTick;
    }

    private static Wielder wielder(ServerPlayer player)
    {
        return WIELDERS.computeIfAbsent(player.getUUID(), uuid ->
        {
            var wielder = new Wielder();
            wielder.lastHurtTick = now(player);
            return wielder;
        });
    }

    private static long now(ServerPlayer player)
    {
        return player.level().getServer().getTickCount();
    }

    /**
     * @return the player striking with a sin weapon in this hit, or null
     */
    private static ServerPlayer striker(DamageSource source)
    {
        if (source.getEntity() instanceof ServerPlayer player && !player.isSpectator())
            return player;
        return null;
    }

    private static Sin sinOfHit(DamageSource source)
    {
        return SinProficiencies.combatSinOf(source.getWeaponItem());
    }

    /**
     * The damage of a hit, raised by the striker's sin. The extra keeps the hit's kind of damage.
     */
    public static float modifyOutgoing(LivingEntity target, DamageSource source, float amount)
    {
        var player = striker(source);
        if (player == null || amount <= 0 || target == player)
            return amount;

        var sin = sinOfHit(source);
        if (sin == null)
            return amount;

        var devotion = SinProficiencies.devotion(player, sin);
        return amount * switch (sin)
        {
            case PRIDE -> SinTemperament.prideMultiplier(prideStacks(player, devotion), devotion);
            case WRATH -> SinTemperament.wrathMultiplier(player.getHealth(), player.getMaxHealth(), devotion);
            case SLOTH -> SinTemperament.slothCharged(wielder(player).slothCharge, devotion) ? SinTemperament.slothMultiplier(devotion) : 1;
            case GLUTTONY, GREED -> 1;
        };
    }

    private static int prideStacks(ServerPlayer player, float devotion)
    {
        return SinTemperament.prideStacks(now(player) - wielder(player).lastHurtTick, devotion);
    }

    /**
     * A sin weapon's hit landed on its target, which lost the given health.
     */
    public static void onHitLanded(LivingEntity target, DamageSource source, float dealt)
    {
        var player = striker(source);
        if (player == null || target == player)
            return;

        var sin = sinOfHit(source);
        if (sin == null)
            return;

        var devotion = SinProficiencies.devotion(player, sin);
        var weight = teachingWeight(target, player);

        switch (sin)
        {
            case PRIDE ->
            {
                if (prideStacks(player, devotion) >= SinBalance.PRIDE_TEACHING_STACKS)
                    teach(player, sin, SinBalance.PRIDE_POINTS * weight);
            }
            case WRATH ->
            {
                if (SinTemperament.wrathTeaches(player.getHealth(), player.getMaxHealth()))
                    teach(player, sin, SinBalance.WRATH_POINTS * weight);
            }
            case GLUTTONY -> feed(player, dealt, devotion, weight);
            case SLOTH ->
            {
                var wielder = wielder(player);
                if (SinTemperament.slothCharged(wielder.slothCharge, devotion))
                {
                    wielder.slothCharge = 0;
                    SinStagger.stagger(target, SinTemperament.slothStaggerTicks(devotion));
                    teach(player, sin, SinBalance.SLOTH_POINTS * weight);
                }
            }
            case GREED -> steal(player, target, devotion, weight);
        }
    }

    private static void teach(ServerPlayer player, Sin sin, float points)
    {
        if (points > 0)
            SinProficiencies.gain(player, sin, points);
    }

    /**
     * Only hostile mobs fighting the player teach, so passive or trapped ones teach nothing.
     */
    private static float teachingWeight(LivingEntity target, ServerPlayer player)
    {
        if (!(target instanceof Enemy) || !(target instanceof Mob mob) || mob.getTarget() != player)
            return 0;
        return SinTemperament.teachingWeight(target.getMaxHealth(), placeWeight(target.level()));
    }

    private static float placeWeight(Level level)
    {
        if (level.dimension().equals(VoidDaedalus.WORLD_KEY))
            return SinBalance.DAEDALUS_WEIGHT;
        if (level.dimension().equals(VoidDungeons.WORLD_KEY))
            return SinBalance.DUNGEON_WEIGHT;
        return 1;
    }

    private static void feed(ServerPlayer player, float dealt, float devotion, float weight)
    {
        var heal = SinTemperament.gluttonyHeal(dealt, devotion);
        if (heal > 0 && player.getHealth() < player.getMaxHealth())
        {
            var before = player.getHealth();
            player.heal(heal);
            teach(player, Sin.GLUTTONY, (player.getHealth() - before) * SinBalance.GLUTTONY_POINTS_PER_HEALTH * weight);
        }

        if (player.getAbilities().instabuild)
            return;

        var food = player.getFoodData();
        var meal = SinTemperament.gluttonyEat(food.getSaturationLevel(), food.getFoodLevel(), SinTemperament.gluttonyCost(devotion));
        food.setSaturation(meal.saturation());
        food.setFoodLevel(meal.food());
        if (meal.healthCost() > 0)
            player.hurtServer(player.level(), player.damageSources().starve(), meal.healthCost());
    }

    private static void steal(ServerPlayer player, LivingEntity target, float devotion, float weight)
    {
        var wielder = wielder(player);
        var now = now(player);
        if (now < wielder.greedReadyTick)
            return;

        MobEffectInstance taken = null;
        for (var effect : target.getActiveEffects())
        {
            if (effect.getEffect().value().isBeneficial() && (taken == null || effect.getAmplifier() > taken.getAmplifier()))
                taken = effect;
        }

        if (taken != null)
        {
            var effect = taken.getEffect();
            var ticks = SinTemperament.greedStolenTicks(taken.getDuration());
            var level = SinTemperament.greedStolenLevel(taken.getAmplifier(), devotion);
            target.removeEffect(effect);
            player.addEffect(new MobEffectInstance(effect, ticks, level));
            GreedDebts.owe(player, new GreedDebts.Debt(effect, GreedDebts.opposite(effect), SinTemperament.greedDebtTicks(ticks, devotion), SinTemperament.greedDebtLevel(level, devotion)));
        }
        else if (target.getAbsorptionAmount() > 0)
        {
            var amount = Math.min(target.getAbsorptionAmount(), SinTemperament.greedAbsorption(devotion));
            target.setAbsorptionAmount(target.getAbsorptionAmount() - amount);
            var level = Math.max(0, (int) Math.ceil(amount / 4) - 1);
            var ticks = SinBalance.GREED_ABSORPTION_TICKS;
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, level));
            GreedDebts.owe(player, new GreedDebts.Debt(MobEffects.ABSORPTION, GreedDebts.opposite(MobEffects.ABSORPTION), SinTemperament.greedDebtTicks(ticks, devotion), SinTemperament.greedDebtLevel(level, devotion)));
        }
        else
            return;

        wielder.greedReadyTick = now + SinTemperament.greedCooldown(devotion);
        teach(player, Sin.GREED, SinBalance.GREED_POINTS * weight);
    }

    /**
     * A player was hurt: pride's stacks shatter, and a pride blade in hand punishes the fall.
     */
    public static void onPlayerHurt(ServerPlayer player)
    {
        var wielder = wielder(player);
        var sin = SinProficiencies.combatSinOf(player.getMainHandItem());
        if (sin == Sin.PRIDE)
        {
            var devotion = SinProficiencies.devotion(player, sin);
            if (prideStacks(player, devotion) > 0)
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SinTemperament.prideWeaknessTicks(devotion), SinTemperament.prideWeaknessLevel(devotion)));
        }
        wielder.lastHurtTick = now(player);
    }

    /**
     * Healing received while a burning wrath blade is held is cut.
     */
    public static float modifyHealing(LivingEntity entity, float amount)
    {
        if (!(entity instanceof ServerPlayer player) || amount <= 0)
            return amount;
        if (SinProficiencies.combatSinOf(player.getMainHandItem()) != Sin.WRATH)
            return amount;
        return amount * SinTemperament.wrathHealingKept(player.getHealth(), player.getMaxHealth(), SinProficiencies.devotion(player, Sin.WRATH));
    }

    public static void tickServer(MinecraftServer server)
    {
        for (var player : server.getPlayerList().getPlayers())
        {
            var wielder = wielder(player);
            tickSloth(player, wielder);
            GreedDebts.tick(player);
        }
    }

    private static void tickSloth(ServerPlayer player, Wielder wielder)
    {
        var position = player.position();
        var moved = wielder.lastPosition == null ? 0 : position.subtract(wielder.lastPosition).horizontalDistance();
        wielder.lastPosition = position;

        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (SinProficiencies.combatSinOf(player.getMainHandItem()) != Sin.SLOTH || !player.isAlive())
        {
            wielder.slothCharge = 0;
            if (speed != null)
                speed.removeModifier(SLOTH_DRAG);
            return;
        }

        var devotion = SinProficiencies.devotion(player, Sin.SLOTH);
        var still = player.onGround() && moved < SinBalance.SLOTH_STILL_DISTANCE;
        wielder.slothCharge = SinTemperament.slothCharge(wielder.slothCharge, still, devotion);

        if (speed != null)
            speed.addOrUpdateTransientModifier(new AttributeModifier(SLOTH_DRAG, -SinTemperament.slothSlow(devotion), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * Whether a sloth wielder's next blow is charged, for its look and for tests.
     */
    public static boolean isCharged(ServerPlayer player)
    {
        return SinTemperament.slothCharged(wielder(player).slothCharge, SinProficiencies.devotion(player, Sin.SLOTH));
    }

    public static void onLeave(ServerPlayer player)
    {
        WIELDERS.remove(player.getUUID());
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
            speed.removeModifier(SLOTH_DRAG);
    }

    public static void clear()
    {
        WIELDERS.clear();
    }
}
