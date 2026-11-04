package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.voxelindustry.voidheart.common.world.pocket.PocketAmbience.LAKE;
import static net.voxelindustry.voidheart.common.world.pocket.PocketAmbience.LUSH;
import static net.voxelindustry.voidheart.common.world.pocket.PocketAmbience.STALACTITES;

public enum PocketVariant implements StringRepresentable
{
    LAGOON(LAKE, lake().island(4.5).crystals(2, 3, 0.3)),
    DEEP_LAKE(LAKE, lake().island(2.8).floor(0, 0.6, 1).cones(5, 0, 0, 4).lights(3).crystals(3, 3, 0.35)),
    MANGROVE_MARSH(LAKE, lake().heartRise(2).island(4).floor(0, 0.8, 1.5).trees(2).crystals(2, 2, 0.35)),
    FROZEN_LAKE(LAKE, lake().island(3.6).frozen().cones(5, 2, 0, 5).crystals(2, 3, 0.35)),

    AZALEA_GARDEN(LUSH, lush().pool(2.6).trees(1).crystals(2, 2, 0.5)),
    BLOOM_FOREST(LUSH, lush().pool(2.2).trees(2).crystals(2, 2, 0.5)),
    CHERRY_GROVE(LUSH, lush().trees(1).crystals(2, 2, 0.55)),
    JUNGLE(LUSH, lush().pool(2.4).trees(2).floor(1.7, 1, 2.5).crystals(2, 3, 0.45)),
    MUSHROOM_HOLLOW(LUSH, lush().trees(2).ceiling(1, 2.5).crystals(2, 3, 0.5)),

    OCHRE_CAVERN(STALACTITES, stalactites().pool(2.2).crystals(2, 3, 0.55)),
    PALE_CATHEDRAL(STALACTITES, stalactites().cones(10, 5, 2, 6).pool(1.8).crystals(2, 3, 0.35)),
    EMBER_DEEP(STALACTITES, stalactites().cones(6, 5, 1, 5).pool(2.4).lights(3).crystals(2, 4, 0.8));

    private final PocketAmbience ambience;
    private final PocketProfile profile;

    PocketVariant(PocketAmbience ambience, PocketProfile profile)
    {
        this.ambience = ambience;
        this.profile = profile;
    }

    private static PocketProfile lake()
    {
        return new PocketProfile()
                .heartRise(3)
                .fluid(-2)
                .floor(0, 0.8, 2.2)
                .ceiling(0.9, 2)
                .cones(3, 0, 0, 3);
    }

    private static PocketProfile lush()
    {
        return new PocketProfile()
                .heartRise(1)
                .fluid(-2)
                .floor(1.6, 0.9, 2.5)
                .ceiling(0.8, 2.2)
                .cones(1, 0, 0, 2);
    }

    private static PocketProfile stalactites()
    {
        return new PocketProfile()
                .heartRise(2)
                .fluid(-3)
                .floor(1.6, 1.1, 2.5)
                .ceiling(1.2, 2)
                .cones(8, 5, 1, 5);
    }

    public PocketAmbience ambience()
    {
        return ambience;
    }

    public PocketProfile profile()
    {
        return profile;
    }

    public static List<PocketVariant> of(PocketAmbience ambience)
    {
        return Arrays.stream(values()).filter(variant -> variant.ambience == ambience).toList();
    }

    @Override
    public String getSerializedName()
    {
        return name().toLowerCase(Locale.ROOT);
    }
}
