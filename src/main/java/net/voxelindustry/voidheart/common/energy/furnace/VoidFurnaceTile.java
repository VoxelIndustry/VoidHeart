package net.voxelindustry.voidheart.common.energy.furnace;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.content.works.OfficiantTile;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.machine.DrawCredit;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.machine.PowerMeter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Smelts what lies in its input on void energy drawn from the field around it, {@link VoidFurnaceBalance#SPEED} times as fast as a furnace. An item
 * started while it holds enough Fiery burns some and cooks at {@link VoidFurnaceBalance#FIERY_SPEED}. Relays fill its Fiery, hoppers fill its input
 * from the top and sides and empty its output from below, and an Officiant under it lays batches out of its hoard.
 * <p>
 * A batch an Officiant lays carries a ticket. Anything else touching the slots tears it up, so a hand or a hopper's items are never taken for the
 * batch's result. What the Officiant smelts gives no experience; only what a player smelts does, taken from the output like a furnace's.
 */
public class VoidFurnaceTile extends TileBase implements WorldlyContainer, IEssenceReceiver
{
    public static final int INPUT  = 0;
    public static final int OUTPUT = 1;

    /** How far the item cooking has got, in thousandths. */
    public static final int DATA_COOKED = 0;
    /** Its heat, in thousandths. */
    public static final int DATA_HEAT   = 1;
    public static final int DATA_FIERY  = 2;
    public static final int DATA_POWER  = 3;
    /** 0 while nothing cooks, 1 for an item cooking on void energy, 2 for one that burned Fiery. */
    public static final int DATA_STATE  = 4;
    public static final int DATA_COUNT  = 5;

    private static final int[] SLOTS_FOR_DOWN  = { OUTPUT };
    private static final int[] SLOTS_FOR_SIDES = { INPUT };

    private static final Codec<Map<ResourceKey<Recipe<?>>, Integer>> RECIPES_USED_CODEC = Codec.unboundedMap(Recipe.KEY_CODEC, Codec.INT);

    private static final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> CHECK = RecipeManager.createCheck(RecipeType.SMELTING);

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);

    /** How far the item in the input has cooked, from 0 to 1. */
    private double  progress;
    /** From 0 when cold to 1 at full heat, raising its pace. */
    private double  heat;
    /** Whether the item in the input has begun, its Fiery paid if it burns fast. */
    private boolean started;
    /** Whether the item cooking burned Fiery, so it cooks at the fast pace. */
    private boolean fast;
    private int     fiery;

    private final Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> recipesUsed = new Reference2IntOpenHashMap<>();

    /** The batch an Officiant laid, and how many of it were smelted so far. */
    @Nullable
    private UUID ticket;
    private int  smelted;

    // Server only
    private final DrawCredit credit = new DrawCredit();
    private final PowerMeter meter  = new PowerMeter();
    private       MachinePower power = MachinePower.IDLE;

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return switch (index)
            {
                case DATA_COOKED -> (int) Math.round(progress * 1000);
                case DATA_HEAT -> (int) Math.round(heat * 1000);
                case DATA_FIERY -> fiery;
                case DATA_POWER -> power.ordinal();
                case DATA_STATE -> started ? (fast ? 2 : 1) : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value)
        {
        }

        @Override
        public int getCount()
        {
            return DATA_COUNT;
        }
    };

    public VoidFurnaceTile(BlockPos pos, BlockState state)
    {
        super(VoidFurnaceContent.VOID_FURNACE_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VoidFurnaceTile furnace)
    {
        if (level instanceof ServerLevel serverLevel)
            furnace.serverTick(serverLevel, state);
    }

    private void serverTick(ServerLevel level, BlockState state)
    {
        var input = items.get(INPUT);
        var recipe = input.isEmpty() ? null : CHECK.getRecipeFor(new SingleRecipeInput(input), level).orElse(null);
        var result = recipe == null ? ItemStack.EMPTY : recipe.value().assemble(new SingleRecipeInput(input));
        if (recipe == null || result.isEmpty() || !fits(result))
        {
            resetCook();
            cool(level);
            record(level, state, 0, 0);
            return;
        }

        if (!started)
        {
            fast = VoidFurnaceBalance.canBurnFiery(fiery);
            if (fast)
                fiery -= VoidFurnaceBalance.FIERY_PER_ITEM;
            started = true;
            progress = 0;
            setChanged();
        }

        var pace = VoidFurnaceBalance.pace(heat, fast);
        var need = VoidFurnaceBalance.draw(pace, fast);
        var paid = credit.pay(need, amount -> VoidEnergy.draw(level, worldPosition, amount));
        record(level, state, need, paid ? need : credit.lastDrawn());
        if (!paid)
        {
            cool(level);
            return;
        }

        progress += VoidFurnaceBalance.progressPerTick(recipe.value().cookingTime(), pace);
        heat = VoidFurnaceBalance.warm(heat);
        if (progress >= 1)
            smeltOne(recipe, input, result);
    }

    /**
     * Loses heat for a tick it cooked nothing, saved now and then rather than every tick.
     */
    private void cool(ServerLevel level)
    {
        if (heat <= 0)
            return;
        heat = VoidFurnaceBalance.cool(heat);
        if (heat <= 0 || level.getGameTime() % 20 == 0)
            setChanged();
    }

    private boolean fits(ItemStack result)
    {
        var output = items.get(OUTPUT);
        if (output.isEmpty())
            return true;
        return ItemStack.isSameItemSameComponents(output, result)
               && output.getCount() + result.getCount() <= Math.min(getMaxStackSize(), result.getMaxStackSize());
    }

    private void smeltOne(RecipeHolder<SmeltingRecipe> recipe, ItemStack input, ItemStack result)
    {
        var output = items.get(OUTPUT);
        if (output.isEmpty())
            items.set(OUTPUT, result.copy());
        else
            output.grow(result.getCount());
        input.shrink(1);
        // Only a player's own smelting gives experience, not an Officiant's batch
        if (ticket != null)
            smelted++;
        else
            recipesUsed.addTo(recipe.id(), 1);
        started = false;
        progress = 0;
        setChanged();
    }

    /**
     * Stops the item cooking, giving back the Fiery it burned since it never finished.
     */
    private void resetCook()
    {
        if (started && fast)
            fiery = Math.min(VoidFurnaceBalance.FIERY_TANK, fiery + VoidFurnaceBalance.FIERY_PER_ITEM);
        if (started || progress != 0)
            setChanged();
        started = false;
        fast = false;
        progress = 0;
    }

    private void record(ServerLevel level, BlockState state, long wanted, long got)
    {
        meter.record(wanted, got);
        power = meter.state();
        var flame = power == MachinePower.POWERED || power == MachinePower.WEAK
                    ? (fast ? VoidFurnaceBlock.Flame.FIERY : VoidFurnaceBlock.Flame.VOID) : VoidFurnaceBlock.Flame.NONE;
        if (state.getValue(VoidFurnaceBlock.FLAME) != flame)
            level.setBlock(worldPosition, state.setValue(VoidFurnaceBlock.FLAME, flame), Block.UPDATE_ALL);
    }

    public ContainerData data()
    {
        return data;
    }

    public MachinePower power()
    {
        return power;
    }

    public int fiery()
    {
        return fiery;
    }

    public boolean isCooking()
    {
        return started;
    }

    public boolean isFast()
    {
        return started && fast;
    }

    public double heat()
    {
        return heat;
    }

    /**
     * @return times a vanilla furnace's pace the item cooking runs at now, or would cooked now on void energy
     */
    public double pace()
    {
        return VoidFurnaceBalance.pace(heat, isFast());
    }

    public boolean smelts(ServerLevel level, ItemStack stack)
    {
        return !stack.isEmpty() && CHECK.getRecipeFor(new SingleRecipeInput(stack), level).isPresent();
    }

    // Officiants

    /**
     * @return whether nothing lies in it, so an Officiant can lay a batch
     */
    public boolean isEmptied()
    {
        return items.get(INPUT).isEmpty() && items.get(OUTPUT).isEmpty();
    }

    /**
     * Lays an Officiant's batch in the input, marked with its ticket.
     *
     * @return whether it was laid, false while anything lies in the furnace or it can't smelt the batch
     */
    public boolean lay(ItemStack batch, UUID ticket)
    {
        if (!isEmptied() || !(level instanceof ServerLevel serverLevel) || !smelts(serverLevel, batch))
            return false;
        resetCook();
        items.set(INPUT, batch.copy());
        this.ticket = ticket;
        smelted = 0;
        setChanged();
        return true;
    }

    @Nullable
    public UUID getTicket()
    {
        return ticket;
    }

    /**
     * @return how many of the batch were smelted
     */
    public int smelted()
    {
        return smelted;
    }

    /**
     * @return whether the batch laid has begun to cook at all, false while no field pays for it
     */
    public boolean hasProgressed()
    {
        return smelted > 0 || progress > 0;
    }

    /**
     * @return whether the batch is all smelted, its input empty
     */
    public boolean batchDone()
    {
        return items.get(INPUT).isEmpty();
    }

    /**
     * Takes what the batch left, its results then whatever of it never smelted, and lets go of its ticket.
     */
    public List<ItemStack> takeBatch()
    {
        var back = new ArrayList<ItemStack>();
        for (int slot : new int[] { OUTPUT, INPUT })
        {
            if (!items.get(slot).isEmpty())
                back.add(items.get(slot));
            items.set(slot, ItemStack.EMPTY);
        }
        resetCook();
        ticket = null;
        smelted = 0;
        setChanged();
        return back;
    }

    /**
     * A hand, a hopper or a menu touched the slots: an Officiant's batch is no longer what it laid.
     */
    private void touched()
    {
        if (ticket != null)
        {
            ticket = null;
            smelted = 0;
        }
    }

    // Container

    @Override
    public int getContainerSize()
    {
        return items.size();
    }

    @Override
    public boolean isEmpty()
    {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        var removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty())
        {
            touched();
            if (slot == INPUT && items.get(INPUT).isEmpty())
                resetCook();
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        touched();
        if (slot == INPUT)
            resetCook();
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        var old = items.get(slot);
        var same = !stack.isEmpty() && ItemStack.isSameItemSameComponents(old, stack);
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        touched();
        if (slot == INPUT && !same)
            resetCook();
        setChanged();
    }

    @Override
    public boolean stillValid(Player player)
    {
        return net.minecraft.world.Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent()
    {
        items.clear();
        touched();
        resetCook();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack)
    {
        if (slot != INPUT)
            return false;
        return !(level instanceof ServerLevel serverLevel) || smelts(serverLevel, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction direction)
    {
        return direction == Direction.DOWN ? SLOTS_FOR_DOWN : SLOTS_FOR_SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction)
    {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction)
    {
        return slot == OUTPUT;
    }

    // Experience, as a furnace gives it

    /**
     * Gives the player the experience of what was smelted since a player last took from the output.
     */
    public void awardExperience(ServerPlayer player)
    {
        var awarded = popExperience(player.level(), player.position());
        player.awardRecipes(awarded);
        for (var recipe : awarded)
            player.triggerRecipeCrafted(recipe, items);
    }

    private List<RecipeHolder<?>> popExperience(ServerLevel level, Vec3 position)
    {
        var awarded = new ArrayList<RecipeHolder<?>>();
        for (var entry : recipesUsed.reference2IntEntrySet())
        {
            level.recipeAccess().byKey(entry.getKey()).ifPresent(recipe ->
            {
                awarded.add(recipe);
                if (recipe.value() instanceof AbstractCookingRecipe cooking)
                {
                    var total = entry.getIntValue() * cooking.experience();
                    int reward = Mth.floor(total);
                    if (Mth.frac(total) != 0 && level.getRandom().nextFloat() < Mth.frac(total))
                        reward++;
                    ExperienceOrb.award(level, position, reward);
                }
            });
        }
        recipesUsed.clear();
        setChanged();
        return awarded;
    }

    // Fiery, through relays

    @Override
    public int spaceFor(EssenceType type)
    {
        return type == EssenceRegistry.FIERY ? Math.max(0, VoidFurnaceBalance.FIERY_TANK - fiery) : 0;
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        int taken = Math.min(amount, spaceFor(type));
        if (taken <= 0)
            return 0;
        fiery += taken;
        setChanged();
        return taken;
    }

    @Override
    public int amountOf(EssenceType type)
    {
        return 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        return 0;
    }

    // Lifecycle

    /**
     * A batch an Officiant still waits on goes back to its hoard, the rest falls like a furnace's, and the experience held is given out.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        if (level instanceof ServerLevel serverLevel)
        {
            if (ticket != null)
            {
                var done = smelted;
                var held = ticket;
                var batch = takeBatch();
                if (!OfficiantTile.handOver(level, pos.below(), held, batch, done))
                    batch.forEach(stack -> Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            }
            Containers.dropContents(level, pos, items);
            popExperience(serverLevel, Vec3.atCenterOf(pos));
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        progress = Math.clamp(tag.getDoubleOr("progress", 0), 0, 1);
        heat = Math.clamp(tag.getDoubleOr("heat", 0), 0, 1);
        started = tag.getBooleanOr("started", false) && !items.get(INPUT).isEmpty();
        fast = started && tag.getBooleanOr("fast", false);
        if (!started)
            progress = 0;
        fiery = Math.clamp(tag.getIntOr("fiery", 0), 0, VoidFurnaceBalance.FIERY_TANK);
        ticket = tag.read("ticket", UUIDUtil.CODEC).orElse(null);
        smelted = ticket == null ? 0 : Math.max(0, tag.getIntOr("smelted", 0));
        recipesUsed.clear();
        recipesUsed.putAll(tag.read("recipes_used", RECIPES_USED_CODEC).orElse(Map.of()));
        power = MachinePower.byOrdinal(tag.getIntOr("power", 0));
        meter.restore(power);
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putDouble("progress", progress);
        tag.putDouble("heat", heat);
        tag.putBoolean("started", started);
        tag.putBoolean("fast", fast);
        tag.putInt("fiery", fiery);
        if (ticket != null)
        {
            tag.store("ticket", UUIDUtil.CODEC, ticket);
            tag.putInt("smelted", smelted);
        }
        tag.store("recipes_used", RECIPES_USED_CODEC, recipesUsed);
        tag.putInt("power", power.ordinal());
    }
}
