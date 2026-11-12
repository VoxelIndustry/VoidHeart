package net.voxelindustry.voidheart.common.world.underway;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;

import java.util.concurrent.ExecutionException;

/** Plans the fort under each district once, and draws the Underway under every district a canvas touches. Thread safe. */
public final class UnderwayPlans
{
    private static final int CACHE_SIZE = 256;

    private record Key(long seed, DistrictPos pos)
    {
    }

    private static final Cache<Key, UnderwayFort> FORTS = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE).build();

    private UnderwayPlans()
    {
    }

    public static UnderwayFort fort(long seed, DistrictPos pos)
    {
        try
        {
            return FORTS.get(new Key(seed, pos), () -> switch (UnderwayNetwork.nodeAt(seed, pos))
            {
                case HUB -> HubPlanner.plan(seed, pos);
                case STATION -> StationPlanner.plan(seed, pos);
                case POST -> PostPlanner.plan(seed, pos);
            });
        } catch (ExecutionException e)
        {
            throw new IllegalStateException("Could not plan the Underway under " + pos, e);
        }
    }

    public static void paint(long seed, UnderwayCanvas canvas)
    {
        int firstX = Math.floorDiv(canvas.minX(), DistrictPos.PITCH);
        int lastX = Math.floorDiv(canvas.maxX(), DistrictPos.PITCH);
        int firstZ = Math.floorDiv(canvas.minZ(), DistrictPos.PITCH);
        int lastZ = Math.floorDiv(canvas.maxZ(), DistrictPos.PITCH);
        for (int z = firstZ; z <= lastZ; z++)
        {
            for (int x = firstX; x <= lastX; x++)
                paintDistrict(seed, new DistrictPos(x, z), canvas);
        }
    }

    private static void paintDistrict(long seed, DistrictPos pos, UnderwayCanvas canvas)
    {
        boolean row = UnderwayNetwork.isLineRow(seed, pos.z());
        boolean column = UnderwayNetwork.isLineColumn(seed, pos.x());
        if (row)
        {
            TrunkPainter.through(seed, pos, true, canvas);
            TrunkPainter.link(seed, pos, true, canvas);
        }
        if (column)
        {
            TrunkPainter.through(seed, pos, false, canvas);
            TrunkPainter.link(seed, pos, false, canvas);
        }
        GalleryPainter.paint(seed, pos, true, canvas);
        GalleryPainter.paint(seed, pos, false, canvas);
        FortPainter.paint(fort(seed, pos), canvas);
        if (row)
            TrainPainter.paint(seed, pos, true, canvas);
        if (column)
            TrainPainter.paint(seed, pos, false, canvas);
        if (row)
            TrunkFurnisher.paint(seed, pos, true, canvas);
        if (column)
            TrunkFurnisher.paint(seed, pos, false, canvas);
        if ((row || column) && UnderwayNetwork.regionRuin(seed, pos) == UnderwayRuin.FLOODED)
            TrunkPainter.flood(new UnderwayBrush(canvas, UnderwayFrame.ofLine(pos, true)));
    }
}
