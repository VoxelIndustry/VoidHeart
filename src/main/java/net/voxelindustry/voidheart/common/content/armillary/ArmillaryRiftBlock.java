package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * The way back, generated with every dungeon near where players arrive, or placed on the bridge a connection opens onto in the Daedalus. It leads somewhere only while its connection holds.
 */
public class ArmillaryRiftBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = box(2, 2, 2, 14, 14, 14);

    public ArmillaryRiftBlock(Properties properties)
    {
        super(properties);
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
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;

        boolean left = world.getBlockEntity(pos) instanceof ArmillaryRiftTile rift && rift.getConnection() != ArmillaryRiftTile.NONE
                ? DaedalusConnections.leave(serverPlayer, rift.getConnection())
                : ArmillaryConnections.leave(serverPlayer);
        if (!left)
            serverPlayer.sendOverlayMessage(Component.translatable("voidheart.armillary.rift_dead"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return world.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.ARMILLARY_RIFT, ArmillaryRiftTile::tick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ArmillaryRiftTile(pos, state);
    }
}
