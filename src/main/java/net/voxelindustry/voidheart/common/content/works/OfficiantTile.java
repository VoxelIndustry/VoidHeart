package net.voxelindustry.voidheart.common.content.works;

import lombok.Getter;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.hoard.HoardReach;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Serves the station above it for its placer: takes one craft at a time from the placer's orders once the hoard holds its ingredients, lays them out
 * one by one through small folds, the catalyst last, and puts what comes back in the hoard. Each item it lays out or takes back costs void energy from
 * the field around it. What it holds is saved, so an unloaded Officiant picks up where it was, and a broken one gives it back to the hoard.
 */
public class OfficiantTile extends TileBase
{
    static final int TICK_PERIOD = 10;
    /** Ticks between two items laid out. */
    static final int LAY_TICKS = 4;

    /** Officiants loaded on the server, which the catalogue asks what stations an owner has. */
    private static final Set<OfficiantTile> LOADED = Collections.newSetFromMap(new WeakHashMap<>());

    @Getter
    @Nullable
    private UUID owner;
    @Getter
    @Nullable
    private WorksQueue.Claim claim;
    /** What goes back to the hoard: results, remainders, strays, or the ingredients of a craft that never ran. */
    private final List<ItemStack> held = new ArrayList<>();
    private int waited;

    /** The ingredients of the craft being laid out, in the order of the recipe's inputs, and how far it has got. */
    private final List<ItemStack> laying = new ArrayList<>();
    private int step;
    private int steps;
    @Nullable
    private StationKind layingAt;

    /** Marks the craft on its station, so a station put in its place or an item a player set there is never taken for its result. */
    @Nullable
    private UUID ticket;
    /** The station's own block when the craft was claimed, where its result is still found should the Officiant lose sight of the station. */
    @Nullable
    private BlockPos workedAt;
    /** Void energy gathered from the field around it, what laying out and taking back are paid from. */
    @Getter
    private long charge;
    /** Whether a field reached it when it last gathered. */
    private boolean fielded;

    /** What an Officiant with no work is at, for the roster and Jade. */
    public enum Energy
    {
        /** Charged enough to lay a craft out. */
        READY,
        /** Gathering from a field that reaches it. */
        GATHERING,
        /** No field reaches it, so it can never pay for a craft. */
        NO_FIELD
    }

    public OfficiantTile(BlockPos pos, BlockState state)
    {
        super(WorksContent.OFFICIANT_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OfficiantTile officiant)
    {
        if (level instanceof ServerLevel serverLevel)
            officiant.gather(serverLevel);
        var period = officiant.isLaying() ? LAY_TICKS : TICK_PERIOD;
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % period == Math.floorMod(pos.asLong(), period))
            officiant.serverTick(serverLevel);
    }

    /**
     * Draws what the field around it can give this tick, up to what it stores.
     */
    private void gather(ServerLevel level)
    {
        if (charge >= WorksBalance.MAX_CHARGE)
        {
            fielded = true;
            return;
        }
        // With no field last time, only looks again now and then
        if (!fielded && level.getGameTime() % TICK_PERIOD != Math.floorMod(worldPosition.asLong(), TICK_PERIOD))
            return;
        var drawn = VoidEnergy.draw(level, worldPosition, WorksBalance.MAX_CHARGE - charge);
        fielded = drawn > 0 || VoidEnergy.available(level, worldPosition) > 0;
        if (drawn <= 0)
            return;
        charge += drawn;
        setChanged();
    }

    /**
     * @return whether it holds enough to lay a craft out, is gathering towards it, or has no field to gather from
     */
    public Energy energy()
    {
        if (charge >= WorksBalance.MAX_CHARGE)
            return Energy.READY;
        return fielded ? Energy.GATHERING : Energy.NO_FIELD;
    }

    public void setOwner(UUID owner)
    {
        this.owner = owner;
        setChanged();
        sync();
    }

    /**
     * @return the kind of station above, or null when it stands under nothing it serves
     */
    public @Nullable StationKind stationKind()
    {
        var station = Stations.above(level, worldPosition);
        return station == null ? null : station.kind();
    }

    /**
     * @return the owner's loaded Officiants, leaving out those where hoards can't be reached, since they never work
     */
    static List<OfficiantTile> loadedOf(UUID owner)
    {
        return LOADED.stream().filter(officiant -> !officiant.isRemoved() && owner.equals(officiant.owner) && !HoardReach.forbidden(officiant.level)).toList();
    }

