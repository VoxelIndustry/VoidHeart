package net.voxelindustry.voidheart.common.ley.anchor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

/**
 * An anchor a player builds beside a ley line, a block and a half tall. Using it opens the travel map, where it can be renamed.
 */
public class WroughtAnchorBlock extends Block implements EntityBlock
{
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(5, 2, 5, 11, 14, 11),
            Block.box(4, 14, 4, 12, 16, 12),
            Block.box(6, 16, 6, 10, 23, 10));

    public WroughtAnchorBlock(Properties properties)
    {
        super(properties.lightLevel(state -> 8));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        var tile = level.getBlockEntity(pos, VoidHeartTiles.WROUGHT_ANCHOR);
        var anchors = OldAnchors.get((ServerLevel) level);
        var anchor = tile.flatMap(found -> anchors.flatMap(service -> service.wrought().get(found.getAnchorId())));
        if (anchor.isEmpty())
            player.sendOverlayMessage(Component.translatable("voidheart.anchor.silent"));
        else
            anchors.get().wrought().use((ServerPlayer) player, anchor.get());
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(4) != 0)
            return;

        level.addParticle(ParticleTypes.PORTAL,
                pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 1.2);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new WroughtAnchorTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide() ? null : createTickerHelper(type, VoidHeartTiles.WROUGHT_ANCHOR, WroughtAnchorTile::tick);
    }
}
