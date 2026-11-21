package net.voxelindustry.voidheart.common.content.armor;

/**
 * How a frame running low keeps itself going: it cuts what it powers one piece at a time, in a fixed order, until it has nothing left to cut and goes limp.
 * The stage follows the charge alone, and a worn frame's charge only falls, so a module once cut stays cut until the frame charges on its base again.
 */
public final class LoadShedding
{
    public enum Stage
    {
        FULL,
        /** Everything still runs, but the frame lets its wearer know. */
        LOW,
        NO_SPECTRAL,
        NO_NIGHT_VISION,
        NO_EXOSKELETON,
        /** The socketed apparatus stops refilling its shield from the frame. */
        NO_REGEN,
        /** Empty: every module off, the joints locked. */
        LIMP;

        public boolean keeps(ArmorModule module)
        {
            return switch (module)
            {
                case SPECTRAL_LENS -> compareTo(NO_SPECTRAL) < 0;
                case NIGHT_VISION -> compareTo(NO_NIGHT_VISION) < 0;
                case EXOSKELETON_POWER -> compareTo(NO_EXOSKELETON) < 0;
                case BATTERY_CELL -> true;
            };
        }

        public boolean regen()
        {
            return compareTo(NO_REGEN) < 0;
        }

        public boolean limp()
        {
            return this == LIMP;
        }

        /**
         * Whether the frame has started cutting, or warning that it will.
         */
        public boolean low()
        {
            return this != FULL;
        }
    }

    private LoadShedding()
    {
    }

    public static Stage stage(long charge, long capacity)
    {
        if (charge <= 0)
            return Stage.LIMP;
        if (capacity <= 0)
            return Stage.FULL;
        double fill = charge / (double) capacity;
        if (fill < ArmorBalance.SHED_REGEN)
            return Stage.NO_REGEN;
        if (fill < ArmorBalance.SHED_EXOSKELETON)
            return Stage.NO_EXOSKELETON;
        if (fill < ArmorBalance.SHED_NIGHT_VISION)
            return Stage.NO_NIGHT_VISION;
        if (fill < ArmorBalance.SHED_SPECTRAL)
            return Stage.NO_SPECTRAL;
        if (fill < ArmorBalance.LOW)
            return Stage.LOW;
        return Stage.FULL;
    }
}
