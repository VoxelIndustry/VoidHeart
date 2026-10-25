package net.voxelindustry.voidheart.common.content.altar;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.core.BlockPos;
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
import net.voxelindustry.voidheart.common.content.pillar.PillarLinkedTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static net.voxelindustry.voidheart.VoidHeart.MODID;

public class VoidAltarTile extends TileBase implements PartialSyncedTile, PillarLinkedTile
{
    public static final int WARMING_TIME = 80;
    public static final int COOLING_TIME = 60;
    public static final int ITEM_EATING_TIME = 60;

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

    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private final List<VoidPillarTile> pillars = new ArrayList<>();

    private final Collection<Identifier> syncElements = singletonList(AltarCraftingSyncElement.IDENTIFIER);

    ////////////
    // CLIENT //
    ////////////

    private Vector3f bezierFirstPoint;
    private Vector3f bezierSecondPoint;

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

        if (isServer())
        {
            if (isCrafting)
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
        }

        if (isCrafting && recipeState != null)
            recipeState.save(tag.child("recipeState"));

        super.saveAdditional(tag);
    }

    public void setStack(Player player, ItemStack stack)
    {
        this.stack = stack;

        if (!stack.isEmpty())
        {
            searchPillars();
            if (pillars.size() == 8)
            {
                retrieveCurrentRecipe();
                if (currentRecipe != null)
                {
                    startCrafting();
                    recipeState = currentRecipe.createState();

                    if (isServer())
                        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
                }
            }
            else
                player.sendOverlayMessage(Component.translatable(MODID + ".altar.missing_pillars", 8 - pillars.size()));
        }
        else if (isCrafting)
            stopCrafting();

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
            return;

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
        currentRecipe = VoidHeartRecipes.ALTAR_CATEGORY.findOneRecipe(stack).orElse(null);
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
        recipeState.consumeSlotted(ItemStack.class, stack, 0);
        stack = currentRecipe.getRecipeOutput(ItemStack.class, 0);
        recipeState.complete(getLevel(), getBlockPos(), null);
        recipeState = null;
        recipeProgress = 0;
        coolProgress = COOLING_TIME;
        currentRecipe = null;
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
        var start = new Vector3f(consumingPillarPos.getX() + 0.5F,
                consumingPillarPos.getY() + 1 + 4 / 16F,
                consumingPillarPos.getZ() + 0.5F);

        var end = new Vector3f(getBlockPos().getX() + 0.5F,
                getBlockPos().getY() + 1 + 4 / 16F,
                getBlockPos().getZ() + 0.5F);

        var forward = end.sub(start);
        float length = forward.length();
        forward = forward.normalize();

        float dispersion = getLevel().getRandom().nextBoolean() ? getLevel().getRandom().nextFloat() * 50 + 20 : -(getLevel().getRandom().nextFloat() * 50 + 20);
        var rotation = Axis.YP.rotationDegrees(dispersion);

        bezierFirstPoint = start.add(forward.rotate(rotation).mul(length * (getLevel().getRandom().nextFloat() / 3 + 0.2F)));

        rotation.conjugate();
        bezierSecondPoint = start.add(forward.rotate(rotation).mul(length * (getLevel().getRandom().nextFloat() / 3 + 0.5F)));

        if (getLevel().getRandom().nextBoolean())
        {
            bezierFirstPoint = bezierFirstPoint.add(0, getLevel().getRandom().nextFloat() * 1.25F, 0);
            bezierFirstPoint = bezierSecondPoint.add(0, -getLevel().getRandom().nextFloat() * 1.25F, 0);
        }
        else
        {
            bezierFirstPoint = bezierFirstPoint.add(0, -getLevel().getRandom().nextFloat() * 1.25F, 0);
            bezierFirstPoint = bezierSecondPoint.add(0, getLevel().getRandom().nextFloat() * 1.25F, 0);
        }
    }

    @Override
    public Optional<TileSyncElement<?>> getSyncElement(Identifier identifier)
    {
        if (identifier.equals(AltarCraftingSyncElement.IDENTIFIER))
        {
            if (recipeState != null)
                return Optional.of(new AltarCraftingSyncElement(recipeState.getOutput(ItemStack.class, 0), recipeState.getIngredientsLeft(ItemStack.class)));
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
        super.preRemoveSideEffects(pos, state);
    }
}
