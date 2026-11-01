package net.voxelindustry.voidheart.common.content.shouldereye;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps every bound Shoulder Eye in the world next to its owner.
 * The eye itself is never saved: the owner carries its state, and a fresh eye is spawned whenever the owner joins, respawns or changes dimension.
 */
public class ShoulderEyes
{
    public static final AttachmentType<ShoulderEyeData> DATA = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "shoulder_eye"),
            builder -> builder.persistent(ShoulderEyeData.CODEC).copyOnDeath());

    private static final int AGGRESSOR_MEMORY_TICKS = 30 * 20;

    private static final Map<UUID, ShoulderEyeEntity>  EYES        = new HashMap<>();
    private static final Map<UUID, Map<UUID, Long>>    AGGRESSORS  = new HashMap<>();

    public static void register()
    {
        // Touching the class is enough to register the attachment
    }

    public static boolean hasEye(Player player)
    {
        return player.hasAttached(DATA);
    }

    public static void bind(ServerPlayer player)
    {
        player.setAttached(DATA, ShoulderEyeData.fresh());
        var from = player.getEyePosition().add(player.getLookAngle().scale(1.5));
        spawn(player, ShoulderEyeData.fresh(), from);
    }

    public static void unbind(ServerPlayer player)
    {
        player.removeAttached(DATA);
        var eye = EYES.remove(player.getUUID());
        if (eye != null && !eye.isRemoved())
            eye.discard();
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
        var data = player.getAttached(DATA);
        var eye = EYES.get(player.getUUID());

        if (data == null || player.isSpectator() || !player.isAlive())
        {
            if (eye != null && !eye.isRemoved() && !eye.isDeadOrDying())
                eye.discard();
            return;
        }

        if (data.isRegenerating())
        {
            var remaining = data.regenTicks() - 1;
            player.setAttached(DATA, new ShoulderEyeData(ShoulderEyeEntity.MAX_HEALTH, remaining));
            if (remaining == 0)
                spawn(player, player.getAttached(DATA), returningFrom(player));
            return;
        }

        if (eye != null && !eye.isRemoved())
        {
            if (eye.level() == player.level() && eye.getOwner() == player)
                return;
            eye.discard();
        }
        spawn(player, data, null);
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
        var eye = EYES.remove(player.getUUID());
        if (eye != null && !eye.isRemoved())
            eye.discard();
        AGGRESSORS.remove(player.getUUID());
    }

    public static void clear()
    {
        EYES.clear();
        AGGRESSORS.clear();
    }
}
