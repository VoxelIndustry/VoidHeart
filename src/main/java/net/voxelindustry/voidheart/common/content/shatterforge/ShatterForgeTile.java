package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.serialization.Codec;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.World;
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

    private final Collection<Identifier> syncElements = singletonList(AltarCraftingSyncElement.IDENTIFIER);

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

    private final BlockPos.Mutable particlePosCache = new BlockPos.Mutable();

    private void playParticleEffects()
    {
        if (connectedMonolithCount != 0)
        {
            if (getWorld().getTime() % 20 == 0)
            {
                particlePosCache.set(getPos());
                var monolithToLit = (connectedMonolithCount + 1) * (warmProgress / (float) getWarmingTime());
                for (int i = 0; i < monolithToLit; i++)
                {
                    getWorld().addParticle(new ShatterForgeRuneParticleEffect(getCachedState().get(Properties.HORIZONTAL_AXIS)),
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
            getWorld().addParticle(new ShatterForgeItemParticleEffect(clientRecipeInput, clientRecipeOutput),
                    getPos().getX() + 0.5,
                    getPos().getY() + 1.3,
                    getPos().getZ() + 0.5,
                    pillarPos.getX() + 0.5,
                    pillarPos.getY() + 1.3,
                    pillarPos.getZ() + 0.5);
            getWorld().addParticle(new ShatterForgeItemParticleEffect(clientRecipeInput, clientRecipeOutput),
                    getPos().getX() + 0.5,
                    getPos().getY() + 1.3,
                    getPos().getZ() + 0.5,
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

        var mutablePos = pos.mutableCopy();
        if (!ifConduitThenAdd(mutablePos.move(Direction.DOWN)))
            return false;

        var axis = getCachedState().get(Properties.HORIZONTAL_AXIS);

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

        var isConduitInFront = ifConduitThenAdd(mutablePos.set(pos).move(Direction.DOWN).move(front));
        var isConduitInBack = ifConduitThenAdd(mutablePos.move(back, 2));

        if (isConduitInFront && isConduitInBack)
        {
            formationError = FormationError.CONDUIT_BOTH_DIRECTION;
            return false;
        }

        var actualForward = isConduitInFront ? front : back;

        mutablePos.set(pos);
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
            mutablePos.set(pos).move(actualForward, 3 * i + 1).move(Direction.DOWN);

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
        var isConduit = world.getBlockState(pos).isOf(VoidHeartBlocks.VOID_CONDUIT);
        if (isConduit)
            this.connectedBlocks.add(pos.toImmutable());
        return isConduit;
    }

    private boolean ifMonolithThenAdd(BlockPos pos)
    {
        var isMonolith = world.getBlockState(pos).isOf(VoidHeartBlocks.VOID_MONOLITH);
        if (isMonolith)
            this.connectedBlocks.add(pos.toImmutable());
        return isMonolith;
    }

    private boolean ifPillarThenAdd(BlockPos pos)
    {
        var isPillar = world.getBlockState(pos).isOf(VoidHeartBlocks.VOID_PILLAR);
        if (isPillar)
        {
            pillar = ((VoidPillarTile) world.getBlockEntity(pos));
            pillar.addAltar(getPos());
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

        markDirty();
        sync();
    }

    public void removeItself()
    {
        if (getPillar() != null)
            pillar.removeAltar(getPos());
    }

    private void retrieveCurrentRecipe()
    {
        currentRecipe = VoidHeartRecipes.SHATTER_FORGE_CATEGORY.findOneRecipe(stack).orElse(null);
    }

    public void startCrafting()
    {
        var litState = getCachedState().with(Properties.LIT, true);
        world.setBlockState(pos, litState);
        setCachedState(litState);

        for (var pos : this.connectedBlocks)
            world.setBlockState(pos, world.getBlockState(pos).with(Properties.LIT, true));
        world.setBlockState(getPillar().getPos(), world.getBlockState(getPillar().getPos()).with(Properties.LIT, true));

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

        if (getCachedState().get(Properties.LIT) && getWorld() != null)
        {
            getWorld().setBlockState(getPos(), getCachedState().with(Properties.LIT, false));

            for (var blockPos : this.connectedBlocks)
            {
                var blockState = getWorld().getBlockState(blockPos);

                if (blockState.isOf(VoidHeartBlocks.VOID_MONOLITH) || blockState.isOf(VoidHeartBlocks.VOID_CONDUIT))
                    getWorld().setBlockState(blockPos, blockState.with(Properties.LIT, false));
            }

            if (getPillar() != null)
            {
                var pillarState = getWorld().getBlockState(getPillar().getPos());
                if (pillarState.isOf(VoidHeartBlocks.VOID_PILLAR))
                    getWorld().setBlockState(getPillar().getPos(), pillarState.with(Properties.LIT, false));
            }
        }

        if (getPillar() != null)
            pillar.removeAltar(getPos());
        PartialTileSync.syncPart(this, ShatterForgeCraftingSyncElement.IDENTIFIER);
    }

    public static void tick(World world, BlockPos pos, BlockState state, ShatterForgeTile forge)
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
            forge.markDirty();
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
        isCrafting = tag.getBoolean("isCrafting");
        hasOutputtedResult = tag.getBoolean("hasOutputtedResult");

        connectedMonolithCount = tag.getInt("connectedMonolithCount");
        trackDirection = Direction.byId(tag.getByte("trackDirection"));

        pillarPos = BlockPos.fromLong(tag.getLong("pillarPos"));

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
    public void writeNbt(NbtCompound tag)
    {
        tag.put("stack", stack.writeNbt(new NbtCompound()));
        tag.putInt("recipeProgress", recipeProgress);
        tag.putInt("warmProgress", warmProgress);
        tag.putBoolean("isCrafting", isCrafting);
        tag.putInt("connectedMonolithCount", connectedMonolithCount);
        tag.putBoolean("hasOutputtedResult", hasOutputtedResult);

        if (trackDirection != null)
            tag.putByte("trackDirection", (byte) trackDirection.ordinal());

        if (pillar != null)
            tag.putLong("pillarPos", pillar.getPos().asLong());
        else if (pillarPos != null)
            tag.putLong("pillarPos", pillarPos.asLong());

        if (isCrafting && recipeState != null)
            tag.put("recipeState", recipeState.toTag());

        super.writeNbt(tag);
    }

    private VoidPillarTile getPillar()
    {
        if (pillar == null)
        {
            if (pillarPos != null)
                return pillar = (VoidPillarTile) world.getBlockEntity(pillarPos);
        }
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

    public enum FormationError
    {
        NO_MONOLITH_AROUND,
        CONDUIT_BOTH_DIRECTION,
        TOO_FEW_MONOLITH,
        NO_PILLAR_AT_END
    }
}
