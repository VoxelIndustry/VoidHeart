package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousStorageTile extends BlockEntity implements IEssenceReceiver
{
    public static final int CAPACITY = 32_000;

    private EssenceStorage storage = new EssenceStorage(EssenceRegistry.EMPTY, CAPACITY);

    public RavenousStorageTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_STORAGE, pos, state);
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        if (storage.isUnattuned())
            return storage.maximumAmount();
        return type == storage.type() ? storage.spaceLeft() : 0;
    }

    /**
     * An unattuned storage attunes to the first aspect it receives.
     */
    @Override
    public int receive(EssenceType type, int amount)
    {
        if (storage.isUnattuned())
            storage.attune(type);

        var received = storage.fill(type, amount);
        if (received > 0)
            setChanged();
        return received;
    }

    @Override
    public int amountOf(EssenceType type)
    {
        return type == storage.type() ? storage.amount() : 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        var extracted = storage.drain(type, amount);
        if (extracted > 0)
            setChanged();
        return extracted;
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        storage = nbt.read("storage", EssenceStorage.CODEC).orElse(storage);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("storage", EssenceStorage.CODEC, storage);
    }

    public void restoreStorage(EssenceStorage source)
    {
        storage = source.copy();
        setChanged();
    }

    public EssenceStorage storage()
    {
        return storage;
    }
}
