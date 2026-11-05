package net.voxelindustry.voidheart.common.content.armillary;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import static net.minecraft.world.level.block.BaseEntityBlock.createTickerHelper;

public class ArmillaryCoreBlock extends Block implements EntityBlock
{
    public ArmillaryCoreBlock(Properties properties)
    {
        super(properties
                .mapColor(MapColor.COLOR_BLACK)
                .requiresCorrectToolForDrops()
                .strength(3F)
                .sound(SoundType.METAL));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (player instanceof ServerPlayer serverPlayer && world.getBlockEntity(pos) instanceof ArmillaryTile armillary)
            armillary.use(serverPlayer, false);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved)
    {
        var hub = ArmillaryLayout.hub(pos);
        if (world.getBlockState(hub).is(VoidHeartBlocks.ARMILLARY_HUB))
            world.setBlockAndUpdate(hub, Blocks.AIR.defaultBlockState());
        super.affectNeighborsAfterRemoval(state, world, pos, moved);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new ArmillaryTile(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type)
    {
        return createTickerHelper(type, VoidHeartTiles.ARMILLARY, ArmillaryTile::tick);
    }
}
