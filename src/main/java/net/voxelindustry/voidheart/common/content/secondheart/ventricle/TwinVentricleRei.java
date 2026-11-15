package net.voxelindustry.voidheart.common.content.secondheart.ventricle;

import net.voxelindustry.voidheart.common.content.secondheart.HeartRules;

import static net.voxelindustry.voidheart.common.content.secondheart.ventricle.VentricleBalance.*;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.amount;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI pages for the Twin Ventricle, called through EnergyRei. Its rite is no recipe, so the cradle's page lists the offering and what follows.
 * Only loaded when REI is present.
 */
public final class TwinVentricleRei
{
    private static final String RESEARCH = "second_heart";

    private TwinVentricleRei()
    {
    }

    public static void registerDisplays()
    {
        page(TwinVentricleContent.HEART_CRADLE, RESEARCH,
                line("heart_cradle.chamber"),
                line("heart_cradle.offering", KERNACIAN_CORES, CONTRAPTIONS, MEMORIAM_SHARDS, WRAITH_INKS, amount(EXPERIENCE)),
                line("heart_cradle.rite", ENERGY_PER_TICK, KINDLING_TICKS / 20, BEATING_TICKS / 20 / 60, WAVES),
                line("heart_cradle.heart", HeartRules.RECHARGE_TICKS / 20 / 60));
        page(TwinVentricleContent.VENTRICLE_BASIN, RESEARCH,
                line("ventricle_basin.fuel", amount(BASIN_TANK), KINDLING_FUEL_PER_TICK, BEATING_FUEL_PER_TICK));
        page(TwinVentricleContent.RUBRUM_VEINED_VOIDSTONE, RESEARCH, line("rubrum_veined_voidstone"));
        page(TwinVentricleContent.AZURIUM_VEINED_VOIDSTONE, RESEARCH, line("azurium_veined_voidstone"));
        page(TwinVentricleContent.RAVENOUS_GOLD_VALVE, RESEARCH, line("ravenous_gold_valve"));
    }
}
