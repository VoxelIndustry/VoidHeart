package net.voxelindustry.voidheart.common.ley.anchor;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

/**
 * A wrought anchor only stands within {@link WroughtAnchorBalance#LINE_REACH} blocks of a ley line. Only the server knows the lines, so the client
 * leaves placing to it.
 */
public class WroughtAnchorItem extends BlockItem
{
    public WroughtAnchorItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context)
    {
        if (!(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.SUCCESS;

        var anchors = OldAnchors.get(level);
        var pos = context.getClickedPos();
        if (anchors.isEmpty() || !anchors.get().wrought().canStandAt(pos))
        {
            if (context.getPlayer() != null)
                context.getPlayer().sendOverlayMessage(Component.translatable(anchors.isEmpty() ? "voidheart.wrought_anchor.no_ley" : "voidheart.wrought_anchor.far_from_line"));
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable("voidheart.wrought_anchor.lore"));
    }
}
