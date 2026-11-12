package net.voxelindustry.voidheart.common.world.underway;

/**
 * A prop standing in a fort, in its frame.
 *
 * @param faceAlong  the way its front looks, as a step along the frame
 * @param faceAcross the same, as a step across the frame
 */
public record UnderwayPropSpot(UnderwayProp prop, int along, int y, int across, int faceAlong, int faceAcross)
{
}
