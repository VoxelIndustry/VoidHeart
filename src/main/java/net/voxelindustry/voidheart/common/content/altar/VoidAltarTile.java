package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Quaternion;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.steamlayer.math.Vec3f;
import net.voxelindustry.steamlayer.math.interpolator.Interpolators;
import net.voxelindustry.steamlayer.network.tilesync.PartialSyncedTile;
import net.voxelindustry.steamlayer.network.tilesync.PartialTileSync;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.steamlayer.recipe.state.RecipeState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class VoidAltarTile extends TileBase implements PartialSyncedTile
{
    public static final int WARMING_TIME     = 80;
    public static final int COOLING_TIME     = 60;
    public static final int ITEM_EATING_TIME = 60;

    private AltarRecipe currentRecipe;
    @Getter
    private RecipeState recipeState;

    private int     recipeProgress;
    @Getter
    private boolean isCrafting;

    @Getter
    private int warmProgress;
    @Getter
    private int coolProgress;
    private int consumeProgress;

    private int       consumingPillarIndex = -1;
    private BlockPos  consumingPillarPos   = BlockPos.ORIGIN;
    private ItemStack cachedToConsume      = ItemStack.EMPTY;

    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private final List<VoidPillarTile> pillars = new ArrayList<>();

    private final Collection<Identifier> syncElements = singletonList(AltarCraftingSyncElement.IDENTIFIER);

    ////////////
    // CLIENT //
    ////////////

    private Vec3f bezierFirstPoint;
    private Vec3f bezierSecondPoint;

    @Getter
    private ItemStack       clientRecipeOutput    = ItemStack.EMPTY;
    @Getter
    private List<ItemStack> clientRecipeToConsume = emptyList();

    public VoidAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_ALTAR, pos, state);
    }

    @Override
    public void setWorld(World world)
    {
        super.setWorld(world);

        // Called on world load
        sync();
    }

    @Override
    public void readNbt(NbtCompound tag)
    {
        super.readNbt(tag);

        stack = ItemStack.fromNbt(tag.getCompound("stack"));
        recipeProgress = tag.getInt("recipeProgress");
        warmProgress = tag.getInt("warmProgress");
        coolProgress = tag.getInt("coolProgress");
        isCrafting = tag.getBoolean("isCrafting");

        if (isClient() && tag.contains("consumingPillarIndex"))
        {
            int previousConsumingPillarIndex = consumingPillarIndex;

            consumingPillarIndex = tag.getInt("consumingPillarIndex");
            consumingPillarPos = BlockPos.fromLong(tag.getLong("consumingPillarPos"));
            cachedToConsume = ItemStack.fromNbt(tag.getCompound("cachedToConsumeStack"));

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
                    recipeState.fromTag(tag.getCompound("recipeState"));
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
    public void writeNbt(NbtCompound tag)
    {
        tag.put("stack", stack.writeNbt(new NbtCompound()));
        tag.putInt("recipeProgress", recipeProgress);
        tag.putInt("warmProgress", warmProgress);
        tag.putInt("coolProgress", coolProgress);
        tag.putBoolean("isCrafting", isCrafting);

        if (isServer())
        {
            tag.putInt("consumingPillarIndex", consumingPillarIndex);
            tag.putLong("consumingPillarPos", consumingPillarPos.asLong());
            tag.put("cachedToConsumeStack", cachedToConsume.writeNbt(new NbtCompound()));
        }

        if (isCrafting && recipeState != null)
            tag.put("recipeState", recipeState.toTag());

        super.writeNbt(tag);
    }

    public void setStack(PlayerEntity player, ItemStack stack)
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
                player.sendMessage(new LiteralText("Missing " + (8 - pillars.size()) + " pillars"), true);
        }
        else if (isCrafting)
            stopCrafting();

        markDirty();
        sync();
    }

    public static void tick(World world, BlockPos pos, BlockState state, VoidAltarTile altar)
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

        if (!ItemUtils.deepEquals(cachedToConsume, pillars.get(consumingPillarIndex).getStack()))
        {
            consumeProgress = 0;
            consumingPillarIndex = -1;
            consumingPillarPos = BlockPos.ORIGIN;
            cachedToConsume = ItemStack.EMPTY;
        }

        if (consumeProgress < VoidAltarTile.ITEM_EATING_TIME)
            consumeProgress++;
        else
        {
            consumeItemStack();
            getWorld().playSound(getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, getWorld().random.nextFloat() * 0.4F + 0.8F, 0.25F, true);

            consumeProgress = 0;
            consumingPillarIndex = -1;
            consumingPillarPos = BlockPos.ORIGIN;
            cachedToConsume = ItemStack.EMPTY;
        }
    }

    private void playParticleEffects()
    {
        if (!stack.isEmpty() && isCrafting)
        {
            if (warmProgress < WARMING_TIME)
            {
                getWorld().addParticle(new AltarVoidParticleEffect(0.05 + 0.05 * Interpolators.EXP_IN.apply(warmProgress / (float) WARMING_TIME)),
                        getPos().getX() + 0.5 + getWorld().random.nextGaussian(),
                        getPos().getY() + 3 + getWorld().random.nextGaussian(),
                        getPos().getZ() + 0.5 + getWorld().random.nextGaussian(),
                        getPos().getX() + 0.5,
                        getPos().getY() + 3,
                        getPos().getZ() + 0.5);
            }
            else
            {
                getWorld().addParticle(new AltarVoidParticleEffect(0.02),
                        getPos().getX() + 0.5 + getWorld().random.nextGaussian() / 8F,
                        getPos().getY() + 3,
                        getPos().getZ() + 0.5 + getWorld().random.nextGaussian() / 8F,
                        getPos().getX() + 0.5,
                        getPos().getY() + 1,
                        getPos().getZ() + 0.5);
            }

            if (consumingPillarIndex != -1)
            {
                for (int index = 0; index < 3; index++)
                {
                    getWorld().addParticle(new AltarItemParticleEffect(cachedToConsume, bezierFirstPoint, bezierSecondPoint),
                            consumingPillarPos.getX() + 0.5,
                            consumingPillarPos.getY() + 1 + 4 / 16D,
                            consumingPillarPos.getZ() + 0.5,
                            getPos().getX() + 0.5,
                            getPos().getY() + 1 + 4 / 16D,
                            getPos().getZ() + 0.5);
                }
            }
        }

        if (coolProgress == 1)
        {
            for (int index = 0; index < 16; index++)
            {
                getWorld().addParticle(new AltarVoidParticleEffect(0.08),
                        getPos().getX() + 0.5,
                        getPos().getY() + 3,
                        getPos().getZ() + 0.5,
                        getPos().getX() + 0.5 + getWorld().random.nextGaussian(),
                        getPos().getY() + 3 + getWorld().random.nextGaussian(),
                        getPos().getZ() + 0.5 + getWorld().random.nextGaussian());
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
        BlockPos.Mutable searchPos = getPos().mutableCopy();

        searchPos.set(getPos(), -1, 0, 3);
        findPillar(searchPos);

        searchPos.set(getPos(), 1, 0, 3);
        findPillar(searchPos);

        searchPos.set(getPos(), 1, 0, -3);
        findPillar(searchPos);

        searchPos.set(getPos(), -1, 0, -3);
        findPillar(searchPos);


        searchPos.set(getPos(), 3, 0, -1);
        findPillar(searchPos);

        searchPos.set(getPos(), 3, 0, 1);
        findPillar(searchPos);

        searchPos.set(getPos(), -3, 0, -1);
        findPillar(searchPos);

        searchPos.set(getPos(), -3, 0, 1);
        findPillar(searchPos);
    }

    private void findPillar(BlockPos pos)
    {
        if (world.getBlockState(pos).getBlock() != VoidHeartBlocks.VOID_PILLAR)
            return;

        VoidPillarTile pillar = (VoidPillarTile) world.getBlockEntity(pos);
        if (pillar == null)
            return;

        if (isCrafting)
            pillar.addAltar(getPos());

        pillars.add(pillar);
    }

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

        if (!getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, true));

        pillars.forEach(pillar -> pillar.addAltar(getPos()));

        warmProgress = 0;
    }

    private void stopCrafting()
    {
        dropAteItems();
        recipeState = null;
        recipeProgress = 0;
        isCrafting = false;

        if (getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getPos()));
        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
    }

    private void finishCrafting()
    {
        recipeState.consumeSlotted(ItemStack.class, stack, 0);
        stack = currentRecipe.getRecipeOutput(ItemStack.class, 0);
        recipeState.complete(getWorld(), getPos(), null);
        recipeState = null;
        recipeProgress = 0;
        coolProgress = COOLING_TIME;
        currentRecipe = null;
        isCrafting = false;
        sync();

        if (getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getPos()));
        PartialTileSync.syncPart(this, AltarCraftingSyncElement.IDENTIFIER);
    }

    public void dropAteItems()
    {
        if (recipeState == null)
            return;

        List<ItemStack> ingredientsConsumed = recipeState.getIngredientsConsumed(ItemStack.class);

        for (ItemStack stack : ingredientsConsumed)
        {
            ItemScatterer.spawn(
                    getWorld(),
                    getPos().getX(),
                    getPos().getY(),
                    getPos().getZ(),
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
            if (ItemUtils.deepEquals(pillar.getStack(), toConsume))
            {
                consumingPillarIndex = pillars.indexOf(pillar);
                consumingPillarPos = pillar.getPos();
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
        pillars.forEach(pillar -> pillar.removeAltar(getPos()));
    }

    private void computeBezier()
    {
        Vec3f start = new Vec3f(consumingPillarPos.getX() + 0.5F,
                consumingPillarPos.getY() + 1 + 4 / 16F,
                consumingPillarPos.getZ() + 0.5F);

        Vec3f end = new Vec3f(getPos().getX() + 0.5F,
                getPos().getY() + 1 + 4 / 16F,
                getPos().getZ() + 0.5F);

        Vec3f forward = end.subtract(start);
        float length = forward.magnitude();
        forward = forward.normalize();


        float dispersion = getWorld().random.nextBoolean() ? getWorld().random.nextFloat() * 50 + 20 : -(getWorld().random.nextFloat() * 50 + 20);
        Quaternion rotation = Vec3f.UP.getDegreesQuaternion(dispersion);

        bezierFirstPoint = start.add(forward.rotate(rotation).scale(length * (getWorld().random.nextFloat() / 3 + 0.2F)));

        rotation.conjugate();
        bezierSecondPoint = start.add(forward.rotate(rotation).scale(length * (getWorld().random.nextFloat() / 3 + 0.5F)));

        if (getWorld().random.nextBoolean())
        {
            bezierFirstPoint = bezierFirstPoint.add(0, getWorld().random.nextFloat() * 1.25F, 0);
            bezierFirstPoint = bezierSecondPoint.add(0, -getWorld().random.nextFloat() * 1.25F, 0);
        }
        else
        {
            bezierFirstPoint = bezierFirstPoint.add(0, -getWorld().random.nextFloat() * 1.25F, 0);
            bezierFirstPoint = bezierSecondPoint.add(0, getWorld().random.nextFloat() * 1.25F, 0);
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

            clientRecipeOutput = altarCrafting.getResult();
            clientRecipeToConsume = altarCrafting.getToConsume();
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
}
