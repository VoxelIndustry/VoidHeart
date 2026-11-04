package net.voxelindustry.voidheart.common.content.tormenter;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Takes the souls of what its holder kills while it sits in the hotbar, and shows how far along it is.
 */
public class MalignantTormenterBlockItem extends BlockItem
{
    private static final int BAR_WIDTH = 13;
    private static final int BAR_COLOR = ARGB.color(255, 196, 42, 58);

    public MalignantTormenterBlockItem(Block block, Item.Properties properties)
    {
        super(block, properties);
    }

    /**
     * A sated one takes up the whole of its droplet, the others stay in their block.
     */
    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state)
    {
        if (!super.canPlace(context, state))
            return false;

        var souls = context.getItemInHand().get(VoidHeartDataComponents.TORMENTER_SOULS);
        if (souls == null || !souls.isComplete())
            return true;

        var level = context.getLevel();
        var pos = context.getClickedPos();
        var part = (MalignantTormenterPartBlock) VoidHeartBlocks.MALIGNANT_TORMENTER_PART;
        for (var cell : TormenterDropletShape.CELLS)
        {
            var at = pos.offset(cell.x(), cell.y(), cell.z());
            if (level.isInsideBuildHeight(at) && level.getBlockState(at).canBeReplaced(context) && level.isUnobstructed(part.stateFor(cell), at, CollisionContext.empty()))
                continue;

            if (context.getPlayer() instanceof ServerPlayer player)
                player.sendOverlayMessage(Component.translatable(MODID + ".tormenter.no_room"));
            return false;
        }
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        var souls = stack.get(VoidHeartDataComponents.TORMENTER_SOULS);
        return souls != null && !souls.isComplete();
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        var souls = stack.get(VoidHeartDataComponents.TORMENTER_SOULS);
        return souls == null ? 0 : Math.round((float) BAR_WIDTH * souls.souls() / TormenterSouls.REQUIRED);
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        var souls = stack.get(VoidHeartDataComponents.TORMENTER_SOULS);
        if (souls == null)
        {
            tooltip.accept(Component.translatable(MODID + ".tormenter.tooltip.empty", TormenterSouls.REQUIRED).withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.accept(souls.type().getDescription().copy().withStyle(ChatFormatting.RED));
        tooltip.accept(souls.isComplete()
                ? Component.translatable(MODID + ".tormenter.tooltip.sated").withStyle(ChatFormatting.GRAY)
                : Component.translatable(MODID + ".tormenter.tooltip.souls", souls.souls(), TormenterSouls.REQUIRED).withStyle(ChatFormatting.GRAY));
    }
}
