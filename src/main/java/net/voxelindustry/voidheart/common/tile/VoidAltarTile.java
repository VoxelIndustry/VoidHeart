package net.voxelindustry.voidheart.common.tile;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.LiteralText;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.steamlayer.recipe.state.RecipeState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.recipe.AltarRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.List;

public class VoidAltarTile extends TileBase implements Tickable
{
    private AltarRecipe currentRecipe;
    @Getter
    private RecipeState recipeState;

    private int     recipeProgress;
    private boolean isCrafting;

    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private final List<VoidPillarTile> pillars = new ArrayList<>();

    public VoidAltarTile()
    {
        super(VoidHeartTiles.VOID_ALTAR);
    }

    @Override
    public void setLocation(World world, BlockPos pos)
    {
        super.setLocation(world, pos);

        // Called on world load
        sync();
    }

    @Override
    public void fromTag(BlockState state, CompoundTag tag)
    {
        super.fromTag(state, tag);

        stack = ItemStack.fromTag(tag.getCompound("stack"));
        recipeProgress = tag.getInt("recipeProgress");
        isCrafting = tag.getBoolean("isCrafting");

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

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        tag.put("stack", stack.toTag(new CompoundTag()));
        tag.putInt("recipeProgress", recipeProgress);
        tag.putBoolean("isCrafting", isCrafting);

        if (isCrafting && recipeState != null)
            tag.put("recipeState", recipeState.toTag());

        return super.toTag(tag);
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
                }
            }
            else
                player.sendMessage(new LiteralText("Missing " + (8 - pillars.size()) + " pillars"), true);
        }
        else if (isCrafting)
            stopCrafting();

        sync();
    }

    @Override
    public void tick()
    {
        if (isClient())
        {
            if (!stack.isEmpty() && isCrafting)
            {
/*                getWorld().addParticle(VoidHeart.ALTAR_PILLAR_PARTICLE,
                        getPos().getX() + 0.5,
                        getPos().getY() + 1 + 6 / 16D,
                        getPos().getZ() + 0.5,
                        1,
                        1,
                        1);*/

/*                getWorld().addParticle(ParticleTypes.PORTAL,
                        getPos().getX() + 0.5,
                        getPos().getY() + 1 + 6 / 16D,
                        getPos().getZ() + 0.5,
                        1,
                        -1,
                        3);*/
            }
            return;
        }

        if (!isCrafting)
            return;

        if (currentRecipe == null)
        {
            stopCrafting();
            sync();
            return;
        }

        recipeProgress++;
        if (recipeProgress >= currentRecipe.getTime())
        {
            ItemStack toEat = getNextItemToEat();
            if (toEat.isEmpty())
                finishCrafting();
            else if (tryEatItem(toEat))
            {
                sync();
                recipeProgress = 0;
                getWorld().playSound(getPos().getX(), getPos().getY(), getPos().getZ(), SoundEvents.BLOCK_PORTAL_TRAVEL, SoundCategory.BLOCKS, getWorld().random.nextFloat() * 0.4F + 0.8F, 0.25F, true);
            }
        }
    }

    private void retrieveCurrentRecipe()
    {
        currentRecipe = (AltarRecipe) VoidHeartRecipes.ALTAR_CATEGORY.findOneRecipe(stack).orElse(null);
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
        pillars.remove(pillar);
    }

    private void startCrafting()
    {
        isCrafting = true;

        if (!getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, true));

        pillars.forEach(pillar -> pillar.addAltar(getPos()));
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

    }

    private void finishCrafting()
    {
        stack = currentRecipe.getRecipeOutputs(ItemStack.class).get(0).getRaw();
        recipeState = null;
        recipeProgress = 0;
        currentRecipe = null;
        isCrafting = false;
        sync();

        if (getCachedState().get(Properties.LIT))
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, false));

        pillars.forEach(pillar -> pillar.removeAltar(getPos()));
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

    private boolean tryEatItem(ItemStack toEat)
    {
        if (toEat.isEmpty())
            return false;

        pillars.removeIf(BlockEntity::isRemoved);

        if (pillars.size() != 8)
            searchPillars();

        for (VoidPillarTile pillar : pillars)
        {
            if (ItemUtils.deepEquals(pillar.getStack(), toEat))
            {
                recipeState.consumeSlotless(ItemStack.class, pillar.getStack());
                pillar.setStack(ItemStack.EMPTY);
                return true;
            }
        }
        return false;
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
}
