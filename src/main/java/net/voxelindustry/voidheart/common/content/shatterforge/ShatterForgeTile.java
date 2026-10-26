package net.voxelindustry.voidheart.common.content.shatterforge;

import net.voxelindustry.voidheart.common.content.beam.BeamComposition;
import net.voxelindustry.voidheart.common.content.beam.BeamResolution;
import net.voxelindustry.voidheart.common.content.beam.BeamResolver;
import net.voxelindustry.voidheart.common.content.beam.BeamSegment;
import net.voxelindustry.voidheart.common.content.beam.FormationError;
import net.voxelindustry.voidheart.common.content.beam.LevelBeamWorldView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.steamlayer.network.tilesync.PartialSyncedTile;
import net.voxelindustry.steamlayer.network.tilesync.PartialTileSync;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.steamlayer.recipe.state.RecipeState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.inventorymover.SingleStackExtractable;
import net.voxelindustry.voidheart.common.content.pillar.PillarLinkedTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.util.Collections.singletonList;

public class ShatterForgeTile extends TileBase implements PillarLinkedTile, PartialSyncedTile, SingleStackExtractable
{
    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private VoidPillarTile pillar;
    private VoidPillarTile candidatePillar;

    private ShatterForgeRecipe currentRecipe;
    @Getter
    private RecipeState recipeState;
    private boolean hasOutputtedResult;

    private int recipeProgress;
    @Getter
    private boolean isCrafting;

    @Getter
    private int warmProgress;

    private final List<BlockPos> connectedBlocks = new ArrayList<>();
    private FormationError formationError;
    @Getter
    private List<BeamSegment> beamSegments = List.of();
    private BeamComposition beamComposition = BeamComposition.EMPTY;
    /**
     * Collectors the current recipe draws essence from.
     */
    @Getter
    private List<BlockPos> drainingCollectors = List.of();

    private int connectedMonolithCount;
    private Direction trackDirection;
    private BlockPos sourcePillarPos;

    private static final int PREVIEW_DURATION_TICKS = 30 * 20;

    @Getter
    private int previewPairs = -1;
    @Getter
    private Direction previewDirection = Direction.NORTH;
    private long previewUntil;

    private final Collection<Identifier> syncElements = singletonList(ShatterForgeCraftingSyncElement.IDENTIFIER);

    ////////////
    // CLIENT //
    ////////////

    @Getter
    private ItemStack clientRecipeInput = ItemStack.EMPTY;
    @Getter
    private ItemStack clientRecipeOutput = ItemStack.EMPTY;
    private BlockPos pillarPos;
    private final long[] runeHeartbeats = new long[ShatterForgeLayout.MAX_TRACK_PAIRS * 4];

    /**
     * Height above the forge level at which beams, item particles and source items line up.
     */
    public static final float BEAM_HEIGHT = 1.35F;

    private static final int BEAM_FORM_MIN_TICKS = 30;

    private int beamFormTicks;
    private int previousBeamFormTicks;
    /**
     * Last non-empty beam segments, kept so the beam can dissolve after the craft stops.
     */
    @Getter
    private List<BeamSegment> displayedBeamSegments = List.of();

