package net.voxelindustry.voidheart.common.energy.reactor;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import net.voxelindustry.voidheart.common.energy.fissure.VoidFissureBalance;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.amount;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W10 Contravoid reactor, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class ContravoidReactorRei
{
    private ContravoidReactorRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        var fullest = ReactorBalance.MAX_PAIRS * ReactorBalance.PAIR_ENERGY;
        var woundCost = Math.round(VoidFissureBalance.CLOSE_COST_PER_WIDTH * VoidFissureBalance.WOUND_SIZE);
        page(ContravoidReactorContent.CONTRAVOID_REACTOR_CORE, "contravoid_reactor",
                line("contravoid_reactor_core.burn", Math.round(fullest * ReactorBalance.STILLNESS_FACTOR), Math.round(fullest * ReactorBalance.FIERY_FACTOR),
                        Math.round(fullest * ReactorBalance.SUFFERING_FACTOR)),
                line("contravoid_reactor_core.light", amount(ReactorBalance.IGNITION), Math.round(ReactorBalance.MIN_BURN * 100)),
                line("contravoid_reactor_core.fail", ReactorBalance.COUNTDOWN / 20),
                line("contravoid_reactor_core.shutdown"),
                line("contravoid_reactor_core.wound", amount(woundCost)));
        page(ContravoidReactorContent.REACTOR_CONTAINMENT, "contravoid_reactor",
                line("reactor_containment.shell"),
                line("reactor_containment.breach"));
        page(ContravoidReactorContent.REACTOR_INTAKE, "contravoid_reactor",
                line("reactor_intake.fuel", amount(ReactorBalance.INTAKE_TANK)),
                line("reactor_intake.wound"));
    }
}
