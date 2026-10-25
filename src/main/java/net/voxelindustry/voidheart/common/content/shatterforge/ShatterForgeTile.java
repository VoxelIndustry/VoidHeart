package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.steamlayer.network.tilesync.PartialSyncedTile;
import net.voxelindustry.steamlayer.network.tilesync.PartialTileSync;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;
import net.voxelindustry.steamlayer.recipe.state.RecipeState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.AltarCraftingSyncElement;
import net.voxelindustry.voidheart.common.content.inventorymover.SingleStackInsertable;
import net.voxelindustry.voidheart.common.content.pillar.PillarLinkedTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.recipe.ShatterForgeRecipe;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartRecipes;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static java.util.Collections.singletonList;

public class ShatterForgeTile extends TileBase implements PillarLinkedTile, PartialSyncedTile, SingleStackInsertable
{
    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private VoidPillarTile pillar;

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

    private int connectedMonolithCount;
    private Direction trackDirection;

    private final Collection<ResourceLocation> syncElements = singletonList(AltarCraftingSyncElement.IDENTIFIER);

    ////////////
    // CLIENT //
    ////////////

    @Getter
    private ItemStack clientRecipeInput = ItemStack.EMPTY;
    @Getter
    private ItemStack clientRecipeOutput = ItemStack.EMPTY;
    private BlockPos pillarPos;

