package net.voxelindustry.voidheart.common.energy.siphon;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A voidstone and gold post crowned with azurium, set on a bridge of the Daedalus. It can only be placed on one, since nowhere else has anything below to
 * draw from. The client doesn't know the seed the bridges come from, so it only refuses outside the Daedalus and leaves the bridge to the server.
 */
public class DeepSiphonBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(3, 0, 3, 13, 3, 13),
            Block.box(4.5, 3, 4.5, 11.5, 13, 11.5),
            Block.box(5, 13, 5, 11, 22, 11));

    public DeepSiphonBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context)
    {
        var level = context.getLevel();
        if (level.dimension() != VoidDaedalus.WORLD_KEY)
        {
            refuse(context);
            return null;
        }
        if (level instanceof ServerLevel serverLevel)
        {
            var pos = context.getClickedPos();
            if (SiphonBridge.at(serverLevel.getSeed(), pos.getX(), pos.getY(), pos.getZ()) == null)
            {
                refuse(context);
                return null;
            }
        }
        return defaultBlockState();
    }

    private static void refuse(BlockPlaceContext context)
    {
        if (context.getPlayer() instanceof ServerPlayer player)
            player.sendOverlayMessage(Component.translatable(MODID + ".siphon.not_on_bridge"));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof DeepSiphonTile siphon)
            siphon.relinkFrom(player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new DeepSiphonTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, DeepSiphonContent.DEEP_SIPHON_TILE, DeepSiphonTile::tick);
    }
}
