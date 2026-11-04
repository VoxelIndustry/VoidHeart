package net.voxelindustry.voidheart.common.content.tormenter;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Gives the soul of a mob a player killed to one Malignant Tormenter of that player's hotbar.
 */
public final class TormenterSoulHarvest
{
    private static final int   SOUL_PARTICLES  = 8;
    private static final int   SATED_PARTICLES = 30;
    private static final float SOUL_VOLUME     = 0.7F;

    private TormenterSoulHarvest()
    {
    }

    public static void onDeath(LivingEntity killed, DamageSource source)
    {
        if (!(killed instanceof Mob) || killed.getType().builtInRegistryHolder().is(VoidHeartTags.TORMENTER_REFUSES))
            return;
        if (!(source.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer)
            return;

        var inventory = player.getInventory();
        var slot = TormenterSoulClaim.claimingSlot(readHotbar(inventory), killed.getType());
        if (slot == TormenterSoulClaim.NONE)
            return;

        announce(player, killed, feed(player, inventory, slot, killed));
    }

    private static List<TormenterSoulClaim.Held<EntityType<?>>> readHotbar(Inventory inventory)
    {
        var hotbar = new ArrayList<TormenterSoulClaim.Held<EntityType<?>>>(Inventory.getSelectionSize());
        for (var slot = 0; slot < Inventory.getSelectionSize(); slot++)
        {
            var stack = inventory.getItem(slot);
            if (!stack.is(VoidHeartBlocks.MALIGNANT_TORMENTER.asItem()))
            {
                hotbar.add(null);
                continue;
            }

            var souls = stack.get(VoidHeartDataComponents.TORMENTER_SOULS);
            hotbar.add(souls == null ? TormenterSoulClaim.Held.empty() : new TormenterSoulClaim.Held<>(souls.type(), souls.souls()));
        }
        return hotbar;
    }

    /**
     * Only one tormenter of a stack feeds, the others stay as they were.
     */
    private static TormenterSouls feed(ServerPlayer player, Inventory inventory, int slot, LivingEntity killed)
    {
        var stack = inventory.getItem(slot);
        var fed = stack.split(1);

        var held = fed.get(VoidHeartDataComponents.TORMENTER_SOULS);
        var souls = held == null ? new TormenterSouls(killed.getType(), 1) : held.withOneMore();
        fed.set(VoidHeartDataComponents.TORMENTER_SOULS, souls);

        if (stack.isEmpty())
            inventory.setItem(slot, fed);
        else
            inventory.placeItemBackInInventory(fed, Prediction.SERVER_ONLY);
        return souls;
    }

    private static void announce(ServerPlayer player, LivingEntity killed, TormenterSouls souls)
    {
        var level = (ServerLevel) killed.level();
        var sated = souls.isComplete();

        level.sendParticles(ParticleTypes.SOUL, killed.getX(), killed.getY(0.5), killed.getZ(), sated ? SATED_PARTICLES : SOUL_PARTICLES, 0.3, 0.4, 0.3, 0.03);
        if (sated)
            level.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1, 0.7F);
        else
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, SOUL_VOLUME, 1);

        player.sendOverlayMessage(sated
                ? Component.translatable(MODID + ".tormenter.sated", souls.type().getDescription())
                : Component.translatable(MODID + ".tormenter.soul", souls.type().getDescription(), souls.souls(), TormenterSouls.REQUIRED));
    }
}
