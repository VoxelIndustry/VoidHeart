package net.voxelindustry.voidheart.common.content.apparatus;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.mob.MalignantBoulder;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Keeps the shield of every player wearing an apparatus, takes hits on it before armor would and sets off its break core when it gives out.
 */
public final class ShieldingApparatus
{
    public static final AttachmentType<ShieldState> SHIELD = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "shield"),
            builder -> builder.persistent(ShieldState.CODEC).syncWith(ShieldState.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    /** Hits the shield lets through untouched: starving, drowning, falling, the void and commands. */
    public static final TagKey<DamageType> BYPASSES = TagKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "bypasses_shielding_apparatus"));

    private static final double PULSE_RADIUS    = 4;
    private static final float  PULSE_DAMAGE    = 4;
    private static final double PULSE_STRENGTH  = 1.2;
    private static final double STASIS_RADIUS   = 6;
    private static final int    STASIS_TICKS    = 80;
    private static final float  MEND_HEALTH     = 6;
    private static final double BLINK_DISTANCE  = 6;
    private static final int    BLINK_TICKS     = 40;

    private ShieldingApparatus()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachment
    }

    public static ItemStack equipped(Player player)
    {
        var attachment = TrinketsApi.getAttachment(player);
        if (attachment == null)
            return ItemStack.EMPTY;
        return attachment.findFirst(stack -> stack.getItem() instanceof ShieldingApparatusItem, true)
                .map(access -> access.get())
                .orElse(ItemStack.EMPTY);
    }

    public static @Nullable ShieldStats stats(ItemStack stack)
    {
        if (!(stack.getItem() instanceof ShieldingApparatusItem apparatus))
            return null;
        return grid(stack).stats(apparatus.tier());
    }

    public static ApparatusGrid grid(ItemStack stack)
    {
        return stack.getOrDefault(VoidHeartDataComponents.APPARATUS_GRID, ApparatusGrid.EMPTY);
    }

    public static ShieldState state(Player player)
    {
        return player.getAttachedOrElse(SHIELD, ShieldState.NONE);
    }

    public static void tickServer(MinecraftServer server)
    {
        for (var player : server.getPlayerList().getPlayers())
            tickPlayer(player);
    }

    private static void tickPlayer(ServerPlayer player)
    {
        var stats = player.isAlive() && !player.isSpectator() ? stats(equipped(player)) : null;
        var state = state(player);
        var next = state.fitTo(stats);
        if (stats != null)
            next = next.tick(stats);

        if (next.equals(ShieldState.NONE))
        {
            if (player.hasAttached(SHIELD))
                player.removeAttached(SHIELD);
        }
        else if (!next.equals(state))
            player.setAttached(SHIELD, next);
    }

    /**
     * Called with what is left of a hit on a player after armor and enchantments, and returns what goes on to absorption hearts and health.
     */
    public static float absorb(Player player, DamageSource source, float damage)
    {
        if (!(player instanceof ServerPlayer serverPlayer) || damage <= 0 || source.is(BYPASSES))
            return damage;

        var stats = stats(equipped(player));
        var state = state(player);
        if (stats == null || state.capacity() <= 0)
            return damage;

        var absorption = state.absorb(stats, damage, isWarded(stats, source));
        player.setAttached(SHIELD, absorption.state());

        if (absorption.absorbed() > 0)
            sendHit(serverPlayer, source, absorption.absorbed() / Math.max(1, state.capacity()), absorption.broke());
        if (absorption.broke())
            onBreak(serverPlayer, source, stats);
        return damage - absorption.absorbed();
    }

    private static boolean isWarded(ShieldStats stats, DamageSource source)
    {
        for (var ward : stats.wards())
        {
            boolean match = switch (ward)
            {
                case FIRE -> source.is(DamageTypeTags.IS_FIRE);
                case PROJECTILE -> source.is(DamageTypeTags.IS_PROJECTILE);
                case BLAST -> source.is(DamageTypeTags.IS_EXPLOSION);
                case ARCANE -> source.is(MalignantBoulder.MAGIC);
            };
            if (match)
                return true;
        }
        return false;
    }

    private static Vec3 center(LivingEntity entity)
    {
        return entity.getBoundingBox().getCenter();
    }

    private static void sendHit(ServerPlayer player, DamageSource source, float strength, boolean broke)
    {
        var center = center(player);
        // The middle of what struck, so a blow from a mob lands at its height rather than at its feet
        var direct = source.getDirectEntity();
        var from = direct != null && direct != player ? direct.getBoundingBox().getCenter() : source.getSourcePosition();
        Vector3f offset;
        if (from == null || from.distanceToSqr(center) < 0.01)
            offset = new Vector3f((float) player.getLookAngle().x, 0, (float) player.getLookAngle().z);
        else
            offset = from.subtract(center).toVector3f();

        var payload = new ShieldPayloads.Hit(player.getId(), offset, Math.min(1, strength), broke);
        ServerPlayNetworking.send(player, payload);
        for (var watcher : PlayerLookup.tracking(player))
            if (watcher != player)
                ServerPlayNetworking.send(watcher, payload);
    }

    private static void onBreak(ServerPlayer player, DamageSource source, ShieldStats stats)
    {
        var level = player.level();
        var center = center(player);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1, 0.6F);

        if (stats.breakEffect().isEmpty())
            return;
        switch (stats.breakEffect().get())
        {
            case PULSE -> pulse(level, player, center);
            case BLINK -> blink(player, source);
            case STASIS -> stasis(level, player, center);
            case MEND -> mend(level, player, center);
        }
    }

    private static boolean isFoe(ServerPlayer player, LivingEntity other)
    {
        if (other == player || !other.isAlive() || other.isAlliedTo(player))
            return false;
        if (other instanceof OwnableEntity ownable && ownable.getOwner() == player)
            return false;
        return !(other instanceof Player);
    }

    private static void pulse(ServerLevel level, ServerPlayer player, Vec3 center)
    {
        var area = player.getBoundingBox().inflate(PULSE_RADIUS);
        // Monsters only, so villagers and animals are spared and an iron golem is never turned on its wearer
        for (var other : level.getEntitiesOfClass(LivingEntity.class, area, other -> other instanceof Enemy && isFoe(player, other)))
        {
            var away = center(other).subtract(center);
            if (away.lengthSqr() > PULSE_RADIUS * PULSE_RADIUS)
                continue;
            var source = level.damageSources().indirectMagic(player, player);
            other.hurtServer(level, source, PULSE_DAMAGE);
            var push = away.horizontalDistanceSqr() < 0.01 ? new Vec3(1, 0, 0) : away.multiply(1, 0, 1).normalize();
            other.knockback(PULSE_STRENGTH, -push.x, -push.z, source, PULSE_DAMAGE);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 60, 1.2, 0.6, 1.2, 0.4);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    /**
     * Steps the wearer away from where the hit came from, onto the first spot along that way they fit.
     */
    private static void blink(ServerPlayer player, DamageSource source)
    {
        var from = source.getSourcePosition();
        var away = from == null ? player.getLookAngle().reverse() : player.position().subtract(from);
        away = away.horizontalDistanceSqr() < 0.01 ? player.getLookAngle().reverse() : away.multiply(1, 0, 1).normalize();

        var start = player.position();
        for (double distance = BLINK_DISTANCE; distance >= 2; distance -= 1)
        {
            var target = start.add(away.scale(distance));
            if (player.randomTeleport(target.x, target.y + 1, target.z, true, state -> false))
            {
                player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y + 1, start.z, 30, 0.3, 0.6, 0.3, 0.05);
                break;
            }
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, BLINK_TICKS, 0, false, false, true));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
    }

    private static void stasis(ServerLevel level, ServerPlayer player, Vec3 center)
    {
        var area = player.getBoundingBox().inflate(STASIS_RADIUS);
        for (var other : level.getEntitiesOfClass(LivingEntity.class, area, other -> other instanceof Enemy && isFoe(player, other)))
        {
            other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STASIS_TICKS, 2), player);
            other.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STASIS_TICKS, 0), player);
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 40, 2.5, 0.5, 2.5, 0.02);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 0.7F);
    }

    private static void mend(ServerLevel level, ServerPlayer player, Vec3 center)
    {
        player.heal(MEND_HEALTH);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 16, 0.4, 0.6, 0.4, 0);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1, 1.2F);
    }
}
