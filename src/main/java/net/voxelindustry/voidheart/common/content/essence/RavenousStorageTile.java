package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.RavenousStorageBlock.RavenousStoragePart;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.hoard.ReserveVessels;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * A vat of one aspect. Standing in a pocket, it pours what it holds into the pocket's reserve, leaves the relays, and shows the reserve's fill for its
 * aspect instead; taken away, it takes its share of the reserve with it.
 */
public class RavenousStorageTile extends TileBase implements IEssenceReceiver, ReserveVessels.Vessel
{
    public static final int CAPACITY = 32_000;

    private EssenceStorage storage = new EssenceStorage(EssenceRegistry.EMPTY, CAPACITY);
    /**
     * Whether it has joined its pocket's reserve, its storage then only showing the reserve's fill.
     */
    private boolean vessel;

    public RavenousStorageTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.RAVENOUS_STORAGE, pos, state);
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        if (vessel)
            return 0;
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
        if (vessel)
            return 0;
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
        return !vessel && type == storage.type() ? storage.amount() : 0;
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
        vessel = nbt.getBooleanOr("vessel", false);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("storage", EssenceStorage.CODEC, storage);
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

    @Override
    public void refreshVessel(ServerLevel level, PocketHoard.Hoard hoard)
    {
        var id = ReserveVessels.slot(worldPosition, 0);
        if (!vessel || !hoard.reserve().slots().containsKey(id))
        {
            hoard.addVessel(id, storage.type(), CAPACITY, vessel ? 0 : storage.amount());
            vessel = true;
            setChanged();
        }
        var shown = hoard.reserve().shown(id);
        var type = shown.aspect() == null ? EssenceRegistry.EMPTY : shown.aspect();
        if (type != storage.type() || shown.amount() != storage.amount())
            showing(type, (int) Math.min(CAPACITY, shown.amount()));
    }

    private void showing(EssenceType type, int amount)
    {
        var held = new EssenceStorage(type, CAPACITY);
        if (type != EssenceRegistry.EMPTY && amount > 0)
            held.fill(type, amount);
        storage = held;
        changed();
    }

    /**
     * Leaves the pocket's reserve, taking its share back into itself, as it is broken or dropped.
     */
    public void leaveReserve()
    {
        if (!vessel || !(level instanceof ServerLevel server))
            return;
        vessel = false;
        var share = ReserveVessels.pocketAt(server, worldPosition).map(hoard -> hoard.removeVessel(ReserveVessels.slot(worldPosition, 0)));
        var type = share.map(taken -> taken.aspect()).orElse(null);
        var amount = share.map(taken -> taken.amount()).orElse(0L);
        var held = new EssenceStorage(type == null ? storage.type() : type, CAPACITY);
        if (type != null && amount > 0)
            held.fill(type, (int) Math.min(CAPACITY, amount));
        storage = held;
        setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, net.minecraft.world.level.block.state.BlockState state)
    {
        leaveReserve();
        super.preRemoveSideEffects(pos, state);
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
