package net.voxelindustry.voidheart.common.content.repair;

import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class ExperienceSkullTile extends TileBase
{
    public static final int MAX_EXPERIENCE = 1500;

    @Getter
    private int experience;

    public ExperienceSkullTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.EXPERIENCE_SKULL, pos, state);
    }

    @Override
    public void setWorld(World world)
    {
        super.setWorld(world);

        // Called on world load
        sync();
    }

    @Override
    public void readNbt(NbtCompound nbt)
    {
        super.readNbt(nbt);

        experience = nbt.getInt("experience");
    }

    @Override
    protected void writeNbt(NbtCompound nbt)
    {
        super.writeNbt(nbt);

        nbt.putInt("experience", experience);
    }

    public void setExperience(int experience)
    {
        this.experience = experience;

        this.sync();
    }
}