    public ShatterForgeTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.SHATTER_FORGE, pos, state);
    }

    private final BlockPos.MutableBlockPos particlePosCache = new BlockPos.MutableBlockPos();

    private void playParticleEffects()
    {
        if (connectedMonolithCount != 0)
        {
            var gameTime = getLevel().getGameTime();
            var litRunes = Math.min(litRuneCount(), runeHeartbeats.length);
            particlePosCache.set(getBlockPos());
            for (int i = 0; i < litRunes; i++)
            {
                if (gameTime - runeHeartbeats[i] > 2)
                {
                    runeHeartbeats[i] = gameTime;
                    getLevel().addParticle(new ShatterForgeRuneParticleEffect(getBlockState().getValue(BlockStateProperties.HORIZONTAL_AXIS), getBlockPos(), i),
                            particlePosCache.getX() + 0.5,
                            particlePosCache.getY() + 1.5,
                            particlePosCache.getZ() + 0.5,
                            0,
                            0,
                            0);
                }
                particlePosCache.move(trackDirection, 3);
            }
        }

        if (isCrafting && recipeProgress > 1 && pillarPos != null && !clientRecipeOutput.isEmpty())
        {
            var beamY = getBlockPos().getY() + BEAM_HEIGHT;
            for (var segment : itemPath())
            {
                for (int i = 0; i < 2; i++)
                {
                    getLevel().addParticle(new ShatterForgeItemParticleEffect(clientRecipeInput, clientRecipeOutput),
                            segment.from().getX() + 0.5,
                            beamY,
                            segment.from().getZ() + 0.5,
                            segment.to().getX() + 0.5,
                            beamY,
                            segment.to().getZ() + 0.5);
                }
            }
        }
    }

    /**
     * Beam segments the input item travels through, from its pillar to the forge.
     */
    private List<BeamSegment> itemPath()
    {
        var path = new ArrayList<BeamSegment>();
        var current = pillarPos;
        while (current != null && !current.equals(getBlockPos()) && path.size() < beamSegments.size())
        {
            BeamSegment next = null;
            for (var segment : beamSegments)
            {
                if (segment.from().equals(current))
                    next = segment;
            }
            if (next == null)
                break;
            path.add(next);
            current = next.to();
        }
        return path;
    }

    public int getWarmingTime()
    {
        return connectedMonolithCount * 25;
    }

    private int getBeamFormDuration()
    {
        return Math.max(BEAM_FORM_MIN_TICKS, getWarmingTime());
    }

    /**
     * Beams form over the warm-up while crafting and dissolve at the same pace once it stops.
     */
    private void tickBeamFormation()
    {
        if (!beamSegments.isEmpty())
            displayedBeamSegments = beamSegments;

        var duration = getBeamFormDuration();
        previousBeamFormTicks = Math.min(beamFormTicks, duration);
        if (isCrafting && !displayedBeamSegments.isEmpty())
            beamFormTicks = Math.min(duration, beamFormTicks + 1);
        else
            beamFormTicks = Math.max(0, beamFormTicks - 1);
    }

    /**
     * @return 0 when no beam is shown, 1 once the beam is fully formed
     */
    public float getBeamFormation(float partialTicks)
    {
        var ticks = previousBeamFormTicks + (beamFormTicks - previousBeamFormTicks) * partialTicks;
        return Math.clamp(ticks / getBeamFormDuration(), 0F, 1F);
    }

    private int litRuneCount()
    {
        if (!isCrafting || trackDirection == null)
            return 0;
        if (getWarmingTime() == 0)
            return connectedMonolithCount + 1;
        return (int) Math.ceil((connectedMonolithCount + 1) * Math.min(1F, warmProgress / (float) getWarmingTime()));
    }

    public boolean isRuneLit(int index)
    {
        return index < litRuneCount();
    }

    public void runeHeartbeat(int index)
    {
        if (index < runeHeartbeats.length && level != null)
            runeHeartbeats[index] = level.getGameTime();
    }

    @Override
    public void removePillar(VoidPillarTile pillar)
    {
        if (this.getPillar() != pillar)
            return;

        this.pillar = null;
        this.pillarPos = null;
        if (this.isCrafting)
            stopCrafting();
    }

    @Override
    public void onPillarStackChanged(VoidPillarTile pillar)
    {
        if (isServer() && !isCrafting && this.getPillar() == pillar)
            tryStartCrafting();
    }

    private void unlinkPillar()
    {
        if (pillar != null)
            pillar.removeAltar(getBlockPos());
        pillar = null;
        pillarPos = null;
    }

    private void tryStartCrafting()
    {
        // The output slot must be emptied before the next craft starts
        if (isCrafting || level == null || !stack.isEmpty())
            return;

        if (isStructureCorrect())
        {
            if (pillar != candidatePillar)
                unlinkPillar();

            pillar = candidatePillar;
            pillarPos = pillar.getBlockPos();
            pillar.addAltar(getBlockPos());

            startCrafting();
            setChanged();
            sync();
        }
        else
            unlinkPillar();
    }


    private boolean isStructureCorrect()
    {
        connectedBlocks.clear();
        candidatePillar = null;
        currentRecipe = null;

        var resolution = BeamResolver.resolve(level, worldPosition);
        markSourcePillar(resolution);
        if (resolution.error().isPresent())
        {
            updateBeamState(List.of(), BeamComposition.EMPTY, resolution.error().get());
            return false;
        }

        if (resolution.itemSource().isEmpty() || !(level.getBlockEntity(resolution.itemSource().get()) instanceof VoidPillarTile pillarTile))
        {
            updateBeamState(List.of(), BeamComposition.EMPTY, FormationError.NO_PILLAR_AT_END);
            return false;
        }

        updateBeamState(resolution.segments(), resolution.result(), null);

        var inputStack = pillarTile.getStack();
        if (inputStack.isEmpty())
            return false;

        var matching = VoidHeartRecipes.SHATTER_FORGE_CATEGORY.findRecipes(inputStack).toList();
        if (matching.isEmpty())
            return false;

        var aspectMatching = matching.stream()
                .filter(recipe -> resolution.result().satisfies(recipe.getAspects()))
                .toList();
        var passing = aspectMatching.stream()
                .filter(recipe -> resolution.totalPairs() + 1 >= recipe.getMonolithCount())
                .sorted(Comparator.comparingInt(ShatterForgeRecipe::totalAspectRequirement)
                        .thenComparingInt(ShatterForgeRecipe::getMonolithCount)
                        .reversed()
                        .thenComparing(recipe -> recipe.getId().toString()))
                .toList();

        if (passing.isEmpty())
        {
            updateBeamState(resolution.segments(), resolution.result(),
                    aspectMatching.isEmpty() ? FormationError.MISSING_ASPECTS : FormationError.TOO_FEW_MONOLITH);
            return false;
        }

        ShatterForgeRecipe selected = null;
        for (var recipe : passing)
        {
            if (hasEnoughEssence(recipe, resolution.collectorContributions()))
            {
                selected = recipe;
                break;
            }
        }

        if (selected == null)
        {
            updateBeamState(resolution.segments(), resolution.result(), FormationError.NOT_ENOUGH_ESSENCE);
            return false;
        }

        collectLitBlocks(resolution.structureBlocks());
        updateDrainingCollectors(selected, resolution.collectorContributions());

        var axis = getBlockState().getValue(BlockStateProperties.HORIZONTAL_AXIS);
        var front = axis == Axis.Z ? Direction.NORTH : Direction.WEST;
        trackDirection = level.getBlockState(worldPosition.below().relative(front)).is(VoidHeartBlocks.VOID_CONDUIT) ? front : front.getOpposite();

        candidatePillar = pillarTile;
        currentRecipe = selected;
        connectedMonolithCount = resolution.totalPairs();
        return true;
    }

    private boolean isCraftingStillValid()
    {
        var resolution = BeamResolver.resolve(level, worldPosition);
        markSourcePillar(resolution);
        if (resolution.error().isPresent())
        {
            updateBeamState(List.of(), BeamComposition.EMPTY, resolution.error().get());
            return false;
        }

        var linkedPillar = getPillar();
        if (linkedPillar == null || resolution.itemSource().isEmpty() || !resolution.itemSource().get().equals(linkedPillar.getBlockPos()))
        {
            updateBeamState(List.of(), BeamComposition.EMPTY, FormationError.NO_PILLAR_AT_END);
            return false;
        }

        FormationError error = null;
        if (resolution.totalPairs() + 1 < currentRecipe.getMonolithCount())
            error = FormationError.TOO_FEW_MONOLITH;
        else if (!resolution.result().satisfies(currentRecipe.getAspects()))
            error = FormationError.MISSING_ASPECTS;
        else if (!hasEnoughEssence(currentRecipe, resolution.collectorContributions()))
            error = FormationError.NOT_ENOUGH_ESSENCE;

        updateBeamState(resolution.segments(), resolution.result(), error);
        if (error != null)
            return false;

        connectedMonolithCount = resolution.totalPairs();
        collectLitBlocks(resolution.structureBlocks());
        updateDrainingCollectors(currentRecipe, resolution.collectorContributions());
        for (var pos : connectedBlocks)
        {
            var state = level.getBlockState(pos);
            if (!state.getValue(BlockStateProperties.LIT))
                level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
        }
        return true;
    }

    /**
     * Also rebuilds the list after a reload mid-craft, where it is not persisted.
     */
    private void collectLitBlocks(List<BlockPos> structureBlocks)
    {
        connectedBlocks.clear();
        for (var structurePos : structureBlocks)
        {
            var state = level.getBlockState(structurePos);
            if (state.is(VoidHeartBlocks.VOID_CONDUIT) || state.is(VoidHeartBlocks.VOID_MONOLITH))
                connectedBlocks.add(structurePos.immutable());
        }
    }

    private record CollectorDrain(RavenousCollectorTile collector, EssenceType type, int amount)
    {
    }

    /**
     * Kept after the craft stops so the beam can dissolve with its collector strands.
     */
    private void updateDrainingCollectors(ShatterForgeRecipe recipe, Map<BlockPos, Integer> contributions)
    {
        var collectors = computeDrains(recipe, contributions).stream()
                .map(drain -> drain.collector().getBlockPos().immutable())
                .sorted()
                .toList();
        if (collectors.equals(drainingCollectors))
            return;

        drainingCollectors = collectors;
        setChanged();
        sync();
    }

    private List<CollectorDrain> computeDrains(ShatterForgeRecipe recipe, Map<BlockPos, Integer> contributions)
    {
        var drains = new ArrayList<CollectorDrain>();
        var totalRequirement = recipe.totalAspectRequirement();
        if (recipe.getDraw() == 0 || totalRequirement == 0)
            return drains;

        var collectors = new HashMap<BlockPos, RavenousCollectorTile>();
        var aspectTotals = new HashMap<EssenceType, Integer>();
        contributions.forEach((collectorPos, contribution) ->
        {
            if (level.getBlockEntity(collectorPos) instanceof RavenousCollectorTile collector)
            {
                collectors.put(collectorPos, collector);
                aspectTotals.merge(collector.storage().type(), contribution, Integer::sum);
            }
        });

        collectors.forEach((collectorPos, collector) ->
        {
            var aspect = collector.storage().type();
            var required = recipe.getAspects().getOrDefault(aspect, 0);
            var aspectTotal = aspectTotals.getOrDefault(aspect, 0);
            if (required == 0 || aspectTotal == 0)
                return;

            var share = contributions.get(collectorPos) / (double) aspectTotal;
            var amount = (int) Math.ceil(recipe.getDraw() * required / (double) totalRequirement * share);
            if (amount > 0)
                drains.add(new CollectorDrain(collector, aspect, amount));
        });
        return drains;
    }

    private boolean hasEnoughEssence(ShatterForgeRecipe recipe, Map<BlockPos, Integer> contributions)
    {
        return computeDrains(recipe, contributions).stream().allMatch(drain -> drain.collector().storage().amount() >= drain.amount());
    }

    private void drainEssence(ShatterForgeRecipe recipe, Map<BlockPos, Integer> contributions)
    {
        for (var drain : computeDrains(recipe, contributions))
        {
            drain.collector().storage().drain(drain.type(), drain.amount());
            drain.collector().setChanged();
            drain.collector().sync();
        }
    }

    /**
     * Tells the item source pillar of a valid structure to raise its item to the beam height, and the previous one to
     * lower it back.
     */
    private void markSourcePillar(BeamResolution resolution)
    {
        BlockPos source = null;
        if (resolution.isValid() && resolution.itemSource().isPresent() && level.getBlockEntity(resolution.itemSource().get()) instanceof VoidPillarTile)
            source = resolution.itemSource().get();
        setSourcePillar(source);
    }

    private void setSourcePillar(BlockPos source)
    {
        if (Objects.equals(source, sourcePillarPos))
            return;

        if (sourcePillarPos != null && level.getBlockEntity(sourcePillarPos) instanceof VoidPillarTile previous)
            previous.clearSourceOfForge(worldPosition);
        sourcePillarPos = source;
        if (source != null && level.getBlockEntity(source) instanceof VoidPillarTile current)
            current.setSourceOfForge(worldPosition);
        setChanged();
    }

    private void updateBeamState(List<BeamSegment> segments, BeamComposition composition, FormationError error)
    {
        var changed = !segments.equals(beamSegments) || !composition.equals(beamComposition) || error != formationError;

        beamSegments = segments;
        beamComposition = composition;
        formationError = error;

        if (changed && isServer())
        {
            setChanged();
            sync();
        }
    }

    public boolean isPreviewActive()
    {
        return previewPairs >= 0 && !isCrafting && level != null && level.getGameTime() < previewUntil;
    }

    /**
     * First use shows the smallest recipe track, or the track already built when it is longer or closed.
     * Later uses grow an open track by one monolith pair, or shrink it when sneaking.
     */
    public void stepPreview(Player player, boolean shrink)
    {
        var view = new LevelBeamWorldView(level);
        var active = isPreviewActive();

        if (!active)
            previewDirection = ShatterForgeLayout.inferDirection(view, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_AXIS));

        var built = ShatterForgeLayout.completeSegments(view, worldPosition, previewDirection);
        var closed = ShatterForgeLayout.isEmitter(view, ShatterForgeLayout.endSlot(worldPosition, previewDirection, built));

        if (!active)
            previewPairs = closed ? built : Math.max(minimumTrackPairs(), built);
        else if (shrink)
            previewPairs = Math.max(0, previewPairs - 1);
        else if (!closed)
            previewPairs = Math.min(ShatterForgeLayout.MAX_TRACK_PAIRS, previewPairs + 1);

        previewUntil = level.getGameTime() + PREVIEW_DURATION_TICKS;
        player.sendOverlayMessage(Component.translatable("voidheart.shatter_forge.preview", previewPairs + 1));

        setChanged();
        sync();
    }

    private static int minimumTrackPairs()
    {
        return VoidHeartRecipes.SHATTER_FORGE_CATEGORY.getRecipes().stream()
                .mapToInt(recipe -> recipe.getMonolithCount() - 1)
                .min()
                .orElse(2);
    }

    public BeamComposition getBeamComposition()
    {
        return beamComposition;
    }

    public FormationError getFormationError()
    {
        return formationError;
    }

    public Map<EssenceType, Integer> getRequiredAspects()
    {
        return currentRecipe == null ? Map.of() : currentRecipe.getAspects();
    }

    public ItemStack getInputStack()
    {
        var linkedPillar = getPillar();
        return linkedPillar == null ? ItemStack.EMPTY : linkedPillar.getStack();
    }

    public ItemStack getOutputStack()
    {
        return stack;
    }

    @Override
    public void setStack(ItemStack stack)
    {
        this.stack = stack;

        setChanged();
        sync();

        if (isServer() && stack.isEmpty())
            tryStartCrafting();
    }

    public void removeItself()
    {
        unlinkPillar();
        setSourcePillar(null);
    }

    public void startCrafting()
    {
        var litState = getBlockState().setValue(BlockStateProperties.LIT, true);
        level.setBlockAndUpdate(worldPosition, litState);
        setBlockState(litState);

        for (var pos : this.connectedBlocks)
            level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(BlockStateProperties.LIT, true));
        level.setBlockAndUpdate(getPillar().getBlockPos(), level.getBlockState(getPillar().getBlockPos()).setValue(BlockStateProperties.LIT, true));

        isCrafting = true;

        recipeState = currentRecipe.createState();
        hasOutputtedResult = false;

        if (isServer())
            PartialTileSync.syncPart(this, ShatterForgeCraftingSyncElement.IDENTIFIER);

        warmProgress = 0;
    }

    private void stopCrafting()
    {
        recipeState = null;
        recipeProgress = 0;
        isCrafting = false;
        warmProgress = 0;

        if (getBlockState().getValue(BlockStateProperties.LIT) && getLevel() != null)
        {
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockStateProperties.LIT, false));

            for (var blockPos : this.connectedBlocks)
            {
                var blockState = getLevel().getBlockState(blockPos);

                if (blockState.hasProperty(BlockStateProperties.LIT))
                    getLevel().setBlockAndUpdate(blockPos, blockState.setValue(BlockStateProperties.LIT, false));
            }

            if (getPillar() != null)
            {
                var pillarState = getLevel().getBlockState(getPillar().getBlockPos());
                if (pillarState.is(VoidHeartBlocks.VOID_PILLAR))
                    getLevel().setBlockAndUpdate(getPillar().getBlockPos(), pillarState.setValue(BlockStateProperties.LIT, false));
            }
        }

        unlinkPillar();
        setChanged();
        sync();
        PartialTileSync.syncPart(this, ShatterForgeCraftingSyncElement.IDENTIFIER);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, ShatterForgeTile forge)
    {
        if (forge.isClient())
        {
            forge.playParticleEffects();
            forge.tickBeamFormation();

            if (forge.isCrafting && forge.warmProgress < forge.getWarmingTime())
                forge.warmProgress++;

            return;
        }

        if (!forge.isCrafting)
        {
            if (world.getGameTime() % 20 == 0)
                forge.tryStartCrafting();
            return;
        }

        if (forge.currentRecipe == null || forge.recipeState == null || forge.getPillar() == null)
        {
            forge.stopCrafting();
            forge.sync();
            return;
        }

        if (world.getGameTime() % 20 == 0 && !forge.isCraftingStillValid())
        {
            forge.stopCrafting();
            return;
        }

        if (forge.warmProgress < forge.getWarmingTime())
        {
            forge.warmProgress++;
            forge.setChanged();
            return;
        }

        var recipeInput = forge.currentRecipe.getRecipeInputs(ItemStack.class).get(0);

        if (!forge.recipeState.isCompleted())
        {
            var inputConsumed = !forge.recipeState.getIngredientConsumed(ItemStack.class, 0).isEmpty();

            if (!inputConsumed && !recipeInput.matchWithQuantity(forge.pillar.getStack()))
            {
                forge.stopCrafting();
                forge.sync();
                return;
            }

            forge.recipeState.tick(1);
            forge.recipeProgress++;

            if (!inputConsumed && forge.recipeState.completionDelta() > 0.5F)
            {
                forge.recipeState.consumeSlotless(ItemStack.class, forge.pillar.getStack());
                forge.pillar.setStack(ItemStack.EMPTY);
            }
        }
        else
        {
            if (!forge.hasOutputtedResult)
            {
                var output = forge.recipeState.getOutput(ItemStack.class, 0);
                if (!forge.stack.isEmpty())
                    return;

                var completionResolution = BeamResolver.resolve(world, pos);
                forge.markSourcePillar(completionResolution);
                if (completionResolution.error().isPresent()
                        || !forge.hasEnoughEssence(forge.currentRecipe, completionResolution.collectorContributions()))
                {
                    forge.updateBeamState(List.of(), BeamComposition.EMPTY, completionResolution.error().orElse(FormationError.NOT_ENOUGH_ESSENCE));
                    forge.stopCrafting();
                    return;
                }

                forge.drainEssence(forge.currentRecipe, completionResolution.collectorContributions());

                forge.stack = output.copy();

                forge.recipeState.complete(world, pos, null);
                forge.hasOutputtedResult = true;
                forge.setChanged();
            }

            forge.stopCrafting();
        }
        forge.sync();
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
        recipeProgress = tag.getIntOr("recipeProgress", 0);
        warmProgress = tag.getIntOr("warmProgress", 0);
        beamSegments = tag.read("beamSegments", BeamSegment.CODEC.listOf()).orElse(List.of());
        beamComposition = tag.read("beamComposition", BeamComposition.CODEC).orElse(BeamComposition.EMPTY);
        drainingCollectors = tag.read("drainingCollectors", BlockPos.CODEC.listOf()).orElse(List.of());
        formationError = tag.getStringOr("formationError", "").isEmpty() ? null : parseError(tag.getStringOr("formationError", ""));
        isCrafting = tag.getBooleanOr("isCrafting", false);
        hasOutputtedResult = tag.getBooleanOr("hasOutputtedResult", false);

        connectedMonolithCount = tag.getIntOr("connectedMonolithCount", 0);
        trackDirection = Direction.from3DDataValue(tag.getByteOr("trackDirection", (byte) 0));

        pillarPos = tag.getBooleanOr("hasPillar", false) ? BlockPos.of(tag.getLongOr("pillarPos", 0L)) : null;
        sourcePillarPos = tag.getBooleanOr("hasSourcePillar", false) ? BlockPos.of(tag.getLongOr("sourcePillarPos", 0L)) : null;

        previewPairs = tag.getIntOr("previewPairs", -1);
        previewDirection = Direction.from3DDataValue(tag.getByteOr("previewDirection", (byte) Direction.NORTH.get3DDataValue()));
        previewUntil = tag.getLongOr("previewUntil", 0L);

        if (isServer())
        {
            if (isCrafting)
            {
                this.recipeState = RecipeState.load(VoidHeartRecipes.SHATTER_FORGE_CATEGORY, tag.childOrEmpty("recipeState"));

                if (recipeState != null)
                    this.currentRecipe = (ShatterForgeRecipe) recipeState.getRecipe();
            }
            else if (recipeState != null)
                recipeState = null;
        }
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("stack", ItemStack.OPTIONAL_CODEC, stack);
        tag.putInt("recipeProgress", recipeProgress);
        tag.putInt("warmProgress", warmProgress);
        tag.store("beamSegments", BeamSegment.CODEC.listOf(), beamSegments);
        tag.store("beamComposition", BeamComposition.CODEC, beamComposition);
        tag.store("drainingCollectors", BlockPos.CODEC.listOf(), drainingCollectors);
        tag.putString("formationError", formationError == null ? "" : formationError.name());
        tag.putBoolean("isCrafting", isCrafting);
        tag.putInt("connectedMonolithCount", connectedMonolithCount);
        tag.putBoolean("hasOutputtedResult", hasOutputtedResult);

        if (trackDirection != null)
            tag.putByte("trackDirection", (byte) trackDirection.ordinal());

        var savedPillarPos = pillar != null ? pillar.getBlockPos() : pillarPos;
        tag.putBoolean("hasPillar", savedPillarPos != null);
        if (savedPillarPos != null)
            tag.putLong("pillarPos", savedPillarPos.asLong());

        tag.putBoolean("hasSourcePillar", sourcePillarPos != null);
        if (sourcePillarPos != null)
            tag.putLong("sourcePillarPos", sourcePillarPos.asLong());

        tag.putInt("previewPairs", previewPairs);
        tag.putByte("previewDirection", (byte) previewDirection.get3DDataValue());
        tag.putLong("previewUntil", previewUntil);

        if (isCrafting && recipeState != null)
            recipeState.save(tag.child("recipeState"));

        super.saveAdditional(tag);
    }

    private static FormationError parseError(String name)
    {
        try
        {
            return FormationError.valueOf(name);
        }
        catch (IllegalArgumentException e)
        {
            return null;
        }
    }

    private VoidPillarTile getPillar()
    {
        if (pillar == null && pillarPos != null && level != null && level.getBlockEntity(pillarPos) instanceof VoidPillarTile pillarTile)
            pillar = pillarTile;
        return pillar;
    }

    @Override
    public Optional<TileSyncElement<?>> getSyncElement(Identifier identifier)
    {
        if (identifier.equals(ShatterForgeCraftingSyncElement.IDENTIFIER))
        {
            if (recipeState != null)
                return Optional.of(new ShatterForgeCraftingSyncElement(recipeState.getInput(ItemStack.class, 0), recipeState.getOutput(ItemStack.class, 0)));
            else
                return Optional.of(new ShatterForgeCraftingSyncElement(ItemStack.EMPTY, ItemStack.EMPTY));
        }
        return Optional.empty();
    }

    @Override
    public void receiveSyncElement(TileSyncElement<?> element)
    {
        if (element.getIdentifier().equals(ShatterForgeCraftingSyncElement.IDENTIFIER))
        {
            var shatterForgeCrafting = (ShatterForgeCraftingSyncElement) element;

            clientRecipeInput = shatterForgeCrafting.ingredient();
            clientRecipeOutput = shatterForgeCrafting.result();
        }
    }

    @Override
    public Codec<?> getSyncElementCodec(Identifier identifier)
    {
        if (identifier.equals(ShatterForgeCraftingSyncElement.IDENTIFIER))
            return ShatterForgeCraftingSyncElement.CODEC;
        return null;
    }

    @Override
    public Collection<Identifier> getAllSyncElements()
    {
        return syncElements;
    }


    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        removeItself();
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, getStack());
        super.preRemoveSideEffects(pos, state);
    }
}
