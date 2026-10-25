package net.voxelindustry.voidheart.common.content.inventorymover;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class InventoryInserterBlock extends Block implements EntityBlock
{
    public InventoryInserterBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE));

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.FACING, Direction.UP));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.INVENTORY_INSERTER, InventoryInserterTile::tick);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx)
    {
        return this.defaultBlockState().setValue(BlockStateProperties.FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.FACING);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new InventoryInserterTile(pos, state);
    }
}
