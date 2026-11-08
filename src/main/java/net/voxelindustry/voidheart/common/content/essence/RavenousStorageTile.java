package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock.RavenousStoragePart;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class RavenousStorageTile extends TileBase implements IEssenceReceiver
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
            changed();
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
            changed();
        return extracted;
    }

    /**
     * Saved, shown to clients drawing the essence held, and raised or lowered on the pillars.
     */
    private void changed()
    {
        setChanged();
        if (level == null || level.isClientSide())
            return;

        sync();
        var fill = StorageFill.fillOf(storage.amount(), storage.maximumAmount());
        setFill(worldPosition, fill);
        var above = worldPosition.above();
        if (level.getBlockState(above).is(getBlockState().getBlock()) && level.getBlockState(above).getValue(RavenousStorageBlock.PART) == RavenousStoragePart.PILLAR)
            setFill(above, fill);
    }

    private void setFill(BlockPos pos, int fill)
    {
        var state = level.getBlockState(pos);
        if (state.hasProperty(RavenousStorageBlock.FILL) && state.getValue(RavenousStorageBlock.FILL) != fill)
            level.setBlock(pos, state.setValue(RavenousStorageBlock.FILL, fill), Block.UPDATE_CLIENTS);
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
        changed();
    }

    public EssenceStorage storage()
    {
        return storage;
    }
}
