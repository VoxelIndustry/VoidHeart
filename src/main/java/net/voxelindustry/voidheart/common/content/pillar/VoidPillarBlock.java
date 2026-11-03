package net.voxelindustry.voidheart.common.content.pillar;

import net.minecraft.util.Prediction;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidPillarBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            box(3, 0, 3, 13, 4, 13),
            box(4, 4, 4, 12, 12, 12),
            box(3, 12, 3, 13, 16, 13)
    );

    public VoidPillarBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(3F)
                .requiresCorrectToolForDrops()
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
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        VoidPillarTile pillar = (VoidPillarTile) world.getBlockEntity(pos);

        if (pillar == null || player.isShiftKeyDown() || !world.isEmptyBlock(pos.above()))
            return InteractionResult.SUCCESS;

        if (pillar.getStack().isEmpty())
        {
            var stackInHand = player.getItemInHand(hand);
            var itemInHand = stackInHand.getItem();

            if (stackInHand.is(VoidHeartTags.PILLAR_PLACE_INSTEAD_OF_STORE) && itemInHand instanceof BlockItem placeable)
            {
                placeable.place(new BlockPlaceContext(player,
                        hand,
                        player.getItemInHand(hand),
                        hit.withPosition(pos.above())));
            }
            else
                pillar.setStack(stackInHand.copyWithCount(1));

            if (!player.isCreative())
                stackInHand.shrink(1);
        }
        else
        {
            player.getInventory().placeItemBackInInventory(pillar.getStack(), Prediction.SERVER_ONLY);
            pillar.setStack(ItemStack.EMPTY);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, @Nullable Orientation orientation, boolean notify)
    {
        super.neighborChanged(state, world, pos, block, orientation, notify);

        var tile = world.getBlockEntity(pos, VoidHeartTiles.VOID_PILLAR);
        if (tile.isEmpty())
            return;

        // The neighbor source is no longer given, the item is ejected as soon as the block above is occupied
        if (!tile.get().getStack().isEmpty() && !world.isEmptyBlock(pos.above()))
        {
            Containers.dropItemStack(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.get().getStack());
            tile.get().setStack(ItemStack.EMPTY);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidPillarTile(pos, state);
    }
}
