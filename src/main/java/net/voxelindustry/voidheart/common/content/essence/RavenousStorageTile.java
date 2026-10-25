package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
    public void load(CompoundTag nbt)
    {
        super.load(nbt);

        storage = EssenceStorage.CODEC.decode(NbtOps.INSTANCE, nbt.getCompound("storage")).result().get().getFirst();
    }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    {
        super.saveAdditional(nbt);

        nbt.put("storage", EssenceStorage.CODEC.encodeStart(NbtOps.INSTANCE, storage).result().get());
    }

    public EssenceStorage storage()
    {
        return storage;
    }
}
