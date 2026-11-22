package net.voxelindustry.voidheart.common.content.armor;

import java.util.Set;

/**
 * What a worn frame is running right now: its fitted modules, less the sights its wearer has off and whatever it has cut to save charge.
 *
 * @param levitation the wings can lift the wearer; they only cost charge while actually flying
 */
public record ArmorLoad(int exoskeletons, boolean nightVision, boolean spectral, boolean levitation, boolean limp)
{
    public static final ArmorLoad NONE = new ArmorLoad(0, false, false, false, false);

    public boolean exoskeleton()
    {
        return exoskeletons > 0;
    }

    public static ArmorLoad of(ArmorStats stats, Set<ArmorModule> on, LoadShedding.Stage stage)
    {
        return new ArmorLoad(
                stage.keeps(ArmorModule.EXOSKELETON_POWER) ? stats.exoskeletons() : 0,
                stats.nightVision() && on.contains(ArmorModule.NIGHT_VISION) && stage.keeps(ArmorModule.NIGHT_VISION),
                stats.spectralLens() && on.contains(ArmorModule.SPECTRAL_LENS) && stage.keeps(ArmorModule.SPECTRAL_LENS),
                stats.levitation() && stage.keeps(ArmorModule.LEVITATION),
                stage.limp());
    }
}
