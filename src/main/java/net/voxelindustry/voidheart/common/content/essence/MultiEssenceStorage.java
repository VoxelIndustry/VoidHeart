package net.voxelindustry.voidheart.common.content.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKey;
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
                    CodecUtil.<RegistryKey<EssenceType>>array(RegistryKey.createCodec(EssenceRegistry.ESSENCE_REGISTRY_TYPE), RegistryKey.class)
                            .fieldOf("types")
                            .forGetter(MultiEssenceStorage::typeKeys)
            ).apply(instance, (maximumAmount, storageCount, amounts, types) ->
            {
                var storage = new MultiEssenceStorage(maximumAmount, storageCount);

                for (int i = 0; i < amounts.length; i++)
                    storage.set(i, EssenceRegistry.ESSENCE_REGISTRY.get(types[i]), amounts[i]);

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

    public EssenceType[] types()
    {
        return types;
    }

    @SuppressWarnings("unchecked")
    private RegistryKey<EssenceType>[] typeKeys()
    {
        return Arrays.stream(types).map(type -> EssenceRegistry.ESSENCE_REGISTRY.getKey(type).get()).toArray(RegistryKey[]::new);
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
