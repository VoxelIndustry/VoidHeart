package net.voxelindustry.voidheart.common.ley.anchor;

/**
 * An anchor generated at a well connected ley node. Everything here derives from the frozen lattice and the seed.
 */
public record OldAnchor(long nodeId, int x, int z, String name, boolean initiallyActive)
{
}
