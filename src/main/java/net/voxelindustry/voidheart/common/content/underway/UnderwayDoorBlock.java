package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An armoured door of the Underway. A hand opens it, except while its section is locked down: with power and an alarm sounding, it stays shut to visitors until the alarm stops. The fort's own machines still pass.
 */
public class UnderwayDoorBlock extends DoorBlock
{
    public UnderwayDoorBlock(BlockSetType type, Properties properties)
    {
        super(type, properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer visitor && !player.isCreative())
        {
            var security = UnderwaySecurity.of(server);
            var key = UnderwaySecurity.Key.at(pos);
            if (security != null && key != null && security.isLockedDown(server, key))
            {
                visitor.sendOverlayMessage(Component.translatable("voidheart.underway_door.locked"));
                level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.5F);
                // The client already swung it: this puts it back
                level.sendBlockUpdated(pos, state, state, 3);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
