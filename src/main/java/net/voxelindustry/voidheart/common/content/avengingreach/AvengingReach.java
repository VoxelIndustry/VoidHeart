package net.voxelindustry.voidheart.common.content.avengingreach;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Void arms grafted by the Avenging Reach. Whenever their bearer is hurt by something within reach, one ready arm lashes back at it, hitting as an
 * iron sword would and throwing it back, then recharges. The blow lands when the drawn claw does, a few ticks after the hit.
 */
public final class AvengingReach
{
    public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "avenging_reach"));

    public static final AttachmentType<Integer> ARMS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "avenging_reach"),
            builder -> builder.persistent(Codec.intRange(0, ReachArms.MAX_ARMS)).copyOnDeath());

    private record Strike(UUID owner, UUID target, long impactAt)
    {
    }

    private static final Map<UUID, long[]> READY_AT = new HashMap<>();
    private static final List<Strike>      PENDING  = new ArrayList<>();

    private AvengingReach()
    {
    }

    public static void register()
    {
        AvengingReachPayload.register();
    }

    public static int armsOf(Player player)
    {
        return player.getAttachedOrElse(ARMS, 0);
    }

    public static void setArms(Player player, int arms)
    {
        if (arms <= 0)
            player.removeAttached(ARMS);
        else
            player.setAttached(ARMS, Math.min(arms, ReachArms.MAX_ARMS));
    }

    public static void onDamage(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked)
    {
        if (blocked || !(victim instanceof ServerPlayer owner) || owner.isSpectator())
            return;
        // Another bearer's arm answering does not call for an answer back
        if (source.is(DAMAGE))
            return;
        int arms = armsOf(owner);
        if (arms <= 0)
            return;
        if (!(source.getEntity() instanceof LivingEntity aggressor) || aggressor == owner || !aggressor.isAlive() || aggressor.level() != owner.level())
            return;
        if (aggressor.isAlliedTo(owner) || aggressor instanceof OwnableEntity pet && pet.getOwner() == owner)
            return;
        if (!ReachArms.inReach(chestOf(owner), aggressor.getBoundingBox()))
            return;

        long now = owner.level().getServer().getTickCount();
        var readyAt = READY_AT.computeIfAbsent(owner.getUUID(), uuid -> new long[ReachArms.MAX_ARMS]);
        int side = ReachArms.sideOf(owner.yBodyRot, aggressor.getX() - owner.getX(), aggressor.getZ() - owner.getZ());
        int arm = ReachArms.nextReady(readyAt, arms, now, side);
        if (arm < 0)
            return;
        readyAt[arm] = now + Math.round(VoidHeartConfig.get().avengingReachCooldownSeconds * 20);
        PENDING.add(new Strike(owner.getUUID(), aggressor.getUUID(), now + ReachArms.IMPACT_TICK));

        var payload = new AvengingReachPayload(owner.getId(), aggressor.getId(), arm, owner.getRandom().nextInt());
        ServerPlayNetworking.send(owner, payload);
        PlayerLookup.tracking(owner).forEach(viewer ->
        {
            if (viewer != owner)
                ServerPlayNetworking.send(viewer, payload);
        });
        owner.level().playSound(null, owner.getX(), owner.getY() + 1, owner.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    public static void tickServer(MinecraftServer server)
    {
        long now = server.getTickCount();
        PENDING.removeIf(strike ->
        {
            if (strike.impactAt() > now)
                return false;
            land(server, strike);
            return true;
        });
    }

    private static void land(MinecraftServer server, Strike strike)
    {
        var owner = server.getPlayerList().getPlayer(strike.owner());
        if (owner == null || !owner.isAlive())
            return;
        var level = owner.level();
        if (!(level.getEntity(strike.target()) instanceof LivingEntity target) || !target.isAlive())
            return;
        // Out of reach by the time the claw arrives: the lash falls short
        if (!ReachArms.inReach(chestOf(owner), target.getBoundingBox()))
            return;

        var oldMovement = target.getDeltaMovement();
        var source = owner.damageSources().source(DAMAGE, owner);
        float dealt = VoidHeartConfig.get().avengingReachDamage;
        if (!target.hurtServer(level, source, dealt))
            return;

        target.knockback(VoidHeartConfig.get().avengingReachKnockback, owner.getX() - target.getX(), owner.getZ() - target.getZ(), source, dealt);
        if (target instanceof ServerPlayer hit && hit.syncVelocity)
        {
            hit.connection.send(new ClientboundSetEntityMotionPacket(hit));
            hit.syncVelocity = false;
            hit.setDeltaMovement(oldMovement);
        }
        level.playSound(null, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1, 0.7F);
    }

    public static Vec3 chestOf(LivingEntity entity)
    {
        return entity.position().add(0, ReachArms.chestHeight(entity.getBbHeight()), 0);
    }

    public static void onLeave(ServerPlayer player)
    {
        READY_AT.remove(player.getUUID());
        PENDING.removeIf(strike -> strike.owner().equals(player.getUUID()));
    }

    public static void clear()
    {
        READY_AT.clear();
        PENDING.clear();
    }
}
