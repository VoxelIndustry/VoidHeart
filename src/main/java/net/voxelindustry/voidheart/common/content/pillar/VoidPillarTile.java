package net.voxelindustry.voidheart.common.content.pillar;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.inventorymover.SingleStackExtractable;
import net.voxelindustry.voidheart.common.content.inventorymover.SingleStackInsertable;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.ArrayList;
import java.util.List;

public class VoidPillarTile extends TileBase implements SingleStackInsertable, SingleStackExtractable
{
    @Getter
    private ItemStack stack = ItemStack.EMPTY;

    private final List<BlockPos> altars = new ArrayList<>();

    public VoidPillarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.VOID_PILLAR, pos, state);
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

        int pillarCount = tag.getInt("pillarCount");
        for (int index = 0; index < pillarCount; index++)
            altars.add(BlockPos.fromLong(tag.getLong("pillar" + index)));
    }

    @Override
    public void writeNbt(NbtCompound tag)
    {
        tag.put("stack", stack.writeNbt(new NbtCompound()));

        tag.putInt("pillarCount", altars.size());
        for (int index = 0; index < altars.size(); index++)
            tag.putLong("pillar" + index, altars.get(index).asLong());

        super.writeNbt(tag);
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
            var tile = getWorld().getBlockEntity(altarPos);

            if (tile instanceof PillarLinkedTile linkedTile)
                linkedTile.removePillar(this);
        });
    }
}
