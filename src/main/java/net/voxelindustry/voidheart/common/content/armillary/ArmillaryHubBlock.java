package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The empty middle of the rings, there to be clicked: the rings have no block of their own, and a portal needs something to right click.
 */
public class ArmillaryHubBlock extends Block
{
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    private static final VoxelShape CLOSED_SHAPE = box(5, 5, 5, 11, 11, 11);
    private static final VoxelShape OPEN_SHAPE   = box(2, 2, 2, 14, 14, 14);
    private static final int        OPEN_LIGHT   = 9;

    public static int lightLevel(BlockState state)
    {
        return state.getValue(OPEN) ? OPEN_LIGHT : 0;
    }

    public ArmillaryHubBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(OPEN, false));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit)
    {
        return use(world, pos, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        return use(world, pos, player);
    }

    private static InteractionResult use(Level world, BlockPos pos, Player player)
    {
        if (player instanceof ServerPlayer serverPlayer && world.getBlockEntity(pos.below(ArmillaryLayout.HUB_HEIGHT)) instanceof ArmillaryTile armillary)
            armillary.use(serverPlayer, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return state.getValue(OPEN) ? OPEN_SHAPE : CLOSED_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(OPEN);
    }
}
