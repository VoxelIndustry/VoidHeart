package net.voxelindustry.voidheart.common.energy.fissure;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.plugin.client.BuiltinClientPlugin;
import net.minecraft.network.chat.Component;

/**
 * REI hand-off for W9 Void Fissure, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class VoidFissureRei
{
    private VoidFissureRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    /**
     * The fissure has no item of its own, so the seed that opens it gets a page of what it does.
     */
    public static void registerDisplays(DisplayRegistry registry)
    {
        var widest = VoidFissureBalance.OUTPUT.length - 1;
        BuiltinClientPlugin.getInstance().registerInformation(EntryStacks.of(VoidFissureContent.FISSURE_SEED),
                Component.translatable("block.voidheart.void_fissure"), lines ->
                {
                    lines.add(Component.translatable("voidheart.rei.fissure_seed.open", VoidFissureBalance.OUTPUT[0]));
                    lines.add(Component.translatable("voidheart.rei.fissure_seed.widen", VoidFissureBalance.OUTPUT[widest]));
                    lines.add(Component.translatable("voidheart.rei.fissure_seed.seep", Math.round(VoidFissureBalance.SEEP_RADIUS[widest])));
                    lines.add(Component.translatable("voidheart.rei.fissure_seed.close", VoidFissureBalance.CLOSE_COST_PER_WIDTH));
                    lines.add(Component.translatable("voidheart.research.locked", Component.translatable("research.voidheart.void_fissure")));
                    return lines;
                });
    }
}
