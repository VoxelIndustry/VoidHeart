package net.voxelindustry.voidheart.common.content.beam;

public enum FormationError
{
    NO_MONOLITH_AROUND,
    CONDUIT_BOTH_DIRECTION,
    TOO_FEW_MONOLITH,
    NO_PILLAR_AT_END,
    MULTIPLE_ITEM_SOURCES,
    INVALID_COLLECTOR_PLACEMENT,
    INVALID_FOCUS_ORIENTATION,
    BEAM_LOOP,
    MISSING_ASPECTS,
    NOT_ENOUGH_ESSENCE;

    public String translationKey()
    {
        return "voidheart.beam.error." + name().toLowerCase();
    }
}
