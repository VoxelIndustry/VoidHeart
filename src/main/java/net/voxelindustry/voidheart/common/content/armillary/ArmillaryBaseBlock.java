package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import java.util.List;
import java.util.Map;

/**
 * What a block of the base becomes while the armillary is formed: one piece of the sculpted base, standing in for the block it was and dropping that block when broken.
 * The facing points away from the core. A corner also reaches one step clockwise of it.
 */
public class ArmillaryBaseBlock extends Block
{
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final Block                      original;
    private final boolean                    corner;
    private final Map<Direction, VoxelShape> shapes;

    public ArmillaryBaseBlock(Properties properties, Block original, boolean corner, VoxelShape northShape)
    {
        super(properties);
        this.original = original;
        this.corner = corner;
        shapes = Shapes.rotateHorizontal(northShape);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    /**
     * Where the core of the armillary this base belongs to stands.
     */
    public BlockPos corePos(BlockState state, BlockPos pos)
    {
        var facing = state.getValue(FACING);
        var core = pos.relative(facing.getOpposite());
        return corner ? core.relative(facing.getCounterClockWise()) : core;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer && world.getBlockEntity(corePos(state, pos)) instanceof ArmillaryTile armillary)
            armillary.use(serverPlayer, false);
        return InteractionResult.SUCCESS;
    }

    /**
     * The armillary puts these back itself. This is for a core taken away without it knowing, by a command that skips block updates for example.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        var core = corePos(state, pos);
        if (level.isLoaded(core) && !level.getBlockState(core).is(VoidHeartBlocks.ARMILLARY_CORE))
            level.setBlockAndUpdate(pos, original.defaultBlockState());
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params)
    {
        return original.defaultBlockState().getDrops(params);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        return new ItemStack(original);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context)
    {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING);
    }
}
