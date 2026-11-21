package net.voxelindustry.voidheart.common.content.hoard;

import lombok.Getter;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The hoard seen from a block that reaches it: its kinds and counts sent in pages whenever it changes, and the player's inventory below. Taking and
 * putting by hand costs nothing, since the player stands there.
 */
public class HoardMenu extends AbstractContainerMenu
{
    public static final int INVENTORY_X = 9;
    public static final int INVENTORY_Y = 141;
    public static final int SLOT        = 18;
    public static final int HOTBAR_GAP  = 4;

    private static final double REACH_SQR = 8 * 8;

    private final Player player;
    private final BlockPos source;
    @Nullable
    private final UUID owner;
    private long sentVersion = -1;

    // Client only
    private final List<HoardPayloads.Entry> pending = new ArrayList<>();
    @Getter
    private List<HoardPayloads.Entry> entries = List.of();
    @Getter
    private long total;
    @Getter
    private long capacity;
    /** Counts up each time a full listing arrives, so the screen knows to sort and filter again. */
    @Getter
    private int listing;

    public HoardMenu(int containerId, Inventory inventory, BlockPos source)
    {
        this(containerId, inventory, source, null);
    }

    private HoardMenu(int containerId, Inventory inventory, BlockPos source, @Nullable UUID owner)
    {
        super(HoardContent.HOARD_MENU, containerId);
        this.player = inventory.player;
        this.source = source;
        this.owner = owner;

        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 9; column++)
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * SLOT, INVENTORY_Y + row * SLOT));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT, INVENTORY_Y + 3 * SLOT + HOTBAR_GAP));
    }

    /**
     * Opens the hoard of a pocket owner, seen from the block at source.
     */
    public static void open(ServerPlayer player, @Nullable UUID owner, BlockPos source)
    {
        if (owner == null)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".hoard.no_pocket"));
            return;
        }
        player.openMenu(new ExtendedMenuProvider<BlockPos>()
        {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer player)
            {
                return source;
            }

            @Override
            public Component getDisplayName()
            {
                return Component.translatable(MODID + ".hoard");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
            {
                return new HoardMenu(containerId, inventory, source, owner);
            }
        });
    }

    private PocketHoard.@Nullable Hoard hoard()
    {
        if (owner == null || !(player instanceof ServerPlayer serverPlayer))
            return null;
        return PocketHoard.get(serverPlayer.level().getServer()).of(owner);
    }

    @Override
    public void broadcastChanges()
    {
        super.broadcastChanges();
        var hoard = hoard();
        if (hoard == null || hoard.version() == sentVersion || !(player instanceof ServerPlayer serverPlayer))
            return;

        sentVersion = hoard.version();
        var all = hoard.view().entrySet().stream().map(entry -> new HoardPayloads.Entry(entry.getKey(), entry.getValue())).toList();
        var pages = Math.max(1, (all.size() + HoardPayloads.PAGE_SIZE - 1) / HoardPayloads.PAGE_SIZE);
        for (int page = 0; page < pages; page++)
        {
            var from = page * HoardPayloads.PAGE_SIZE;
            var entries = all.subList(from, Math.min(all.size(), from + HoardPayloads.PAGE_SIZE));
            ServerPlayNetworking.send(serverPlayer, new HoardPayloads.Page(containerId, page, pages, hoard.total(), hoard.capacity(), List.copyOf(entries)));
        }
    }

    /**
     * Client side: gathers the pages of a listing, and shows it once the last one is in.
     */
    public void receive(HoardPayloads.Page page)
    {
        if (page.page() == 0)
            pending.clear();
        pending.addAll(page.entries());
        if (page.page() == page.pages() - 1)
        {
            entries = List.copyOf(pending);
            total = page.total();
            capacity = page.capacity();
            listing++;
            pending.clear();
        }
    }

    void take(ItemVariant variant, boolean toInventory)
    {
        var hoard = hoard();
        if (hoard == null || variant.isBlank())
            return;

        var carried = getCarried();
        if (!toInventory && !carried.isEmpty() && !variant.matches(carried))
            return;

        var room = toInventory ? variant.getItem().getDefaultMaxStackSize() : variant.toStack().getMaxStackSize() - carried.getCount();
        var taken = (int) hoard.extract(variant, Math.max(0, room));
        if (taken <= 0)
            return;

        if (toInventory)
        {
            var stack = variant.toStack(taken);
            player.getInventory().add(stack);
            if (!stack.isEmpty())
                hoard.restore(variant, stack.getCount());
        }
        else if (carried.isEmpty())
            setCarried(variant.toStack(taken));
        else
            carried.grow(taken);
    }

    void putCarried()
    {
        var hoard = hoard();
        var carried = getCarried();
        if (hoard == null || carried.isEmpty())
            return;

        var put = (int) hoard.insert(ItemVariant.of(carried), carried.getCount());
        carried.shrink(put);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        var hoard = hoard();
        var slot = slots.get(index);
        if (hoard == null || !slot.hasItem())
            return ItemStack.EMPTY;

        var stack = slot.getItem();
        var put = (int) hoard.insert(ItemVariant.of(stack), stack.getCount());
        if (put > 0)
        {
            stack.shrink(put);
            slot.setChanged();
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return player.isAlive() && player.distanceToSqr(Vec3.atCenterOf(source)) <= REACH_SQR;
    }
}
