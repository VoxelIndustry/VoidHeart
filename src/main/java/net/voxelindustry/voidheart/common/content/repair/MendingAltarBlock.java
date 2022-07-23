package net.voxelindustry.voidheart.common.content.repair;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.block.BlockWithEntity.checkType;

public class MendingAltarBlock extends Block implements BlockEntityProvider
{
    public MendingAltarBlock(Settings settings)
    {
        super(settings);

        this.setDefaultState(getStateManager().getDefaultState()
                .with(Properties.LIT, false));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit)
    {
        if (!player.getStackInHand(hand).isDamageable())
            return ActionResult.PASS;

        var altarOpt = world.getBlockEntity(pos, VoidHeartTiles.MENDING_ALTAR);

        if (altarOpt.isEmpty())
            return ActionResult.SUCCESS;

        var altar = altarOpt.get();
        if (altar.getTool().isEmpty())
        {
            altar.setTool(player, ItemUtils.copyWithSize(player.getStackInHand(hand), 1));

            if (!player.isCreative())
                player.getStackInHand(hand).decrement(1);
        }
        else
        {
            player.getInventory().offerOrDrop(altar.getTool());
            altar.setTool(player, ItemStack.EMPTY);
        }

        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state)
    {
        return new MendingAltarTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type)
    {
        return checkType(type, VoidHeartTiles.MENDING_ALTAR, MendingAltarTile::tick);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder)
    {
        builder.add(Properties.LIT);
    }
}
