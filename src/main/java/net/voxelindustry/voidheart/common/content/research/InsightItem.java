package net.voxelindustry.voidheart.common.content.research;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Thought left behind in the void, found in its dungeons. Taking it in gives a few insights at once, how many only known once read.
 */
public class InsightItem extends Item
{
    private final int least;
    private final int most;

    public InsightItem(Properties properties, int least, int most)
    {
        super(properties);
        this.least = least;
        this.most = most;
    }

    public int least()
    {
        return least;
    }

    public int most()
    {
        return most;
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand)
    {
        var stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;

        int gained = least + serverPlayer.getRandom().nextInt(most - least + 1);
        var server = serverPlayer.level().getServer();
        Knowledge.of(server, serverPlayer.getUUID()).addInsights(gained);
        KnowledgeState.get(server).setDirty();
        if (serverPlayer.containerMenu instanceof KnowledgeRepositoryMenu)
            Knowledge.sendSnapshot(serverPlayer);

        serverPlayer.sendOverlayMessage(Component.translatable(MODID + (gained == 1 ? ".insight_loot.gained_one" : ".insight_loot.gained"), gained, stack.getHoverName()));
        world.playSound(null, serverPlayer.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 0.7F);
        stack.consume(1, serverPlayer);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".insight_loot.holds", least, most).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".insight_loot.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
