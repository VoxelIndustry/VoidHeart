package net.voxelindustry.voidheart.common.content.crystal;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.voxelindustry.voidheart.common.content.mob.VoidMonster;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartTags;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Scoured memoriam is a blank memory: used on one of the void's creatures, it remembers that kind, and a Ley Ward it is set in keeps that kind out. The
 * great ones can't be held in a memory.
 */
public final class CrystalImprint
{
    private CrystalImprint()
    {
    }

    public static void register()
    {
        UseEntityCallback.EVENT.register(CrystalImprint::use);
    }

    public static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit)
    {
        var stack = player.getItemInHand(hand);
        if (!isBlank(stack))
            return InteractionResult.PASS;
        if (!canImprint(entity))
        {
            if (!level.isClientSide())
                player.sendOverlayMessage(Component.translatable(MODID + ".crystal.imprint.refused"));
            return InteractionResult.FAIL;
        }
        if (level.isClientSide())
            return InteractionResult.SUCCESS;

        var imprinted = stack.copyWithCount(1);
        imprinted.set(VoidHeartDataComponents.CRYSTAL_IMPRINT, entity.getType());
        stack.consume(1, player);
        if (stack.isEmpty())
            player.setItemInHand(hand, imprinted);
        else
            player.getInventory().placeItemBackInInventory(imprinted, Prediction.SERVER_ONLY);
        player.sendOverlayMessage(Component.translatable(MODID + ".crystal.imprint.done", entity.getType().getDescription()));
        level.playSound(null, entity.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /**
     * A Scoured memoriam bundle that remembers nothing yet.
     */
    public static boolean isBlank(ItemStack stack)
    {
        var bundle = CrystalBundles.bundleOf(stack);
        return bundle != null && CrystalBundles.kindOfBundle(bundle) == CrystalKind.MEMORIAM && CrystalBundles.habitOf(stack) == CrystalHabit.SCOURED
                && stack.get(VoidHeartDataComponents.CRYSTAL_IMPRINT) == null;
    }

    public static boolean canImprint(Entity entity)
    {
        return entity instanceof VoidMonster && entity.getType().builtInRegistryHolder().is(VoidHeartTags.MOBS) && !entity.getType().builtInRegistryHolder().is(VoidHeartTags.MOBS_BOSSES);
    }
}
