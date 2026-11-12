package net.voxelindustry.voidheart.common.world.underway;

/**
 * A box of one material drawn once a fort's rooms are hollowed, in its frame.
 *
 * @param over the only material it replaces, or null to replace everything
 */
public record UnderwayStroke(UnderwayMaterial material, UnderwayMaterial over, int a0, int y0, int c0, int a1, int y1, int c1)
{
}
