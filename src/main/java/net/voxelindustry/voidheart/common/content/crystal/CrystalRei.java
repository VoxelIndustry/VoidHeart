package net.voxelindustry.voidheart.common.content.crystal;

import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import java.util.ArrayList;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for crystallography, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class CrystalRei
{
    private CrystalRei()
    {
    }

    public static void registerDisplays()
    {
        page(CrystalContent.CRYSTAL_CRADLE, "crystal_cradle",
                line("crystal_cradle.grow", CradleGrowth.MAX_WORTH, CradleBalance.LEY_NEED, CradleBalance.TICKS_PER_WORTH / 20 / 60),
                line("crystal_cradle.clear"),
                line("crystal_cradle.seep"),
                line("crystal_cradle.scour", (int) CradleBalance.TAINT_PER_WORTH),
                line("crystal_cradle.fed", Math.round(CradleBalance.ESSENCE_PER_TICK * CradleBalance.TICKS_PER_WORTH)),
                line("crystal_cradle.void", CradleBalance.VOID_NEED));

        // Each habit made so far on the bundles that carry it, saying where it comes from and what it does
        for (var kind : CrystalKind.values())
        {
            for (var habit : new CrystalHabit[] { CrystalHabit.FOULED, CrystalHabit.SCOURED, CrystalHabit.SEARING, CrystalHabit.STILL, CrystalHabit.WRUNG, CrystalHabit.DEEP })
            {
                if (!habit.takenBy(kind))
                    continue;
                for (var size : BundleSize.values())
                {
                    var stack = CrystalBundles.bundleItem(kind, size, habit);
                    var lines = new ArrayList<Component>();
                    lines.add(line("habit." + habit.getSerializedName() + ".source"));
                    HabitEffects.of(habit, kind).forEach(effect -> lines.add(effect.onItem()));
                    if (kind == CrystalKind.MEMORIAM && habit == CrystalHabit.SCOURED)
                        lines.add(line("habit.scoured.memory"));
                    if (kind == CrystalKind.MEMORIAM && habit == CrystalHabit.DEEP)
                        lines.add(line("habit.deep.memory", CradleBalance.RECORD_RADIUS));
                    if (kind == CrystalKind.RUBRUM && HabitShapedRecipe.CARRIED.contains(habit))
                        lines.add(line("habit.cell"));
                    BuiltinClientPlugin.getInstance().registerInformation(EntryStacks.of(stack), stack.getHoverName(), list ->
                    {
                        list.addAll(lines);
                        return list;
                    });
                }
            }
        }

        // The cells that carry a habit, which act as their bundle says
        for (var habit : HabitShapedRecipe.CARRIED.stream().sorted().toList())
        {
            var cell = CrystalBundles.withHabit(new ItemStack(RubrumContent.RUBRUM_CELL), habit);
            var lines = new ArrayList<Component>();
            lines.add(line("habit.cell"));
            HabitEffects.of(habit, CrystalKind.RUBRUM).forEach(effect -> lines.add(effect.onBlock()));
            BuiltinClientPlugin.getInstance().registerInformation(EntryStacks.of(cell), HabitEffects.name(habit).append(" ").append(cell.getHoverName()), list ->
            {
                list.addAll(lines);
                return list;
            });
        }
    }
}
