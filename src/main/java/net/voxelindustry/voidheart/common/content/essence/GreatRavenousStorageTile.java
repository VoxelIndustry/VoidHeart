package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.GreatStorageLayout.Part;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One block of a Great Ravenous Storage, holding its share of the cube's essence. The cube's lowest north-west block
 * answers relays for all eight, and only while the cube is whole.
 */
public class GreatRavenousStorageTile extends TileBase implements IEssenceReceiver
{
    public static final int CAPACITY       = RavenousStorageTile.CAPACITY * 128;
    public static final int SHARE_CAPACITY = CAPACITY / GreatStorageLayout.BLOCKS;

    private EssenceStorage share = new EssenceStorage(EssenceRegistry.EMPTY, SHARE_CAPACITY);

    public GreatRavenousStorageTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.GREAT_RAVENOUS_STORAGE, pos, state);
    }

    /**
     * The eight blocks of the cube this one belongs to, in the order of {@link Part#CORNERS}, or null while it is loose
     * or the cube is not whole.
     */
    public GreatRavenousStorageTile @Nullable [] cube()
    {
        return level == null ? null : cubeAt(level, worldPosition, getBlockState());
    }

    public static GreatRavenousStorageTile @Nullable [] cubeAt(BlockGetter level, BlockPos pos, BlockState state)
    {
        if (!(state.getBlock() instanceof GreatRavenousStorageBlock) || state.getValue(GreatRavenousStorageBlock.PART) == Part.LOOSE)
            return null;
        var origin = GreatRavenousStorageBlock.origin(pos, state.getValue(GreatRavenousStorageBlock.PART));
        var tiles = new GreatRavenousStorageTile[GreatStorageLayout.BLOCKS];
        for (var index = 0; index < tiles.length; index++)
        {
            var part = Part.CORNERS.get(index);
            var member = origin.offset(part.dx(), part.dy(), part.dz());
            // A cube across a chunk border is not whole while half of it is unloaded
            if (level instanceof Level world && !world.isLoaded(member))
                return null;
            var memberState = level.getBlockState(member);
            if (!(memberState.getBlock() instanceof GreatRavenousStorageBlock) || memberState.getValue(GreatRavenousStorageBlock.PART) != part
                    || !(level.getBlockEntity(member) instanceof GreatRavenousStorageTile tile))
                return null;
            tiles[index] = tile;
        }
        return tiles;
    }

    private GreatRavenousStorageTile @Nullable [] heldCube()
    {
        return getBlockState().getValue(GreatRavenousStorageBlock.PART).isHolder() ? cube() : null;
    }

    /**
     * The aspect of a whole cube: that of its blocks, which join attuned to one aspect or none.
     */
    private static EssenceType aspect(GreatRavenousStorageTile[] cube)
    {
        for (var tile : cube)
        {
            if (!tile.share.isUnattuned())
                return tile.share.type();
        }
        return EssenceRegistry.EMPTY;
    }

    static int[] amounts(GreatRavenousStorageTile[] cube)
    {
        var amounts = new int[cube.length];
        for (var index = 0; index < cube.length; index++)
            amounts[index] = cube[index].share.amount();
        return amounts;
    }

    private static int total(GreatRavenousStorageTile[] cube)
    {
        var total = 0;
        for (var tile : cube)
            total += tile.share.amount();
        return total;
    }

    @Override
    public boolean isStore()
    {
        return heldCube() != null;
    }

    @Override
    public boolean linkable()
    {
        return isStore();
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        var cube = heldCube();
        if (cube == null)
            return 0;
        var aspect = aspect(cube);
        return aspect == EssenceRegistry.EMPTY || aspect == type ? CAPACITY - total(cube) : 0;
    }

    /**
     * An unattuned cube attunes all its blocks to the first aspect it receives.
     */
    @Override
    public int receive(EssenceType type, int amount)
    {
        var cube = heldCube();
        if (cube == null || type == EssenceRegistry.EMPTY)
            return 0;
        var aspect = aspect(cube);
        if (aspect == EssenceRegistry.EMPTY)
        {
            for (var tile : cube)
                tile.share.attune(type);
        }
        else if (aspect != type)
            return 0;

        var added = GreatStorageShares.fill(amounts(cube), SHARE_CAPACITY, amount);
        var received = 0;
        for (var index = 0; index < cube.length; index++)
            received += cube[index].share.fill(type, added[index]);
        if (received > 0)
            changed(cube, added);
        return received;
    }

    @Override
    public int amountOf(EssenceType type)
    {
        var cube = heldCube();
        return cube != null && aspect(cube) == type ? total(cube) : 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        var cube = heldCube();
        if (cube == null || aspect(cube) != type)
            return 0;

        var taken = GreatStorageShares.drain(amounts(cube), amount);
        var extracted = 0;
        for (var index = 0; index < cube.length; index++)
            extracted += cube[index].share.drain(type, taken[index]);
        if (extracted > 0)
            changed(cube, taken);
        return extracted;
    }

    /**
     * What the whole cube holds, for Jade and the renderer, or this block's share while it is loose.
     */
    public EssenceStorage held()
    {
        var cube = cube();
        if (cube == null)
            return share;
        var held = new EssenceStorage(aspect(cube), CAPACITY);
        held.fill(held.type(), total(cube));
        return held;
    }

    /**
     * Saves and shows the blocks whose share moved, and raises or lowers the pillars of all eight.
     */
    private void changed(GreatRavenousStorageTile[] cube, int[] moved)
    {
        for (var index = 0; index < cube.length; index++)
        {
            if (moved[index] == 0)
                continue;
            cube[index].setChanged();
            if (!level.isClientSide())
                cube[index].sync();
        }
        if (!level.isClientSide())
            setFill(cube, StorageFill.fillOf(total(cube), CAPACITY));
    }

    static void setFill(GreatRavenousStorageTile[] cube, int fill)
    {
        for (var tile : cube)
        {
            var state = tile.getBlockState();
            if (state.getValue(GreatRavenousStorageBlock.FILL) != fill)
                tile.level.setBlock(tile.worldPosition, state.setValue(GreatRavenousStorageBlock.FILL, fill), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Joins eight loose blocks into a cube: their essence is pooled, spread evenly over their shares, and all are
     * attuned to its aspect, or to none when they hold nothing.
     */
    static void join(GreatRavenousStorageTile[] blocks, EssenceType aspect)
    {
        long total = 0;
        for (var tile : blocks)
            total += tile.share.amount();
        var shares = GreatStorageShares.spread(total, blocks.length);
        for (var index = 0; index < blocks.length; index++)
        {
            var tile = blocks[index];
            tile.share = new EssenceStorage(aspect, SHARE_CAPACITY);
            tile.share.fill(aspect, shares[index]);
            tile.setChanged();
            tile.sync();
        }
    }

    static List<EssenceType> aspects(GreatRavenousStorageTile[] blocks)
    {
        var aspects = new ArrayList<EssenceType>();
        for (var tile : blocks)
            aspects.add(tile.share.type());
        return aspects;
    }

    public EssenceStorage share()
    {
        return share;
    }

    /**
     * Takes back the share an item carried. A share saved with another capacity keeps its essence up to this one.
     */
    public void restoreShare(EssenceStorage source)
    {
        restoreLoaded(source);
        setChanged();
        if (level != null && !level.isClientSide())
            sync();
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        nbt.read("share", EssenceStorage.CODEC).ifPresent(this::restoreLoaded);
    }

    private void restoreLoaded(EssenceStorage loaded)
    {
        // An aspect no longer registered leaves the share unattuned
        var type = loaded.type() == null ? EssenceRegistry.EMPTY : loaded.type();
        share = new EssenceStorage(type, SHARE_CAPACITY);
        if (type != EssenceRegistry.EMPTY)
            share.fill(type, loaded.amount());
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("share", EssenceStorage.CODEC, share);
    }
}
