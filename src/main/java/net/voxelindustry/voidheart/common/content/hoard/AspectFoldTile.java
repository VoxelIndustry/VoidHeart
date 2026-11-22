package net.voxelindustry.voidheart.common.content.hoard;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A store on the relays opening onto its placer's essence reserve, wherever it stands: collectors fill it and machines draw from it as from any store.
 * What enters pays a toll in void energy from the field around it, Contravoid ten times as much; what leaves is free. Without void energy it closes.
 */
public class AspectFoldTile extends TileBase implements IEssenceReceiver
{
    @Getter
    @Nullable
    private UUID owner;

    public AspectFoldTile(BlockPos pos, BlockState state)
    {
        super(HoardContent.ASPECT_FOLD_TILE, pos, state);
    }

    public void setOwner(UUID owner)
    {
        this.owner = owner;
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

    private long energy()
    {
        return level instanceof ServerLevel serverLevel ? VoidEnergy.available(serverLevel, worldPosition) : 0;
    }

    /**
     * @return whether the field around it can keep it open
     */
    public boolean isOpen()
    {
        return energy() > 0;
    }

    private static boolean contravoid(EssenceType type)
    {
        return type == EssenceRegistry.CONTRAVOID;
    }

    private static int clamp(long amount)
    {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, amount));
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        var hoard = hoard();
        if (hoard == null || type == EssenceRegistry.EMPTY)
            return 0;
        var energy = energy();
        if (energy <= 0)
            return 0;
        return clamp(Math.min(hoard.reserve().room(type), HoardBalance.affordableEssence(contravoid(type), energy)));
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        var wanted = Math.min(amount, spaceFor(type));
        if (wanted <= 0 || !(level instanceof ServerLevel serverLevel))
            return 0;
        var paid = VoidEnergy.draw(serverLevel, worldPosition, HoardBalance.toll(contravoid(type), wanted));
        var letIn = Math.min(wanted, HoardBalance.affordableEssence(contravoid(type), paid));
        return clamp(hoard().insertEssence(type, letIn));
    }

    @Override
    public int amountOf(EssenceType type)
    {
        var hoard = hoard();
        if (hoard == null || !isOpen())
            return 0;
        return clamp(hoard.reserve().amountOf(type));
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        var hoard = hoard();
        if (hoard == null || !isOpen())
            return 0;
        return clamp(hoard.extractEssence(type, amount));
    }

    @Override
    public boolean isStore()
    {
        return true;
    }

    /**
     * Every Aspect Fold of one pocket opens onto the same reserve.
     */
    @Override
    public Object essencePool()
    {
        var hoard = hoard();
        return hoard == null ? this : hoard;
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        if (owner != null)
            tag.store("owner", UUIDUtil.CODEC, owner);
    }
}
