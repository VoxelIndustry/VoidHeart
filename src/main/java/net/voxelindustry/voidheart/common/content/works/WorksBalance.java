package net.voxelindustry.voidheart.common.content.works;

/**
 * Numbers of the automated stations, guesses until tuned from play.
 */
public final class WorksBalance
{
    /** Void energy an Officiant draws for each item it lays out or takes back. */
    public static final long ITEM_COST = 200;

    /** The most of an item one order or keep level asks for, so a plan stays quick to work out. */
    public static final long MAX_ORDER = 4096;

    /**
     * How far past its highest order a queue saved before its counter was kept starts counting, so a claim an Officiant saved for an order since
     * cancelled never matches a new one.
     */
    public static final int LEGACY_ID_GAP = 1_000;

    private WorksBalance()
    {
    }
}
