package net.voxelindustry.voidheart.common.content.essence;

/**
 * Storage relays route essence into, as long as it has room for an aspect. The Shatter Forge draws it back out through
 * the relays when its collectors fall short.
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

    int amountOf(EssenceType type);

    /**
     * @return amount actually taken out
     */
    int extract(EssenceType type, int amount);
}