    public ShatterForgeTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.SHATTER_FORGE, pos, state);
    }

    private final BlockPos.MutableBlockPos particlePosCache = new BlockPos.MutableBlockPos();

    private void playParticleEffects()
    {
        if (connectedMonolithCount != 0)
        {
            if (getLevel().getGameTime() % 20 == 0)
            {
                particlePosCache.set(getBlockPos());
                var monolithToLit = (connectedMonolithCount + 1) * (warmProgress / (float) getWarmingTime());
                for (int i = 0; i < monolithToLit; i++)
                {
                    getLevel().addParticle(new ShatterForgeRuneParticleEffect(getBlockState().getValue(BlockStateProperties.HORIZONTAL_AXIS)),
                            particlePosCache.getX() + 0.5,
                            particlePosCache.getY() + 1.5,
                            particlePosCache.getZ() + 0.5,
                            0,
                            0,
                            0);
                    particlePosCache.move(trackDirection, 3);
                }
            }
        }

        if (isCrafting && recipeProgress > 1 && getPillar() != null && !clientRecipeOutput.isEmpty())
        {
            getLevel().addParticle(new ShatterForgeItemParticleEffect(clientRecipeInput, clientRecipeOutput),
                    getBlockPos().getX() + 0.5,
                    getBlockPos().getY() + 1.3,
                    getBlockPos().getZ() + 0.5,
                    pillarPos.getX() + 0.5,
                    pillarPos.getY() + 1.3,
                    pillarPos.getZ() + 0.5);
            getLevel().addParticle(new ShatterForgeItemParticleEffect(clientRecipeInput, clientRecipeOutput),
                    getBlockPos().getX() + 0.5,
                    getBlockPos().getY() + 1.3,
                    getBlockPos().getZ() + 0.5,
                    pillarPos.getX() + 0.5,
                    pillarPos.getY() + 1.3,
                    pillarPos.getZ() + 0.5);
        }
    }

    public int getWarmingTime()
    {
        return connectedMonolithCount * 25;
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

    private boolean isStructureCorrect()
    {
        if (this.currentRecipe == null)
            return false;

        connectedBlocks.clear();

        var neededMonoliths = this.currentRecipe.getMonolithCount();

        var mutablePos = worldPosition.mutable();
        if (!ifConduitThenAdd(mutablePos.move(Direction.DOWN)))
            return false;

        var axis = getBlockState().getValue(BlockStateProperties.HORIZONTAL_AXIS);

        var front = axis == Axis.Z ? Direction.NORTH : Direction.WEST;
        var back = axis == Axis.Z ? Direction.SOUTH : Direction.EAST;
        var left = axis == Axis.Z ? Direction.WEST : Direction.NORTH;
        var right = axis == Axis.Z ? Direction.EAST : Direction.SOUTH;


        // First Monolith Pair
        if (!ifConduitThenAdd(mutablePos.move(left))
                || !ifConduitThenAdd(mutablePos.move(left))
                || !ifMonolithThenAdd(mutablePos.move(Direction.UP))
                || !ifMonolithThenAdd(mutablePos.move(Direction.UP))
                || !ifMonolithThenAdd(mutablePos.move(Direction.UP))
                || !ifMonolithThenAdd(mutablePos.move(right, 4))
                || !ifMonolithThenAdd(mutablePos.move(Direction.DOWN))
                || !ifMonolithThenAdd(mutablePos.move(Direction.DOWN))
                || !ifConduitThenAdd(mutablePos.move(Direction.DOWN))
                || !ifConduitThenAdd(mutablePos.move(left))
        )
        {
            formationError = FormationError.NO_MONOLITH_AROUND;
            return false;
        }

        var isConduitInFront = ifConduitThenAdd(mutablePos.set(worldPosition).move(Direction.DOWN).move(front));
        var isConduitInBack = ifConduitThenAdd(mutablePos.move(back, 2));

        if (isConduitInFront && isConduitInBack)
        {
            formationError = FormationError.CONDUIT_BOTH_DIRECTION;
            return false;
        }

        var actualForward = isConduitInFront ? front : back;

        mutablePos.set(worldPosition);
        var advance = 0;
        while (true)
        {
            if (advance > 31)
            {
                formationError = FormationError.NO_PILLAR_AT_END;
                return false;
            }

            if (ifPillarThenAdd(mutablePos.move(actualForward)))
                break;

            advance++;
        }

        if (advance % 3 != 0)
        {
            formationError = FormationError.NO_PILLAR_AT_END;
            return false;
        }

        var availableMonolith = Math.floorDiv(advance, 3);
        if (availableMonolith < neededMonoliths - 1)
        {
            formationError = FormationError.TOO_FEW_MONOLITH;
            return false;
        }

        for (int i = 0; i < availableMonolith; i++)
        {
            mutablePos.set(worldPosition).move(actualForward, 3 * i + 1).move(Direction.DOWN);

            if (!ifConduitThenAdd(mutablePos)
                    || !ifConduitThenAdd(mutablePos.move(actualForward))
                    || !ifConduitThenAdd(mutablePos.move(actualForward))
                    || !ifConduitThenAdd(mutablePos.move(left))
                    || !ifConduitThenAdd(mutablePos.move(left))
                    || !ifMonolithThenAdd(mutablePos.move(Direction.UP))
                    || !ifMonolithThenAdd(mutablePos.move(Direction.UP))
                    || !ifMonolithThenAdd(mutablePos.move(Direction.UP))
                    || !ifMonolithThenAdd(mutablePos.move(right, 4))
                    || !ifMonolithThenAdd(mutablePos.move(Direction.DOWN))
                    || !ifMonolithThenAdd(mutablePos.move(Direction.DOWN))
                    || !ifConduitThenAdd(mutablePos.move(Direction.DOWN))
                    || !ifConduitThenAdd(mutablePos.move(left))
            )
            {
                formationError = FormationError.TOO_FEW_MONOLITH;
                return false;
            }
        }

        this.connectedMonolithCount = availableMonolith;
        this.trackDirection = actualForward;
        return true;
    }

    private boolean ifConduitThenAdd(BlockPos pos)
    {
        var isConduit = level.getBlockState(pos).is(VoidHeartBlocks.VOID_CONDUIT);
        if (isConduit)
            this.connectedBlocks.add(pos.immutable());
        return isConduit;
    }

    private boolean ifMonolithThenAdd(BlockPos pos)
    {
        var isMonolith = level.getBlockState(pos).is(VoidHeartBlocks.VOID_MONOLITH);
        if (isMonolith)
            this.connectedBlocks.add(pos.immutable());
        return isMonolith;
    }

    private boolean ifPillarThenAdd(BlockPos pos)
    {
        var isPillar = level.getBlockState(pos).is(VoidHeartBlocks.VOID_PILLAR);
        if (isPillar)
        {
            pillar = ((VoidPillarTile) level.getBlockEntity(pos));
            pillar.addAltar(getBlockPos());
        }
        return isPillar;
    }

    public void setStack(ItemStack stack)
    {
        var previousStack = this.stack;
        this.stack = stack;

        if (stack.isEmpty())
        {
            if (!previousStack.isEmpty() && isCrafting)
            {
                stopCrafting();
                sync();
            }
            return;
        }

        retrieveCurrentRecipe();
        var isStructureCorrect = isStructureCorrect();

        if (isStructureCorrect && !isCrafting())
            startCrafting();

        setChanged();
        sync();
    }

    public void removeItself()
    {
        if (getPillar() != null)
            pillar.removeAltar(getBlockPos());
    }

    private void retrieveCurrentRecipe()
    {
        currentRecipe = VoidHeartRecipes.SHATTER_FORGE_CATEGORY.findOneRecipe(stack).orElse(null);
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

                if (blockState.is(VoidHeartBlocks.VOID_MONOLITH) || blockState.is(VoidHeartBlocks.VOID_CONDUIT))
                    getLevel().setBlockAndUpdate(blockPos, blockState.setValue(BlockStateProperties.LIT, false));
            }

            if (getPillar() != null)
            {
                var pillarState = getLevel().getBlockState(getPillar().getBlockPos());
                if (pillarState.is(VoidHeartBlocks.VOID_PILLAR))
                    getLevel().setBlockAndUpdate(getPillar().getBlockPos(), pillarState.setValue(BlockStateProperties.LIT, false));
            }
        }

        if (getPillar() != null)
            pillar.removeAltar(getBlockPos());
        PartialTileSync.syncPart(this, ShatterForgeCraftingSyncElement.IDENTIFIER);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, ShatterForgeTile forge)
    {
        if (forge.isClient())
        {
            forge.playParticleEffects();

            if (forge.isCrafting && forge.warmProgress < forge.getWarmingTime())
                forge.warmProgress++;

            return;
        }

        if (!forge.isCrafting)
            return;

        if (forge.currentRecipe == null)
        {
            forge.stopCrafting();
            forge.sync();
            return;
        }

        if (forge.warmProgress < forge.getWarmingTime())
        {
            forge.warmProgress++;
            forge.setChanged();
            return;
        }

        if (forge.recipeState != null && !forge.recipeState.isCompleted())
        {
            if (forge.getPillar() == null || !forge.pillar.getStack().isEmpty())
                return;

            forge.recipeState.tick(1);
            forge.recipeProgress++;

            if (forge.recipeState.completionDelta() > 0.5F && forge.recipeState.getIngredientConsumed(ItemStack.class, 0).isEmpty())
            {
                if (forge.getStack().isEmpty())
                {
                    forge.stopCrafting();
                    forge.sync();
                    return;
                }
                forge.recipeState.consumeSlotless(ItemStack.class, forge.getStack());
                forge.stack = ItemStack.EMPTY;
            }
        }
        else
        {
            if (forge.getPillar() != null)
            {
                if (!forge.hasOutputtedResult)
                {
                    forge.pillar.setStack(forge.recipeState.getOutput(ItemStack.class, 0));
                    forge.recipeState.complete(world, pos, null);
                    forge.hasOutputtedResult = true;
                }

                if (!forge.stack.isEmpty() && forge.pillar.getStack().isEmpty())
                {
                    if (forge.recipeState.getRecipe().getRecipeInputs(ItemStack.class).get(0).matchWithQuantity(forge.stack))
                    {
                        forge.recipeState.reset();
                        forge.hasOutputtedResult = false;
                    }
                    else
                    {
                        forge.stopCrafting();
                        forge.startCrafting();
                    }
                }
            }

            if (forge.getStack().isEmpty())
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
    public void load(CompoundTag tag)
    {
        super.load(tag);

        stack = ItemStack.of(tag.getCompound("stack"));
        recipeProgress = tag.getInt("recipeProgress");
        warmProgress = tag.getInt("warmProgress");
        isCrafting = tag.getBoolean("isCrafting");
        hasOutputtedResult = tag.getBoolean("hasOutputtedResult");

        connectedMonolithCount = tag.getInt("connectedMonolithCount");
        trackDirection = Direction.from3DDataValue(tag.getByte("trackDirection"));

        pillarPos = BlockPos.of(tag.getLong("pillarPos"));

        if (isServer())
        {
            if (isCrafting)
            {
                this.recipeState = RecipeState.fromTag(VoidHeartRecipes.SHATTER_FORGE_CATEGORY, tag.getCompound("recipeState"));

                if (recipeState != null)
                    this.currentRecipe = (ShatterForgeRecipe) recipeState.getRecipe();
            }
            else if (recipeState != null)
                recipeState = null;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag)
    {
        tag.put("stack", stack.save(new CompoundTag()));
        tag.putInt("recipeProgress", recipeProgress);
        tag.putInt("warmProgress", warmProgress);
        tag.putBoolean("isCrafting", isCrafting);
        tag.putInt("connectedMonolithCount", connectedMonolithCount);
        tag.putBoolean("hasOutputtedResult", hasOutputtedResult);

        if (trackDirection != null)
            tag.putByte("trackDirection", (byte) trackDirection.ordinal());

        if (pillar != null)
            tag.putLong("pillarPos", pillar.getBlockPos().asLong());
        else if (pillarPos != null)
            tag.putLong("pillarPos", pillarPos.asLong());

        if (isCrafting && recipeState != null)
            tag.put("recipeState", recipeState.toTag());

        super.saveAdditional(tag);
    }

    private VoidPillarTile getPillar()
    {
        if (pillar == null)
        {
            if (pillarPos != null)
                return pillar = (VoidPillarTile) level.getBlockEntity(pillarPos);
        }
        return pillar;
    }

    @Override
    public Optional<TileSyncElement<?>> getSyncElement(ResourceLocation identifier)
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
    public Codec<?> getSyncElementCodec(ResourceLocation identifier)
    {
        if (identifier.equals(ShatterForgeCraftingSyncElement.IDENTIFIER))
            return ShatterForgeCraftingSyncElement.CODEC;
        return null;
    }

    @Override
    public Collection<ResourceLocation> getAllSyncElements()
    {
        return syncElements;
    }

    public enum FormationError
    {
        NO_MONOLITH_AROUND,
        CONDUIT_BOTH_DIRECTION,
        TOO_FEW_MONOLITH,
        NO_PILLAR_AT_END
    }
}
