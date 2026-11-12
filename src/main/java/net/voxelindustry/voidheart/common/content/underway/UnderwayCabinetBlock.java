package net.voxelindustry.voidheart.common.content.underway;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

/**
 * The breaker cabinet at the threshold of a section. A rubrum cell set in it powers the section until the cell runs dry, and wakes all of its security. The cell and its charge are kept by {@link UnderwaySecurity}, not by the block, which only shows them.
 */
public class UnderwayCabinetBlock extends UnderwayPropBlock
{
    public UnderwayCabinetBlock(Properties properties, UnderwayProp prop)
    {
        super(properties, prop);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit)
    {
        if (!stack.is(RubrumContent.RUBRUM_CELL))
            return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer visitor))
            return InteractionResult.SUCCESS;

        var security = UnderwaySecurity.of(server);
        var key = UnderwaySecurity.Key.at(pos);
        String refusal = null;
        if (security == null || key == null)
            refusal = "unwired";
        else if (security.hasCell(key))
            refusal = "busy";
        else if (RubrumCellItem.charge(stack) <= 0)
            refusal = "empty_cell";
        if (refusal != null)
        {
            visitor.sendOverlayMessage(Component.translatable("voidheart.underway_cabinet." + refusal));
            return InteractionResult.SUCCESS;
        }
        long charge = RubrumCellItem.charge(stack);
        stack.consume(1, player);
        security.insertCell(server, key, charge);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit)
    {
        if (!(level instanceof ServerLevel server))
            return InteractionResult.SUCCESS;
        var security = UnderwaySecurity.of(server);
        var key = UnderwaySecurity.Key.at(pos);
        if (security == null || key == null || !security.hasCell(key))
            return InteractionResult.PASS;

        var cell = new ItemStack(RubrumContent.RUBRUM_CELL);
        RubrumCellItem.setCharge(cell, security.removeCell(server, key));
        player.getInventory().placeItemBackInInventory(cell, Prediction.SERVER_ONLY);
        return InteractionResult.SUCCESS;
    }
}
