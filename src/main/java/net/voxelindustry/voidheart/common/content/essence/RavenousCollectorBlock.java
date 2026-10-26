package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import java.util.List;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RavenousCollectorBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.box(3 / 16D, 3 / 16D, 3 / 16D, 13 / 16D, 13 / 16D, 13 / 16D);

    public RavenousCollectorBlock(Properties settings)
    {
        super(settings);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack heldStack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        var stackInHand = player.getItemInHand(hand);
        if (stackInHand.isEmpty())
            return InteractionResult.PASS;

        var collector = ((RavenousCollectorTile) world.getBlockEntity(pos));

        if (collector == null)
            return InteractionResult.PASS;

        if (!collector.storage().isUnattuned())
            return InteractionResult.PASS;

        var essenceType = EssenceRegistry.getTypeFromCatalyst(stackInHand);

        if (essenceType == null)
            return InteractionResult.PASS;

        collector.storage().attune(essenceType);
        collector.setChanged();
        collector.sync();

        return InteractionResult.SUCCESS;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        var stack = new ItemStack(VoidHeartBlocks.RAVENOUS_COLLECTOR);
        stack.setDamageValue(RavenousCollectorTile.CAPACITY);

        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof RavenousCollectorTile collector
                && !collector.storage().isUnattuned())
        {
            stack.set(VoidHeartDataComponents.ESSENCE_STORAGE, RavenousCollectorTile.copyOf(collector.storage()));
            stack.setDamageValue(RavenousCollectorTile.CAPACITY - collector.storage().amount());
        }

        return List.of(stack);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        var storage = stack.get(VoidHeartDataComponents.ESSENCE_STORAGE);
        if (storage != null && world.getBlockEntity(pos) instanceof RavenousCollectorTile collector)
            collector.restoreStorage(storage);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new RavenousCollectorTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.RAVENOUS_COLLECTOR, RavenousCollectorTile::tick);
    }

    private static VoxelShape makeShape()
    {
        var shape = Shapes.empty();
        shape = Shapes.or(shape, Shapes.box(0.5, 0.25, 0.25, 0.75, 0.5, 0.5));
        shape = Shapes.or(shape, Shapes.box(0.5, 0.25, 0.5, 0.75, 0.5, 0.75));
        shape = Shapes.or(shape, Shapes.box(0.25, 0.25, 0.5, 0.5, 0.5, 0.75));
        shape = Shapes.or(shape, Shapes.box(0.25, 0.25, 0.25, 0.5, 0.5, 0.5));
        shape = Shapes.or(shape, Shapes.box(0.25, 0.5, 0.5, 0.5, 0.75, 0.75));
        shape = Shapes.or(shape, Shapes.box(0.5, 0.5, 0.5, 0.75, 0.75, 0.75));
        shape = Shapes.or(shape, Shapes.box(0.5, 0.5, 0.25, 0.75, 0.75, 0.5));
        shape = Shapes.or(shape, Shapes.box(0.25, 0.5, 0.25, 0.5, 0.75, 0.5));
        shape = Shapes.or(shape, Shapes.box(0.3125, 0.3125, 0.3125, 0.6875, 0.6875, 0.6875));

        return shape;
    }
}
