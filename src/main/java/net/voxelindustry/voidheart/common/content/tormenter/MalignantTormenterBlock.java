package net.voxelindustry.voidheart.common.content.tormenter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A mass of flesh torturing the souls it was fed. Once sated, it yields what its mob drops without one ever spawning.
 */
public class MalignantTormenterBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = box(1, 0, 1, 15, 16, 15);

    public MalignantTormenterBlock(Properties settings)
    {
        super(settings);
    }

    /**
     * Keeps the souls on the dropped item, an empty tormenter drops a plain item so it stacks.
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder)
    {
        return List.of(itemOf(builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)));
    }

    /**
     * What a placed tormenter is as an item, with the souls of the block entity if it is one.
     */
    public static ItemStack itemOf(@Nullable BlockEntity tile)
    {
        var stack = new ItemStack(VoidHeartBlocks.MALIGNANT_TORMENTER);
        if (tile instanceof MalignantTormenterTile tormenter)
        {
            var souls = tormenter.souls();
            if (souls != null)
                stack.set(VoidHeartDataComponents.TORMENTER_SOULS, souls);
        }
        return stack;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        return itemOf(level.getBlockEntity(pos));
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(world, pos, state, placer, stack);

        var souls = stack.get(VoidHeartDataComponents.TORMENTER_SOULS);
        if (souls != null && world.getBlockEntity(pos) instanceof MalignantTormenterTile tormenter)
        {
            tormenter.restoreSouls(souls);
            if (souls.isComplete() && !world.isClientSide())
                placeParts(world, pos);
        }
    }

    private static void placeParts(Level world, BlockPos pos)
    {
        var part = (MalignantTormenterPartBlock) VoidHeartBlocks.MALIGNANT_TORMENTER_PART;
        for (var cell : TormenterDropletShape.CELLS)
        {
            var at = pos.offset(cell.x(), cell.y(), cell.z());
            if (world.getBlockState(at).canBeReplaced())
                world.setBlockAndUpdate(at, part.stateFor(cell));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moved)
    {
        for (var cell : TormenterDropletShape.CELLS)
        {
            var at = pos.offset(cell.x(), cell.y(), cell.z());
            var there = level.getBlockState(at);
            if (there.is(VoidHeartBlocks.MALIGNANT_TORMENTER_PART) && MalignantTormenterPartBlock.tormenterOf(at, there).equals(pos))
                level.removeBlock(at, false);
        }

        super.affectNeighborsAfterRemoval(state, level, pos, moved);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    /**
     * Drawn whole by its renderer, since its surface never holds still.
     */
    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new MalignantTormenterTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        if (world.isClientSide())
            return null;
        return createTickerHelper(type, VoidHeartTiles.MALIGNANT_TORMENTER,
                (level, pos, blockState, tormenter) -> tormenter.tickServer((ServerLevel) level));
    }
}
