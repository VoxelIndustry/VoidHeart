package net.voxelindustry.voidheart.common.content.athanor;

import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RelayNetwork;
import net.voxelindustry.voidheart.common.content.oil.Brew;
import net.voxelindustry.voidheart.common.content.oil.Brews;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.energy.VoidEnergy;
import net.voxelindustry.voidheart.common.energy.machine.DrawCredit;
import net.voxelindustry.voidheart.common.energy.machine.MachineEnergy;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;
import net.voxelindustry.voidheart.common.energy.machine.PowerMeter;
import net.voxelindustry.voidheart.common.recipe.AthanorRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The seething steel furnace at the heart of an athanor. Its base sits inside it. Once the reagents of the crucibles beside it answer a recipe, the
 * intake below gathers the aspects it needs, then the brew runs and leaves its product where the base was. Once refinement is researched, an Azurium
 * or Rubrum shard in a crucible the recipe doesn't use strengthens or prolongs the brew, and a sin-metal flask can stand in for the glass bottle. A brew draws
 * void energy as it runs, slows on a weak supply and waits without one.
 */
public class AthanorTile extends TileBase
{
    public static final int CHECK_INTERVAL = 10;
    public static final Identifier REFINEMENT = Identifier.fromNamespaceAndPath(MODID, "refinement");

