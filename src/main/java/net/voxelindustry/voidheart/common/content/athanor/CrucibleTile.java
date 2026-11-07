package net.voxelindustry.voidheart.common.content.athanor;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.inventorymover.SingleStackExtractable;
import net.voxelindustry.voidheart.common.content.inventorymover.SingleStackInsertable;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Holds the one reagent an athanor next to it draws for its brew.
 */
public class CrucibleTile extends TileBase implements SingleStackInsertable, SingleStackExtractable
{
    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    public CrucibleTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.CRUCIBLE, pos, state);
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
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("stack", ItemStack.OPTIONAL_CODEC, stack);
        super.saveAdditional(tag);
    }

    @Override
    public void setStack(ItemStack stack)
    {
        this.stack = stack;
        setChanged();
        sync();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        super.preRemoveSideEffects(pos, state);
    }
}
