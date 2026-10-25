package net.voxelindustry.voidheart.common.content.repair;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

public class ExperienceSkullTile extends TileBase
{
    public static final int MAX_EXPERIENCE = 1395;

    @Getter
    private int experience;

    public ExperienceSkullTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.EXPERIENCE_SKULL, pos, state);
    }

    @Override
    public void setLevel(Level world)
    {
        super.setLevel(world);

        // Called on world load
        sync();
    }

    @Override
    public void load(CompoundTag nbt)
    {
        super.load(nbt);

        experience = nbt.getInt("experience");
    }

    @Override
    protected void saveAdditional(CompoundTag nbt)
    {
        super.saveAdditional(nbt);

        nbt.putInt("experience", experience);
    }

    public void setExperience(int experience)
    {
        this.experience = experience;

        this.sync();
    }
}
