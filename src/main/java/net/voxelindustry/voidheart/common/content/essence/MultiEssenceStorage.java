package net.voxelindustry.voidheart.common.content.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceKey;
import net.voxelindustry.voidheart.common.util.CodecUtil;

import java.util.Arrays;

public class MultiEssenceStorage implements IEssenceStorage
{
    public static final Codec<MultiEssenceStorage> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.INT
                            .fieldOf("maximumAmount")
                            .forGetter(MultiEssenceStorage::maximumAmount),
                    Codec.INT
                            .fieldOf("storageCount")
                            .forGetter(MultiEssenceStorage::storageCount),
                    CodecUtil.arrayInt()
                            .fieldOf("amounts")
                            .forGetter(MultiEssenceStorage::amounts),
                    CodecUtil.<ResourceKey<EssenceType>>array(EssenceRegistry.TYPE_CODEC, ResourceKey.class)
                            .fieldOf("types")
                            .forGetter(MultiEssenceStorage::typeKeys)
            ).apply(instance, (maximumAmount, storageCount, amounts, types) ->
            {
                var storage = new MultiEssenceStorage(maximumAmount, storageCount);

                // Free slots are saved as the empty aspect
                for (int i = 0; i < amounts.length; i++)
                {
                    var type = EssenceRegistry.ESSENCE_REGISTRY.getValue(types[i]);
                    storage.set(i, type == EssenceRegistry.EMPTY ? null : type, amounts[i]);
                }

                return storage;
            }));

    private final EssenceType[] types;
    private final int maximumAmount;
    private final int[] amounts;

    public MultiEssenceStorage(int maximumAmount, int storageCount)
    {
        this.types = new EssenceType[storageCount];
        this.amounts = new int[storageCount];

        this.maximumAmount = maximumAmount;
    }

    @Override
    public int fill(EssenceType type, int amount)
    {
        var index = findIndexFor(type);

        if (index == -1)
            return 0;

        types[index] = type;
        var previousAmount = this.amounts[index];
        this.amounts[index] = Math.min(maximumAmount, this.amounts[index] + amount);
        return this.amounts[index] - previousAmount;
    }

    @Override
    public int drain(EssenceType type, int amount)
    {
        var index = findIndexFor(type);

        if (index == -1)
            return 0;

        if (types[index] == null)
            return 0;

        var previousAmount = this.amounts[index];
        this.amounts[index] = Math.max(0, this.amounts[index] - amount);
        return previousAmount - this.amounts[index];
    }

    /**
     * @return room left in the slot holding this aspect, or in a free slot when none does
     */
    public int spaceFor(EssenceType type)
    {
        var index = findIndexFor(type);
        return index == -1 ? 0 : maximumAmount - amounts[index];
    }

    public int amountOf(EssenceType type)
    {
        for (int i = 0; i < types.length; i++)
        {
            if (types[i] == type)
                return amounts[i];
        }
        return 0;
    }

    public void set(int index, EssenceType type, int amount)
    {
        this.amounts[index] = amount;
        this.types[index] = type;
    }

    private int findIndexFor(EssenceType type)
    {
        var firstEmpty = -1;
        for (int i = 0; i < types.length; i++)
        {
            var candidate = types[i];
            if (candidate == type)
                return i;

            if (candidate == null && firstEmpty == -1)
                firstEmpty = i;
        }

        return firstEmpty;
    }

    public MultiEssenceStorage copy()
    {
        var copy = new MultiEssenceStorage(maximumAmount, storageCount());
        for (int i = 0; i < amounts.length; i++)
            copy.set(i, types[i], amounts[i]);
        return copy;
    }

    public boolean isEmpty()
    {
        return Arrays.stream(amounts).allMatch(amount -> amount == 0);
    }

    public EssenceType[] types()
    {
        return types;
    }

    @SuppressWarnings("unchecked")
    private ResourceKey<EssenceType>[] typeKeys()
    {
        return Arrays.stream(types).map(type -> EssenceRegistry.ESSENCE_REGISTRY.getResourceKey(type == null ? EssenceRegistry.EMPTY : type).get()).toArray(ResourceKey[]::new);
    }

    public int maximumAmount()
    {
        return maximumAmount;
    }

    public int[] amounts()
    {
        return amounts;
    }

    public int storageCount()
    {
        return amounts.length;
    }
}
