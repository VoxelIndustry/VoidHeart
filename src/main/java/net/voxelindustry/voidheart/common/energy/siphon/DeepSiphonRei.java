package net.voxelindustry.voidheart.common.energy.siphon;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergyBalance;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W11 Deep siphon, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class DeepSiphonRei
{
    private DeepSiphonRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(DeepSiphonContent.DEEP_SIPHON, "deep_siphon",
                line("deep_siphon.draw", DeepSiphonBalance.OUTPUT),
                line("deep_siphon.hold", MachineEnergyBalance.DAEDALUS_HOLD),
                line("deep_siphon.notice"));
    }
}
