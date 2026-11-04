package net.voxelindustry.voidheart.common.content.mob;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
import net.voxelindustry.voidheart.common.setup.VoidHeartSounds;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Barnacles growing inside a player. The attachments are the truth and the status effect only displays them, which is why milk cures nothing.
 * Neither is kept through death, and only the amount is sent to clients, to draw the growths on the model.
 */
public final class BarnacleInfection
{
    public static final AttachmentType<Integer> AMOUNT = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "barnacle_infection_amount"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final AttachmentType<Integer> TICKS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(MODID, "barnacle_infection_ticks"),
            builder -> builder.persistent(Codec.INT));

    private static final int EFFECT_DRIFT = 20;

    private BarnacleInfection()
    {
    }

    public static void register()
    {
        // Touching the class is enough to register the attachments
    }

    public static void infect(ServerPlayer player)
    {
        if (isImmune(player))
            return;

        int amount = amount(player);
        player.setAttached(TICKS, InfectionRules.ticksAfterHit(amount, ticks(player)));
        player.setAttached(AMOUNT, InfectionRules.amountAfterHit(amount));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), VoidHeartSounds.BARNACLE_INFECT, SoundSource.HOSTILE, 1, 1);
    }

    public static int amount(Player player)
    {
        return player.getAttachedOrElse(AMOUNT, 0);
    }

    public static int ticks(ServerPlayer player)
    {
        return player.getAttachedOrElse(TICKS, 0);
    }

    public static void clear(ServerPlayer player)
    {
        player.removeAttached(AMOUNT);
        player.removeAttached(TICKS);
        player.removeEffect(VoidHeartEffects.BARNACLE_INFECTION);
    }

    public static void tickServer(MinecraftServer server)
    {
        for (var player : server.getPlayerList().getPlayers())
        {
            int amount = amount(player);
            if (amount > 0 && player.isAlive() && !isImmune(player))
                tick(player, amount);
        }
    }

    private static boolean isImmune(ServerPlayer player)
    {
        return player.isCreative() || player.isSpectator();
    }

    private static void tick(ServerPlayer player, int amount)
    {
        int ticks = ticks(player) - 1;
        if (ticks <= 0)
        {
            burst(player, amount);
            return;
        }

        player.setAttached(TICKS, ticks);
        showEffect(player, amount, ticks);
        if (InfectionRules.isWarning(ticks) && ticks % InfectionRules.warningInterval(ticks) == 0)
            warn(player);
    }

    private static void showEffect(ServerPlayer player, int amount, int ticks)
    {
        var shown = player.getEffect(VoidHeartEffects.BARNACLE_INFECTION);
        if (shown == null || shown.getAmplifier() != amount - 1 || Math.abs(shown.getDuration() - ticks) > EFFECT_DRIFT)
        {
            player.removeEffect(VoidHeartEffects.BARNACLE_INFECTION);
            player.addEffect(new MobEffectInstance(VoidHeartEffects.BARNACLE_INFECTION, ticks, amount - 1, false, false, true));
        }
    }

    private static void warn(ServerPlayer player)
    {
        var level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), VoidHeartSounds.BARNACLE_STIR, SoundSource.HOSTILE, 0.8F, 1);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY(0.6), player.getZ(), 6, 0.3, 0.45, 0.3, 0.01);
    }

    private static void burst(ServerPlayer player, int amount)
    {
        clear(player);

        var level = player.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), VoidHeartSounds.BARNACLE_BURST, SoundSource.HOSTILE, 1.2F, 1);
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY(0.6), player.getZ(), 24, 0.4, 0.5, 0.4, 0.05);

        if (level.getDifficulty() == Difficulty.PEACEFUL)
            return;

        for (int i = 0; i < amount; i++)
        {
            var barnacle = VoidHeartEntities.CORRUPTED_BARNACLE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (barnacle == null)
                continue;

            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            barnacle.snapTo(player.getX() + Math.cos(angle) * 0.4, player.getY() + 0.5, player.getZ() + Math.sin(angle) * 0.4, (float) Math.toDegrees(angle), 0);
            barnacle.scatterFrom(player.position(), angle);
            level.addFreshEntity(barnacle);
        }
    }
}
