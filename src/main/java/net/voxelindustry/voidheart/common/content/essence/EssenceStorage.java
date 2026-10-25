package net.voxelindustry.voidheart.common.content.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;

public class EssenceStorage implements IEssenceStorage
{
    public static final Codec<EssenceStorage> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.INT.fieldOf("maximumAmount")
                            .forGetter(EssenceStorage::maximumAmount),
                    Codec.INT.fieldOf("amount")
                            .forGetter(EssenceStorage::amount),
                    ResourceKey.codec(EssenceRegistry.ESSENCE_REGISTRY_TYPE).fieldOf("essenceType").forGetter(storage -> EssenceRegistry.ESSENCE_REGISTRY.getResourceKey(storage.type).get())
            ).apply(instance, (maximumAmount, amount, essenceTypeKey) ->
                    new EssenceStorage(EssenceRegistry.ESSENCE_REGISTRY.get(essenceTypeKey), maximumAmount, amount)));

    private EssenceType type;
    private final int maximumAmount;
    private int amount;

    public EssenceStorage(EssenceType type, int maximumAmount)
    {
        this.type = type;
        this.maximumAmount = maximumAmount;
    }

    private EssenceStorage(EssenceType type, int maximumAmount, int amount)
    {
        this.type = type;
        this.maximumAmount = maximumAmount;
        this.amount = amount;
    }

    @Override
    public int fill(EssenceType type, int amount)
    {
        if (type != this.type)
            return 0;

        var previousAmount = this.amount;
        this.amount = Math.min(maximumAmount, this.amount + amount);
        return this.amount - previousAmount;
    }

    @Override
    public int drain(EssenceType type, int amount)
    {
        if (type != this.type)
            return 0;

        var previousAmount = this.amount;
        this.amount = Math.max(0, this.amount - amount);
        return previousAmount - this.amount;
    }

    public void type(EssenceType type)
    {
        this.type = type;
        this.amount = 0;
    }

    public EssenceType type()
    {
        return type;
    }

    public int maximumAmount()
    {
        return maximumAmount;
    }

    public int amount()
    {
        return amount;
    }

    public int spaceLeft()
    {
        return maximumAmount - amount;
    }

    public boolean isUnattuned()
    {
        return type == EssenceRegistry.EMPTY;
    }

    public void attune(EssenceType type)
    {
        this.type = type;
    }

    @Override
    public String toString()
    {
        return "EssenceStorage{" +
                "type=" + type +
                ", maximumAmount=" + maximumAmount +
                ", amount=" + amount +
                '}';
    }

    public static EssenceStorage fromNbt(CompoundTag compound)
    {
        return CODEC.decode(NbtOps.INSTANCE, compound).result().get().getFirst();
    }
}
