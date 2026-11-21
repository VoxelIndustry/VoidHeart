package net.voxelindustry.voidheart.common.content.crystal;

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
                line("crystal_cradle.clear"));
    }
}
