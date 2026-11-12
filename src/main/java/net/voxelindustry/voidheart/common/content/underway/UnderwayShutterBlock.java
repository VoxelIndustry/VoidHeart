package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.HashSet;

/**
 * A steel shutter over the doorway of a room worth locking. A hand raises it only while its section has power, and once raised it stays up: nothing is left to lower it.
 */
public class UnderwayShutterBlock extends Block
{
    /** More blocks than any doorway holds, so a shutter placed by hand cannot take a wall of them with it. */
    private static final int MAX_BLOCKS = 12;

    public UnderwayShutterBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer visitor))
            return InteractionResult.SUCCESS;
        var security = UnderwaySecurity.of(server);
        var key = UnderwaySecurity.Key.at(pos);
        if (security == null || key == null || !security.isLive(server, key))
        {
            visitor.sendOverlayMessage(Component.translatable("voidheart.underway_shutter.dead"));
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8F, 0.5F);
            return InteractionResult.SUCCESS;
        }
        raise(server, pos);
        return InteractionResult.SUCCESS;
    }

    /** Raises the whole shutter a block belongs to. */
    public static void raise(ServerLevel level, BlockPos start)
    {
        var found = new HashSet<BlockPos>();
        var open = new ArrayDeque<BlockPos>();
        found.add(start.immutable());
        open.add(start.immutable());
        while (!open.isEmpty() && found.size() < MAX_BLOCKS)
        {
            var at = open.poll();
            for (var direction : Direction.values())
            {
                var next = at.relative(direction);
                if (found.size() < MAX_BLOCKS && level.getBlockState(next).getBlock() instanceof UnderwayShutterBlock && found.add(next))
                    open.add(next);
            }
        }
        for (var pos : found)
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.playSound(null, start, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.2F, 0.5F);
        level.playSound(null, start, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.8F, 0.6F);
    }
}