    /**
     * @return what the forge this Officiant serves can run, nothing when it serves no forge
     */
    List<ShatterForgeRecipe> forgeRecipes()
    {
        var forge = Stations.forgeAbove(level, worldPosition);
        return forge == null ? List.of() : forge.runnableRecipes();
    }

    private void serverTick(ServerLevel level)
    {
        LOADED.add(this);
        if (owner == null || HoardReach.forbidden(level) || !VoidPocketState.getVoidPocketState(level).hasPocket(owner))
            return;
        var hoard = PocketHoard.get(level.getServer()).of(owner);

        if (!held.isEmpty() && !store(level, hoard))
            return;

        var station = Stations.above(level, worldPosition);
        var claimedAt = claim == null ? null : claim.station();
        if (station == null || claimedAt != null && station.kind() != claimedAt)
        {
            // The station went away or was replaced: what was not laid out goes back, and a craft it worked counts again
            if (isLaying())
                abortLaying(level, hoard, null);
            else if (claim != null)
                abandonClaim(level, hoard);
            return;
        }

        var catalogue = WorksCatalogues.of(level.getServer(), owner, hoard);
        if (isLaying())
        {
            layStep(level, hoard, station, catalogue);
            return;
        }
        if (claim != null)
        {
            waited += TICK_PERIOD;
            var outcome = station.poll(catalogue.source(claim.recipe()), waited, ticket);
            if (!outcome.finished())
                return;
            held.addAll(outcome.items());
            if (!outcome.failed() && !outcome.items().isEmpty())
                fold(level, station.resultFold(), outcome.items().getFirst(), OfficiantFolds.TAKE);
            if (outcome.failed())
                WorksState.get(level.getServer()).edit(owner).giveBack(claim);
            else
                WorksState.get(level.getServer()).edit(owner).finish(claim);
            release();
            setChanged();
            store(level, hoard);
            return;
        }

        if (!station.isFree())
            return;

        var affordable = charge / WorksBalance.ITEM_COST;
        var picked = new WorksRecipe[1];
        var queue = WorksState.get(level.getServer()).of(owner);
        var batched = station.kind() == StationKind.FURNACE;
        var claimed = queue.claimBatch(station.kind(), task ->
        {
            var source = catalogue.source(task.recipe());
            if (source == null || !station.canRun(source) || !station.canSupply(source))
                return 0;
            for (var recipe : catalogue.byId(task.recipe()))
            {
                if (batched)
                {
                    // As many of one item as the hoard holds, up to a batch and what the charge pays to lay out
                    var batch = Ingredients.batch(recipe, hoard, Math.min(Math.min(WorksBalance.MAX_BATCH, task.remaining()), affordable));
                    if (batch != null)
                    {
                        picked[0] = recipe;
                        return batch.count();
                    }
                    continue;
                }
                var taken = Ingredients.plan(recipe, hoard);
                if (taken != null && Ingredients.count(taken) <= affordable)
                {
                    picked[0] = recipe;
                    return 1;
                }
            }
            return 0;
        });
        if (claimed == null)
            return;

        WorksState.get(level.getServer()).setDirty();
        @SuppressWarnings("unchecked")
        var recipe = (WorksRecipe<ItemVariant>) picked[0];
        var ingredients = batched ? Ingredients.takeBatch(recipe, hoard, claimed.count()) : Ingredients.take(recipe, hoard);
        charge -= Ingredients.items(ingredients) * WorksBalance.ITEM_COST;
        claim = claimed;
        ticket = UUID.randomUUID();
        workedAt = station.pos().immutable();
        waited = 0;
        held.addAll(station.clear());
        laying.clear();
        laying.addAll(ingredients);
        step = 0;
        steps = station.steps(ingredients);
        layingAt = station.kind();
        layStep(level, hoard, station, catalogue);
    }

    public boolean isLaying()
    {
        return step < steps;
    }

    /**
     * @return whether the craft with that ticket is laid out and still waited on by this Officiant
     */
    public boolean holds(@Nullable UUID ticket)
    {
        return ticket != null && claim != null && !isLaying() && ticket.equals(this.ticket);
    }

    /**
     * Takes the finished result of its craft from a station being broken, so it reaches the hoard rather than the ground and is not made again.
     *
     * @return whether the Officiant at pos took it
     */
    public static boolean handOver(@Nullable Level level, BlockPos pos, @Nullable UUID ticket, ItemStack result)
    {
        return !result.isEmpty() && handOver(level, pos, ticket, List.of(result), Integer.MAX_VALUE);
    }

