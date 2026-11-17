package net.voxelindustry.voidheart.common.content.blight;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeState;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What a slain flesh mass guarded. Used, it shows a few designs the player does not know, and spends itself teaching the one they pick.
 */
public class BlessedContraptionItem extends Item
{
    public BlessedContraptionItem(Properties properties)
    {
        super(properties.rarity(Rarity.EPIC).stacksTo(16));
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand)
    {
        if (!(player instanceof ServerPlayer serverPlayer))
            return InteractionResult.SUCCESS;

        var stack = player.getItemInHand(hand);
        var server = serverPlayer.level().getServer();
        var tree = Knowledge.tree(server.registryAccess());
        var known = Knowledge.of(server, serverPlayer.getUUID()).getBlueprints();
        var shown = Blueprints.offer(stack.getOrDefault(VoidHeartDataComponents.BLUEPRINT_DRAW, List.of()), Blueprints.pool(tree), known,
                new Random(serverPlayer.getRandom().nextLong()));
        if (shown.isEmpty())
        {
            serverPlayer.sendOverlayMessage(Component.translatable(MODID + ".blessed_contraption.exhausted"));
            return InteractionResult.FAIL;
        }

        stack.set(VoidHeartDataComponents.BLUEPRINT_DRAW, shown);
        var designs = shown.stream()
                .map(blueprint -> new BlueprintPayloads.Design(blueprint, Blueprints.researchOf(blueprint, tree).map(entry -> entry.getValue().icon()).orElse(Identifier.withDefaultNamespace("paper"))))
                .toList();
        ServerPlayNetworking.send(serverPlayer, new BlueprintPayloads.Offer(hand, designs));
        world.playSound(null, serverPlayer.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, 0.5F);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Teaches the picked design if the contraption still in that hand offers it, and spends the contraption.
     */
    public static void choose(ServerPlayer player, InteractionHand hand, Identifier blueprint)
    {
        var stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlessedContraptionItem) || !stack.getOrDefault(VoidHeartDataComponents.BLUEPRINT_DRAW, List.of()).contains(blueprint))
            return;

        var server = player.level().getServer();
        var tree = Knowledge.tree(server.registryAccess());
        if (!Blueprints.pool(tree).contains(blueprint) || !learn(player, blueprint))
            return;

        stack.consume(1, player);
        player.sendOverlayMessage(Component.translatable(MODID + ".blessed_contraption.learned", Component.translatable(Blueprints.nameKey(blueprint))));
        player.level().playSound(null, player.blockPosition(), SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5F, 0.4F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 0.5F);
    }

    /** @return whether the player did not know it yet */
    public static boolean learn(ServerPlayer player, Identifier blueprint)
    {
        var server = player.level().getServer();
        if (!Knowledge.of(server, player.getUUID()).learnBlueprint(blueprint))
            return false;
        KnowledgeState.get(server).setDirty();
        Knowledge.sendSnapshot(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable(MODID + ".blessed_contraption.lore").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable(MODID + ".blessed_contraption.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
