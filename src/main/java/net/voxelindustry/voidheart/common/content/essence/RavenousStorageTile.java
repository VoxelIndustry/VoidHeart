package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.math.BlockPos;
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
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        storage = EssenceStorage.CODEC.decode(NbtOps.INSTANCE, nbt.getCompound("storage")).result().get().getFirst();
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        nbt.put("storage", EssenceStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).result().get());
    }

    public EssenceStorage storage()
    {
        return storage;
    }
}
