package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.energy.storage.ConstructLayout.FramePart;

import java.util.EnumMap;
import java.util.Map;

/**
 * Edge block of a rubrum construct. Its core sets the part it shows once the whole frame stands, a beam hugging the
 * cube's edge, and using it acts on that core.
 */
public class ConstructFrameBlock extends Block
{
    public static final EnumProperty<FramePart> PART = EnumProperty.create("part", FramePart.class);

    private static final Map<FramePart, VoxelShape> SHAPES = new EnumMap<>(FramePart.class);

    static
    {
        for (var part : FramePart.values())
        {
            var shape = Shapes.empty();
            for (var box : FrameGeometry.boxes(part))
                shape = Shapes.or(shape, box(box.x0(), box.y0(), box.z0(), box.x1(), box.y1(), box.z1()));
            SHAPES.put(part, shape.optimize());
        }
    }

    public ConstructFrameBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(PART, FramePart.LOOSE));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPES.get(state.getValue(PART));
    }

    /**
     * A bud grows against any outer face, beam or not, so every face holds like a whole block's.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(PART);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (state.getValue(PART) == FramePart.LOOSE && stack.is(RubrumContent.RUBRUM_CONSTRUCT_CORE.asItem()))
            return ConstructInteraction.placeCore(level, pos, player, hand, stack);
        if (state.getValue(PART) == FramePart.LOOSE || ConstructBundles.bundleOf(stack) == null)
            return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        return ConstructInteraction.useBundle(level, stack, player, hit.getLocation(), ConstructInteraction.coreOf(level, pos));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (state.getValue(PART) == FramePart.LOOSE)
            return InteractionResult.PASS;
        return ConstructInteraction.useEmptyHand(level, player, hit.getLocation(), ConstructInteraction.coreOf(level, pos));
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (state.getValue(PART) == FramePart.LOOSE)
            return;
        var core = ConstructInteraction.coreOf(level, pos);
        if (core != null)
            core.requestCheck();
    }
}
