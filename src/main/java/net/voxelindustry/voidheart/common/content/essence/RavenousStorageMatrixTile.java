package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousStorageMatrixTile extends BlockEntity implements IEssenceReceiver
{
    public static final int CAPACITY = 32_000;

    private MultiEssenceStorage storage = new MultiEssenceStorage(CAPACITY, 6);

    public RavenousStorageMatrixTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_STORAGE_MATRIX, pos, state);
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        return storage.spaceFor(type);
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        var received = storage.fill(type, amount);
        if (received > 0)
            setChanged();
        return received;
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        storage = nbt.read("storage", MultiEssenceStorage.CODEC).orElse(storage);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("storage", MultiEssenceStorage.CODEC, storage);
    }

    public MultiEssenceStorage storage()
    {
        return storage;
    }
}
