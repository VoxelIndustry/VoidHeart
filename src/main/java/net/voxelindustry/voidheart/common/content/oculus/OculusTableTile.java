package net.voxelindustry.voidheart.common.content.oculus;

import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.config.VoidHeartConfig;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

import java.util.Optional;
import java.util.UUID;

/**
 * Every block of a table keeps a copy of the shared view, so the table survives losing any of its blocks.
 */
public class OculusTableTile extends TileBase
{
    private static final int FOLLOW_CHECK_INTERVAL = 20;

    @Getter
    private OculusView view = OculusView.DEFAULT;
    /**
     * Who placed this block, whose beholders the table looks through. Blocks placed by no one share the beholders
     * placed by no one.
     */
    @Getter
    private Optional<UUID> owner = Optional.empty();

    public OculusTableTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.OCULUS_TABLE, pos, state);
    }

    /**
     * Once a second, the table's anchor block stops following a player who left range, the dimension or the game.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, OculusTableTile tile)
    {
        if (tile.view.follow().isEmpty() || (level.getGameTime() + pos.asLong()) % FOLLOW_CHECK_INTERVAL != 0 || !(level instanceof ServerLevel serverLevel))
            return;

        var footprint = OculusTableStructure.footprint(level, pos);
        if (footprint.isPresent() && !footprint.get().anchor().equals(pos))
            return;

        var target = serverLevel.getServer().getPlayerList().getPlayer(tile.view.follow().get());
        boolean inRange = footprint.isPresent() && target != null && target.level() == level && !target.isSpectator()
                && OculusControlPayload.horizontalDistanceSq(target, footprint.get()) <= square(VoidHeartConfig.get().oculusTableFollowRange);
        if (!inRange)
            OculusTableStructure.updateView(level, pos, view -> view.following(Optional.empty()));
    }

    private static double square(double value)
    {
        return value * value;
    }

    public void setView(OculusView view)
    {
        if (this.view.equals(view))
            return;
        this.view = view;
        setChanged();
        sync();
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

        view = nbt.read("view", OculusView.CODEC).orElse(OculusView.DEFAULT);
        owner = nbt.read("owner", UUIDUtil.CODEC);
    }

    @Override
    protected void saveAdditional(ValueOutput nbt)
    {
        super.saveAdditional(nbt);

        nbt.store("view", OculusView.CODEC, view);
        owner.ifPresent(id -> nbt.store("owner", UUIDUtil.CODEC, id));
    }
}
