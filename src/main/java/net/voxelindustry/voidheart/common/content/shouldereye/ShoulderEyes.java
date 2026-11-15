package net.voxelindustry.voidheart.common.content.shouldereye;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps a Shoulder Eye in the world next to whoever wears one in its Trinkets slot.
 * The eye itself is never saved: the wearer carries its state, kept even while the item is off so swapping it can't heal the eye,
 * and a fresh eye is spawned whenever the wearer joins, respawns or changes dimension.
 */
public class ShoulderEyes
{
    public static final String SLOT = "head/shoulder_eye";

    public static final AttachmentType<ShoulderEyeData> DATA = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "shoulder_eye_state"),
            builder -> builder.persistent(ShoulderEyeData.CODEC).copyOnDeath());

    /**
     * Left by the time eyes were bound by using the item. Read once to hand the item back to its slot.
     */
    private static final AttachmentType<ShoulderEyeData> LEGACY_BOUND = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "shoulder_eye"),
            builder -> builder.persistent(ShoulderEyeData.CODEC).copyOnDeath());

    private static final int AGGRESSOR_MEMORY_TICKS = 30 * 20;
    // A player older than this who puts the eye on watches it fly in, rather than finding it perched as after joining
    private static final int SETTLE_TICKS           = 20;

    private static final Map<UUID, ShoulderEyeEntity>  EYES        = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>>    AGGRESSORS  = new HashMap<>();
    private static final Set<UUID>                     WEARING     = new HashSet<>();

    public static void register()
    {
        // Touching the class is enough to register the attachments
    }

    public static boolean hasEye(Player player)
    {
        var attachment = TrinketsApi.getAttachment(player);
        return attachment != null && attachment.isEquipped(stack -> stack.getItem() instanceof ShoulderEyeItem, true);
    }

    public static void tickServer(MinecraftServer server)
    {
        for (var player : server.getPlayerList().getPlayers())
            tickPlayer(player);

        EYES.entrySet().removeIf(entry -> entry.getValue().isRemoved());

        long now = server.overworld().getGameTime();
        AGGRESSORS.values().forEach(attackers -> attackers.values().removeIf(expiry -> expiry < now));
        AGGRESSORS.values().removeIf(Map::isEmpty);
    }

    private static void tickPlayer(ServerPlayer player)
    {
        migrateLegacy(player);

        // Grows back whether or not it is worn, so taking it off doesn't stall it
        var data = player.getAttachedOrElse(DATA, ShoulderEyeData.fresh());
        boolean regrown = false;
        if (data.isRegenerating())
        {
            data = new ShoulderEyeData(ShoulderEyeEntity.MAX_HEALTH, data.regenTicks() - 1);
            player.setAttached(DATA, data);
            regrown = !data.isRegenerating();
        }

        boolean wearing = hasEye(player);
        boolean justPutOn = wearing && WEARING.add(player.getUUID()) && player.tickCount > SETTLE_TICKS;
        if (!wearing)
            WEARING.remove(player.getUUID());

        var eye = EYES.get(player.getUUID());
        if (!wearing || player.isSpectator() || !player.isAlive())
        {
            if (eye != null && !eye.isRemoved() && !eye.isDeadOrDying())
                eye.discard();
            return;
        }
        if (data.isRegenerating())
            return;

        if (eye != null && !eye.isRemoved())
        {
            if (eye.level() == player.level() && eye.getOwner() == player)
                return;
            eye.discard();
        }

        player.setAttached(DATA, data);
        Vec3 from = null;
        if (regrown)
            from = returningFrom(player);
        else if (justPutOn)
            from = player.getEyePosition().add(player.getLookAngle().scale(1.5));
        spawn(player, data, from);
    }

    /**
     * Hands back the eye of a player who bound it before it had a slot: into the slot if it is free, else into their inventory.
     */
    private static void migrateLegacy(ServerPlayer player)
    {
        var legacy = player.removeAttached(LEGACY_BOUND);
        if (legacy == null)
            return;

        player.setAttached(DATA, legacy);
        var stack = new ItemStack(VoidHeartItems.SHOULDER_EYE);
        var attachment = TrinketsApi.getAttachment(player);
        var inventory = attachment == null ? null : attachment.getInventory(SLOT);
        if (inventory != null && inventory.getContainerSize() > 0 && inventory.getItem(0).isEmpty())
            inventory.setItem(0, stack);
        else
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
    }

    /**
     * A regrown eye shows up a little above and away from its owner, then flies to its shoulder.
     */
    private static Vec3 returningFrom(ServerPlayer player)
    {
        var random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        return player.position().add(Math.cos(angle) * 3, 3 + random.nextDouble(), Math.sin(angle) * 3);
    }

    /**
     * @param from where the eye appears before flying to the shoulder, or null to appear right on it
     */
    private static void spawn(ServerPlayer player, ShoulderEyeData data, @Nullable Vec3 from)
    {
        var level = player.level();
        var eye = VoidHeartEntities.SHOULDER_EYE.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (eye == null)
            return;

        eye.bindTo(player, data.health());
        if (from != null)
        {
            eye.snapTo(from.x, from.y, from.z, player.getYRot(), 0);
            eye.setPerched(false);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 0.2, from.z, 24, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDER_EYE_DEATH, SoundSource.PLAYERS, 0.8F, 0.6F);
        }
        else
        {
            var shoulder = ShoulderEyeEntity.shoulderPosition(player, 1);
            eye.snapTo(shoulder.x, shoulder.y, shoulder.z, player.getYRot(), 0);
            eye.setPerched(true);
        }

        level.addFreshEntity(eye);
        EYES.put(player.getUUID(), eye);
    }

    public static void onEyeHealthChanged(ServerPlayer owner, float health)
    {
        var data = owner.getAttached(DATA);
        if (data != null && !data.isRegenerating() && data.health() != health)
            owner.setAttached(DATA, new ShoulderEyeData(health, 0));
    }

    public static void onEyeDeath(ServerPlayer owner)
    {
        if (owner.hasAttached(DATA))
            owner.setAttached(DATA, new ShoulderEyeData(ShoulderEyeEntity.MAX_HEALTH, VoidHeartConfig.get().shoulderEyeRegenSeconds * 20));
    }

    /**
     * Remembers the players hitting someone who owns an eye, so the eye can strike back.
     */
    public static void onDamage(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked)
    {
        if (!(victim instanceof ServerPlayer owner) || !hasEye(owner))
            return;
        if (!(source.getEntity() instanceof Player attacker) || attacker == owner)
            return;

        AGGRESSORS.computeIfAbsent(owner.getUUID(), uuid -> new HashMap<>())
                .put(attacker.getUUID(), owner.level().getServer().overworld().getGameTime() + AGGRESSOR_MEMORY_TICKS);
    }

    public static boolean isAggressor(ServerPlayer owner, Player player)
    {
        var attackers = AGGRESSORS.get(owner.getUUID());
        return attackers != null && attackers.containsKey(player.getUUID());
    }

    public static void onLeave(ServerPlayer player)
    {
        WEARING.remove(player.getUUID());
        var eye = EYES.remove(player.getUUID());
        if (eye != null && !eye.isRemoved())
            eye.discard();
        AGGRESSORS.remove(player.getUUID());
    }

    public static void clear()
    {
        EYES.clear();
        AGGRESSORS.clear();
        WEARING.clear();
    }
}
