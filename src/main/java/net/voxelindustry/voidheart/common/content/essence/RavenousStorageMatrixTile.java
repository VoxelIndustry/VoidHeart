package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Six aspects, each slot showing on its own face of the matrix, its column reaching out as it fills.
 */
public class RavenousStorageMatrixTile extends TileBase implements IEssenceReceiver
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
            changed();
        return received;
    }

    @Override
    public int amountOf(EssenceType type)
    {
        return storage.amountOf(type);
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        var extracted = storage.drain(type, amount);
        if (extracted > 0)
            changed();
        return extracted;
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

    public void restoreStorage(MultiEssenceStorage source)
    {
        storage = source.copy();
        changed();
    }

    private void changed()
    {
        setChanged();
        if (level == null || level.isClientSide())
            return;
        sync();
        refreshColumns();
    }

    /**
     * Raises or lowers each column to the fill of the slot on its face.
     */
    public void refreshColumns()
    {
        if (level == null || level.isClientSide())
            return;

        var faces = RavenousStorageMatrixBlock.FACES;
        for (int slot = 0; slot < faces.length && slot < storage.storageCount(); slot++)
        {
            var pos = worldPosition.relative(faces[slot]);
            var state = level.getBlockState(pos);
            if (!RavenousStorageMatrixBlock.isColumnOf(state, pos, worldPosition))
                continue;
            var fill = StorageFill.fillOf(storage.amounts()[slot], storage.maximumAmount(), StorageFill.MATRIX_MAX_FILL);
            if (state.getValue(RavenousStorageMatrixBlock.FILL) != fill)
                level.setBlock(pos, state.setValue(RavenousStorageMatrixBlock.FILL, fill), Block.UPDATE_CLIENTS);
        }
    }

    public MultiEssenceStorage storage()
    {
        return storage;
    }
}
