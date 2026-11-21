package net.voxelindustry.voidheart.common.content.armor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

/**
 * Holds the frame docked on a base and charges it, from the pylon field the base stands in first, then from the rubrum cell in its own socket, which is
 * what lets a base charge far from any field. The frame's racks and apparatus socket are a live container while it is docked, written back onto the frame
 * stack on every change, so the stack always carries everything it holds wherever it goes next.
 */
public class VoidArmorBaseTile extends TileBase
{
    private ItemStack frame = ItemStack.EMPTY;
    private boolean   loading;
    private long      syncedCharge;

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

    private final SimpleContainer cell = new SimpleContainer(1)
    {
        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack)
        {
            return stack.is(RubrumContent.RUBRUM_CELL);
        }

        @Override
        public void setChanged()
        {
            super.setChanged();
            if (!loading)
                changed();
        }
    };

    public VoidArmorBaseTile(BlockPos pos, BlockState state)
    {
        super(VoidArmorContent.VOID_ARMOR_BASE_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidArmorBaseTile tile)
    {
        if (level instanceof ServerLevel serverLevel)
            tile.charge(serverLevel);
    }

    private void charge(ServerLevel level)
    {
        if (frame.isEmpty())
            return;
        long capacity = VoidArmorItem.stats(frame).capacity();
        long charge = VoidArmorItem.charge(frame);
        if (charge < capacity)
        {
            long wanted = Math.min(ArmorBalance.BASE_INTAKE, capacity - charge);
            long taken = VoidEnergy.draw(level, worldPosition, wanted);
            var held = cell.getItem(0);
            if (taken < wanted && !held.isEmpty())
            {
                long fromCell = Math.min(wanted - taken, RubrumCellItem.charge(held));
                if (fromCell > 0)
                {
                    RubrumCellItem.setCharge(held, RubrumCellItem.charge(held) - fromCell);
                    taken += fromCell;
                }
            }
            if (taken > 0)
            {
                frame.set(RubrumContent.VOID_CHARGE, charge + taken);
                setChanged();
            }
        }
        if (VoidArmorItem.charge(frame) != syncedCharge && level.getGameTime() % ArmorBalance.BASE_SYNC == 0)
        {
            syncedCharge = VoidArmorItem.charge(frame);
            sync();
        }
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

    public SimpleContainer cell()
    {
        return cell;
    }

    /**
     * Stands a frame on this base, which becomes the base it goes home to.
     */
    public void dock(ItemStack frame)
    {
        var docked = frame.copyWithCount(1);
        if (level instanceof ServerLevel serverLevel)
            docked.set(VoidArmorContent.ARMOR_HOME, ArmorWear.home(serverLevel, worldPosition));
        setFrame(docked);
        if (level != null)
        {
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(VoidArmorBaseBlock.DOCKED, true));
            level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 1, 0.8F);
        }
    }

    /**
     * Lets the frame go, to be worn, and leaves the base empty.
     */
    public ItemStack undock()
    {
        var taken = frame;
        setFrame(ItemStack.EMPTY);
        if (level != null)
            level.setBlockAndUpdate(worldPosition, getBlockState().setValue(VoidArmorBaseBlock.DOCKED, false));
        return taken;
    }

    private void setFrame(ItemStack frame)
    {
        this.frame = frame.copyWithCount(frame.isEmpty() ? 0 : 1);
        syncedCharge = VoidArmorItem.charge(this.frame);
        reloadSlots();
        changed();
    }

    public ArmorGrids grids()
    {
        return frame.getOrDefault(VoidArmorContent.ARMOR_GRIDS, ArmorGrids.EMPTY);
    }

    /**
     * Sets the frame's modules. Charge above what is left of its capacity when batteries come out is lost.
     */
    public void setGrids(ArmorGrids grids)
    {
        if (frame.isEmpty())
            return;
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        long capacity = grids.stats().capacity();
        if (VoidArmorItem.charge(frame) > capacity)
            frame.set(RubrumContent.VOID_CHARGE, capacity);
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
     * Only a creative break or a command can remove a base holding a frame, since a frame travels only worn. It drops then rather than vanish, and the
     * cell always drops.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level != null)
        {
            if (!frame.isEmpty())
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, frame);
            if (!cell.getItem(0).isEmpty())
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, cell.getItem(0));
            // The arch comes down with its plinth
            VoidArmorBaseBlock.removeArch(level, pos, state.getValue(VoidArmorBaseBlock.FACING));
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        frame = tag.read("frame", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        syncedCharge = VoidArmorItem.charge(frame);
        reloadSlots();
        loading = true;
        cell.setItem(0, tag.read("cell", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        loading = false;
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("frame", ItemStack.OPTIONAL_CODEC, frame);
        tag.store("cell", ItemStack.OPTIONAL_CODEC, cell.getItem(0));
        super.saveAdditional(tag);
    }
}
