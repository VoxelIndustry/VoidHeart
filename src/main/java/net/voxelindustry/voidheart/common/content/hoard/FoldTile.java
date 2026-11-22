package net.voxelindustry.voidheart.common.content.hoard;

import lombok.Getter;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A place folded onto its placer's hoard. It takes anything put into it, and gives back the one item it was shown, each item paid for in void energy
 * from the field around it.
 */
public class FoldTile extends TileBase
{
    @Getter
    @Nullable
    private UUID owner;
    /**
     * The only item the Fold gives back, null while it only takes.
     */
    @Getter
    @Nullable
    private ItemVariant attuned;

    private final FoldStorage storage = new FoldStorage(this);

    public FoldTile(BlockPos pos, BlockState state)
    {
        super(HoardContent.FOLD_TILE, pos, state);
    }

    public Storage<ItemVariant> storage()
    {
        return storage;
    }

    public void setOwner(UUID owner)
    {
        this.owner = owner;
        setChanged();
        sync();
    }

    /**
     * @param attuned the item to give back from now on, or null to only take
     */
    public void attune(@Nullable ItemVariant attuned)
    {
        this.attuned = attuned == null || attuned.isBlank() ? null : attuned;
        setChanged();
        sync();
    }

    /**
     * @return the hoard of the owner's pocket, or null on the client, without an owner or without a pocket
     */
    public PocketHoard.@Nullable Hoard hoard()
    {
        if (owner == null || !(level instanceof ServerLevel serverLevel))
            return null;
        if (!VoidPocketState.getVoidPocketState(serverLevel).hasPocket(owner))
            return null;
        return PocketHoard.get(serverLevel.getServer()).of(owner);
    }

    /**
     * @return how many items the field around the Fold can pay to move this tick
     */
    long affordableItems()
    {
        if (!(level instanceof ServerLevel serverLevel))
            return 0;
        return VoidEnergy.available(serverLevel, worldPosition) / HoardBalance.FOLD_COST_PER_ITEM;
    }

    /**
     * @return names for what pays the Fold's tolls, which Folds whose names don't meet never share
     */
    java.util.Set<Object> drawsFrom()
    {
        if (!(level instanceof ServerLevel serverLevel))
            return java.util.Set.of();
        return VoidEnergy.drawsFrom(serverLevel, worldPosition);
    }

    void pay(long items)
    {
        if (items > 0 && level instanceof ServerLevel serverLevel)
            VoidEnergy.draw(serverLevel, worldPosition, items * HoardBalance.FOLD_COST_PER_ITEM);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
        attuned = tag.read("attuned", ItemVariant.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (owner != null)
            tag.store("owner", UUIDUtil.CODEC, owner);
        if (attuned != null)
            tag.store("attuned", ItemVariant.CODEC, attuned);
        super.saveAdditional(tag);
    }
}
