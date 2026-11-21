package net.voxelindustry.voidheart.common.content.hoard;

import lombok.Getter;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.works.WorksPayloads;
import net.voxelindustry.voidheart.common.content.works.WorksSession;
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

    /**
     * What the client is told on opening: where the hoard is seen from, and whether it is a Reckoning table that also plans works.
     */
    public record Opening(BlockPos source, boolean works)
    {
        public static final StreamCodec<RegistryFriendlyByteBuf, Opening> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Opening::source,
                ByteBufCodecs.BOOL, Opening::works,
                Opening::new);
    }

    private final Player player;
    private final BlockPos source;
    @Getter
    private final boolean works;
    @Nullable
    private final UUID owner;
    private long sentVersion = -1;
    @Nullable
    private final WorksSession session;
    private boolean sessionStarted;

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

    // Client only, the works of a Reckoning
    private final List<ItemVariant> pendingMakeable = new ArrayList<>();
    @Getter
    private List<ItemVariant> makeable = List.of();
    @Getter
    private int makeableListing;
    @Getter
    @Nullable
    private WorksPayloads.Plan plan;
    @Getter
    private List<WorksPayloads.Job> jobs = List.of();
    @Getter
    private List<WorksPayloads.Kept> keeps = List.of();

    public HoardMenu(int containerId, Inventory inventory, Opening opening)
    {
        this(containerId, inventory, opening, null);
    }

    private HoardMenu(int containerId, Inventory inventory, Opening opening, @Nullable UUID owner)
    {
        super(HoardContent.HOARD_MENU, containerId);
        this.player = inventory.player;
        this.source = opening.source();
        this.works = opening.works();
        this.owner = owner;
        this.session = works && owner != null && inventory.player instanceof ServerPlayer serverPlayer ? new WorksSession(serverPlayer, owner, containerId) : null;

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
        open(player, owner, source, false);
    }

    /**
     * @param works whether it is seen from a Reckoning table, which also plans works
     */
    public static void open(ServerPlayer player, @Nullable UUID owner, BlockPos source, boolean works)
    {
        if (owner == null)
        {
            player.sendOverlayMessage(Component.translatable(MODID + ".hoard.no_pocket"));
            return;
        }
        var opening = new Opening(source, works);
        player.openMenu(new ExtendedMenuProvider<Opening>()
        {
            @Override
            public Opening getScreenOpeningData(ServerPlayer player)
            {
                return opening;
            }

            @Override
            public Component getDisplayName()
            {
                return Component.translatable(MODID + (works ? ".reckoning" : ".hoard"));
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player)
            {
                return new HoardMenu(containerId, inventory, opening, owner);
            }
        });
    }

    private PocketHoard.@Nullable Hoard hoard()
    {
        if (owner == null || !(player instanceof ServerPlayer serverPlayer))
            return null;
        return PocketHoard.get(serverPlayer.level().getServer()).of(owner);
    }

    public @Nullable WorksSession session()
    {
        return session;
    }

    public void receive(WorksPayloads.Makeable page)
    {
        if (page.page() == 0)
            pendingMakeable.clear();
        pendingMakeable.addAll(page.items());
        if (page.page() == page.pages() - 1)
        {
            makeable = List.copyOf(pendingMakeable);
            makeableListing++;
            pendingMakeable.clear();
        }
    }

    public void receive(WorksPayloads.Plan plan)
    {
        this.plan = plan;
    }

    public void receive(WorksPayloads.Jobs jobs)
    {
        this.jobs = List.copyOf(jobs.jobs());
        this.keeps = List.copyOf(jobs.keeps());
    }

    /**
     * Forgets the plan shown, client side, going back to choosing what to make.
     */
    public void clearPlan()
    {
        plan = null;
    }

    @Override
    public void broadcastChanges()
    {
        super.broadcastChanges();
        if (session != null)
        {
            session.broadcast(!sessionStarted);
            sessionStarted = true;
        }
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
