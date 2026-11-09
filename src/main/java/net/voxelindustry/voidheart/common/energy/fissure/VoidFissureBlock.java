package net.voxelindustry.voidheart.common.energy.fissure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A crack torn in the ground into the void, or the wound a failed reactor leaves. It stands in the air block above the ground it cracks, with nothing to
 * collide with, and only Contravoid closes it: it can't be mined, blown up, pushed or washed away, and losing the ground under it changes nothing.
 * The crack itself is drawn by its renderer along its axis, wider than the block once widened.
 */
public class VoidFissureBlock extends Block implements EntityBlock
{
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    private final boolean wound;

    public VoidFissureBlock(boolean wound, Properties properties)
    {
        super(properties);
        this.wound = wound;
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    public boolean isWound()
    {
        return wound;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(AXIS);
    }

    /**
     * A seed only tears open on firm ground, and the crack runs across the placer's view.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var below = context.getClickedPos().below();
        if (!context.getLevel().getBlockState(below).isFaceSturdy(context.getLevel(), below, Direction.UP))
            return null;
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var size = bundleSize(stack);
        if (size == 0)
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (wound)
        {
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".void_fissure.wound_widen"));
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof VoidFissureTile fissure))
            return InteractionResult.PASS;

        var width = fissure.width();
        var needed = SeepMath.widenBundle(width);
        if (level.isClientSide())
            return needed == size && !fissure.isClosing() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;

        if (fissure.isClosing())
            player.sendOverlayMessage(Component.translatable(MODID + ".void_fissure.closing"));
        else if (needed == 0)
            player.sendOverlayMessage(Component.translatable(MODID + ".void_fissure.widest"));
        else if (needed != size)
            player.sendOverlayMessage(Component.translatable(MODID + ".void_fissure.needs", width + 1, bundle(needed).getName()));
        else
        {
            fissure.widen((ServerLevel) level);
            stack.consume(1, player);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.CONSUME;
    }

    /**
     * 1 for a small azurium bundle, 2 medium, 3 large, 0 for anything else.
     */
    static int bundleSize(ItemStack stack)
    {
        if (stack.is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE.asItem()))
            return 1;
        if (stack.is(VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE.asItem()))
            return 2;
        if (stack.is(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE.asItem()))
            return 3;
        return 0;
    }

    static Block bundle(int size)
    {
        return switch (size)
        {
            case 1 -> VoidHeartBlocks.SMALL_AZURIUM_BUNDLE;
            case 2 -> VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE;
            default -> VoidHeartBlocks.LARGE_AZURIUM_BUNDLE;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidFissureTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidFissureContent.VOID_FISSURE_TILE, VoidFissureTile::tick);
    }
}
