package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.BlockPos;
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
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        storage = MultiEssenceStorage.CODEC.decode(NbtOps.INSTANCE, nbt.getCompound("storage")).result().get().getFirst();
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        nbt.put("storage", MultiEssenceStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).result().get());
    }

    public MultiEssenceStorage storage()
    {
        return storage;
    }
}
