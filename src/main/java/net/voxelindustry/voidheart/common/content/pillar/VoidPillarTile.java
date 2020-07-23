package net.voxelindustry.voidheart.common.content.pillar;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.List;

public class VoidPillarTile extends TileBase
{
    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private List<BlockPos> altars = new ArrayList<>();

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

        int pillarCount = tag.getInt("pillarCount");
        for (int index = 0; index < pillarCount; index++)
            altars.add(BlockPos.fromLong(tag.getLong("pillar" + index)));
    }

    @Override
    public CompoundTag toTag(CompoundTag tag)
    {
        tag.put("stack", stack.toTag(new CompoundTag()));

        tag.putInt("pillarCount", altars.size());
        for (int index = 0; index < altars.size(); index++)
            tag.putLong("pillar" + index, altars.get(index).asLong());

        return super.toTag(tag);
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack;

        sync();
    }

    public void addAltar(BlockPos pos)
    {
        if (!altars.contains(pos))
            altars.add(pos);

        setActive(true);
    }

    public void removeAltar(BlockPos pos)
    {
        altars.remove(pos);

        if (altars.isEmpty())
            setActive(false);
    }

    private void setActive(boolean active)
    {
        BlockState cachedState = getCachedState();

        if (cachedState.getBlock() != VoidHeartBlocks.VOID_PILLAR)
            return;

        if (cachedState.get(Properties.LIT) != active)
            getWorld().setBlockState(getPos(), cachedState.with(Properties.LIT, active));
    }

    public void removeItself()
    {
        altars.forEach(altarPos ->
        {
            VoidAltarTile altar = (VoidAltarTile) getWorld().getBlockEntity(altarPos);

            if (altar != null)
                altar.removePillar(this);
        });
    }
}
