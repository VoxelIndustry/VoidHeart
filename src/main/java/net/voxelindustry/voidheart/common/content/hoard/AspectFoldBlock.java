package net.voxelindustry.voidheart.common.content.hoard;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Opens onto its placer's essence reserve; relays within reach link to it as to a store.
 */
public class AspectFoldBlock extends Block implements EntityBlock
{
    public AspectFoldBlock(Properties properties)
    {
        super(properties);
    }

    /**
     * A player with no pocket has nothing for it to open onto, so it is not placed.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context)
    {
        if (!HoardPlacing.mayPlace(context))
            return null;
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player && level.getBlockEntity(pos) instanceof AspectFoldTile fold)
            fold.setOwner(player.getUUID());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new AspectFoldTile(pos, state);
    }
}
