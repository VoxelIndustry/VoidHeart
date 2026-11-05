package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.consume_effects.TeleportRandomlyConsumeEffect;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.item.VoidPearlItem;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Nothing teleports a player inside a dungeon or the Daedalus, whichever way it is attempted. Commands and creative players stay free, for building and review.
 */
public final class DungeonTeleportLock
{
    private static final float THUD_VOLUME = 1;
    private static final float THUD_PITCH = 0.5F;
    private static final int REPEAT_TICKS = 20;

    private static final Map<UUID, Integer> lastRefusalTick = new HashMap<>();

    private DungeonTeleportLock()
    {
    }

    public static boolean isLocked(Player player)
    {
        var dimension = player.level().dimension();
        return (dimension.equals(VoidDungeons.WORLD_KEY) || dimension.equals(VoidDaedalus.WORLD_KEY)) && !player.isCreative() && !player.isSpectator();
    }

    /**
     * @return true when the teleport must not happen, after telling the player so. Portals ask every tick, so the reaction is limited to once per second.
     */
    public static boolean refuse(Player player)
    {
        if (!isLocked(player))
            return false;

        if (player instanceof ServerPlayer serverPlayer)
            react(serverPlayer);
        return true;
    }

    private static void react(ServerPlayer player)
    {
        int tick = player.level().getServer().getTickCount();
        var last = lastRefusalTick.get(player.getUUID());
        if (last != null && tick - last < REPEAT_TICKS)
            return;

        lastRefusalTick.put(player.getUUID(), tick);
        player.level().playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, THUD_VOLUME, THUD_PITCH);
        player.sendOverlayMessage(Component.translatable(MODID + ".teleport_interfered"));
    }

    public static InteractionResult onUseItem(Player player, Level level, InteractionHand hand)
    {
        if (!teleports(player.getItemInHand(hand)) || !isLocked(player))
            return InteractionResult.PASS;

        // On the client only SUCCESS sends the use to the server, which is where the refusal is told to the player
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        refuse(player);
        return InteractionResult.FAIL;
    }

    private static boolean teleports(ItemStack stack)
    {
        if (stack.is(Items.ENDER_PEARL) || stack.getItem() instanceof VoidPearlItem)
            return true;

        var consumable = stack.get(DataComponents.CONSUMABLE);
        return consumable != null && consumable.onConsumeEffects().stream().anyMatch(TeleportRandomlyConsumeEffect.class::isInstance);
    }

    public static void clear()
    {
        lastRefusalTick.clear();
    }
}
