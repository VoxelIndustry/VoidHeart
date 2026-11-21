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

    public OfficiantTile(BlockPos pos, BlockState state)
    {
        super(WorksContent.OFFICIANT_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OfficiantTile officiant)
    {
        var period = officiant.isLaying() ? LAY_TICKS : TICK_PERIOD;
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % period == Math.floorMod(pos.asLong(), period))
            officiant.serverTick(serverLevel);
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

    static List<OfficiantTile> loadedOf(UUID owner)
    {
        return LOADED.stream().filter(officiant -> !officiant.isRemoved() && owner.equals(officiant.owner)).toList();
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
        if (owner == null || !VoidPocketState.getVoidPocketState(level).hasPocket(owner))
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
            var outcome = station.poll(catalogue.source(claim.recipe()), waited);
            if (!outcome.finished())
                return;
            held.addAll(outcome.items());
            if (!outcome.failed() && !outcome.items().isEmpty())
                fold(level, station.resultFold(), outcome.items().getFirst(), OfficiantFolds.TAKE);
            if (outcome.failed())
                WorksState.get(level.getServer()).edit(owner).giveBack(claim);
            else
                WorksState.get(level.getServer()).edit(owner).finish(claim);
            claim = null;
            waited = 0;
            setChanged();
            store(level, hoard);
            return;
        }

        if (!station.isFree())
            return;

        var affordable = VoidEnergy.available(level, worldPosition) / WorksBalance.ITEM_COST;
        var picked = new WorksRecipe[1];
        var queue = WorksState.get(level.getServer()).of(owner);
        var claimed = queue.claim(station.kind(), task ->
        {
            var source = catalogue.source(task.recipe());
            if (source == null || !station.canRun(source) || !station.canSupply(source))
                return false;
            for (var recipe : catalogue.byId(task.recipe()))
            {
                var taken = Ingredients.plan(recipe, hoard);
                if (taken != null && Ingredients.count(taken) <= affordable)
                {
                    picked[0] = recipe;
                    return true;
                }
            }
            return false;
        });
        if (claimed == null)
            return;

        WorksState.get(level.getServer()).setDirty();
        @SuppressWarnings("unchecked")
        var recipe = (WorksRecipe<ItemVariant>) picked[0];
        var ingredients = Ingredients.take(recipe, hoard);
        VoidEnergy.draw(level, worldPosition, Ingredients.items(ingredients) * WorksBalance.ITEM_COST);
        claim = claimed;
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
        var laid = station.lay(step, owner, claim.recipe(), source, laying);
        held.addAll(laid.back());
        if (!laid.placed())
        {
            abortLaying(level, hoard, station);
            return;
        }
        if (station.kind() == StationKind.TABLE)
            fold(level, fold, laid.back().isEmpty() ? ItemStack.EMPTY : laid.back().getFirst(), OfficiantFolds.PASS);
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
        claim = null;
        finishLaying();
        setChanged();
        store(level, hoard);
    }

    /**
     * Lets go of a craft whose station went away or was replaced once it was laid out. A table's craft was over when laid, so it counts as done;
     * any other is lost with its station and counts again.
     */
    private void abandonClaim(ServerLevel level, PocketHoard.Hoard hoard)
    {
        var works = WorksState.get(level.getServer()).edit(owner);
        if (claim.station() == StationKind.TABLE)
            works.finish(claim);
        else
            works.giveBack(claim);
        claim = null;
        waited = 0;
        setChanged();
        store(level, hoard);
    }

    /**
     * Shows a fold opening over a station to everyone near, with a soft sound.
     */
    private void fold(ServerLevel level, OfficiantStation.Fold fold, ItemStack item, int kind)
    {
        var payload = new OfficiantFolds.Payload(fold.pos(), fold.rest(), item.copy(), kind);
        for (var player : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(level, fold.pos()))
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
        var sound = kind == OfficiantFolds.TAKE ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE;
        level.playSound(null, fold.pos(), sound, SoundSource.BLOCKS, 0.4F, 0.85F + level.getRandom().nextFloat() * 0.2F);
    }

    /**
     * Puts what is held into the hoard, paying for each item.
     *
     * @return whether everything went in
     */
    private boolean store(ServerLevel level, PocketHoard.Hoard hoard)
    {
        var iterator = held.iterator();
        while (iterator.hasNext())
        {
            var stack = iterator.next();
            var cost = stack.getCount() * WorksBalance.ITEM_COST;
            if (VoidEnergy.available(level, worldPosition) < cost)
                break;
            var put = (int) hoard.insert(ItemVariant.of(stack), stack.getCount());
            if (put > 0)
                VoidEnergy.draw(level, worldPosition, put * WorksBalance.ITEM_COST);
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
            var hoard = PocketHoard.get(serverLevel.getServer()).of(owner);
            // What was still to be laid out goes back too, and its craft counts again
            if (isLaying() && layingAt != null)
            {
                Stations.unlaid(layingAt, step, laying).forEach(stack -> held.add(stack.copy()));
                if (claim != null)
                    WorksState.get(serverLevel.getServer()).edit(owner).giveBack(claim);
                claim = null;
                finishLaying();
            }
            for (var stack : held)
            {
                stack.shrink((int) hoard.insert(ItemVariant.of(stack), stack.getCount()));
                if (!stack.isEmpty())
                    Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
            held.clear();
            // What the station still works on is no longer watched, so its order does not wait on it
            if (claim != null)
                WorksState.get(serverLevel.getServer()).edit(owner).finish(claim);
            claim = null;
        }
        super.preRemoveSideEffects(pos, state);
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
        claim = tag.read("claim", WorksQueue.Claim.CODEC).orElse(null);
        held.clear();
        held.addAll(tag.read("held", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()).stream().filter(stack -> !stack.isEmpty()).toList());
        waited = tag.getIntOr("waited", 0);
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
        if (!held.isEmpty())
            tag.store("held", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(held));
        tag.putInt("waited", waited);
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
