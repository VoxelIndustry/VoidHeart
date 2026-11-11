package net.voxelindustry.voidheart.common.content.kleinstar;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.voxelindustry.voidheart.common.setup.VoidHeartEffects;

import java.util.ArrayList;
import java.util.List;

/**
 * What a Klein Star leaves on the blocks around its burst. Every face of a full block that the burst shone on stays lit for a while, and whatever living
 * comes near one is left Radiant again. Nothing is placed in the world and none of it is saved: the server only remembers where bursts happened, and each
 * client works out the same faces to draw from {@link #lit}.
 */
public final class RadiantCover
{
    // The ray to a face ends this far off it, so the face's own block does not stand in its way
    private static final double FACE_LIFT = 0.05;

    private record Cover(ResourceKey<Level> dimension, Vec3 center, double radius, long until)
    {
    }

    private static final List<Cover> COVERS = new ArrayList<>();

    private RadiantCover()
    {
    }

    public static void add(ServerLevel level, Vec3 center, double radius)
    {
        COVERS.add(new Cover(level.dimension(), center, radius, level.getServer().getTickCount() + KleinStarBalance.COVER_TICKS));
    }

    public static void clear()
    {
        COVERS.clear();
    }

    public static void tickServer(MinecraftServer server)
    {
        if (COVERS.isEmpty())
            return;
        long now = server.getTickCount();
        COVERS.removeIf(cover -> cover.until() <= now);
        if (now % KleinStarBalance.COVER_INTERVAL != 0)
            return;

        for (var cover : COVERS)
        {
            var level = server.getLevel(cover.dimension());
            if (level == null)
                continue;
            double reach = cover.radius() + KleinStarBalance.COVER_REACH;
            for (var living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(cover.center(), reach * 2, reach * 2, reach * 2), LivingEntity::isAlive))
            {
                if (!living.isSpectator() && near(level, living.getBoundingBox(), cover.center(), cover.radius()))
                    living.addEffect(new MobEffectInstance(VoidHeartEffects.RADIANT, KleinStarBalance.RADIANT_TICKS));
            }
        }
    }

    /**
     * @return whether a body is close enough to a lit face to burn
     */
    public static boolean near(BlockGetter level, AABB body, Vec3 center, double radius)
    {
        var reach = body.inflate(KleinStarBalance.COVER_REACH);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(reach.minX, reach.minY, reach.minZ), BlockPos.containing(reach.maxX, reach.maxY, reach.maxZ)))
        {
            if (!level.getBlockState(pos).isSolidRender())
                continue;
            for (var face : Direction.values())
                if (lit(level, pos, face, center, radius))
                    return true;
        }
        return false;
    }

    /**
     * @return whether a burst at the center lit this face: an open face of a full block within the blast, turned toward the burst with nothing between
     */
    public static boolean lit(BlockGetter level, BlockPos pos, Direction face, Vec3 center, double radius)
    {
        if (!level.getBlockState(pos).isSolidRender() || level.getBlockState(pos.relative(face)).isSolidRender())
            return false;

        var normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        var faceCenter = Vec3.atCenterOf(pos).add(normal.scale(0.5));
        var toBurst = center.subtract(faceCenter);
        if (toBurst.lengthSqr() > radius * radius || toBurst.dot(normal) <= 0)
            return false;

        var hit = level.clip(new ClipContext(center, faceCenter.add(normal.scale(FACE_LIFT)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
