package net.voxelindustry.voidheart.common.content.essence;

/**
 * Destination relays route essence into. It keeps pulling as long as it has room for an aspect.
 */
public interface IEssenceReceiver
{
    /**
     * @return amount of this aspect the receiver can still take in
     */
    int spaceFor(EssenceType type);

    /**
     * @return amount actually taken in
     */
    int receive(EssenceType type, int amount);
}
