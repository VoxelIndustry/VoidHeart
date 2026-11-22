package net.voxelindustry.voidheart.common.content.works;

/**
 * Numbers of the automated stations, guesses until tuned from play.
 */
public final class WorksBalance
{
    /** Void energy an Officiant draws for each item it lays out or takes back. */
    public static final long ITEM_COST = 200;

    /**
     * What an Officiant stores between crafts: the most items one craft lays out at once, an altar's catalyst and eight pillars. A pylon passes far
     * less in a tick than a craft costs, so the Officiant gathers it over time.
     */
    public static final long MAX_CHARGE = 9 * ITEM_COST;

    /** The most of an item one order or keep level asks for, so a plan stays quick to work out. */
    public static final long MAX_ORDER = 4096;

    private WorksBalance()
    {
    }
}
