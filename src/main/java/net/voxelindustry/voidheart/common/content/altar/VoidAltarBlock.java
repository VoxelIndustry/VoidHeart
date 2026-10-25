package net.voxelindustry.voidheart.common.content.altar;

import net.voxelindustry.voidheart.client.render.VoidAltarRender;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class VoidAltarBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            box(0, 0, 0, 16, 4, 16),
            box(1, 4, 1, 15, 8, 15),
            box(0, 8, 0, 16, 14, 16),
            box(5, 14, 5, 11, 16, 11)
    );

    public VoidAltarBlock()
    {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(3F)
                .sound(SoundType.STONE)
                .lightLevel(state -> state.getValue(BlockStateProperties.LIT) ? 11 : 0));

        registerDefaultState(getStateDefinition().any()
                .setValue(BlockStateProperties.LIT, false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        VoidAltarTile altar = (VoidAltarTile) world.getBlockEntity(pos);

        if (altar == null)
            return InteractionResult.SUCCESS;

        if (altar.getStack().isEmpty())
        {
            var stackInHand = player.getItemInHand(hand);
            if(!stackInHand.isEmpty())
            {
                altar.setStack(player, stackInHand.copyWithCount(1));

                if (!player.isCreative())
                    stackInHand.shrink(1);
            }
            else if(world.isClientSide())
                VoidAltarRender.lastTouchedMillis = System.currentTimeMillis();
        }
        else
        {
            player.getInventory().placeItemBackInInventory(altar.getStack());
            altar.setStack(player, ItemStack.EMPTY);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.is(newState.getBlock()))
        {
            VoidAltarTile tile = (VoidAltarTile) world.getBlockEntity(pos);
            if (tile != null)
            {
                tile.removeItself();
                Containers.dropItemStack(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack());
                tile.dropAteItems();
            }
        }
        super.onRemove(state, world, pos, newState, moved);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.LIT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidAltarTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.VOID_ALTAR, VoidAltarTile::tick);
    }
}
