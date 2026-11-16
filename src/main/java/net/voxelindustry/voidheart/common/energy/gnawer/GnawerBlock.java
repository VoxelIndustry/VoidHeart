package net.voxelindustry.voidheart.common.energy.gnawer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A squat core of Ravenous Gold with a jaw on top. A Gnawing Stake used on it starts marking a box, and sneaking with one drops the box unfinished. A
 * pickaxe used on it is lent to it, and sneaking with an empty hand takes it back. An empty hand switches between keeping what it eats and erasing it.
 */
public class GnawerBlock extends Block implements EntityBlock
{
    public static final BooleanProperty ERASE = BooleanProperty.create("erase");

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(2, 4, 2, 14, 11, 14),
            Block.box(1, 11, 1, 15, 14, 15));

    public GnawerBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(ERASE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(ERASE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof GnawerTile gnawer)
            gnawer.setOwner(player.getUUID(), player.getGameProfile().name());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof GnawerTile gnawer))
            return InteractionResult.PASS;

        if (stack.is(GnawerContent.GNAWING_STAKE.asItem()))
        {
            if (level instanceof ServerLevel serverLevel)
            {
                if (player.isShiftKeyDown())
                {
                    if (gnawer.box() != null)
                    {
                        gnawer.clear(serverLevel);
                        player.sendOverlayMessage(message("cleared"));
                    }
                }
                else if (GnawerTile.refusesToRun(level))
                    player.sendOverlayMessage(message("refused"));
                else if (gnawer.box() != null)
                    player.sendOverlayMessage(message("busy"));
                else
                {
                    stack.set(VoidHeartDataComponents.GNAWER_LINK, new GnawerLink(GlobalPos.of(level.dimension(), pos.immutable()), Optional.empty()));
                    player.sendOverlayMessage(message("marking"));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(ItemTags.PICKAXES))
        {
            if (!level.isClientSide())
            {
                var lent = stack.copyWithCount(1);
                stack.shrink(1);
                var held = gnawer.swapPickaxe(lent);
                if (!held.isEmpty() && !player.addItem(held))
                    player.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, held);
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.6F, 0.8F);
                player.sendOverlayMessage(message("lent", lent.getHoverName()));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof GnawerTile gnawer))
            return InteractionResult.PASS;

        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        if (player.isShiftKeyDown())
        {
            var held = gnawer.swapPickaxe(ItemStack.EMPTY);
            if (!held.isEmpty())
            {
                if (!player.addItem(held))
                    player.spawnAtLocation((net.minecraft.server.level.ServerLevel) level, held);
                player.sendOverlayMessage(message("returned"));
            }
            return InteractionResult.SUCCESS;
        }

        var erase = !state.getValue(ERASE);
        level.setBlock(pos, state.setValue(ERASE, erase), Block.UPDATE_ALL);
        level.playSound(null, pos, erase ? SoundEvents.SCULK_CLICKING : SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.7F, erase ? 0.6F : 0.9F);
        player.sendOverlayMessage(message(erase ? "erase" : "keep"));
        return InteractionResult.SUCCESS;
    }

    static Component message(String key, Object... args)
    {
        return Component.translatable(MODID + ".gnawer." + key, args);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new GnawerTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, GnawerContent.GNAWER_TILE, GnawerTile::tick);
    }
}
