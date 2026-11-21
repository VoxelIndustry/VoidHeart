package net.voxelindustry.voidheart.common.content.hoard;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * A coffer in a pocket, its rubrum bundle adding room to that pocket's hoard. It holds nothing itself: the hoard is kept as data, so the pocket can
 * stay unloaded. Its fill shows how full the hoard is.
 */
public class HoardCofferTile extends TileBase
{
    private static final int FILL_SYNC_PERIOD = 20;

    @Getter
    private ItemStack bundle = ItemStack.EMPTY;
    @Nullable
    private UUID owner;
    /** How full the pocket's hoard is, from 0 to 1, as last sent to clients. */
    @Getter
    private float fill;

    public HoardCofferTile(BlockPos pos, BlockState state)
    {
        super(HoardContent.HOARD_COFFER_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, HoardCofferTile coffer)
    {
        if (level instanceof ServerLevel && level.getGameTime() % FILL_SYNC_PERIOD == 0)
            coffer.updateFill();
    }

    /**
     * @return whether the bundle is a rubrum bundle a coffer can hold
     */
    public static boolean fits(ItemStack stack)
    {
        var block = CrystalBundles.bundleOf(stack);
        return block != null && CrystalBundles.kindOfBundle(block) == CrystalKind.RUBRUM;
    }

    /**
     * @return the owner of the pocket the coffer stands in, if it stands in one
     */
    public Optional<UUID> pocketOwner()
    {
        if (!(level instanceof ServerLevel serverLevel) || !serverLevel.dimension().equals(VoidHeart.VOID_WORLD_KEY))
            return Optional.empty();
        return VoidPocketState.getVoidPocketState(serverLevel).ownerAt(worldPosition);
    }

    public long capacity()
    {
        var block = CrystalBundles.bundleOf(bundle);
        var size = block == null ? null : CrystalBundles.sizeOf(block);
        return size == null ? 0 : HoardBalance.cofferCapacity(size);
    }

    public void setBundle(ItemStack bundle)
    {
        this.bundle = bundle.isEmpty() ? ItemStack.EMPTY : bundle.copyWithCount(1);
        register();
        updateFill();
        setChanged();
        sync();
    }

    /**
     * Tells the pocket's hoard how much room this coffer adds, and forgets it in a hoard it no longer stands in.
     */
    private void register()
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        var hoards = PocketHoard.get(serverLevel.getServer());
        var now = pocketOwner().orElse(null);
        if (owner != null && !owner.equals(now))
            hoards.of(owner).setCoffer(worldPosition, 0);
        owner = now;
        if (owner != null)
            hoards.of(owner).setCoffer(worldPosition, capacity());
    }

    private void updateFill()
    {
        if (!(level instanceof ServerLevel serverLevel))
            return;

        var next = 0F;
        if (owner != null && !bundle.isEmpty())
        {
            var hoard = PocketHoard.get(serverLevel.getServer()).of(owner);
            next = hoard.capacity() == 0 ? 0 : Math.min(1, hoard.total() / (float) hoard.capacity());
        }
        if (Math.abs(next - fill) > 0.005F)
        {
            fill = next;
            sync();
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level instanceof ServerLevel serverLevel && owner != null)
            PocketHoard.get(serverLevel.getServer()).of(owner).setCoffer(pos, 0);
        if (!bundle.isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, bundle);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        bundle = tag.read("bundle", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
        fill = tag.getFloatOr("fill", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("bundle", ItemStack.OPTIONAL_CODEC, bundle);
        if (owner != null)
            tag.store("owner", UUIDUtil.CODEC, owner);
        tag.putFloat("fill", fill);
        super.saveAdditional(tag);
    }
}
