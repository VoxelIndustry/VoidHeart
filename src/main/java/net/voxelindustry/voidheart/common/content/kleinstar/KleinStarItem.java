package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class KleinStarItem extends Item
{
    private static final int BAR_COLOR = 0xFFE9A8;

    public KleinStarItem(Properties properties)
    {
        super(properties.rarity(Rarity.EPIC).stacksTo(1));
    }

    public static int charge(ItemStack stack)
    {
        return Mth.clamp(stack.getOrDefault(VoidHeartDataComponents.CONTRAVOID_CHARGE, 0), 0, KleinStarBalance.CAPACITY);
    }

    public static void setCharge(ItemStack stack, int charge)
    {
        stack.set(VoidHeartDataComponents.CONTRAVOID_CHARGE, Mth.clamp(charge, 0, KleinStarBalance.CAPACITY));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".klein_star.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".klein_star.charge", charge(stack), KleinStarBalance.CAPACITY).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable(MODID + ".klein_star.use", KleinStarSettings.shownCost()).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack)
    {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack)
    {
        return Math.round(13F * charge(stack) / KleinStarBalance.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack)
    {
        return BAR_COLOR;
    }

    /**
     * Used on a Ravenous Storage, it drinks the Contravoid in it.
     */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        var level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof RavenousStorageTile storage))
            return InteractionResult.PASS;

        var stack = context.getItemInHand();
        int wanted = Math.min(KleinStarBalance.CAPACITY - charge(stack), storage.amountOf(EssenceRegistry.CONTRAVOID));
        if (wanted <= 0)
            return InteractionResult.PASS;

        if (!level.isClientSide())
        {
            setCharge(stack, charge(stack) + storage.extract(EssenceRegistry.CONTRAVOID, wanted));
            level.playSound(null, context.getClickedPos(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1, 0.6F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        var stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack))
            return InteractionResult.PASS;

        // On a client, by what its server said a star costs, so the staff answers a click the same way on both sides
        int cost = player.hasInfiniteMaterials() ? 0 : KleinStarSettings.cost(level);
        if (charge(stack) < cost)
        {
            if (level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".klein_star.empty", cost));
            return InteractionResult.FAIL;
        }

        if (level instanceof ServerLevel serverLevel)
        {
            boolean right = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.RIGHT);
            var star = new KleinStar(level, player, right);
            serverLevel.addFreshEntity(star);
            setCharge(stack, charge(stack) - cost);
            player.getCooldowns().addCooldown(stack, Math.round(VoidHeartConfig.get().kleinStarCooldownSeconds * 20));
            level.playSound(null, star.getX(), star.getY(), star.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 1.8F);
        }
        return InteractionResult.SUCCESS;
    }
}
