package net.voxelindustry.voidheart.common.world.pocket;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public record PocketBlueprint(PocketVariant variant, long seed)
{
    /**
     * Walks every ambience in turn, then the next variant of each, in an order shuffled per world, so that neighbouring pockets always differ.
     */
    public static PocketBlueprint forPocket(long worldSeed, int pocketIndex, BlockPos origin)
    {
        var random = RandomSource.create(worldSeed);
        var ambiences = shuffled(List.of(PocketAmbience.values()), random);
        var ambience = ambiences.get(pocketIndex % ambiences.size());
        var variants = shuffled(PocketVariant.of(ambience), random);
        var variant = variants.get(pocketIndex / ambiences.size() % variants.size());
        return new PocketBlueprint(variant, worldSeed * 31 + origin.asLong());
    }

    private static <T> List<T> shuffled(List<T> values, RandomSource random)
    {
        var result = new ArrayList<>(values);
        for (int i = result.size() - 1; i > 0; i--)
        {
            int other = random.nextInt(i + 1);
            var swapped = result.get(i);
            result.set(i, result.get(other));
            result.set(other, swapped);
        }
        return result;
    }
}
