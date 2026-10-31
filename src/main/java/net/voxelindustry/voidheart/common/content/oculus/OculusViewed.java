package net.voxelindustry.voidheart.common.content.oculus;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A table as seen by a player asking about it: only players standing near a table may ask, and only about the land
 * it can show.
 */
record OculusViewed(OculusTableTile tile, OculusFootprint footprint, List<Vec3> centers, double reach)
{
    static final double VIEWER_DISTANCE = 48;
    /**
     * World blocks allowed past the land shown, covering a view still gliding or built ahead of a followed player.
     */
    private static final double AREA_MARGIN = 64;

    static @Nullable OculusViewed of(ServerPlayer player, BlockPos table)
    {
        var level = player.level();
        if (player.position().distanceToSqr(Vec3.atCenterOf(table)) > VIEWER_DISTANCE * VIEWER_DISTANCE)
            return null;

        var tile = OculusTableStructure.getTile(level, table);
        var footprint = OculusTableStructure.footprint(level, table);
        if (tile == null || footprint.isEmpty())
            return null;

        var centers = new ArrayList<Vec3>();
        centers.add(new Vec3(footprint.get().centerX(), 0, footprint.get().centerZ()));
        tile.getView().follow()
                .map(id -> level.getServer().getPlayerList().getPlayer(id))
                .filter(target -> target.level() == level)
                .ifPresent(target -> centers.add(target.position()));

        double reach = Math.max(footprint.get().width(), footprint.get().depth()) * tile.getView().scale() * Math.sqrt(2) / 2 + AREA_MARGIN;
        return new OculusViewed(tile, footprint.get(), centers, reach);
    }

    boolean shows(long chunk)
    {
        double chunkX = SectionPos.sectionToBlockCoord(ChunkPos.getX(chunk), 8);
        double chunkZ = SectionPos.sectionToBlockCoord(ChunkPos.getZ(chunk), 8);
        return centers.stream().anyMatch(center ->
                (chunkX - center.x) * (chunkX - center.x) + (chunkZ - center.z) * (chunkZ - center.z) <= reach * reach);
    }
}
