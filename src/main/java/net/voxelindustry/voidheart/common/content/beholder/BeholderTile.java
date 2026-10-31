package net.voxelindustry.voidheart.common.content.beholder;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;
import java.util.UUID;

public class BeholderTile extends TileBase
{
    @Getter
    private Optional<UUID> owner = Optional.empty();

    /**
     * Where the floating eye looks, eased toward what it watches on the client.
     */
    public float eyeYaw;
    public float eyePitch;
    public long lastEyeNanos;

    public BeholderTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.BEHOLDER, pos, state);
    }

    public void setOwner(Optional<UUID> owner)
    {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput nbt)
    {
        super.loadAdditional(nbt);

        owner = nbt.read("owner", UUIDUtil.CODEC);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        owner.ifPresent(id -> nbt.store("owner", UUIDUtil.CODEC, id));
    }
}
