package net.voxelindustry.voidheart.common.content.altar;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.steamlayer.network.tilesync.PartialSyncedTile;
import net.voxelindustry.steamlayer.network.tilesync.PartialTileSync;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.steamlayer.recipe.state.RecipeState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.pillar.PillarLinkedTile;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.minecraft.server.level.ServerPlayer;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidAltarTile extends TileBase implements PartialSyncedTile, PillarLinkedTile
{
    public static final int WARMING_TIME = 80;
    public static final int COOLING_TIME = 60;
    public static final int ITEM_EATING_TIME = 60;
    public static final int EXPERIENCE_EATING_TIME = 60;
    public static final int SKULL_SEARCH_RADIUS = 4;
    public static final int IDLE_RETRY_TICKS = 10;

    // Fixes Minecraft world.getRandom() being too coherent in certain conditions
    private final Random actualRandom = new Random();

    private AltarRecipe currentRecipe;
    @Getter
    private RecipeState recipeState;

    private int recipeProgress;
    @Getter
    private boolean isCrafting;

    @Getter
    private int warmProgress;
    @Getter
    private int coolProgress;
    private int consumeProgress;

    private int consumingPillarIndex = -1;
    private BlockPos consumingPillarPos = BlockPos.ZERO;
    private ItemStack cachedToConsume = ItemStack.EMPTY;

    private int experienceConsumed;
    private BlockPos consumingSkullPos = BlockPos.ZERO;
    private int skullConsumeProgress;

    @Getter
    private ItemStack stack = ItemStack.EMPTY;
    /**
     * Who set the item down, whose research decides whether a recipe can start, online or not.
     */
    @Getter
    @Nullable
    private UUID owner;
    /**
     * The only recipe the altar may run on the item set down, so a rite laid out for one recipe never becomes another sharing its catalyst.
     */
    @Getter
    @Nullable
    private Identifier pinned;

    private final List<VoidPillarTile> pillars = new ArrayList<>();

    private final Collection<Identifier> syncElements = singletonList(AltarCraftingSyncElement.IDENTIFIER);

    ////////////
    // CLIENT //
    ////////////

    private Vector3f bezierFirstPoint;
    private Vector3f bezierSecondPoint;

    private Vector3f skullBezierFirstPoint;
    private Vector3f skullBezierSecondPoint;

    @Getter
    private ItemStack clientRecipeOutput = ItemStack.EMPTY;
    @Getter
    private List<ItemStack> clientRecipeToConsume = emptyList();

    public VoidAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_ALTAR, pos, state);
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
        coolProgress = tag.getIntOr("coolProgress", 0);
        isCrafting = tag.getBooleanOr("isCrafting", false);

        if (isClient() && tag.read("consumingPillarIndex", Codec.PASSTHROUGH).isPresent())
        {
            int previousConsumingPillarIndex = consumingPillarIndex;

            consumingPillarIndex = tag.getIntOr("consumingPillarIndex", 0);
            consumingPillarPos = BlockPos.of(tag.getLongOr("consumingPillarPos", 0L));
            cachedToConsume = tag.read("cachedToConsumeStack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);

            if (previousConsumingPillarIndex != consumingPillarIndex)
            {
                computeBezier();
            }
        }

        if (isClient() && tag.read("consumingSkullPos", Codec.PASSTHROUGH).isPresent())
        {
            var previousConsumingSkullPos = consumingSkullPos;
            var savedSkullPos = tag.getLongOr("consumingSkullPos", 0L);
            consumingSkullPos = savedSkullPos == 0L ? BlockPos.ZERO : BlockPos.of(savedSkullPos);

            if (consumingSkullPos != BlockPos.ZERO && !consumingSkullPos.equals(previousConsumingSkullPos))
                computeSkullBezier();
        }

        experienceConsumed = tag.getIntOr("experienceConsumed", 0);
        owner = tag.read("owner", UUIDUtil.CODEC).orElse(null);
        pinned = tag.read("pinned", Identifier.CODEC).orElse(null);

        if (isServer())
        {
            if (isCrafting)
            {
                // The saved state names its recipe, since several can share a catalyst
                var savedState = RecipeState.load(VoidHeartRecipes.ALTAR_CATEGORY, tag.childOrEmpty("recipeState"));
                if (savedState != null && savedState.getRecipe() instanceof AltarRecipe savedRecipe)
                {
                    currentRecipe = savedRecipe;
                    recipeState = savedState;
                }
                else
                {
                    if (currentRecipe == null)
                        retrieveCurrentRecipe();

                    if (currentRecipe != null)
                    {
                        RecipeState recipeState = currentRecipe.createState();
                        recipeState.load(tag.childOrEmpty("recipeState"));
                        this.recipeState = recipeState;
                    }
                    else
                        stopCrafting();
                }
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
        tag.putInt("coolProgress", coolProgress);
        tag.putBoolean("isCrafting", isCrafting);

        if (isServer())
        {
            tag.putInt("consumingPillarIndex", consumingPillarIndex);
            tag.putLong("consumingPillarPos", consumingPillarPos.asLong());
            tag.store("cachedToConsumeStack", ItemStack.OPTIONAL_CODEC, cachedToConsume);
            tag.putLong("consumingSkullPos", consumingSkullPos.asLong());
        }
        tag.putInt("experienceConsumed", experienceConsumed);
        if (owner != null)
            tag.store("owner", UUIDUtil.CODEC, owner);
        if (pinned != null)
            tag.store("pinned", Identifier.CODEC, pinned);

        if (isCrafting && recipeState != null)
            recipeState.save(tag.child("recipeState"));

        super.saveAdditional(tag);
    }

    /**
     * The client doesn't know what its player researched, so on locked recipes it waits for the server to start the craft rather than predict it.
     *
     * @param player the hand setting the item down, told what research is missing, or null for a quiet check of the owner's research
     */
    private boolean canStart(@Nullable Player player, AltarRecipe recipe)
    {
        if (player instanceof ServerPlayer serverPlayer)
            return Knowledge.canUse(serverPlayer, recipe.getId());
        if (getLevel() instanceof ServerLevel serverLevel)
            return owner != null && Knowledge.canUse(serverLevel.getServer(), owner, recipe.getId());
        return Knowledge.lockOf(getLevel().registryAccess(), recipe.getId()).isEmpty();
    }

    public void setStack(Player player, ItemStack stack)
    {
        this.stack = stack;
        owner = stack.isEmpty() ? null : player.getUUID();
        pinned = null;

        if (!stack.isEmpty())
        {
            searchPillars();
            if (pillars.size() == 8)
                tryStart(player);
            else
                player.sendOverlayMessage(Component.translatable(MODID + ".altar.missing_pillars", 8 - pillars.size()));
        }
        else if (isCrafting)
            stopCrafting();

        setChanged();
        sync();
    }

    /**
     * Whether nothing is set on the altar, so a rite can be laid out on it.
     */
    public boolean isVacant()
    {
        return stack.isEmpty() && !isCrafting;
    }

    /**
     * Sets an item down in someone's name with no hand involved, for one recipe only. Its ingredients should already wait on the pillars, since a rite
     * pinned to a recipe whose catalyst is a whole tag only starts once they are laid.
     *
     * @return false when the altar already holds something
     */
    public boolean layCatalyst(UUID owner, ItemStack catalyst, Identifier recipe)
    {
        if (!isVacant() || catalyst.isEmpty())
            return false;

        stack = catalyst.copyWithCount(1);
        this.owner = owner;
        pinned = recipe;

        searchPillars();
        if (pillars.size() == 8)
            tryStart(null);

        setChanged();
        sync();
        return true;
    }

    /**
     * @return what the altar holds once no rite runs on it, its result or an item that never started, now taken off
     */
    public ItemStack takeResult()
    {
        if (isCrafting || stack.isEmpty())
            return ItemStack.EMPTY;

        var taken = stack;
        stack = ItemStack.EMPTY;
        owner = null;
        pinned = null;
        setChanged();
        sync();
        return taken;
    }

    private boolean tryStart(@Nullable Player player)
    {
        retrieveCurrentRecipe();
        if (currentRecipe != null && !canStart(player, currentRecipe))
            currentRecipe = null;

        if (currentRecipe == null)
            return false;

        startCrafting();
        recipeState = currentRecipe.createState();

        if (isServer())
            PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
        return true;
    }

    /**
     * A recipe still waiting for its first ingredient gives way to another recipe for the same item whose ingredients are all laid, such as a sword set down
     * for the Avenging Reach before the ingots of a sin were laid.
     */
    private void yieldToLaidRecipe()
    {
        if (owner == null || pinned != null || getLevel().getGameTime() % IDLE_RETRY_TICKS != 0 || consumingPillarIndex != -1 || !cachedToConsume.isEmpty() || experienceConsumed > 0)
            return;
        if (recipeState == null || !recipeState.getIngredientsConsumed(ItemStack.class).isEmpty())
            return;

        var pillarStacks = pillars.stream().map(VoidPillarTile::getStack).toList();
        if (ingredientsLaid(currentRecipe, pillarStacks))
            return;

        var laid = VoidHeartRecipes.ALTAR_CATEGORY.findRecipes(stack)
                .filter(recipe -> recipe != currentRecipe && recipe.changes(stack) && ingredientsLaid(recipe, pillarStacks) && canStart(null, recipe))
                .findFirst();
        if (laid.isEmpty())
            return;

        currentRecipe = laid.get();
        recipeState = currentRecipe.createState();
        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
        setChanged();
    }

    /**
     * An idle altar holding an item wakes once the ingredients of a recipe for it are laid on the pillars, as if the item were set down again.
     */
    private void retryIdleStart()
    {
        if (owner == null || stack.isEmpty() || coolProgress > 0 || getLevel().getGameTime() % IDLE_RETRY_TICKS != 0)
            return;

        searchPillars();
        if (pillars.size() != 8)
            return;

        var pillarStacks = pillars.stream().map(VoidPillarTile::getStack).toList();
        var laid = pinnedTo(VoidHeartRecipes.ALTAR_CATEGORY.findRecipes(stack).toList(), pinned).stream()
                .filter(recipe -> recipe.changes(stack) && ingredientsLaid(recipe, pillarStacks))
                .findFirst();
        if (laid.isEmpty() || !tryStart(null))
            return;

        setChanged();
        sync();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, VoidAltarTile altar)
    {
        if (altar.isClient())
        {
            altar.playParticleEffects();

            if (altar.isCrafting && altar.warmProgress < WARMING_TIME)
                altar.warmProgress++;

            if (altar.coolProgress > 0)
                altar.coolProgress--;

            return;
        }

        if (altar.coolProgress > 0)
            altar.coolProgress--;

        if (!altar.isCrafting)
        {
            altar.retryIdleStart();
            return;
        }

        if (altar.currentRecipe == null)
        {
            altar.stopCrafting();
            altar.sync();
            return;
        }

        if (altar.warmProgress < WARMING_TIME)
        {
            altar.warmProgress++;
            altar.setChanged();
            return;
        }

        if (altar.consumeProgress == 0 && altar.pillars.size() != 8)
            altar.refreshPillars();

        altar.yieldToLaidRecipe();

        altar.computeConsumeAction();
        altar.computeCraftFinalization();
        altar.sync();
    }

    private void computeCraftFinalization()
    {
        if (consumingPillarIndex != -1 || !cachedToConsume.isEmpty())
            return;

        ItemStack toConsume = getNextItemToEat();

        if (toConsume.isEmpty())
        {
            if (!computeExperienceConsumption())
                return;

            finishCrafting();
            PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
        }
        else
            cachedToConsume = findItemToConsume(toConsume);
    }

    private void computeConsumeAction()
    {
        if (consumingPillarIndex == -1)
            return;

        if (!ItemStack.isSameItemSameComponents(cachedToConsume, pillars.get(consumingPillarIndex).getStack()))
        {
            consumeProgress = 0;
            consumingPillarIndex = -1;
            consumingPillarPos = BlockPos.ZERO;
            cachedToConsume = ItemStack.EMPTY;
        }

        if (consumeProgress < VoidAltarTile.ITEM_EATING_TIME)
            consumeProgress++;
        else
        {
            consumeItemStack();
            getLevel().playLocalSound(getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ(), SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, getLevel().getRandom().nextFloat() * 0.4F + 0.8F, 0.25F, true);

            consumeProgress = 0;
            consumingPillarIndex = -1;
            consumingPillarPos = BlockPos.ZERO;
            cachedToConsume = ItemStack.EMPTY;
        }
    }

    /**
     * Once every ingredient is eaten, the altar drains the nearby experience skulls one by one, least filled first.
     *
     * @return true once the recipe experience is fully drained
     */
    private boolean computeExperienceConsumption()
    {
        var remaining = currentRecipe.getExperience() - experienceConsumed;
        if (remaining <= 0)
            return true;

        if (consumingSkullPos == BlockPos.ZERO)
        {
            var skull = findSkullToDrain();
            if (skull != null)
            {
                consumingSkullPos = skull.getBlockPos().immutable();
                skullConsumeProgress = 0;
            }
            return false;
        }

        if (!(level.getBlockEntity(consumingSkullPos) instanceof ExperienceSkullTile skull) || skull.getExperience() <= 0)
        {
            consumingSkullPos = BlockPos.ZERO;
            skullConsumeProgress = 0;
            return false;
        }

        if (skullConsumeProgress < EXPERIENCE_EATING_TIME)
        {
            skullConsumeProgress++;
            return false;
        }

        var taken = Math.min(remaining, skull.getExperience());
        skull.setExperience(skull.getExperience() - taken);
        skull.setChanged();
        experienceConsumed += taken;
        getLevel().playSound(null, getBlockPos(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, getLevel().getRandom().nextFloat() * 0.4F + 0.8F);

        consumingSkullPos = BlockPos.ZERO;
        skullConsumeProgress = 0;
        setChanged();
        return experienceConsumed >= currentRecipe.getExperience();
    }

    private ExperienceSkullTile findSkullToDrain()
    {
        ExperienceSkullTile leastFilled = null;
        for (var pos : BlockPos.betweenClosed(getBlockPos().offset(-SKULL_SEARCH_RADIUS, -1, -SKULL_SEARCH_RADIUS), getBlockPos().offset(SKULL_SEARCH_RADIUS, 3, SKULL_SEARCH_RADIUS)))
        {
            if (level.getBlockEntity(pos) instanceof ExperienceSkullTile skull && skull.getExperience() > 0
                    && (leastFilled == null || skull.getExperience() < leastFilled.getExperience()))
                leastFilled = skull;
        }
        return leastFilled;
    }

    /**
     * Experience drained for a craft that did not complete is given back as orbs.
     */
    private void refundExperience()
    {
        if (experienceConsumed > 0 && getLevel() instanceof ServerLevel serverLevel)
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(getBlockPos().above()), experienceConsumed);

        experienceConsumed = 0;
        consumingSkullPos = BlockPos.ZERO;
        skullConsumeProgress = 0;
    }

    private void playParticleEffects()
    {
        if (!stack.isEmpty() && isCrafting)
        {
            if (warmProgress < WARMING_TIME)
            {
                getLevel().addParticle(new AltarVoidParticleEffect(0.05 + 0.05 * Interpolators.EXP_IN.apply(warmProgress / (float) WARMING_TIME)),
                        getBlockPos().getX() + 0.5 + this.actualRandom.nextGaussian(),
                        getBlockPos().getY() + 3 + this.actualRandom.nextGaussian(),
                        getBlockPos().getZ() + 0.5 + this.actualRandom.nextGaussian(),
                        getBlockPos().getX() + 0.5,
                        getBlockPos().getY() + 3,
                        getBlockPos().getZ() + 0.5);
            }
            else
            {
                getLevel().addParticle(new AltarVoidParticleEffect(0.02),
                        getBlockPos().getX() + 0.5 + this.actualRandom.nextGaussian() / 8F,
                        getBlockPos().getY() + 3,
                        getBlockPos().getZ() + 0.5 + this.actualRandom.nextGaussian() / 8F,
                        getBlockPos().getX() + 0.5,
                        getBlockPos().getY() + 1,
                        getBlockPos().getZ() + 0.5);
            }

            if (consumingPillarIndex != -1)
            {
                for (int index = 0; index < 3; index++)
                {
                    getLevel().addParticle(new AltarItemParticleEffect(cachedToConsume, bezierFirstPoint, bezierSecondPoint),
                            consumingPillarPos.getX() + 0.5,
                            consumingPillarPos.getY() + 1 + 4 / 16D,
                            consumingPillarPos.getZ() + 0.5,
                            getBlockPos().getX() + 0.5,
                            getBlockPos().getY() + 1 + 4 / 16D,
                            getBlockPos().getZ() + 0.5);
                }
            }

            if (consumingSkullPos != BlockPos.ZERO && skullBezierFirstPoint != null)
            {
                for (int index = 0; index < 2; index++)
                {
                    getLevel().addParticle(new AltarExperienceParticleEffect(skullBezierFirstPoint, skullBezierSecondPoint),
                            consumingSkullPos.getX() + 0.5,
                            consumingSkullPos.getY() + 0.5,
                            consumingSkullPos.getZ() + 0.5,
                            getBlockPos().getX() + 0.5,
                            getBlockPos().getY() + 1 + 4 / 16D,
                            getBlockPos().getZ() + 0.5);
                }
            }
        }

        if (coolProgress == 1)
        {
            for (int index = 0; index < 16; index++)
            {
                getLevel().addParticle(new AltarVoidParticleEffect(0.08),
                        getBlockPos().getX() + 0.5,
                        getBlockPos().getY() + 3,
                        getBlockPos().getZ() + 0.5,
                        getBlockPos().getX() + 0.5 + getLevel().getRandom().nextGaussian(),
                        getBlockPos().getY() + 3 + getLevel().getRandom().nextGaussian(),
                        getBlockPos().getZ() + 0.5 + getLevel().getRandom().nextGaussian());
            }
        }
    }

    private void retrieveCurrentRecipe()
    {
        var pillarStacks = pillars.stream().map(VoidPillarTile::getStack).toList();
        currentRecipe = chooseRecipe(pinnedTo(VoidHeartRecipes.ALTAR_CATEGORY.findRecipes(stack).toList(), pinned), stack, pillarStacks);
    }

    /**
     * @return only the pinned recipe among the candidates, or all of them when nothing is pinned
     */
    public static List<AltarRecipe> pinnedTo(List<AltarRecipe> candidates, @Nullable Identifier pinned)
    {
        if (pinned == null)
            return candidates;
        return candidates.stream().filter(recipe -> recipe.getId().equals(pinned)).toList();
    }

    /**
     * Several recipes can take the same catalyst, so the one whose ingredients all wait on the pillars is chosen. Without one, a recipe for that exact catalyst
     * starts and waits for its ingredients, while a recipe taking a whole tag of catalysts, such as a sin for any weapon, waits until its ingredients are laid.
     * A recipe that would give back its catalyst unchanged never starts. A recipe asking for a catalyst with exact components, such as a Fouled bundle, is
     * tried before one taking that item however it is, so the bundle's habit isn't lost to the plain recipe.
     */
    public static AltarRecipe chooseRecipe(List<AltarRecipe> candidates, ItemStack catalyst, List<ItemStack> pillarStacks)
    {
        var ordered = new java.util.ArrayList<>(candidates);
        ordered.sort(java.util.Comparator.comparing(recipe -> !recipe.hasComponentCatalyst()));
        AltarRecipe waiting = null;
        for (var recipe : ordered)
        {
            if (!recipe.changes(catalyst))
                continue;
            if (ingredientsLaid(recipe, pillarStacks))
                return recipe;
            if (waiting == null && !recipe.hasTagCatalyst())
                waiting = recipe;
        }
        return waiting;
    }

    private static boolean ingredientsLaid(AltarRecipe recipe, List<ItemStack> pillarStacks)
    {
        var inputs = recipe.getRecipeInputs(ItemStack.class);
        var used = new boolean[pillarStacks.size()];
        for (int index = 1; index < inputs.size(); index++)
        {
            var wanted = inputs.get(index).getRaw();
            var found = false;
            for (int pillar = 0; pillar < pillarStacks.size() && !found; pillar++)
            {
                if (!used[pillar] && ItemStack.isSameItemSameComponents(pillarStacks.get(pillar), wanted))
                {
                    used[pillar] = true;
                    found = true;
                }
            }
            if (!found)
                return false;
        }
        return true;
    }

    public int foundPillars()
    {
        return pillars.size();
    }

    public void searchPillars()
    {
        pillars.clear();
        BlockPos.MutableBlockPos searchPos = getBlockPos().mutable();

        searchPos.setWithOffset(getBlockPos(), -1, 0, 3);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), 1, 0, 3);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), 1, 0, -3);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), -1, 0, -3);
        findPillar(searchPos);


        searchPos.setWithOffset(getBlockPos(), 3, 0, -1);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), 3, 0, 1);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), -3, 0, -1);
        findPillar(searchPos);

        searchPos.setWithOffset(getBlockPos(), -3, 0, 1);
        findPillar(searchPos);
    }

    private void findPillar(BlockPos pos)
    {
        if (level.getBlockState(pos).getBlock() != VoidHeartBlocks.VOID_PILLAR)
            return;

        var pillar = level.getBlockEntity(pos, VoidHeartTiles.VOID_PILLAR);
        if (pillar.isEmpty())
            return;

        if (isCrafting)
            pillar.get().addAltar(getBlockPos());

        pillars.add(pillar.get());
    }

    @Override
    public void removePillar(VoidPillarTile pillar)
    {
        if (consumingPillarIndex == pillars.indexOf(pillar))
        {
            consumingPillarIndex = -1;
            cachedToConsume = ItemStack.EMPTY;
        }

        pillars.remove(pillar);
    }

    private void startCrafting()
    {
        isCrafting = true;

        if (!getBlockState().getValue(BlockStateProperties.LIT))
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockStateProperties.LIT, true));

        pillars.forEach(pillar -> pillar.addAltar(getBlockPos()));

        warmProgress = 0;
    }

    private void stopCrafting()
    {
        dropAteItems();
        refundExperience();
        recipeState = null;
        recipeProgress = 0;
        isCrafting = false;

        if (getBlockState().getValue(BlockStateProperties.LIT))
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockStateProperties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getBlockPos()));
        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
    }

    private void finishCrafting()
    {
        // A recipe that transforms its catalyst gives it back changed, enchantments and all
        var result = currentRecipe.resultFor(stack);
        recipeState.consumeSlotted(ItemStack.class, stack, 0);
        stack = result;
        recipeState.complete(getLevel(), getBlockPos(), null);
        recipeState = null;
        recipeProgress = 0;
        coolProgress = COOLING_TIME;
        currentRecipe = null;
        pinned = null;
        experienceConsumed = 0;
        consumingSkullPos = BlockPos.ZERO;
        skullConsumeProgress = 0;
        isCrafting = false;
        sync();

        if (getBlockState().getValue(BlockStateProperties.LIT))
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(BlockStateProperties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getBlockPos()));
        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
    }

    public void dropAteItems()
    {
        if (recipeState == null)
            return;

        List<ItemStack> ingredientsConsumed = recipeState.getIngredientsConsumed(ItemStack.class);

        for (ItemStack stack : ingredientsConsumed)
        {
            Containers.dropItemStack(
                    getLevel(),
                    getBlockPos().getX(),
                    getBlockPos().getY(),
                    getBlockPos().getZ(),
                    stack);
        }
    }

    private void refreshPillars()
    {
        pillars.removeIf(BlockEntity::isRemoved);

        if (pillars.size() != 8)
            searchPillars();
    }

    private ItemStack findItemToConsume(ItemStack toConsume)
    {
        if (toConsume.isEmpty())
            return ItemStack.EMPTY;

        refreshPillars();

        for (VoidPillarTile pillar : pillars)
        {
            if (ItemStack.isSameItemSameComponents(pillar.getStack(), toConsume))
            {
                consumingPillarIndex = pillars.indexOf(pillar);
                consumingPillarPos = pillar.getBlockPos();
                return toConsume;
            }
        }
        return ItemStack.EMPTY;
    }

    private void consumeItemStack()
    {
        VoidPillarTile pillar = pillars.get(consumingPillarIndex);
        recipeState.consumeSlotless(ItemStack.class, pillar.getStack());
        pillar.setStack(ItemStack.EMPTY);
        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
        setChanged();
    }

    private ItemStack getNextItemToEat()
    {
        List<ItemStack> ingredientsLeft = recipeState.getIngredientsLeft(ItemStack.class);

        if (ingredientsLeft.size() == 1)
            return ItemStack.EMPTY;

        for (int index = 1; index < ingredientsLeft.size(); index++)
        {
            if (ingredientsLeft.get(index).isEmpty())
                continue;

            return ingredientsLeft.get(index);
        }
        return ItemStack.EMPTY;
    }

    public void removeItself()
    {
        pillars.forEach(pillar -> pillar.removeAltar(getBlockPos()));
    }

    private void computeBezier()
    {
        var points = computeBezier(new Vector3f(consumingPillarPos.getX() + 0.5F,
                consumingPillarPos.getY() + 1 + 4 / 16F,
                consumingPillarPos.getZ() + 0.5F));
        bezierFirstPoint = points[0];
        bezierSecondPoint = points[1];
    }

    private void computeSkullBezier()
    {
        var points = computeBezier(new Vector3f(consumingSkullPos.getX() + 0.5F,
                consumingSkullPos.getY() + 0.5F,
                consumingSkullPos.getZ() + 0.5F));
        skullBezierFirstPoint = points[0];
        skullBezierSecondPoint = points[1];
    }

    /**
     * @return both inner control points of a stream from the given start to the altar item
     */
    private Vector3f[] computeBezier(Vector3f start)
    {
        return AltarStreams.controlPoints(getLevel().getRandom(), start, AltarStreams.altarTop(getBlockPos()));
    }

    @Override
    public Optional<TileSyncElement<?>> getSyncElement(Identifier identifier)
    {
        if (identifier.equals(AltarCraftingSyncElement.IDENTIFIER))
        {
            if (recipeState != null)
                return Optional.of(new AltarCraftingSyncElement(currentRecipe != null ? currentRecipe.resultFor(stack) : ItemStack.EMPTY, recipeState.getIngredientsLeft(ItemStack.class)));
            else
                return Optional.of(new AltarCraftingSyncElement(ItemStack.EMPTY, emptyList()));
        }
        return Optional.empty();
    }

    @Override
    public void receiveSyncElement(TileSyncElement<?> element)
    {
        if (element.getIdentifier().equals(AltarCraftingSyncElement.IDENTIFIER))
        {
            AltarCraftingSyncElement altarCrafting = (AltarCraftingSyncElement) element;

            clientRecipeOutput = altarCrafting.result();
            clientRecipeToConsume = altarCrafting.toConsume();
        }
    }

    @Override
    public Codec<?> getSyncElementCodec(Identifier identifier)
    {
        if (identifier.equals(AltarCraftingSyncElement.IDENTIFIER))
            return AltarCraftingSyncElement.CODEC;
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
        dropAteItems();
        refundExperience();
        super.preRemoveSideEffects(pos, state);
    }
}
