package net.voxelindustry.voidheart.common.content.essence;

/**
 * Something relays route essence into, as long as it has room for an aspect: a store that holds it for later, or a machine that uses it up.
 * Stores also give it back: relays feed machines from them before drawing on collectors, and the Shatter Forge draws on them when its collectors
 * fall short.
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

    /**
     * @return whether it holds essence for the network, so relays feed machines from it and never fill one store from another
     */
    default boolean isStore()
    {
        return false;
    }

    /**
     * @return whether relays link to it, false for a block that only takes part in a receiver standing elsewhere
     */
    default boolean linkable()
    {
        return true;
    }
}
