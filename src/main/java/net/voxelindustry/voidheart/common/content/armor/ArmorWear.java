package net.voxelindustry.voidheart.common.content.armor;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldStats;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Putting the Void Armor on, wearing it and taking it off. A worn frame lives on its wearer, who keeps moving and fighting as themselves: the frame only
 * changes their size, speed, reach, jump and hit through attribute modifiers, put back every tick so they survive a relog. Holding sneak steps out, onto an
 * empty base if the wearer stands on one, otherwise leaving the frame parked where they stood. On a real death the frame goes back to the last base it
 * docked on.
 * <p>
 * Worn, the frame spends its charge every tick and on each step, powered jump, powered hit and fall it takes, sheds its modules as it runs low
 * ({@link LoadShedding}) and goes limp when empty. What it spends is counted here and written back to the frame now and then, so clients aren't sent the
 * whole frame every tick.
 * <p>
 * A Shielding Apparatus in the frame's socket gives its wearer that apparatus's shield, which the frame refills faster than the apparatus would and pays
 * for by the point.
 */
public final class ArmorWear
{
    public static final AttachmentType<WornArmor> WORN = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "void_armor"),
            builder -> builder.persistent(WornArmor.CODEC).copyOnDeath().syncWith(WornArmor.STREAM_CODEC, AttachmentSyncPredicate.all()));

    private static final Identifier SCALE_ID        = id("scale");
    private static final Identifier SPEED_ID        = id("speed");
    private static final Identifier JUMP_ID         = id("jump");
    private static final Identifier ENTITY_REACH_ID = id("entity_reach");
    private static final Identifier BLOCK_REACH_ID  = id("block_reach");
    private static final Identifier HIT_ID          = id("hit");
    private static final Identifier KNOCKBACK_ID    = id("knockback");
    private static final Identifier STEP_ID         = id("step");
    private static final Identifier FALL_ID         = id("fall");

    private static final List<Identifier> MODIFIERS = List.of(SCALE_ID, SPEED_ID, JUMP_ID, ENTITY_REACH_ID, BLOCK_REACH_ID, HIT_ID, KNOCKBACK_ID, STEP_ID,
            FALL_ID);

    private static final List<Holder<Attribute>> TOUCHED = List.of(Attributes.SCALE, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH,
            Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ATTACK_DAMAGE, Attributes.ATTACK_KNOCKBACK,
            Attributes.STEP_HEIGHT, Attributes.SAFE_FALL_DISTANCE);

    /** A step longer than this in one move is a correction or a teleport, not walking. */
    private static final double MOST_STEP = 2;

    /**
     * What the server keeps about a wearer between ticks and doesn't save.
     */
    private static final class Wearer
    {
        /** How long sneak has been held, in ticks. */
        int    sneak;
        /** Charge spent since it was last written back to the frame. */
        long   drawn;
        /** Walking and shield drain not yet a whole unit. */
        double owed;
        /** Blocks run since the last puff of steam. */
        double stride;
        long   writtenAt;
        LoadShedding.@Nullable Stage stage;
    }

    private static final Map<UUID, Wearer> WEARERS = new HashMap<>();

    private ArmorWear()
    {
    }

    private static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MODID, "void_armor_" + path);
    }

    public static void register()
    {
        ServerTickEvents.END_SERVER_TICK.register(server ->
        {
            for (var player : server.getPlayerList().getPlayers())
                tick(player);
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(ArmorWear::onDeath);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(ArmorWear::onHurt);
        ArmorPayloads.register();
        ServerPlayerEvents.LEAVE.register(player ->
        {
            writeBack(player);
            WEARERS.remove(player.getUUID());
        });
        EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) ->
                wearing(player) ? new Player.BedSleepingProblem(Component.translatable(MODID + ".void_armor.no_bed")) : null);

        UseItemCallback.EVENT.register((player, level, hand) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
        {
            if (moving(player))
                return InteractionResult.FAIL;
            if (player instanceof ServerPlayer serverPlayer && load(player).exoskeleton())
                draw(serverPlayer, ArmorBalance.POWERED_HIT_DRAIN);
            return InteractionResult.PASS;
        });
    }

    /**
     * The frame the player has on, stepping in and out included, or null.
     */
    public static @Nullable WornArmor worn(Player player)
    {
        var worn = player.getAttached(WORN);
        return worn != null && worn.isOn() ? worn : null;
    }

    public static boolean wearing(Player player)
    {
        return worn(player) != null;
    }

    /**
     * Whether the player is stepping in or out, so can't act.
     */
    public static boolean moving(Player player)
    {
        var worn = worn(player);
        return worn != null && worn.isMoving();
    }

    // Charge

    /**
     * What the worn frame has left. Clients hear of it now and then, and straight away when the frame cuts a module or goes limp.
     */
    public static long charge(Player player)
    {
        var worn = worn(player);
        if (worn == null)
            return 0;
        var wearer = player instanceof ServerPlayer ? WEARERS.get(player.getUUID()) : null;
        return Math.max(0, worn.charge() - (wearer == null ? 0 : wearer.drawn));
    }

    public static LoadShedding.Stage stage(Player player)
    {
        var worn = worn(player);
        if (worn == null)
            return LoadShedding.Stage.FULL;
        return LoadShedding.stage(charge(player), VoidArmorItem.stats(worn.frame()).capacity());
    }

    /**
     * What the worn frame runs right now, after its wearer's switches and what it has cut.
     */
    public static ArmorLoad load(Player player)
    {
        var worn = worn(player);
        if (worn == null)
            return ArmorLoad.NONE;
        var stats = VoidArmorItem.stats(worn.frame());
        return ArmorLoad.of(stats, worn.on(), LoadShedding.stage(charge(player), stats.capacity()));
    }

    public static boolean limp(Player player)
    {
        return wearing(player) && load(player).limp();
    }

    /**
     * Takes up to the amount from the worn frame.
     *
     * @return what it had to give
     */
    public static long draw(ServerPlayer player, long amount)
    {
        var worn = worn(player);
        if (worn == null || amount <= 0)
            return 0;
        var wearer = wearer(player);
        long taken = Math.min(amount, Math.max(0, worn.charge() - wearer.drawn));
        wearer.drawn += taken;
        return taken;
    }

    public static void setCharge(ServerPlayer player, long charge)
    {
        var worn = worn(player);
        if (worn == null)
            return;
        var wearer = wearer(player);
        wearer.drawn = 0;
        wearer.owed = 0;
        long capacity = VoidArmorItem.stats(worn.frame()).capacity();
        long clamped = Math.clamp(charge, 0, capacity);
        player.setAttached(WORN, worn.withCharge(clamped));
        wearer.stage = LoadShedding.stage(clamped, capacity);
        wearer.writtenAt = player.level().getGameTime();
    }

    /**
     * Switches one of the frame's sights on or off. Cut sights stay dark whatever their switch says.
     *
     * @return false when the frame has no such sight working
     */
    public static boolean toggle(ServerPlayer player, ArmorModule module)
    {
        var worn = worn(player);
        if (worn == null || worn.isMoving())
            return false;
        var stats = VoidArmorItem.stats(worn.frame());
        boolean fitted = switch (module)
        {
            case NIGHT_VISION -> stats.nightVision();
            case SPECTRAL_LENS -> stats.spectralLens();
            default -> false;
        };
        if (!fitted)
            return false;
        writeBack(player);
        var toggled = worn(player).toggled(module);
        player.setAttached(WORN, toggled);
        var sound = toggled.on().contains(module) ? SoundEvents.SPYGLASS_USE : SoundEvents.SPYGLASS_STOP_USING;
        float pitch = module == ArmorModule.SPECTRAL_LENS ? 0.7F : 1.1F;
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundSource.PLAYERS, 0.8F, pitch);
        return true;
    }

    // The apparatus socket

    /**
     * The Shielding Apparatus in the worn frame's socket, or an empty stack.
     */
    public static ItemStack socketed(Player player)
    {
        var worn = worn(player);
        return worn == null ? ItemStack.EMPTY : VoidArmorItem.socketed(worn.frame());
    }

    /**
     * The socketed apparatus's stats as the frame runs it: refilling faster while the frame can spare the charge, not at all once it can't.
     */
    public static ShieldStats shieldStats(Player player, ShieldStats stats)
    {
        return stats.withRegen(ArmorDrain.shieldRegen(stats.regenPerTick(), stage(player)));
    }

    public static void payForShield(ServerPlayer player, float refilled)
    {
        if (refilled > 0 && wearing(player))
            wearer(player).owed += ArmorDrain.shieldCost(refilled);
    }

    /**
     * Stepping in or out changes which apparatus holds the shield, and a new one starts empty.
     */
    private static void restartShield(ServerPlayer player)
    {
        if (player.hasAttached(ShieldingApparatus.SHIELD))
            player.removeAttached(ShieldingApparatus.SHIELD);
    }

    private static Wearer wearer(ServerPlayer player)
    {
        return WEARERS.computeIfAbsent(player.getUUID(), uuid -> new Wearer());
    }

    /**
     * Writes what the frame spent back onto it, which also tells clients.
     */
    private static void writeBack(ServerPlayer player)
    {
        var wearer = WEARERS.get(player.getUUID());
        var worn = worn(player);
        if (wearer == null || worn == null)
            return;
        if (wearer.drawn != 0)
            player.setAttached(WORN, worn.withCharge(worn.charge() - wearer.drawn));
        wearer.drawn = 0;
        wearer.writtenAt = player.level().getGameTime();
    }

    // Stepping in

    /**
     * The box a wearer fills standing at a spot, at the frame's size.
     */
    public static AABB wearerBox(Vec3 feet)
    {
        double half = ArmorBalance.WEARER_WIDTH / 2 - 1.0E-3;
        return new AABB(feet.x - half, feet.y + 1.0E-3, feet.z - half, feet.x + half, feet.y + ArmorBalance.WEARER_HEIGHT, feet.z + half);
    }

    /**
     * Why the player can't step into the frame standing at a spot, or null when they can.
     */
    public static @Nullable Component refusal(ServerPlayer player, ItemStack frame, Vec3 feet)
    {
        if (player.getAttached(WORN) != null)
            return Component.translatable(MODID + ".void_armor.already_worn");
        var owner = frame.get(VoidArmorContent.ARMOR_OWNER);
        if (owner != null && !owner.equals(player.getUUID()))
            return Component.translatable(MODID + ".void_armor.fitted_to_another");
        if (!player.level().noCollision(player, wearerBox(feet)))
            return Component.translatable(MODID + ".void_armor.no_room");
        return null;
    }

    /**
     * Closes the frame around the player, standing them where it stood. The caller has checked {@link #refusal} and taken the frame from where it was.
     */
    public static void stepIn(ServerPlayer player, ItemStack frame, Vec3 feet)
    {
        var fitted = frame.copyWithCount(1);
        if (!fitted.has(VoidArmorContent.ARMOR_OWNER))
            fitted.set(VoidArmorContent.ARMOR_OWNER, player.getUUID());
        player.stopRiding();
        fitted = ArmorHotbar.take(player, fitted);
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, Set.of(), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setAttached(WORN, new WornArmor(fitted, WornArmor.Phase.STEPPING_IN, player.level().getGameTime()));
        WEARERS.remove(player.getUUID());
        restartShield(player);
        applyModifiers(player, player.getAttached(WORN));
        player.level().playSound(null, feet.x, feet.y, feet.z, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1, 0.7F);
    }

    // Each tick

    private static void tick(ServerPlayer player)
    {
        var worn = player.getAttached(WORN);
        if (worn == null)
        {
            applyModifiers(player, null);
            // A tick after stepping out, so the client already knows the frame is off and lets the selection leave the racks
            ArmorHotbar.restoreSelection(player);
            return;
        }
        long time = player.level().getGameTime();
        long elapsed = time - worn.since();
        switch (worn.phase())
        {
            case HOMELESS ->
            {
                if (player.isAlive())
                {
                    player.removeAttached(WORN);
                    park(player.level(), besideRespawn(player), player.getYRot(), worn.frame());
                }
            }
            case STEPPING_IN ->
            {
                closingSounds(player, elapsed);
                if (elapsed >= ArmorBalance.STEP_IN_TICKS)
                    player.setAttached(WORN, worn.in(WornArmor.Phase.WORN, time));
            }
            case WORN ->
            {
                spend(player);
                var wearer = wearer(player);
                wearer.sneak = player.isShiftKeyDown() ? wearer.sneak + 1 : 0;
                if (wearer.sneak >= ArmorBalance.STEP_OUT_HOLD_TICKS)
                {
                    wearer.sneak = 0;
                    writeBack(player);
                    player.setAttached(WORN, worn(player).in(WornArmor.Phase.STEPPING_OUT, time));
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1, 0.6F);
                }
            }
            case STEPPING_OUT ->
            {
                if (elapsed >= ArmorBalance.STEP_OUT_TICKS)
                {
                    stepOut(player);
                    return;
                }
            }
        }
        applyModifiers(player, player.getAttached(WORN));
    }

    /** Ticks into stepping in when each chest row drops, the shoulder guards fall and the mask closes, as the frame's model closes. */
    private static final int[] ROW_CLACKS   = { 11, 14, 17, 20 };
    private static final int   GUARDS_FALL  = 25;
    private static final int   MASK_CLOSES  = 29;

    /**
     * A clack as each chest row drops, a heavier one as the shoulder guards fall, and the mask shutting with a burst of steam from the back.
     */
    private static void closingSounds(ServerPlayer player, long elapsed)
    {
        var level = player.level();
        for (int clack : ROW_CLACKS)
            if (elapsed == clack)
                level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.7F, 0.8F + clack * 0.02F);
        if (elapsed == GUARDS_FALL)
            level.playSound(null, player.getX(), player.getY() + 2, player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9F, 0.7F);
        if (elapsed == MASK_CLOSES)
        {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.8F, 0.6F);
            level.playSound(null, player.getX(), player.getY() + 1.5, player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.3F);
            var back = Vec3.directionFromRotation(0, player.yBodyRot).reverse().scale(0.5);
            level.sendParticles(ParticleTypes.CLOUD, player.getX() + back.x, player.getY() + 1.9, player.getZ() + back.z, 10, 0.15, 0.25, 0.15, 0.02);
        }
    }

    /**
     * Takes the tick's steady drain and its walking, then writes the charge back if the frame cut a module or it's been a while.
     */
    private static void spend(ServerPlayer player)
    {
        var wearer = wearer(player);
        var load = load(player);
        draw(player, ArmorDrain.perTick(load));
        if (load.limp())
            wearer.owed = 0;
        long owed = (long) wearer.owed;
        wearer.owed -= owed;
        draw(player, owed);

        var stage = stage(player);
        if (stage != wearer.stage)
        {
            if (wearer.stage != null && stage.compareTo(wearer.stage) > 0)
                shedSound(player, stage);
            wearer.stage = stage;
            writeBack(player);
        }
        else if (wearer.drawn != 0 && player.level().getGameTime() - wearer.writtenAt >= ArmorBalance.CHARGE_SYNC)
            writeBack(player);
    }

    private static void shedSound(ServerPlayer player, LoadShedding.Stage stage)
    {
        SoundEvent sound = stage.limp() ? SoundEvents.ARMOR_EQUIP_NETHERITE.value() : SoundEvents.BEACON_DEACTIVATE;
        float pitch = stage.limp() ? 0.4F : 0.6F + stage.ordinal() * 0.15F;
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, stage == LoadShedding.Stage.LOW ? 0.4F : 0.8F, pitch);
    }

    /**
     * Lets the wearer out at once: onto the empty base they stand on, or leaving the frame parked where they stood.
     */
    public static void stepOut(ServerPlayer player)
    {
        writeBack(player);
        releaseHotbar(player);
        var worn = worn(player);
        if (worn == null)
            return;
        player.removeAttached(WORN);
        WEARERS.remove(player.getUUID());
        restartShield(player);
        applyModifiers(player, null);

        var level = player.level();
        for (var pos : List.of(player.blockPosition(), player.blockPosition().below()))
            if (level.getBlockEntity(pos) instanceof VoidArmorBaseTile base && !base.hasFrame())
            {
                base.dock(worn.frame());
                return;
            }
        park(level, player.position(), player.getYRot(), worn.frame());
    }

    // The body in the frame

    /**
     * A wearer's step, from the server's movement check. Walking is paid by the block, so standing still costs only the frame's hum.
     */
    public static void onMoved(ServerPlayer player, double dx, double dz)
    {
        var worn = worn(player);
        if (worn == null || worn.phase() != WornArmor.Phase.WORN || player.isPassenger())
            return;
        double blocks = Math.sqrt(dx * dx + dz * dz);
        if (blocks > MOST_STEP)
            return;
        var wearer = wearer(player);
        wearer.owed += ArmorDrain.walking(blocks, player.isSprinting());
        // Heavy steps vent a puff of steam now and then, more often at a run
        if (player.isSprinting() && !limp(player))
        {
            wearer.stride += blocks;
            if (wearer.stride >= STEAM_STRIDE)
            {
                wearer.stride = player.getRandom().nextFloat() * STEAM_STRIDE / 3;
                steam(player, ParticleTypes.WHITE_SMOKE, 2 + player.getRandom().nextInt(2));
            }
        }
    }

    /** Blocks run between puffs of steam, a third of it random. */
    private static final double STEAM_STRIDE = 4.5;

    /**
     * A small cloud from the vents between the frame's shoulder blades.
     */
    private static void steam(ServerPlayer player, net.minecraft.core.particles.SimpleParticleType kind, int puffs)
    {
        var back = Vec3.directionFromRotation(0, player.yBodyRot).reverse().scale(0.5);
        player.level().sendParticles(kind, player.getX() + back.x, player.getY() + 2.1, player.getZ() + back.z, puffs, 0.12, 0.1, 0.12, 0.015);
    }

    /**
     * Sparks fly from the frame's joints where a blow lands.
     */
    private static void onHurt(LivingEntity entity, DamageSource source, float baseDamage, float damage, boolean blocked)
    {
        if (!(entity instanceof ServerPlayer player) || !wearing(player) || baseDamage <= 0 || source.is(ShieldingApparatus.BYPASSES))
            return;
        var from = source.getSourcePosition();
        var center = player.getBoundingBox().getCenter();
        var toward = from == null ? Vec3.ZERO : from.subtract(center).multiply(1, 0, 1).normalize().scale(0.45);
        player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x + toward.x, center.y + 0.3, center.z + toward.z,
                8, 0.2, 0.4, 0.2, 0.1);
        player.level().playSound(null, player.getX(), center.y, player.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.7F, 0.8F);
    }

    public static void onJumped(ServerPlayer player)
    {
        var worn = worn(player);
        if (worn != null && worn.phase() == WornArmor.Phase.WORN && load(player).exoskeleton())
            draw(player, ArmorBalance.POWERED_JUMP_DRAIN);
    }

    /**
     * Whether the frame feeds its wearer's walking and jumping, so they don't tire from them.
     */
    public static boolean paysForMoving(Player player)
    {
        return wearing(player) && charge(player) > 0;
    }

    /**
     * The legs take what they can of a fall from the battery, in whole points of damage.
     *
     * @return the damage left for the wearer
     */
    public static int takeFall(LivingEntity entity, int damage)
    {
        if (!(entity instanceof ServerPlayer player) || damage <= 0 || !wearing(player))
            return damage;
        int taken = ArmorDrain.fallTaken(damage, charge(player));
        if (taken <= 0)
            return damage;
        draw(player, ArmorDrain.fallCost(taken));
        if (taken == damage)
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NETHERITE_BLOCK_FALL, SoundSource.PLAYERS, 1, 0.6F);
        // The legs vent the landing
        steam(player, ParticleTypes.CLOUD, 4 + taken / 3);
        return damage - taken;
    }

    /**
     * Whether a wearer can't sprint: the frame is stepping, limp, or wading.
     */
    public static boolean holdsBackSprint(LivingEntity entity)
    {
        return entity instanceof Player player && wearing(player) && (moving(player) || entity.isInWater() || limp(player));
    }

    /**
     * Puts the racks back on the worn frame from the hotbar and gives the wearer their own back, before they step out or their inventory drops on death.
     */
    public static void releaseHotbar(ServerPlayer player)
    {
        var worn = worn(player);
        if (worn != null && player.getAttached(ArmorHotbar.STASH) != null)
            player.setAttached(WORN, worn.withFrame(ArmorHotbar.give(player, worn.frame())));
    }

    // Death

    private static void onDeath(LivingEntity entity, DamageSource source)
    {
        if (!(entity instanceof ServerPlayer player))
            return;
        var worn = worn(player);
        if (worn == null)
            return;
        WEARERS.remove(player.getUUID());
        releaseHotbar(player);
        var frame = worn(player).frame().copy();
        frame.set(RubrumContent.VOID_CHARGE, 0L);
        if (sendHome(player.level().getServer(), frame))
            player.removeAttached(WORN);
        else
            player.setAttached(WORN, new WornArmor(frame, WornArmor.Phase.HOMELESS, player.level().getGameTime()));
    }

    /**
     * Docks the frame back on the last base it stood on, loading that base's chunk if it must.
     *
     * @return false when that base is gone or holds another frame
     */
    public static boolean sendHome(MinecraftServer server, ItemStack frame)
    {
        var home = frame.get(VoidArmorContent.ARMOR_HOME);
        if (home == null)
            return false;
        var level = server.getLevel(home.dimension());
        if (level == null)
            return false;
        level.getChunkAt(home.pos());
        if (!(level.getBlockEntity(home.pos()) instanceof VoidArmorBaseTile base) || base.hasFrame())
            return false;
        base.dock(frame);
        return true;
    }

    /**
     * A free spot a step from the respawned player, ahead of them first, then to either side, then behind, so the frame doesn't stand inside them.
     */
    private static Vec3 besideRespawn(ServerPlayer player)
    {
        for (float turn : new float[] { 0, 90, -90, 180 })
        {
            var look = Vec3.directionFromRotation(0, player.getYRot() + turn);
            var spot = player.position().add(look.x * ArmorBalance.PARK_BESIDE, 0, look.z * ArmorBalance.PARK_BESIDE);
            if (player.level().noCollision(wearerBox(spot)))
                return spot;
        }
        return player.position();
    }

    public static void park(ServerLevel level, Vec3 feet, float yaw, ItemStack frame)
    {
        var parked = new ParkedArmor(VoidArmorContent.PARKED_ARMOR, level);
        parked.setFrame(frame);
        parked.snapTo(feet.x, feet.y, feet.z, yaw, 0);
        level.addFreshEntity(parked);
    }

    // What the frame does to its wearer

    private static void applyModifiers(Player player, @Nullable WornArmor worn)
    {
        if (worn == null || !worn.isOn())
        {
            // The modifiers always go on together, so a player without the scale one has none to take off
            var scale = player.getAttribute(Attributes.SCALE);
            if (scale == null || scale.getModifier(SCALE_ID) == null)
                return;
            for (var attribute : TOUCHED)
            {
                var instance = player.getAttribute(attribute);
                if (instance != null)
                    for (var id : MODIFIERS)
                        instance.removeModifier(id);
            }
            return;
        }

        var load = load(player);
        var total = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
        set(player, Attributes.SCALE, SCALE_ID, ArmorBalance.SCALE - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (worn.isMoving())
        {
            set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, -1, total);
            set(player, Attributes.JUMP_STRENGTH, JUMP_ID, -1, total);
        }
        else if (load.limp())
        {
            set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, -ArmorBalance.LIMP_SLOW, total);
            set(player, Attributes.JUMP_STRENGTH, JUMP_ID, -1, total);
        }
        else
        {
            float speed = ArmorBalance.FRAME_SPEED + (load.exoskeleton() ? ArmorBalance.EXOSKELETON_SPEED : 0);
            set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            set(player, Attributes.JUMP_STRENGTH, JUMP_ID, load.exoskeleton() ? ArmorBalance.EXOSKELETON_JUMP_STRENGTH : 0,
                    AttributeModifier.Operation.ADD_VALUE);
        }
        set(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, ArmorBalance.ENTITY_REACH, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, ArmorBalance.BLOCK_REACH, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, HIT_ID, load.exoskeleton() ? ArmorBalance.EXOSKELETON_HIT : 0, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_KNOCKBACK, KNOCKBACK_ID, load.exoskeleton() ? ArmorBalance.EXOSKELETON_KNOCKBACK : 0,
                AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.STEP_HEIGHT, STEP_ID, ArmorBalance.STEP_HEIGHT_BONUS, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, ArmorBalance.SAFE_FALL_BONUS, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(Player player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation)
    {
        var instance = player.getAttribute(attribute);
        if (instance == null)
            return;
        var current = instance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == operation)
            return;
        // Updating a modifier in place leaves its old copy counted under its old operation, so one changing operation is taken off first
        if (current != null && current.operation() != operation)
            instance.removeModifier(id);
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /**
     * Where a frame goes home to, set each time it docks.
     */
    public static GlobalPos home(ServerLevel level, BlockPos pos)
    {
        return GlobalPos.of(level.dimension(), pos);
    }
}
