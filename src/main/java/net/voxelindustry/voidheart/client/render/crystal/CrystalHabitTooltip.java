package net.voxelindustry.voidheart.client.render.crystal;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.energy.spike.LeySpikeContent;
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
        // A spike driven around a Fouled bundle is where the habit acts, so it says the change alone
        if (stack.is(LeySpikeContent.AZURIUM_SPIKE.asItem()))
        {
            HabitEffects.of(habit, CrystalKind.AZURIUM).forEach(effect -> lines.add(effect.onBlock()));
            return;
        }
        var bundle = CrystalBundles.bundleOf(stack);
        if (bundle == null)
        {
            if (CrystalBundles.kindOfShard(stack) != null)
                lines.add(Component.translatable(MODID + ".habit.regrows", HabitEffects.name(habit)).withStyle(ChatFormatting.GRAY));
            return;
        }
        var kind = CrystalBundles.kindOfBundle(bundle);
        for (var effect : HabitEffects.of(habit, kind))
            lines.add(effect.onItem());
        if (CrystalBundles.soakOf(stack) > 0)
            lines.add(Component.translatable(MODID + ".habit.scoured.soaked").withStyle(ChatFormatting.GRAY));
        if (kind == CrystalKind.MEMORIAM && habit == CrystalHabit.SCOURED)
        {
            var imprint = stack.get(VoidHeartDataComponents.CRYSTAL_IMPRINT);
            if (imprint == null)
                lines.add(Component.translatable(MODID + ".habit.scoured.blank").withStyle(ChatFormatting.WHITE));
            else
            {
                lines.add(Component.translatable(MODID + ".habit.scoured.remembers", imprint.getDescription().copy().withStyle(ChatFormatting.WHITE))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(new HabitEffects.Effect("ward", MODID + ".habit.scoured.keeps").onItem());
            }
        }
    }
}