    /**
     * Takes what is left of its batch from a station being broken, results and what never smelted alike, so it reaches the hoard rather than the ground.
     *
     * @param done how many of the batch's crafts were made; the rest count again
     * @return whether the Officiant at pos took it
     */
    public static boolean handOver(@Nullable Level level, BlockPos pos, @Nullable UUID ticket, List<ItemStack> items, int done)
    {
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof OfficiantTile officiant)
                || !officiant.holds(ticket) || officiant.owner == null)
            return false;
        items.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> officiant.held.add(stack.copy()));
        WorksState.get(serverLevel.getServer()).edit(officiant.owner).finish(officiant.claim, done);
        officiant.release();
        officiant.setChanged();
        return true;
    }

    private void release()
    {
        claim = null;
        ticket = null;
        workedAt = null;
        waited = 0;
    }

    /**
     * @return what it holds for the hoard, for tests
     */
    public List<ItemStack> heldItems()
    {
        return held.stream().map(ItemStack::copy).toList();
    }

    public int layingStep()
    {
        return step;
    }

    public int layingSteps()
    {
        return steps;
    }

    /**
     * Lays the next item out through a fold; the last step starts the craft.
     */
    private void layStep(ServerLevel level, PocketHoard.Hoard hoard, OfficiantStation station, WorksCatalogue catalogue)
    {
        var source = claim == null ? null : catalogue.source(claim.recipe());
        if (source == null || !station.ready(step, steps))
        {
            abortLaying(level, hoard, station);
            return;
        }
        var fold = station.foldOf(step, steps);
        var laid = station.lay(step, owner, claim.recipe(), source, laying, ticket);
        held.addAll(laid.back());
        if (!laid.placed())
        {
            abortLaying(level, hoard, station);
            return;
        }
        if (station.kind() == StationKind.TABLE)
            fold(level, fold, laid.back().isEmpty() ? ItemStack.EMPTY : laid.back().getFirst(), OfficiantFolds.PASS, laid.grid());
        else
            fold(level, fold, ItemStack.EMPTY, OfficiantFolds.LAY);
        step++;
        if (!isLaying())
            finishLaying();
        setChanged();
        store(level, hoard);
    }

    private void finishLaying()
    {
        laying.clear();
        step = 0;
        steps = 0;
        layingAt = null;
        waited = 0;
    }

    /**
     * Stops laying a craft out: what was not laid goes back, and so does what was laid if its station is still there, having been cleared for it.
     * The craft counts again.
     */
    private void abortLaying(ServerLevel level, PocketHoard.Hoard hoard, @Nullable OfficiantStation station)
    {
        if (station != null && station.kind() == layingAt)
            held.addAll(station.clear());
        if (layingAt != null)
            Stations.unlaid(layingAt, step, laying).forEach(stack -> held.add(stack.copy()));
        if (claim != null)
            WorksState.get(level.getServer()).edit(owner).giveBack(claim);
        release();
        finishLaying();
        setChanged();
        store(level, hoard);
    }

    /**
     * Lets go of a craft whose station went away or was replaced once it was laid out. A table's craft was over when laid, and what still waits where
     * the station's block stands is taken back, such as a forge's result whose source pillar was taken or a furnace's batch, the crafts it made counting
     * as done. Any other is lost with its station and counts again.
     */
    private void abandonClaim(ServerLevel level, PocketHoard.Hoard hoard)
    {
        var works = WorksState.get(level.getServer()).edit(owner);
        var left = Stations.leftAt(level, workedAt, ticket);
        held.addAll(left.items());
        if (claim.station() == StationKind.TABLE)
            works.finish(claim);
        else
            works.finish(claim, left.done());
        release();
        setChanged();
        store(level, hoard);
    }

    /**
     * Shows a fold opening over a station to everyone near, with a soft sound.
     */
    private void fold(ServerLevel level, OfficiantStation.Fold fold, ItemStack item, int kind)
    {
        fold(level, fold, item, kind, List.of());
    }

    /**
     * @param grid what a table's nine slots held, for the fold to lay out before its result shows
     */
    private void fold(ServerLevel level, OfficiantStation.Fold fold, ItemStack item, int kind, List<ItemStack> grid)
    {
        var payload = new OfficiantFolds.Payload(fold.pos(), fold.rest(), item.copy(), kind, grid.stream().map(ItemStack::copy).toList());
        for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(level, fold.pos()))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        var sound = kind == OfficiantFolds.TAKE ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE;
        level.playSound(null, fold.pos(), sound, SoundSource.BLOCKS, 0.4F, 0.85F + level.getRandom().nextFloat() * 0.2F);
    }

    /**
     * Puts what is held into the hoard, paying each item from its charge; what it can't pay for yet waits for more.
     *
     * @return whether everything went in
     */
    private boolean store(ServerLevel level, PocketHoard.Hoard hoard)
    {
        var iterator = held.iterator();
        while (iterator.hasNext())
        {
            var stack = iterator.next();
            var paid = (int) Math.min(stack.getCount(), charge / WorksBalance.ITEM_COST);
            var put = paid <= 0 ? 0 : (int) hoard.insert(ItemVariant.of(stack), paid);
            charge -= put * WorksBalance.ITEM_COST;
            stack.shrink(put);
            if (!stack.isEmpty())
                break;
            iterator.remove();
        }
        setChanged();
        return held.isEmpty();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        LOADED.remove(this);
        if (level instanceof ServerLevel serverLevel && owner != null)
        {
            var hoard = HoardReach.forbidden(serverLevel) ? null : PocketHoard.get(serverLevel.getServer()).of(owner);
            // What was still to be laid out goes back too, and its craft counts again
            if (isLaying() && layingAt != null)
            {
                Stations.unlaid(layingAt, step, laying).forEach(stack -> held.add(stack.copy()));
                if (claim != null)
                    WorksState.get(serverLevel.getServer()).edit(owner).giveBack(claim);
                release();
                finishLaying();
            }
            for (var stack : held)
            {
                if (hoard != null)
                    stack.shrink((int) hoard.insert(ItemVariant.of(stack), stack.getCount()));
                if (!stack.isEmpty())
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
            held.clear();
            // What the station still works on is no longer watched, so its order does not wait on it. A furnace's batch is taken back, what it made
            // counting as done and the rest again, so the furnace is free for the next Officiant
            if (claim != null)
            {
                var left = Stations.leftAt(level, workedAt, ticket);
                for (var stack : left.items())
                {
                    if (hoard != null)
                        stack.shrink((int) hoard.insert(ItemVariant.of(stack), stack.getCount()));
                    if (!stack.isEmpty())
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, stack);
                }
                if (left.items().isEmpty())
                    WorksState.get(serverLevel.getServer()).edit(owner).finish(claim);
                else
                    WorksState.get(serverLevel.getServer()).edit(owner).finish(claim, left.done());
            }
            release();
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
        claim = tag.read("claim", WorksQueue.Claim.CODEC).orElse(null);
        ticket = claim == null ? null : tag.read("ticket", UUIDUtil.CODEC).orElse(null);
        workedAt = claim == null ? null : tag.read("worked_at", BlockPos.CODEC).orElse(null);
        held.clear();
        held.addAll(tag.read("held", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()).stream().filter(stack -> !stack.isEmpty()).toList());
        waited = tag.getIntOr("waited", 0);
        charge = Math.clamp(tag.getLongOr("charge", 0L), 0, WorksBalance.MAX_CHARGE);
        laying.clear();
        laying.addAll(tag.read("laying", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        step = tag.getIntOr("step", 0);
        steps = tag.getIntOr("steps", 0);
        layingAt = tag.getString("laying_at").map(StationKind::valueOf).orElse(null);
        if (layingAt == null || laying.isEmpty())
            finishLaying();
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        if (owner != null)
            tag.store("owner", UUIDUtil.CODEC, owner);
        if (claim != null)
            tag.store("claim", WorksQueue.Claim.CODEC, claim);
        if (claim != null && ticket != null)
            tag.store("ticket", UUIDUtil.CODEC, ticket);
        if (claim != null && workedAt != null)
            tag.store("worked_at", BlockPos.CODEC, workedAt);
        if (!held.isEmpty())
            tag.store("held", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(held));
        tag.putInt("waited", waited);
        tag.putLong("charge", charge);
        if (isLaying() && layingAt != null)
        {
            tag.store("laying", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(laying));
            tag.putInt("step", step);
            tag.putInt("steps", steps);
            tag.putString("laying_at", layingAt.name());
        }
        super.saveAdditional(tag);
    }
}
