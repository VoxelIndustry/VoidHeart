package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousStorageTile extends BlockEntity
{
    public static final int CAPACITY = 32_000;

    private EssenceStorage storage = new EssenceStorage(EssenceRegistry.EMPTY, CAPACITY);

    public RavenousStorageTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_STORAGE, pos, state);
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

    public EssenceStorage storage()
    {
        return storage;
    }
}
