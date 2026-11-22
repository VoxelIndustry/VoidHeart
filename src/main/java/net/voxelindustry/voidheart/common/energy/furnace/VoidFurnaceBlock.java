package net.voxelindustry.voidheart.common.energy.furnace;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * A block of dark voidstone with a mouth on its front. Used, it opens like a furnace with no fuel slot: it burns void energy from the field around it,
 * and the Fiery that relays bring it. Hoppers fill it from the top and sides and empty it from below.
 */
public class VoidFurnaceBlock extends Block implements EntityBlock
{
    public enum Flame implements StringRepresentable
    {
        /** Cold: nothing to smelt, or no void energy reaching it. */
        NONE,
        /** Smelting on void energy alone. */
        VOID,
        /** Smelting an item it burned Fiery for. */
        FIERY;

        @Override
        public String getSerializedName()
        {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Flame>     FLAME  = EnumProperty.create("flame", Flame.class);

    public VoidFurnaceBlock(Properties properties)
    {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(FACING, Direction.NORTH).setValue(FLAME, Flame.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, FLAME);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level.getBlockEntity(pos) instanceof VoidFurnaceTile))
            return InteractionResult.PASS;
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
                    return state.getBlock().getName();
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
                {
                    return new VoidFurnaceMenu(containerId, inventory, pos);
                }
            });
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state)
    {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction)
    {
        return level.getBlockEntity(pos) instanceof VoidFurnaceTile furnace ? AbstractContainerMenu.getRedstoneSignalFromContainer(furnace) : 0;
    }

    /**
     * A few motes rising from the mouth while it burns, violet on void energy and embers on Fiery, with the crackle of a furnace now and then.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        var flame = state.getValue(FLAME);
        if (flame == Flame.NONE)
            return;
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        if (random.nextDouble() < 0.1)
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, flame == Flame.FIERY ? 1.1F : 0.7F, false);

        var facing = state.getValue(FACING);
        double along = random.nextDouble() * 0.6 - 0.3;
        double out = 0.52;
        double px = x + facing.getStepX() * out + (facing.getAxis() == Direction.Axis.Z ? along : 0);
        double pz = z + facing.getStepZ() * out + (facing.getAxis() == Direction.Axis.X ? along : 0);
        double py = y + random.nextDouble() * 6 / 16D + 0.15;
        level.addParticle(flame == Flame.FIERY ? ParticleTypes.SMALL_FLAME : ParticleTypes.REVERSE_PORTAL, px, py, pz, 0, 0.01, 0);
        if (random.nextDouble() < 0.3)
            level.addParticle(ParticleTypes.SMOKE, px, py, pz, 0, 0, 0);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new VoidFurnaceTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, VoidFurnaceContent.VOID_FURNACE_TILE, VoidFurnaceTile::tick);
    }
}
