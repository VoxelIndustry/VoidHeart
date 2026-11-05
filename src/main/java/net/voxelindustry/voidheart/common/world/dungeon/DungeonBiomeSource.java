package net.voxelindustry.voidheart.common.world.dungeon;

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
 * Each dungeon has the biome of its theme, read from where it lies. Biomes bring the fog, water color and sounds of the world it was taken from.
 */
public final class DungeonBiomeSource extends BiomeSource implements BiomeResolver
{
    private static final ResourceKey<Biome> BETWEEN = key("dungeon_between");
    private static final ResourceKey<Biome> CATHEDRAL = key("dungeon_cathedral");
    private static final ResourceKey<Biome> FOUNDRY = key("dungeon_foundry");
    private static final ResourceKey<Biome> PALACE = key("dungeon_palace");
    private static final ResourceKey<Biome> TEMPLE = key("dungeon_temple");
    private static final ResourceKey<Biome> FOREST = key("dungeon_forest");
    private static final ResourceKey<Biome> MYCOSIS = key("dungeon_mycosis");
    private static final ResourceKey<Biome> DEPTHS = key("dungeon_depths");

    public static final MapCodec<DungeonBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    RegistryOps.retrieveElement(BETWEEN),
                    RegistryOps.retrieveElement(CATHEDRAL),
                    RegistryOps.retrieveElement(FOUNDRY),
                    RegistryOps.retrieveElement(PALACE),
                    RegistryOps.retrieveElement(TEMPLE),
                    RegistryOps.retrieveElement(FOREST),
                    RegistryOps.retrieveElement(MYCOSIS),
                    RegistryOps.retrieveElement(DEPTHS))
            .apply(instance, instance.stable(DungeonBiomeSource::new)));

    private final Holder<Biome> between;
    private final Map<DungeonTheme, Holder<Biome>> byTheme = new EnumMap<>(DungeonTheme.class);

    private DungeonBiomeSource(Holder<Biome> between,
                               Holder<Biome> cathedral,
                               Holder<Biome> foundry,
                               Holder<Biome> palace,
                               Holder<Biome> temple,
                               Holder<Biome> forest,
                               Holder<Biome> mycosis,
                               Holder<Biome> depths)
    {
        this.between = between;
        byTheme.put(DungeonTheme.CATHEDRAL, cathedral);
        byTheme.put(DungeonTheme.FOUNDRY, foundry);
        byTheme.put(DungeonTheme.PALACE, palace);
        byTheme.put(DungeonTheme.TEMPLE, temple);
        byTheme.put(DungeonTheme.FOREST, forest);
        byTheme.put(DungeonTheme.MYCOSIS, mycosis);
        byTheme.put(DungeonTheme.DEPTHS, depths);
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
        return Stream.concat(Stream.of(between), byTheme.values().stream());
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler)
    {
        return this;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ)
    {
        var address = DungeonAddress.at(QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
        return address == null ? between : byTheme.get(address.theme());
    }
}
