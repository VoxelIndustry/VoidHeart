package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Projects the land around it as a hologram. Its controls float above the table and are handled client side.
 */
public class OculusTableBlock extends Block implements EntityBlock
{
    public OculusTableBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(level, pos, state, placer, itemStack);

        if (!level.isClientSide())
            OculusTableStructure.onBlockAdded(level, pos);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new OculusTableTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.OCULUS_TABLE, OculusTableTile::serverTick);
    }
}
