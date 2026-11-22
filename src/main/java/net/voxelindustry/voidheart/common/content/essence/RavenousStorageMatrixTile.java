package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.hoard.ReserveVessels;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Six aspects, each slot showing on its own face of the matrix, its column reaching out as it fills. Standing in a pocket, each slot pours into the
 * pocket's reserve and shows its fill, as a single storage does.
 */
public class RavenousStorageMatrixTile extends TileBase implements IEssenceReceiver, ReserveVessels.Vessel
{
    public static final int CAPACITY = 32_000;

    private MultiEssenceStorage storage = new MultiEssenceStorage(CAPACITY, 6);
    private boolean vessel;

    public RavenousStorageMatrixTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_STORAGE_MATRIX, pos, state);
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        return vessel ? 0 : storage.spaceFor(type);
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        if (vessel)
            return 0;
        var received = storage.fill(type, amount);
        if (received > 0)
            changed();
        return received;
    }

    @Override
    public int amountOf(EssenceType type)
    {
        return vessel ? 0 : storage.amountOf(type);
    }

    @Override
    public boolean linkable()
    {
        return !vessel;
    }

    @Override
    public boolean isStore()
    {
        return true;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        if (vessel)
            return 0;
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
        vessel = nbt.getBooleanOr("vessel", false);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("storage", MultiEssenceStorage.CODEC, storage);
        if (vessel)
            nbt.putBoolean("vessel", true);
    }

    // As a vessel of its pocket's reserve

    @Override
    public void clearRemoved()
    {
        super.clearRemoved();
        if (level instanceof ServerLevel)
            ReserveVessels.track(this);
    }

    @Override
    public ServerLevel serverLevel()
    {
        return level instanceof ServerLevel server ? server : null;
    }

    @Override
    public BlockPos vesselPos()
    {
        return worldPosition;
    }

    public boolean isVessel()
    {
        return vessel;
    }

    private static EssenceType typeOrEmpty(EssenceType type)
    {
        return type == null ? EssenceRegistry.EMPTY : type;
    }

    @Override
    public void refreshVessel(ServerLevel level, PocketHoard.Hoard hoard)
    {
        var count = storage.storageCount();
        for (int slot = 0; slot < count; slot++)
        {
            var id = ReserveVessels.slot(worldPosition, slot);
            if (!vessel || !hoard.reserve().slots().containsKey(id))
                hoard.addVessel(id, typeOrEmpty(storage.types()[slot]), CAPACITY, vessel ? 0 : storage.amounts()[slot]);
        }
        if (!vessel)
        {
            vessel = true;
            setChanged();
        }

        var shown = new MultiEssenceStorage(CAPACITY, count);
        var differs = false;
        for (int slot = 0; slot < count; slot++)
        {
            var share = hoard.reserve().shown(ReserveVessels.slot(worldPosition, slot));
            var type = share.aspect();
            var amount = (int) Math.min(CAPACITY, share.amount());
            shown.set(slot, type, amount);
            differs |= typeOrEmpty(type) != typeOrEmpty(storage.types()[slot]) || amount != storage.amounts()[slot];
        }
        if (differs)
        {
            storage = shown;
            changed();
        }
    }

    /**
     * Leaves the pocket's reserve, each slot taking its share back into itself, as it is broken or dropped.
     */
    public void leaveReserve()
    {
        if (!vessel || !(level instanceof ServerLevel server))
            return;
        vessel = false;
        var hoard = ReserveVessels.pocketAt(server, worldPosition);
        var held = new MultiEssenceStorage(CAPACITY, storage.storageCount());
        for (int slot = 0; slot < storage.storageCount(); slot++)
        {
            var id = ReserveVessels.slot(worldPosition, slot);
            var share = hoard.map(pocket -> pocket.removeVessel(id)).orElse(null);
            if (share != null && share.aspect() != null)
                held.set(slot, share.aspect(), (int) Math.min(CAPACITY, share.amount()));
        }
        storage = held;
        setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        leaveReserve();
        super.preRemoveSideEffects(pos, state);
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
