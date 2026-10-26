package net.voxelindustry.voidheart.common.content.pillar;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.Containers;
import lombok.Getter;
import net.minecraft.core.BlockPos;
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

    /**
     * Shatter Forge using this pillar as its item source, its item is raised to the forge beam height.
     */
    private BlockPos sourceOfForge;

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
    protected void loadAdditional(ValueInput tag)
    {
        super.loadAdditional(tag);

        stack = tag.read("stack", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);

        altars.clear();
        int pillarCount = tag.getIntOr("pillarCount", 0);
        for (int index = 0; index < pillarCount; index++)
            altars.add(BlockPos.of(tag.getLongOr("pillar" + index, 0L)));

        sourceOfForge = tag.getBooleanOr("hasSourceOfForge", false) ? BlockPos.of(tag.getLongOr("sourceOfForge", 0L)) : null;
    }

    @Override
    protected void saveAdditional(ValueOutput tag)
    {
        tag.store("stack", ItemStack.OPTIONAL_CODEC, stack);

        tag.putInt("pillarCount", altars.size());
        for (int index = 0; index < altars.size(); index++)
            tag.putLong("pillar" + index, altars.get(index).asLong());

        tag.putBoolean("hasSourceOfForge", sourceOfForge != null);
        if (sourceOfForge != null)
            tag.putLong("sourceOfForge", sourceOfForge.asLong());

        super.saveAdditional(tag);
    }

    public boolean isForgeSource()
    {
        return sourceOfForge != null;
    }

    public void setSourceOfForge(BlockPos forgePos)
    {
        if (forgePos.equals(sourceOfForge))
            return;
        sourceOfForge = forgePos;
        setChanged();
        sync();
    }

    /**
     * Only clears when the given forge is the one using this pillar.
     */
    public void clearSourceOfForge(BlockPos forgePos)
    {
        if (!forgePos.equals(sourceOfForge))
            return;
        sourceOfForge = null;
        setChanged();
        sync();
    }

    public void setStack(ItemStack stack)
    {
        this.stack = stack;

        setChanged();
        sync();

        if (level != null && !level.isClientSide())
            notifyLinkedTiles();
    }

    private void notifyLinkedTiles()
    {
        for (var altarPos : new ArrayList<>(altars))
        {
            if (level.getBlockEntity(altarPos) instanceof PillarLinkedTile linkedTile)
                linkedTile.onPillarStackChanged(this);
        }
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
        for (var altarPos : new ArrayList<>(altars))
        {
            if (getLevel().getBlockEntity(altarPos) instanceof PillarLinkedTile linkedTile)
                linkedTile.removePillar(this);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state)
    {
        removeItself();
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, getStack());
        super.preRemoveSideEffects(pos, state);
    }
}
