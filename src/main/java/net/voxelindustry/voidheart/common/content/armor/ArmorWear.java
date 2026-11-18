package net.voxelindustry.voidheart.common.content.armor;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Putting the Void Armor on and taking it off. A worn frame lives on its wearer, who keeps moving and fighting as themselves: the frame only changes their
 * size, speed, reach, jump and hit through attribute modifiers, put back every tick so they survive a relog. Holding sneak steps out, onto an empty base if
 * the wearer stands on one, otherwise leaving the frame parked where they stood. On a real death the frame goes back to the last base it docked on.
 */
public final class ArmorWear
{
    public static final AttachmentType<WornArmor> WORN = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "void_armor"),
            builder -> builder.persistent(WornArmor.CODEC).copyOnDeath().syncWith(WornArmor.STREAM_CODEC, AttachmentSyncPredicate.all()));

    private static final Identifier SCALE_ID       = id("scale");
    private static final Identifier SPEED_ID       = id("speed");
    private static final Identifier JUMP_ID        = id("jump");
    private static final Identifier ENTITY_REACH_ID = id("entity_reach");
    private static final Identifier BLOCK_REACH_ID = id("block_reach");
    private static final Identifier HIT_ID         = id("hit");
    private static final Identifier STEP_ID        = id("step");
    private static final Identifier FALL_ID        = id("fall");

    private static final List<Holder<Attribute>> TOUCHED = List.of(Attributes.SCALE, Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH,
            Attributes.ENTITY_INTERACTION_RANGE, Attributes.BLOCK_INTERACTION_RANGE, Attributes.ATTACK_DAMAGE, Attributes.STEP_HEIGHT,
            Attributes.SAFE_FALL_DISTANCE);

    /** How long each wearer has held sneak, in ticks. */
    private static final Map<UUID, Integer> SNEAK = new HashMap<>();

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
        ServerPlayerEvents.LEAVE.register(player -> SNEAK.remove(player.getUUID()));

        UseItemCallback.EVENT.register((player, level, hand) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> moving(player) ? InteractionResult.FAIL : InteractionResult.PASS);
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
        player.teleportTo(player.level(), feet.x, feet.y, feet.z, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setAttached(WORN, new WornArmor(fitted, WornArmor.Phase.STEPPING_IN, player.level().getGameTime()));
        SNEAK.remove(player.getUUID());
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
                if (elapsed >= ArmorBalance.STEP_IN_TICKS)
                    player.setAttached(WORN, worn.in(WornArmor.Phase.WORN, time));
            }
            case WORN ->
            {
                int held = player.isShiftKeyDown() ? SNEAK.merge(player.getUUID(), 1, Integer::sum) : 0;
                if (held == 0)
                    SNEAK.remove(player.getUUID());
                if (held >= ArmorBalance.STEP_OUT_HOLD_TICKS)
                {
                    SNEAK.remove(player.getUUID());
                    player.setAttached(WORN, worn.in(WornArmor.Phase.STEPPING_OUT, time));
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

    /**
     * Lets the wearer out at once: onto the empty base they stand on, or leaving the frame parked where they stood.
     */
    public static void stepOut(ServerPlayer player)
    {
        var worn = worn(player);
        if (worn == null)
            return;
        player.removeAttached(WORN);
        SNEAK.remove(player.getUUID());
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

    // Death

    private static void onDeath(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source)
    {
        if (!(entity instanceof ServerPlayer player))
            return;
        var worn = worn(player);
        if (worn == null)
            return;
        SNEAK.remove(player.getUUID());
        var frame = worn.frame().copy();
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
                    for (var id : List.of(SCALE_ID, SPEED_ID, JUMP_ID, ENTITY_REACH_ID, BLOCK_REACH_ID, HIT_ID, STEP_ID, FALL_ID))
                        instance.removeModifier(id);
            }
            return;
        }

        var stats = VoidArmorItem.stats(worn.frame());
        boolean moving = worn.isMoving();
        set(player, Attributes.SCALE, SCALE_ID, ArmorBalance.SCALE - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, moving ? -1 : stats.speedBonus(),
                moving ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL : AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        set(player, Attributes.JUMP_STRENGTH, JUMP_ID, moving ? -1 : stats.exoskeleton() ? ArmorBalance.EXOSKELETON_JUMP_STRENGTH : 0,
                moving ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL : AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, ArmorBalance.ENTITY_REACH, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, ArmorBalance.BLOCK_REACH, AttributeModifier.Operation.ADD_VALUE);
        set(player, Attributes.ATTACK_DAMAGE, HIT_ID, stats.hitBonus(), AttributeModifier.Operation.ADD_VALUE);
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
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /**
     * Where a frame goes home to, set each time it docks.
     */
    public static GlobalPos home(ServerLevel level, net.minecraft.core.BlockPos pos)
    {
        return GlobalPos.of(level.dimension(), pos);
    }
}
