package net.voxelindustry.voidheart.common.content.works;

import lombok.Getter;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
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
 * Serves the station above it for its placer: takes one craft at a time from the placer's orders once the hoard holds its ingredients, lays them out,
 * and puts what comes back in the hoard. Each item it lays out or takes back costs void energy from the field around it. What it holds is saved, so
 * an unloaded Officiant picks up where it was, and a broken one gives it back to the hoard.
 */
public class OfficiantTile extends TileBase
{
    static final int TICK_PERIOD = 10;

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

    public OfficiantTile(BlockPos pos, BlockState state)
    {
        super(WorksContent.OFFICIANT_TILE, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, OfficiantTile officiant)
    {
        if (level instanceof ServerLevel serverLevel && level.getGameTime() % TICK_PERIOD == Math.floorMod(pos.asLong(), TICK_PERIOD))
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
        if (station == null)
            return;

        var catalogue = WorksCatalogues.of(level.getServer(), owner, hoard);
        if (claim != null)
        {
            waited += TICK_PERIOD;
            var outcome = station.poll(catalogue.source(claim.recipe()), waited);
            if (!outcome.finished())
                return;
            held.addAll(outcome.items());
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
        held.addAll(station.begin(owner, claimed.recipe(), catalogue.source(claimed.recipe()), ingredients));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, 24, 0.4, 0.3, 0.4, 0.05);
        level.playSound(null, worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.3F, 1.4F);
        setChanged();
        store(level, hoard);
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
        super.saveAdditional(tag);
    }
}
