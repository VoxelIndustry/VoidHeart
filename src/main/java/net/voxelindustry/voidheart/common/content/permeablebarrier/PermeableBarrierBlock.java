package net.voxelindustry.voidheart.common.content.permeablebarrier;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import org.jspecify.annotations.Nullable;

/**
 * Full block whose collision depends on the entity asking. Its look is drawn by a block entity renderer,
 * since it also differs for each viewer.
 */
public class PermeableBarrierBlock extends Block implements EntityBlock
{
    public PermeableBarrierBlock(Properties properties)
    {
        super(properties
                .noOcclusion()
                .isValidSpawn(VoidHeartBlocks::never)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, box) -> false)
                .strength(3F, 1200F)
                .sound(SoundType.AMETHYST));

        registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.POWERED, false));
    }

    public static @Nullable Entity getEntity(CollisionContext context)
    {
        return context instanceof EntityCollisionContext entityContext ? entityContext.getEntity() : null;
    }

    public static boolean isSolidFor(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity entity)
    {
        var tile = PermeableBarrierStructure.getTile(level, pos);
        return tile != null && tile.getConfig().isSolidFor(entity, state.getValue(BlockStateProperties.POWERED));
    }

    /**
     * Whether the barrier can be seen and targeted by this entity.
     */
    public static boolean isVisibleFor(BlockState state, BlockGetter level, BlockPos pos, @Nullable Entity entity)
    {
        var tile = PermeableBarrierStructure.getTile(level, pos);
        if (tile == null)
            return true;

        var config = tile.getConfig();
        if (!config.isConfigured() || !config.has(BarrierConfig.Flag.INVISIBLE_WHEN_OPEN))
            return true;
        if (entity instanceof Player player && config.isAlly(player))
            return true;
        return config.isSolidFor(entity, state.getValue(BlockStateProperties.POWERED));
    }

    /**
     * The block a configured barrier passes itself off as in particles and pick-block.
     */
    public static BlockState disguise(BlockGetter level, BlockPos pos, BlockState state)
    {
        if (!state.is(VoidHeartBlocks.PERMEABLE_BARRIER))
            return state;
        var tile = PermeableBarrierStructure.getTile(level, pos);
        return tile != null ? tile.getConfig().appearance().orElse(state) : state;
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData)
    {
        var disguise = disguise(level, pos, state);
        return disguise != state ? new ItemStack(disguise.getBlock()) : super.getCloneItemStack(level, pos, state, includeData);
    }

    @Override
    public void spawnDestroyByEntityParticles(Level level, @Nullable Entity entity, BlockPos pos, BlockState state)
    {
        super.spawnDestroyByEntityParticles(level, entity, pos, disguise(level, pos, state));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        if (!PermeableBarrierStructure.canExtend(context.getLevel(), context.getClickedPos(), context.getPlayer()))
            return null;
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack)
    {
        super.setPlacedBy(level, pos, state, placer, itemStack);

        if (level instanceof ServerLevel serverLevel)
            PermeableBarrierStructure.onBlockAdded(serverLevel, pos, placer != null ? placer.getUUID() : null);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult)
    {
        var tile = PermeableBarrierStructure.getTile(level, pos);
        if (tile == null || !tile.getConfig().isAlly(player))
            return InteractionResult.PASS;

        if (player instanceof ServerPlayer serverPlayer)
        {
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
                    return Component.translatable("block.voidheart.permeable_barrier");
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
                {
                    return new PermeableBarrierMenu(containerId, inventory, pos);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Owners break it like stone, everyone else as if it were obsidian.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos)
    {
        var tile = PermeableBarrierStructure.getTile(level, pos);
        if (tile != null && !tile.getConfig().isAlly(player))
            return Blocks.OBSIDIAN.defaultBlockState().getDestroyProgress(player, level, pos);
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston)
    {
        if (!level.isClientSide() && level.hasNeighborSignal(pos) != state.getValue(BlockStateProperties.POWERED))
            PermeableBarrierStructure.updatePower(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston)
    {
        if (!state.getValue(BlockStateProperties.POWERED))
            return;

        // The removed block may have been the one powering what remains of the structure
        for (var direction : Direction.values())
            PermeableBarrierStructure.updatePower(level, pos.relative(direction));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return isVisibleFor(state, level, pos, getEntity(context)) ? Shapes.block() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return isSolidFor(state, level, pos, getEntity(context)) ? Shapes.block() : Shapes.empty();
    }

    /**
     * Lets levers, buttons and torches attach to the barrier whatever its current state.
     */
    @Override
    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos)
    {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return Shapes.empty();
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos)
    {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state)
    {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type)
    {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(BlockStateProperties.POWERED);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new PermeableBarrierTile(pos, state);
    }
}
