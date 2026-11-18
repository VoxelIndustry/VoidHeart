package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;

/**
 * Holds the frame docked on a base. Its racks and apparatus socket are a live container while it is docked, written back onto the frame stack on every
 * change, so the stack always carries everything it holds wherever it goes next.
 */
public class VoidArmorBaseTile extends TileBase
{
    private ItemStack frame = ItemStack.EMPTY;
    private boolean   loading;

    private final SimpleContainer slots = new SimpleContainer(ArmorBalance.FRAME_SLOTS)
    {
        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack)
        {
            return mayHold(slot, stack);
        }

        @Override
        public boolean stillValid(Player player)
        {
            return hasFrame() && !isRemoved();
        }

        @Override
        public void setChanged()
        {
            super.setChanged();
            slotsChanged();
        }
    };

    public VoidArmorBaseTile(BlockPos pos, BlockState state)
    {
        super(VoidArmorContent.VOID_ARMOR_BASE_TILE, pos, state);
    }

    /**
     * Whether a slot of the frame takes the stack: weapons in the racks, a Shielding Apparatus in the socket.
     */
    public static boolean mayHold(int slot, ItemStack stack)
    {
        if (slot == ArmorBalance.SOCKET_SLOT)
            return stack.getItem() instanceof ShieldingApparatusItem;
        return slot >= 0 && slot < ArmorBalance.RACKS && stack.is(VoidArmorContent.RACK_WEAPONS);
    }

    public boolean hasFrame()
    {
        return !frame.isEmpty();
    }

    public ItemStack frame()
    {
        return frame;
    }

    public SimpleContainer slots()
    {
        return slots;
    }

    public void setFrame(ItemStack frame)
    {
        this.frame = frame.copyWithCount(frame.isEmpty() ? 0 : 1);
        reloadSlots();
        changed();
    }

    public ArmorGrids grids()
    {
        return frame.getOrDefault(VoidArmorContent.ARMOR_GRIDS, ArmorGrids.EMPTY);
    }

    public void setGrids(ArmorGrids grids)
    {
        if (frame.isEmpty())
            return;
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        changed();
    }

    public long charge()
    {
        return VoidArmorItem.charge(frame);
    }

    private void slotsChanged()
    {
        if (loading || frame.isEmpty())
            return;
        frame.set(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.fromItems(slots.getItems()));
        changed();
    }

    private void reloadSlots()
    {
        loading = true;
        var items = NonNullList.withSize(ArmorBalance.FRAME_SLOTS, ItemStack.EMPTY);
        frame.getOrDefault(VoidArmorContent.ARMOR_SLOTS, ItemContainerContents.EMPTY).copyInto(items);
        for (int i = 0; i < items.size(); i++)
            slots.setItem(i, items.get(i));
        loading = false;
    }

    private void changed()
    {
        setChanged();
        if (level != null && !level.isClientSide())
            sync();
    }

    /**
     * Only a creative break or a command can remove a base holding a frame, since a frame travels only worn. It drops then rather than vanish.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null && !frame.isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, frame);
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        frame = tag.read("frame", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        reloadSlots();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("frame", ItemStack.OPTIONAL_CODEC, frame);
        super.saveAdditional(tag);
    }
}
