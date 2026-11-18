package net.voxelindustry.voidheart.common.content.armor;

import java.util.Set;

/**
 * What a worn frame is running right now: its fitted modules, less the sights its wearer has off and whatever it has cut to save charge.
 */
public record ArmorLoad(boolean exoskeleton, boolean nightVision, boolean spectral, boolean limp)
{
    public static final ArmorLoad NONE = new ArmorLoad(false, false, false, false);

    public static ArmorLoad of(ArmorStats stats, Set<ArmorModule> on, LoadShedding.Stage stage)
    {
        return new ArmorLoad(
                stats.exoskeleton() && stage.keeps(ArmorModule.EXOSKELETON_POWER),
                stats.nightVision() && on.contains(ArmorModule.NIGHT_VISION) && stage.keeps(ArmorModule.NIGHT_VISION),
                stats.spectralLens() && on.contains(ArmorModule.SPECTRAL_LENS) && stage.keeps(ArmorModule.SPECTRAL_LENS),
                stage.limp());
    }
}
