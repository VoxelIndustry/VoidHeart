package net.voxelindustry.voidheart.common.content.pillar;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Instrument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidPillarBlock extends Block implements BlockEntityProvider
{
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(3, 0, 3, 13, 4, 13),
            createCuboidShape(4, 4, 4, 12, 12, 12),
            createCuboidShape(3, 12, 3, 13, 16, 13)
    );

    public VoidPillarBlock()
    {
        super(Settings.create()
                .mapColor(MapColor.BLACK)
                .instrument(Instrument.BASEDRUM)
                .strength(3F)
                .requiresTool()
                .sounds(BlockSoundGroup.STONE)
                .luminance(state -> state.get(Properties.LIT) ? 11 : 0));

        setDefaultState(getStateManager().getDefaultState()
                .with(Properties.LIT, false));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context)
    {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        VoidPillarTile pillar = (VoidPillarTile) world.getBlockEntity(pos);

        if (pillar == null || player.isSneaking() || !world.isAir(pos.up()))
            return ActionResult.SUCCESS;

        if (pillar.getStack().isEmpty())
        {
            var stackInHand = player.getStackInHand(hand);
            var itemInHand = stackInHand.getItem();

            if (stackInHand.isIn(VoidHeartTags.PILLAR_PLACE_INSTEAD_OF_STORE) && itemInHand instanceof BlockItem placeable)
            {
                placeable.place(new ItemPlacementContext(player,
                        hand,
                        player.getStackInHand(hand),
                        hit.withBlockPos(pos.up())));
            }
            else
                pillar.setStack(stackInHand.copyWithCount(1));

            if (!player.isCreative())
                stackInHand.decrement(1);
        }
        else
        {
            player.getInventory().offerOrDrop(pillar.getStack());
            pillar.setStack(ItemStack.EMPTY);
        }

        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved)
    {
        if (!state.isOf(newState.getBlock()))
        {
            VoidPillarTile tile = (VoidPillarTile) world.getBlockEntity(pos);
            if (tile != null)
            {
                tile.removeItself();
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.getStack());
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block block, BlockPos fromPos, boolean notify)
    {
        super.neighborUpdate(state, world, pos, block, fromPos, notify);

        if (!fromPos.equals(pos.up()))
            return;

        var tile = world.getBlockEntity(pos, VoidHeartTiles.VOID_PILLAR);
        if (tile.isEmpty())
            return;

        if (!tile.get().getStack().isEmpty() && !world.isAir(fromPos))
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.get().getStack());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.LIT);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidPillarTile(pos, state);
    }
}
