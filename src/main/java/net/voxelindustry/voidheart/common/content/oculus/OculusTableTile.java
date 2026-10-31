package net.voxelindustry.voidheart.common.content.oculus;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * Every block of a table keeps a copy of the shared view, so the table survives losing any of its blocks.
 */
public class OculusTableTile extends TileBase
{
    @Getter
    private OculusView view = OculusView.DEFAULT;

    public OculusTableTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.OCULUS_TABLE, pos, state);
    }

    public void setView(OculusView view)
    {
        if (this.view.equals(view))
            return;
        this.view = view;
        setChanged();
        sync();
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        view = nbt.read("view", OculusView.CODEC).orElse(OculusView.DEFAULT);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("view", OculusView.CODEC, view);
    }
}
