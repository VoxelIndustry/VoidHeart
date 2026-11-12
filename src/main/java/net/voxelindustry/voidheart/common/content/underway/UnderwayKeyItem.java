package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

/** Set into a district's hatch, it is spent: the hatch draws its charge through it, and opens for good if the key lasts until the charge is full. */
public class UnderwayKeyItem extends Item
{
    public UnderwayKeyItem(Properties properties)
    {
        super(properties.rarity(Rarity.RARE).stacksTo(16));
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        var state = context.getLevel().getBlockState(context.getClickedPos());
        if (!state.is(VoidHeartBlocks.UNDERWAY_HATCH) && !state.is(VoidHeartBlocks.UNDERWAY_HAZARD_ARMOUR))
            return InteractionResult.PASS;
        if (!(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.SUCCESS;

        var hatch = UnderwayHatch.at(level, context.getClickedPos());
        if (hatch == null)
            return InteractionResult.PASS;
        String refusal = !hatch.isClosed(level) ? "open" : !level.getEntitiesOfClass(UnderwayKeyEntity.class, hatch.above()).isEmpty() ? "busy" : null;
        if (refusal != null)
        {
            if (context.getPlayer() instanceof ServerPlayer player)
                player.sendOverlayMessage(Component.translatable("voidheart.underway_key." + refusal));
            return InteractionResult.FAIL;
        }

        var key = VoidHeartEntities.UNDERWAY_KEY.create(level, EntitySpawnReason.TRIGGERED);
        if (key == null)
            return InteractionResult.FAIL;
        var spot = hatch.keySpot();
        key.snapTo(spot.x, spot.y, spot.z, 0, 0);
        level.addFreshEntity(key);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.2F, 0.6F);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1, 0.7F);
        context.getItemInHand().consume(1, context.getPlayer());
        return InteractionResult.SUCCESS;
    }
}
