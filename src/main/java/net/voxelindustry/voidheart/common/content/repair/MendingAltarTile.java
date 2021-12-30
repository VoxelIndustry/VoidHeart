package net.voxelindustry.voidheart.common.content.repair;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class MendingAltarTile extends TileBase
{
    @Getter
    private ItemStack tool = ItemStack.EMPTY;

    public MendingAltarTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.MENDING_ALTAR, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, MendingAltarTile altar)
    {

    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        tool = ItemStack.fromNbt(nbt.getCompound("tool"));
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        nbt.put("tool", tool.writeNbt(new NbtCompound()));
    }
}
