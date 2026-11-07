package net.voxelindustry.voidheart.common.content.flask;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The flasks standing on one block, oldest first, one item each.
 */
public class PlacedFlasksTile extends TileBase
{
    private final List<ItemStack> flasks = new ArrayList<>();

    public PlacedFlasksTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.PLACED_FLASKS, pos, state);
    }

    public List<ItemStack> getFlasks()
    {
        return Collections.unmodifiableList(flasks);
    }

    public boolean isFull()
    {
        return flasks.size() >= FlaskLayout.MAX_FLASKS;
    }

    public boolean add(ItemStack flask)
    {
        if (isFull() || flask.isEmpty())
            return false;
        flasks.add(flask.copyWithCount(1));
        changed();
        return true;
    }

    /**
     * Takes back the flask placed last, or nothing if none stand here.
     */
    public ItemStack removeLast()
    {
        if (flasks.isEmpty())
            return ItemStack.EMPTY;
        var flask = flasks.removeLast();
        changed();
        return flask;
    }

    public ItemStack peekLast()
    {
        return flasks.isEmpty() ? ItemStack.EMPTY : flasks.getLast();
    }

    private void changed()
    {
        setChanged();
        sync();
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
        flasks.clear();
        // Saved data may hold more than fits or empty stacks, so keep only what this block can show
        for (var flask : tag.read("flasks", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()))
        {
            if (!flask.isEmpty() && flasks.size() < FlaskLayout.MAX_FLASKS)
                flasks.add(flask.copyWithCount(1));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("flasks", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(flasks));
        super.saveAdditional(tag);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        for (var flask : flasks)
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, flask);
        flasks.clear();
        super.preRemoveSideEffects(pos, state);
    }
}
