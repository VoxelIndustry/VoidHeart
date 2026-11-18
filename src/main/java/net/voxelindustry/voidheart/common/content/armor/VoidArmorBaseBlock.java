package net.voxelindustry.voidheart.common.content.armor;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * Where a frame stands between sorties and charges. A frame used on an empty base docks there. On a docked base, a use steps into the frame and sneaking
 * opens its grids. A rubrum cell used on it goes in its socket, for charging away from any pylon, and comes back out with an empty hand while no frame
 * stands there; with one docked, the cell is in the frame's screen.
 * <p>
 * The plinth is low, so a wearer standing on it still fits under a ceiling three blocks up.
 */
public class VoidArmorBaseBlock extends Block implements EntityBlock
{
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");

    /** Where a wearer's feet stand on the plinth, above the base's own position. */
    public static final double STAND_HEIGHT = 3 / 16D;

    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 2, 15), box(3, 2, 3, 13, 3, 13));

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

    public static Vec3 standAt(BlockPos pos)
    {
        return new Vec3(pos.getX() + 0.5, pos.getY() + STAND_HEIGHT, pos.getZ() + 0.5);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof VoidArmorBaseTile base))
            return InteractionResult.PASS;
        // An empty hand comes here first too; the empty hand use decides between stepping in and opening the grids
        if (stack.isEmpty())
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.getItem() instanceof VoidArmorItem && !base.hasFrame())
        {
            if (!level.isClientSide())
                base.dock(stack.consumeAndReturn(1, player));
            return InteractionResult.SUCCESS;
        }
        if (stack.is(RubrumContent.RUBRUM_CELL) && base.cell().getItem(0).isEmpty())
        {
            if (!level.isClientSide())
                base.cell().setItem(0, stack.consumeAndReturn(1, player));
            return InteractionResult.SUCCESS;
        }
        return base.hasFrame() ? stepIn(level, pos, player, base) : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof VoidArmorBaseTile base))
            return InteractionResult.PASS;
        if (!base.hasFrame())
        {
            var held = base.cell().getItem(0);
            if (held.isEmpty())
                return InteractionResult.PASS;
            if (!level.isClientSide())
            {
                player.getInventory().placeItemBackInInventory(held, Prediction.SERVER_ONLY);
                base.cell().setItem(0, ItemStack.EMPTY);
            }
            return InteractionResult.SUCCESS;
        }
        return player.isSecondaryUseActive() ? open(pos, player) : stepIn(level, pos, player, base);
    }

    private static InteractionResult stepIn(Level level, BlockPos pos, Player player, VoidArmorBaseTile base)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;
        var feet = standAt(pos);
        var refusal = ArmorWear.refusal(serverPlayer, base.frame(), feet);
        if (refusal != null)
        {
            serverPlayer.sendOverlayMessage(refusal);
            return InteractionResult.FAIL;
        }
        ArmorWear.stepIn(serverPlayer, base.undock(), feet);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult open(BlockPos pos, Player player)
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

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, VoidArmorContent.VOID_ARMOR_BASE_TILE, VoidArmorBaseTile::tick);
    }
}
