package net.voxelindustry.voidheart.common.content.portalframe;

import com.qouteall.immersive_portals.portal.Portal;
import net.minecraft.client.util.math.Vector3f;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.voxelindustry.steamlayer.math.Vec3f;
import org.apache.commons.lang3.tuple.Pair;

public class ImmersivePortalFrameCreator
{
    public static void linkImmersivePortal(PortalFrameTile portalFrameTile, Direction facing)
    {
        PortalFrameTile linkedPortal = portalFrameTile.getLinkedPortal();
        Pair<BlockPos, BlockPos> linkedPortalPoints = linkedPortal.getPortalPoints();
        Vec3d linkedCenter = new Vec3d(
                (linkedPortalPoints.getRight().getX() - linkedPortalPoints.getLeft().getX()) / 2F,
                (linkedPortalPoints.getRight().getY() - linkedPortalPoints.getLeft().getY()) / 2F,
                (linkedPortalPoints.getRight().getZ() - linkedPortalPoints.getLeft().getZ()) / 2F
        ).add(linkedPortalPoints.getLeft().getX() + 0.5, linkedPortalPoints.getLeft().getY() + 0.5, linkedPortalPoints.getLeft().getZ() + 0.5);

        Portal portal = Portal.entityType.create(portalFrameTile.getWorld());
        portal.dimensionTo = portalFrameTile.getLinkedWorldKey();
        portal.width = portalFrameTile.getWidth() - 1;
        portal.height = portalFrameTile.getHeight() - 1;
        portal.destination = linkedCenter;

        if (portalFrameTile.getFacing().getAxis().isHorizontal())
        {
            portal.axisH = new Vec3d(getUnitVector(Direction.UP));
            portal.axisW = new Vec3d(getUnitVector(facing.rotateYCounterclockwise()));

            if (portalFrameTile.getLinkedFacing() == facing)
                portal.rotation = Vec3f.UP.getDegreesQuaternion(180);
            else if (portalFrameTile.getLinkedFacing() == facing.getOpposite())
            {
                // DO NOTHING
            }
            else if (portalFrameTile.getLinkedFacing() == facing.rotateYClockwise())
            {
                portal.rotation = Vec3f.UP.getDegreesQuaternion(90);
            }
            else if (portalFrameTile.getLinkedFacing() == facing.rotateYCounterclockwise())
            {
                portal.rotation = Vec3f.UP.getDegreesQuaternion(-90);
            }
        }
        else
        {
            if (facing == Direction.DOWN)
                portal.axisH = new Vec3d(getUnitVector(Direction.SOUTH));
            else
                portal.axisH = new Vec3d(getUnitVector(Direction.NORTH));
            portal.axisW = new Vec3d(getUnitVector(Direction.EAST));

            if (portalFrameTile.getLinkedFacing() == facing)
                portal.rotation = Vec3f.EAST.getDegreesQuaternion(180);
        }

        Vec3d center = new Vec3d(
                (portalFrameTile.getPortalPoints().getRight().getX() - portalFrameTile.getPortalPoints().getLeft().getX()) / 2F,
                (portalFrameTile.getPortalPoints().getRight().getY() - portalFrameTile.getPortalPoints().getLeft().getY()) / 2F,
                (portalFrameTile.getPortalPoints().getRight().getZ() - portalFrameTile.getPortalPoints().getLeft().getZ()) / 2F
        ).add(portalFrameTile.getPortalPoints().getLeft().getX() + 0.5, portalFrameTile.getPortalPoints().getLeft().getY() + 0.5, portalFrameTile.getPortalPoints().getLeft().getZ() + 0.5);
        portal.updatePosition(center.x, center.y, center.z);
        portalFrameTile.getWorld().spawnEntity(portal);

        portalFrameTile.setPortalEntityID(portal.getUuid());
    }

    public static Vector3f getUnitVector(Direction direction)
    {
        return new Vector3f(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }
}
