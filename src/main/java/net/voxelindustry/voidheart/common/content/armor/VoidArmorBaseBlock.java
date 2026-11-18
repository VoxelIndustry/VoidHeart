package net.voxelindustry.voidheart.common.content.armor;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Where a frame stands between sorties. A frame used on an empty base docks there, and a docked one opens its grids and racks.
 * <p>
 * Until stepping in is built, a plain use opens the grids too; it will step into the frame then, and sneaking will open them.
 */
public class VoidArmorBaseBlock extends Block implements EntityBlock
{
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");

    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 4, 15), box(3, 4, 3, 13, 6, 13));

    public VoidArmorBaseBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(DOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(DOCKED);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof VoidArmorBaseTile base))
            return InteractionResult.PASS;
        if (stack.getItem() instanceof VoidArmorItem && !base.hasFrame())
        {
            if (!level.isClientSide())
            {
                base.setFrame(stack.consumeAndReturn(1, player));
                level.setBlockAndUpdate(pos, state.setValue(DOCKED, true));
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 1, 0.8F);
            }
            return InteractionResult.SUCCESS;
        }
        return base.hasFrame() ? open(level, pos, player) : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof VoidArmorBaseTile base) || !base.hasFrame())
            return InteractionResult.PASS;
        return open(level, pos, player);
    }

    private static InteractionResult open(Level level, BlockPos pos, Player player)
    {
        if (player instanceof ServerPlayer serverPlayer)
            serverPlayer.openMenu(new ExtendedMenuProvider<BlockPos>()
            {
                @Override
                public BlockPos getScreenOpeningData(ServerPlayer player)
                {
                    return pos;
                }

                @Override
                public Component getDisplayName()
                {
                    return Component.translatable("item.voidheart.void_armor");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
                {
                    return new VoidArmorMenu(containerId, inventory, pos);
                }
            });
        return InteractionResult.SUCCESS;
    }

    /**
     * A base holding a frame can't be mined, since the frame travels only worn. A creative break still removes it.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos)
    {
        if (state.getValue(DOCKED) && !player.isCreative())
            return 0;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidArmorBaseTile(pos, state);
    }
}
