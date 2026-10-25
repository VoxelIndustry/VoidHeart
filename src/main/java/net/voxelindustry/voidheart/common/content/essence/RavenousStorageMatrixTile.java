package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousStorageMatrixTile extends BlockEntity
{
    public static final int CAPACITY = 32_000;

    private MultiEssenceStorage storage = new MultiEssenceStorage(CAPACITY, 6);

    public RavenousStorageMatrixTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_STORAGE_MATRIX, pos, state);
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
