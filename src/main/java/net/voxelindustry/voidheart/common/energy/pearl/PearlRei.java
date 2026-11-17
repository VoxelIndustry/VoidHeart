package net.voxelindustry.voidheart.common.energy.pearl;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.amount;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for the Collapsing Rubrum Pearls, called through EnergyRei from the client plugin. Only loaded when REI is present. The kindling
 * shows on its own as a Shatter Forge recipe.
 */
public final class PearlRei
{
    private static final String RESEARCH = "rubrum_pearls";

    private PearlRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(PearlContent.FRACTAL_RUBRUM_SEED, RESEARCH,
                line("fractal_rubrum_seed.source"));
        page(PearlContent.KINDLED_FRACTAL_SEED, RESEARCH,
                line("kindled_fractal_seed.hang"),
                line("kindled_fractal_seed.feed", PearlBalance.VOID_DRAW, amount(Math.round(PearlBalance.FIERY_PER_TICK * 20 * 60))),
                line("kindled_fractal_seed.choir", PearlBalance.ALONE_HOURS, PearlBalance.MAX_CHOIR, (int) PearlBalance.REACH,
                        (int) (PearlBalance.ALONE_HOURS / PearlBalance.FULL_BOOST)),
                line("kindled_fractal_seed.take"));
        page(PearlContent.COLLAPSING_RUBRUM_PEARL, RESEARCH,
                line("collapsing_rubrum_pearl.use"));
    }
}
