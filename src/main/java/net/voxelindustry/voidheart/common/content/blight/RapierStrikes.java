package net.voxelindustry.voidheart.common.content.blight;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Charged rapier hits puncture their target, and enough of them unseam it: it is folded out of the world for a moment, held where it was, and takes the
 * Unseaming when it snaps back. The punctures live on the target and are sent to everyone who sees it.
 */
public final class RapierStrikes
{
    public static final AttachmentType<Punctured> PUNCTURED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "punctured"),
            builder -> builder.syncWith(Punctured.STREAM_CODEC, AttachmentSyncPredicate.all()));

    public static final ResourceKey<DamageType> UNSEAMING = ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(MODID, "unseaming"));

    /** How far a puncture lands from where the thrust went, in blocks. */
    private static final float SLIT_SPREAD = 0.16F;

    /** The game time each player last swung a rapier charged enough to puncture, since the charge is spent before the item hears of the hit. */
    private static final WeakHashMap<Player, Long> CHARGED = new WeakHashMap<>();
    /** Those carrying punctures or a fold, ticked until nothing is left on them. */
    private static final Set<LivingEntity>         TRACKED = Collections.newSetFromMap(new IdentityHashMap<>());

    private RapierStrikes()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachment
    }

    public static Punctured of(Entity entity)
    {
        return entity.getAttachedOrElse(PUNCTURED, Punctured.NONE);
    }

    /** Called before a player's attack lands, while the charge of the swing can still be read. */
    public static InteractionResult beforeAttack(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit)
    {
        if (!level.isClientSide() && player.getMainHandItem().getItem() instanceof BlightRapierItem
            && player.getAttackStrengthScale(0.5F) >= BlightBalance.RAPIER_CHARGE)
            CHARGED.put(player, level.getGameTime());
        return InteractionResult.PASS;
    }

    /** A rapier hit that landed. It punctures only when its swing was charged. */
    public static void onHit(LivingEntity target, LivingEntity attacker)
    {
        if (!(attacker instanceof Player player) || !(target.level() instanceof ServerLevel level))
            return;
        long now = level.getGameTime();
        var charged = CHARGED.remove(player);
        if (charged == null || charged != now || !target.isAlive())
            return;

        var punctured = of(target);
        var wound = punctured.woundOf(player.getUUID());
        if (wound.isLocked(now) || punctured.fold().isPresent())
            return;

        wound = wound.hit(slitFor(target, player), now);
        level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5F,
                1.4F + 0.08F * wound.open(now));

        if (wound.isReady(now))
        {
            var fold = new Punctured.Fold(player.getUUID(), now, target.position(), wound.slits());
            punctured = punctured.with(player.getUUID(), wound.unseam(now)).withFold(Optional.of(fold));
            level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1, 1.6F);
        }
        else
            punctured = punctured.with(player.getUUID(), wound);

        target.setAttached(PUNCTURED, punctured);
        TRACKED.add(target);
    }

    /** Where the thrust went into the target, in its own frame, scattered a little across the thrust and turned at random. */
    private static Wound.Slit slitFor(LivingEntity target, Player player)
    {
        var box = target.getBoundingBox();
        var eye = player.getEyePosition();
        var look = player.getLookAngle();
        var through = eye.add(look.scale(player.entityInteractionRange() + 2));
        var point = box.clip(eye, through).orElse(box.getCenter());

        // Thrusts at one spot would stack their slits into one blot
        var random = target.getRandom();
        var across = new Vec3(-look.z, 0, look.x);
        across = across.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : across.normalize();
        point = point.add(across.scale((random.nextFloat() - 0.5F) * 2 * SLIT_SPREAD)).add(0, (random.nextFloat() - 0.5F) * 2 * SLIT_SPREAD, 0);

        var offset = point.subtract(target.position());
        float yaw = target.yBodyRot * Mth.DEG_TO_RAD;
        double side = offset.x * Math.cos(yaw) + offset.z * Math.sin(yaw);
        double depth = -offset.x * Math.sin(yaw) + offset.z * Math.cos(yaw);
        float width = Math.max(0.1F, target.getBbWidth());
        float height = Math.max(0.1F, target.getBbHeight());
        float angle = (random.nextFloat() - 0.5F) * Mth.PI * 0.6F;
        return new Wound.Slit((float) (side / width), Mth.clamp((float) (offset.y / height), 0.08F, 0.92F), (float) (depth / width), angle);
    }

    public static void tickServer(MinecraftServer server)
    {
        if (TRACKED.isEmpty())
            return;

        var iterator = TRACKED.iterator();
        while (iterator.hasNext())
        {
            var target = iterator.next();
            if (target.isRemoved() || !(target.level() instanceof ServerLevel level))
            {
                iterator.remove();
                continue;
            }

            long now = level.getGameTime();
            var punctured = of(target);
            var fold = punctured.fold();
            if (fold.isPresent())
            {
                if (now >= fold.get().end())
                {
                    punctured = punctured.withFold(Optional.empty());
                    target.setAttached(PUNCTURED, punctured);
                    unseam(level, target, fold.get());
                }
                else
                    hold(target, fold.get().anchor());
            }

            if (!target.isAlive())
            {
                target.removeAttached(PUNCTURED);
                iterator.remove();
                continue;
            }

            var pruned = punctured.pruned(now);
            if (pruned.isEmpty())
            {
                target.removeAttached(PUNCTURED);
                iterator.remove();
            }
            else if (pruned != punctured)
                target.setAttached(PUNCTURED, pruned);
        }
    }

    private static void hold(LivingEntity target, Vec3 anchor)
    {
        target.setDeltaMovement(Vec3.ZERO);
        if (target.position().distanceToSqr(anchor) > 1.0E-4)
            target.teleportTo(anchor.x, anchor.y, anchor.z);
    }

    private static void unseam(ServerLevel level, LivingEntity target, Punctured.Fold fold)
    {
        Entity wielder = level.getServer().getPlayerList().getPlayer(fold.wielder());
        var source = wielder == null ? level.damageSources().source(UNSEAMING) : level.damageSources().source(UNSEAMING, wielder);
        target.hurtServer(level, source, BlightBalance.unseamingDamage(target.getMaxHealth()));
        level.playSound(null, target.getX(), target.getY(0.5), target.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.7F, 1.8F);
    }
}
