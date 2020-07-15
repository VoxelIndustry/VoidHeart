package net.voxelindustry.voidheart.common.tile;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class VoidPillarTile extends TileBase
{
    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    public VoidPillarTile()
    {
        super(VoidHeartTiles.VOID_PILLAR);
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
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        tag.put("stack", stack.toTag(new CompoundTag()));

        return super.toTag(tag);
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack;

        sync();
    }
}
