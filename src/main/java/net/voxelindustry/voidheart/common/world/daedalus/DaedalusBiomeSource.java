package net.voxelindustry.voidheart.common.world.daedalus;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Each district has the biome of its theme, which brings its fog and water. Chasms have their own, and so does the Underway beneath them all.
 */
public final class DaedalusBiomeSource extends BiomeSource implements BiomeResolver
{
    private static final ResourceKey<Biome> CHASM = key("daedalus_chasm");
    private static final ResourceKey<Biome> ARCHIVE = key("daedalus_archive");
    private static final ResourceKey<Biome> WARREN = key("daedalus_warren");
    private static final ResourceKey<Biome> GALLERY = key("daedalus_gallery");
    private static final ResourceKey<Biome> CISTERN = key("daedalus_cistern");
    private static final ResourceKey<Biome> STAIRWELL = key("daedalus_stairwell");
    private static final ResourceKey<Biome> WRITHING = key("daedalus_writhing");
    public static final ResourceKey<Biome> UNDERWAY = key("underway");

    public static final MapCodec<DaedalusBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    RegistryOps.retrieveElement(CHASM),
                    RegistryOps.retrieveElement(ARCHIVE),
                    RegistryOps.retrieveElement(WARREN),
                    RegistryOps.retrieveElement(GALLERY),
                    RegistryOps.retrieveElement(CISTERN),
                    RegistryOps.retrieveElement(STAIRWELL),
                    RegistryOps.retrieveElement(WRITHING),
                    RegistryOps.retrieveElement(UNDERWAY))
            .apply(instance, instance.stable(DaedalusBiomeSource::new)));

    private final Holder<Biome> chasm;
    private final Holder<Biome> underway;
    private final Map<DaedalusTheme, Holder<Biome>> byTheme = new EnumMap<>(DaedalusTheme.class);

    private DaedalusBiomeSource(Holder<Biome> chasm,
                                Holder<Biome> archive,
                                Holder<Biome> warren,
                                Holder<Biome> gallery,
                                Holder<Biome> cistern,
                                Holder<Biome> stairwell,
                                Holder<Biome> writhing,
                                Holder<Biome> underway)
    {
        this.chasm = chasm;
        this.underway = underway;
        byTheme.put(DaedalusTheme.ARCHIVE, archive);
        byTheme.put(DaedalusTheme.WARREN, warren);
        byTheme.put(DaedalusTheme.GALLERY, gallery);
        byTheme.put(DaedalusTheme.CISTERN, cistern);
        byTheme.put(DaedalusTheme.STAIRWELL, stairwell);
        byTheme.put(DaedalusTheme.WRITHING, writhing);
    }

    private static ResourceKey<Biome> key(String name)
    {
        return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(MODID, name));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec()
    {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes()
    {
        return Stream.concat(Stream.of(chasm, underway), byTheme.values().stream());
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler)
    {
        return this;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ)
    {
        if (quartY < 0)
            return underway;
        int x = QuartPos.toBlock(quartX);
        int z = QuartPos.toBlock(quartZ);
        if (!DistrictPos.isInsideDistrict(x, z))
            return chasm;
        return byTheme.get(DaedalusTheme.at(DistrictPos.at(x, z)));
    }
}
