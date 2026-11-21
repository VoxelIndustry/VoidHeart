package net.voxelindustry.voidheart.client.render.crystal;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.HabitEffects;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A crystal's habit under its name in its color, then one plain line per change: where it acts and what it does there. A shard says what it regrows as.
 */
public final class CrystalHabitTooltip
{
    private CrystalHabitTooltip()
    {
    }

    public static void register()
    {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> append(stack, lines));
    }

    private static void append(ItemStack stack, List<Component> lines)
    {
        if (!stack.has(VoidHeartDataComponents.CRYSTAL_HABIT))
            return;
        var habit = CrystalBundles.habitOf(stack);
        if (habit == CrystalHabit.PLAIN)
        {
            lines.add(Component.translatable(habit.translationKey()).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!habit.isHabit())
            return;

        lines.add(HabitEffects.name(habit));
        var bundle = CrystalBundles.bundleOf(stack);
        if (bundle == null)
        {
            lines.add(Component.translatable(MODID + ".habit.regrows", HabitEffects.name(habit)).withStyle(ChatFormatting.GRAY));
            return;
        }
        for (var effect : HabitEffects.of(habit, CrystalBundles.kindOfBundle(bundle)))
            lines.add(effect.onItem());
    }
}
