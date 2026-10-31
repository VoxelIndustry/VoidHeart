package net.voxelindustry.voidheart.client.render.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * Reads the land into hologram voxels: solid blocks and fluids keep a color, anything else is empty.
 */
final class OculusVoxels
{
    private OculusVoxels()
    {
    }

    @SuppressWarnings("deprecation")
    static int color(BlockState state, BlockGetter level, BlockPos pos)
    {
        if (state.isAir() || !state.isSolid() && state.getFluidState().isEmpty())
            return 0;

        var mapColor = state.getMapColor(level, pos);
        return mapColor == MapColor.NONE ? 0 : 0xFF000000 | mapColor.col;
    }

    /**
     * Copies the voxels of a chunk column between the window bottom and top, with a one block border.
     */
    static OculusMesher.Volume snapshot(BlockGetter level, int chunkX, int chunkZ, int windowBottom, int windowHeight)
    {
        var volume = OculusMesher.Volume.empty(windowHeight);
        var pos = new BlockPos.MutableBlockPos();
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;

        for (int y = 0; y < windowHeight; y++)
        {
            for (int z = -1; z <= OculusMesher.CHUNK; z++)
            {
                for (int x = -1; x <= OculusMesher.CHUNK; x++)
                {
                    pos.set(originX + x, windowBottom + y, originZ + z);
                    volume.set(x, y, z, color(level.getBlockState(pos), level, pos));
                }
            }
        }
        return volume;
    }
}
