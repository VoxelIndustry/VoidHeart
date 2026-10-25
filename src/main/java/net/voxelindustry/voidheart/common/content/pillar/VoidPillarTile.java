package net.voxelindustry.voidheart.common.content.pillar;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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

        int pillarCount = tag.getInt("pillarCount");
        for (int index = 0; index < pillarCount; index++)
            altars.add(BlockPos.of(tag.getLong("pillar" + index)));
    }

    @Override
    public void saveAdditional(CompoundTag tag)
    {
        tag.put("stack", stack.save(new CompoundTag()));

        tag.putInt("pillarCount", altars.size());
        for (int index = 0; index < altars.size(); index++)
            tag.putLong("pillar" + index, altars.get(index).asLong());

        super.saveAdditional(tag);
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
        BlockState cachedState = getBlockState();

        if (cachedState.getBlock() != VoidHeartBlocks.VOID_PILLAR)
            return;

        if (cachedState.getValue(BlockStateProperties.LIT) != active)
            getLevel().setBlockAndUpdate(getBlockPos(), cachedState.setValue(BlockStateProperties.LIT, active));
    }

    public void removeItself()
    {
        altars.forEach(altarPos ->
        {
            var tile = getLevel().getBlockEntity(altarPos);

            if (tile instanceof PillarLinkedTile linkedTile)
                linkedTile.removePillar(this);
        });
    }
}
