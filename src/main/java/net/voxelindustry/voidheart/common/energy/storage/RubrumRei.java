package net.voxelindustry.voidheart.common.energy.storage;

import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;

import static net.voxelindustry.voidheart.compat.rei.EnergyRei.amount;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.line;
import static net.voxelindustry.voidheart.compat.rei.EnergyRei.page;

/**
 * REI hand-off for W2 Rubrum storage, called through EnergyRei from the client plugin. Only loaded when REI is present.
 */
public final class RubrumRei
{
    private RubrumRei()
    {
    }

    public static void registerCategories(CategoryRegistry registry)
    {
    }

    public static void registerDisplays(DisplayRegistry registry)
    {
        page(RubrumContent.RUBRUM_CONSTRUCT_CORE, "void_energy",
                line("rubrum_construct_core.build"),
                line("rubrum_construct_core.bundles", amount(RubrumBalance.SMALL_RUBRUM_CAPACITY), amount(RubrumBalance.MEDIUM_RUBRUM_CAPACITY),
                        amount(RubrumBalance.LARGE_RUBRUM_CAPACITY), RubrumBalance.SMALL_AZURIUM_RATE, RubrumBalance.MEDIUM_AZURIUM_RATE,
                        RubrumBalance.LARGE_AZURIUM_RATE),
                line("rubrum_construct_core.field"),
                line("rubrum_construct_core.bud", Math.round(RubrumBalance.BUD_THRESHOLD * 100), RubrumBalance.BUD_MINUTES / 60));
        page(RubrumContent.RUBRUM_CONSTRUCT_FRAME, "void_energy",
                line("rubrum_construct_frame.edges"),
                line("rubrum_construct_frame.sockets"));
        page(RubrumContent.CELL_SOCKET, "void_energy",
                line("cell_socket.use", RubrumBalance.CELL_SOCKET_RATE));
        page(RubrumContent.RUBRUM_CELL, "void_energy",
                line("rubrum_cell.use", amount(RubrumBalance.CELL_CAPACITY)));
    }
}
