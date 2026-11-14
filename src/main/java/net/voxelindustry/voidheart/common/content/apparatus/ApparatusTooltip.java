package net.voxelindustry.voidheart.common.content.apparatus;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * The grid of an apparatus drawn in its tooltip, so what is set and where shows without opening it.
 */
public record ApparatusTooltip(ApparatusGrid grid, int size) implements TooltipComponent
{
}
