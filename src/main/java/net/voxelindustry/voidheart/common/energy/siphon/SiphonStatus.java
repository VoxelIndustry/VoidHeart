package net.voxelindustry.voidheart.common.energy.siphon;

import java.util.Locale;

/**
 * Why a siphon draws or doesn't, for Jade and for its look: only {@link #DRAWING} lights its threads and crown.
 */
public enum SiphonStatus
{
    /** Not standing on a bridge of the Daedalus. */
    OFF_BRIDGE,
    /** No armillary holds a way onto its bridge. */
    NO_LINK,
    /** Its way is severed and closing. */
    SEVERED,
    /** The armillary holding its way isn't loaded. */
    UNANSWERED,
    /** The hold is paid ahead and nothing around the armillary takes more. */
    FULL,
    DRAWING;

    public String key()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SiphonStatus byOrdinal(int ordinal)
    {
        var values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : OFF_BRIDGE;
    }
}