    private static final Codec<Map<Identifier, Integer>> GATHERED_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.INT);

    @Getter
    private ItemStack stack = ItemStack.EMPTY;
    /**
     * Who set the base, whose research decides which brews it may start.
     */
    @Nullable
    private UUID owner;

    private final Map<EssenceType, Integer> gathered = new LinkedHashMap<>();

    @Getter
    private boolean brewing;
    @Getter
    private int progress;
    @Nullable
    private Identifier recipeId;

    @Nullable
    private AthanorRecipe currentRecipe;
    private AthanorLayout layout = new AthanorLayout(false, List.of(), false);
    /**
     * Crucible of each reagent of the current recipe, in reagent order.
     */
    private List<BlockPos> reagentSources = List.of();
    /**
     * Shards and vessel of the current brew, or null until matched again after a reload, like the crucibles.
     */
    @Nullable
    private Brew brew;

    private final DrawCredit   brewCredit = new DrawCredit();
    private final PowerMeter   meter      = new PowerMeter();
    /**
     * How well the brew's draw is met, synced so the fire goes out while it waits.
     */
    @Getter
    private       MachinePower power      = MachinePower.IDLE;

    public AthanorTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.ATHANOR, pos, state);
    }

    @Override
    public void setLevel(Level world)
    {
        super.setLevel(world);

        // Called on world load
        sync();
    }

    @Override
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        stack = tag.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
        brewing = tag.getBooleanOr("brewing", false);
        progress = tag.getIntOr("progress", 0);
        recipeId = tag.read("recipe", Identifier.CODEC).orElse(null);
        power = MachinePower.byOrdinal(tag.getIntOr("power", 0));
        meter.restore(power);

        gathered.clear();
        tag.read("gathered", GATHERED_CODEC).orElse(Map.of()).forEach((id, amount) ->
        {
            if (EssenceRegistry.ESSENCE_REGISTRY != null && EssenceRegistry.ESSENCE_REGISTRY.containsKey(id) && amount > 0)
                gathered.put(EssenceRegistry.ESSENCE_REGISTRY.getValue(id), amount);
        });

        currentRecipe = null;
        brew = null;
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("stack", ItemStack.OPTIONAL_CODEC, stack);
        if (owner != null)
            tag.store("owner", UUIDUtil.CODEC, owner);
        tag.putBoolean("brewing", brewing);
        tag.putInt("progress", progress);
        if (recipeId != null)
            tag.store("recipe", Identifier.CODEC, recipeId);
        tag.putInt("power", power.ordinal());

        var gatheredIds = new LinkedHashMap<Identifier, Integer>();
        gathered.forEach((type, amount) -> gatheredIds.put(type.identifier(), amount));
        tag.store("gathered", GATHERED_CODEC, gatheredIds);

        super.saveAdditional(tag);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, AthanorTile athanor)
    {
        if (athanor.isClient())
        {
            athanor.clientTick();
            return;
        }
        athanor.serverTick();
    }

    /**
     * Sets the base, or takes it back with an empty stack, which stops any brew. Aspects already gathered stay for the next one.
     */
    public void setStack(@Nullable ServerPlayer player, ItemStack stack)
    {
        this.stack = stack;
        if (player != null)
            owner = player.getUUID();

        stopBrewing();
        if (!stack.isEmpty())
            evaluate();

        setChanged();
        sync();
    }

    private void serverTick()
    {
        if (brewing)
        {
            if (Math.floorMod(level.getGameTime() + worldPosition.asLong(), CHECK_INTERVAL) == 0 && !stillMatches())
            {
                stopBrewing();
                sync();
                return;
            }

            var recipe = getCurrentRecipe();
            if (recipe == null)
            {
                stopBrewing();
                sync();
                return;
            }

            long need = MachineEnergy.shareOfTick(brewCost(), recipe.getTime(), progress);
            boolean paid = brewCredit.pay(need, amount -> VoidEnergy.draw((ServerLevel) level, worldPosition, amount));
            recordPower(need, brewCredit.lastDrawn());
            if (!paid)
            {
                setChanged();
                return;
            }

            progress++;
            if (progress >= recipe.getTime())
            {
                // The crucibles to empty are only known once matched again, as after a reload
                if (stillMatches())
                    finishBrewing();
                else
                {
                    stopBrewing();
                    sync();
                }
            }
            setChanged();
            return;
        }

        recordPower(0, 0);
        if (stack.isEmpty() || Math.floorMod(level.getGameTime() + worldPosition.asLong(), CHECK_INTERVAL) != 0)
            return;
        evaluate();
    }

    private void recordPower(long want, long got)
    {
        if (!meter.record(want, got))
            return;
        power = meter.state();
        if (brewing)
            setLit(power != MachinePower.UNPOWERED);
        setChanged();
        sync();
    }

    /**
     * Void energy the whole of the current brew draws, more for each refinement. Nothing without a recipe.
     */
    public long brewCost()
    {
        var recipe = getCurrentRecipe();
        if (recipe == null)
            return 0;
        int aspects = recipe.getAspects().values().stream().mapToInt(Integer::intValue).sum();
        var brew = getBrew();
        int refinements = (brew.empowered() ? 1 : 0) + (brew.prolonged() ? 1 : 0) + (brew.remembering() ? 1 : 0);
        return MachineEnergy.brew(aspects, refinements);
    }

    /**
     * What the brew under way draws each tick, on average, or nothing while it isn't brewing.
     */
    public long drawRate()
    {
        var recipe = getCurrentRecipe();
        if (!brewing || recipe == null || recipe.getTime() <= 0)
            return 0;
        return Math.round(brewCost() / (double) recipe.getTime());
    }

    private void clientTick()
    {
        if (!brewing || power == MachinePower.UNPOWERED)
            return;

        var random = level.getRandom();
        if (random.nextInt(4) == 0)
            level.addParticle(ParticleTypes.SMOKE, worldPosition.getX() + 0.5 + random.nextGaussian() * 0.06, worldPosition.getY() + 2.0,
                    worldPosition.getZ() + 0.5 + random.nextGaussian() * 0.06, 0, 0.03, 0);
        if (random.nextInt(8) == 0)
            level.addParticle(ParticleTypes.FLAME, worldPosition.getX() + 0.5 + random.nextGaussian() * 0.12, worldPosition.getY() + 0.2,
                    worldPosition.getZ() + 0.5 + random.nextGaussian() * 0.12, 0, 0.01, 0);
    }

    /**
     * The parts around the core as they stand now.
     */
    public AthanorLayout getLayout()
    {
        return layout = scanLayout();
    }

    private AthanorLayout scanLayout()
    {
        return AthanorLayout.scan(worldPosition, pos ->
        {
            var block = level.getBlockState(pos).getBlock();
            if (block == VoidHeartBlocks.ALEMBIC)
                return AthanorLayout.Part.ALEMBIC;
            if (block == VoidHeartBlocks.CRUCIBLE)
                return AthanorLayout.Part.CRUCIBLE;
            if (block == VoidHeartBlocks.ASPECT_INTAKE)
                return AthanorLayout.Part.INTAKE;
            return AthanorLayout.Part.NONE;
        });
    }

    /**
     * @param sources the crucibles the brew empties: one per reagent, in reagent order, then the shards set aside
     */
    private record Match(AthanorRecipe recipe, List<BlockPos> sources, Brew brew)
    {
    }

    @Nullable
    private Match findMatch()
    {
        if (!layout.canBrew() || stack.isEmpty())
            return null;

        var crucibles = new ArrayList<CrucibleTile>();
        for (var side : layout.crucibles())
        {
            if (level.getBlockEntity(worldPosition.relative(side)) instanceof CrucibleTile crucible)
                crucibles.add(crucible);
        }

        boolean refining = mayRefine();
        var vessel = Brews.vesselOf(stack);
        if (vessel != null && !refining)
            return null;
        // A sin-metal flask is a bottle to every recipe, and decides after whether it can hold what that recipe makes
        var base = vessel == null ? stack : new ItemStack(Items.GLASS_BOTTLE);
        var asides = refining
                ? AthanorMath.<CrucibleTile>asides(crucibles, crucible -> crucible.getStack().is(VoidHeartItems.AZURIUM_SHARD),
                crucible -> crucible.getStack().is(VoidHeartItems.RUBRUM_SHARD), crucible -> crucible.getStack().is(VoidHeartItems.MEMORIAM_SHARD))
                : List.of(new int[0]);

        for (var aside : asides)
        {
            var reagents = new ArrayList<>(crucibles);
            var setAside = new ArrayList<CrucibleTile>();
            for (int index : aside)
                setAside.add(crucibles.get(index));
            reagents.removeAll(setAside);
            boolean empowered = setAside.stream().anyMatch(crucible -> crucible.getStack().is(VoidHeartItems.AZURIUM_SHARD));
            boolean prolonged = setAside.stream().anyMatch(crucible -> crucible.getStack().is(VoidHeartItems.RUBRUM_SHARD));
            boolean remembering = setAside.stream().anyMatch(crucible -> crucible.getStack().is(VoidHeartItems.MEMORIAM_SHARD));

            for (var recipe : allRecipes())
            {
                if (!recipe.getBase().match(base))
                    continue;

                var assignment = AthanorMath.matchUnordered(recipe.getReagents(), reagents, crucible -> !crucible.getStack().isEmpty(),
                        (ingredient, crucible) -> ingredient.match(crucible.getStack()));
                if (assignment == null)
                    continue;

                var brew = Brews.brewFor(recipe.getOutput(), empowered, prolonged, remembering, vessel);
                if (brew.isEmpty())
                    continue;

                var sources = new ArrayList<BlockPos>();
                for (int index : assignment)
                    sources.add(reagents.get(index).getBlockPos());
                for (var crucible : setAside)
                    sources.add(crucible.getBlockPos());
                return new Match(recipe, sources, brew.get());
            }
        }
        return null;
    }

    /**
     * Whether whoever set the base knows how to refine a brew.
     */
    private boolean mayRefine()
    {
        if (owner == null || !(level instanceof ServerLevel serverLevel))
            return false;
        return Knowledge.hasCompleted(serverLevel.getServer(), owner, REFINEMENT);
    }

    public Brew getBrew()
    {
        return brew == null ? Brew.PLAIN : brew;
    }

    /**
     * Recipes from data packs live apart from those added in code.
     */
    private static List<AthanorRecipe> allRecipes()
    {
        var recipes = new ArrayList<AthanorRecipe>(VoidHeartRecipes.ATHANOR_CATEGORY.getVanillaRecipes());
        recipes.addAll(VoidHeartRecipes.ATHANOR_CATEGORY.getRecipes());
        return recipes;
    }

    private boolean allowed(AthanorRecipe recipe)
    {
        if (Knowledge.lockOf(level.registryAccess(), recipe.getId()).isEmpty())
            return true;
        if (owner == null || !(level instanceof ServerLevel serverLevel))
            return false;

        var player = serverLevel.getServer().getPlayerList().getPlayer(owner);
        return player != null && Knowledge.canUse(player, recipe.getId());
    }

    private void evaluate()
    {
        layout = scanLayout();
        var match = findMatch();
        if (match == null || !allowed(match.recipe()))
        {
            setRecipe(null);
            return;
        }

        setRecipe(match.recipe());
        reagentSources = match.sources();
        brew = match.brew();

        if (!AthanorMath.missing(match.recipe().getAspects(), gathered).isEmpty() && layout.intake())
            drawFromStorages();

        if (AthanorMath.missing(match.recipe().getAspects(), gathered).isEmpty())
            startBrewing();
    }

    private void setRecipe(@Nullable AthanorRecipe recipe)
    {
        var id = recipe == null ? null : recipe.getId();
        currentRecipe = recipe;
        if (id == null ? recipeId != null : !id.equals(recipeId))
        {
            recipeId = id;
            setChanged();
            sync();
        }
    }

    /**
     * Storages the intake's relays reach make up what the collectors haven't brought yet.
     */
    private void drawFromStorages()
    {
        var intakePos = worldPosition.below();
        AthanorMath.missing(currentRecipe.getAspects(), gathered).forEach((aspect, missing) ->
        {
            int needed = Math.min(missing, RelayNetwork.TRANSFER_AMOUNT);
            for (var storage : RelayNetwork.suppliersOfReceiver(level, intakePos, aspect).values())
            {
                int taken = storage.extract(aspect, needed);
                if (taken > 0)
                    gathered.merge(aspect, taken, Integer::sum);
                needed -= taken;
                if (needed <= 0)
                    break;
            }
        });
        setChanged();
    }

    /**
     * @return how much of the aspect the recipe waiting for its aspects still lacks
     */
    public int missingAspect(EssenceType type)
    {
        if (brewing || currentRecipe == null)
            return 0;
        return AthanorMath.missing(currentRecipe.getAspects(), gathered).getOrDefault(type, 0);
    }

    public int receiveAspect(EssenceType type, int amount)
    {
        int taken = Math.min(amount, missingAspect(type));
        if (taken <= 0)
            return 0;

        gathered.merge(type, taken, Integer::sum);
        setChanged();
        return taken;
    }

    public Map<EssenceType, Integer> getGathered()
    {
        return Map.copyOf(gathered);
    }

    @Nullable
    public AthanorRecipe getCurrentRecipe()
    {
        if (currentRecipe == null && recipeId != null)
            currentRecipe = VoidHeartRecipes.ATHANOR_CATEGORY.getRecipe(recipeId).orElse(null);
        return currentRecipe;
    }

    private void startBrewing()
    {
        brewing = true;
        progress = 0;
        setLit(power != MachinePower.UNPOWERED);
        level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.4F, 0.6F);
        setChanged();
        sync();
    }

    private void stopBrewing()
    {
        if (!brewing)
            return;
        brewing = false;
        progress = 0;
        setLit(false);
        setChanged();
    }

    private boolean stillMatches()
    {
        if (getCurrentRecipe() == null)
            return false;

        layout = scanLayout();
        var match = findMatch();
        if (match == null || !match.recipe().getId().equals(currentRecipe.getId()))
            return false;
        // A shard taken away or added mid-brew makes it another brew, which has to start over
        if (brew != null && !match.brew().equals(brew))
            return false;
        reagentSources = match.sources();
        brew = match.brew();
        return true;
    }

    private void finishBrewing()
    {
        var recipe = currentRecipe;
        for (var source : reagentSources)
        {
            if (level.getBlockEntity(source) instanceof CrucibleTile crucible)
                crucible.setStack(ItemStack.EMPTY);
        }

        var left = AthanorMath.consume(gathered, recipe.getAspects());
        gathered.clear();
        gathered.putAll(left);

        stack = Brews.apply(recipe.getOutput().copy(), getBrew());
        brew = null;
        brewing = false;
        progress = 0;
        setRecipe(null);
        reagentSources = List.of();
        setLit(false);

        if (level instanceof ServerLevel serverLevel)
        {
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, worldPosition.getX() + 0.5, worldPosition.getY() + 2.0, worldPosition.getZ() + 0.5,
                    12, 0.1, 0.1, 0.1, 0.02);
            serverLevel.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1F, 0.8F);
        }

        setChanged();
        sync();
    }

    private void setLit(boolean lit)
    {
        var state = getBlockState();
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT) != lit)
            level.setBlockAndUpdate(worldPosition, state.setValue(BlockStateProperties.LIT, lit));
    }

    /**
     * @return 0 to 1 through the current brew
     */
    public float brewProgress()
    {
        var recipe = getCurrentRecipe();
        return !brewing || recipe == null || recipe.getTime() <= 0 ? 0 : Math.min(1, progress / (float) recipe.getTime());
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        super.preRemoveSideEffects(pos, state);
    }
}
