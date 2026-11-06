package net.voxelindustry.voidheart.common.content.athanor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.voxelindustry.steamlayer.tile.TileBase;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;
import net.voxelindustry.voidheart.common.content.essence.IEssenceReceiver;
import net.voxelindustry.voidheart.common.setup.VoidHeartTiles;

/**
 * The part of an athanor relays link to. It only ever takes what the brew of the core above still lacks, and gives nothing back, so no Shatter Forge
 * mistakes it for a storage.
 */
public class AspectIntakeTile extends TileBase implements IEssenceReceiver
{
    public AspectIntakeTile(BlockPos pos, BlockState state)
    {
        super(VoidHeartTiles.ASPECT_INTAKE, pos, state);
    }

    private AthanorTile core()
    {
        return level != null && level.getBlockEntity(worldPosition.above()) instanceof AthanorTile athanor ? athanor : null;
    }

    @Override
    public int spaceFor(EssenceType type)
    {
        var core = core();
        return core == null ? 0 : core.missingAspect(type);
    }

    @Override
    public int receive(EssenceType type, int amount)
    {
        var core = core();
        return core == null ? 0 : core.receiveAspect(type, amount);
    }

    @Override
    public int amountOf(EssenceType type)
    {
        return 0;
    }

    @Override
    public int extract(EssenceType type, int amount)
    {
        return 0;
    }
}
