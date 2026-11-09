package net.voxelindustry.voidheart.common.energy.ley;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W4 Standing stones and wards, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class LeyStoneRei
{
    private LeyStoneRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(LeyStoneContent.LEY_STONE, "ley_sense",
                line("ley_stone.spur", LeyStoneBalance.STONE_REACH),
                line("ley_stone.supply", LeyStoneBalance.STONE_SUPPLY_RADIUS),
                line("ley_stone.share"));
        page(LeyStoneContent.LEY_WARD, "ley_sense",
                line("ley_ward.slow", LeyStoneBalance.WARD_RADIUS, Math.round((1 - LeyStoneBalance.WARD_SLOW) * 100)),
                line("ley_ward.weaken", LeyStoneBalance.WARD_NEED));
        int side = LeyStoneBalance.GROWER_RADIUS * 2 + 1;
        page(LeyStoneContent.LEY_GROWER, "ley_sense",
                line("ley_grower.grow", side, side, Math.round(1 + LeyStoneBalance.GROWER_BOOST)),
                line("ley_grower.supply", LeyStoneBalance.GROWER_NEED));
    }
}
