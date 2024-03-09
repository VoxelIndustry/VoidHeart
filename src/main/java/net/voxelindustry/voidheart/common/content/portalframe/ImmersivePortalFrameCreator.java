package net.voxelindustry.voidheart.common.content.portalframe;

import lombok.extern.log4j.Log4j2;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.DQuaternion;

@Log4j2
public class ImmersivePortalFrameCreator
{
    public static void linkImmersivePortal(PortalFrameCoreTile portalFrameTile, Direction facing)
    {
        var linkedPortalOpt = portalFrameTile.getLinkedPortal();

        if (linkedPortalOpt.isEmpty())
        {
            log.error("Unable to make ImmersivePortal for linking. Linked BlockEntity does not conform to PortalFrameCore or is null. world={}, pos={}", portalFrameTile.getWorld().getRegistryKey(), portalFrameTile.getLinkedPos());
            return;
        }

        var linkedPortal = linkedPortalOpt.get();
        PortalFormerState linkedPortalPoints = linkedPortal.getPortalState();
        Vec3d linkedCenter = new Vec3d(
                (linkedPortalPoints.getTo().getX() - linkedPortalPoints.getFrom().getX()) / 2F,
                (linkedPortalPoints.getTo().getY() - linkedPortalPoints.getFrom().getY()) / 2F,
                (linkedPortalPoints.getTo().getZ() - linkedPortalPoints.getFrom().getZ()) / 2F
        ).add(linkedPortalPoints.getFrom().getX() + 0.5, linkedPortalPoints.getFrom().getY() + 0.5, linkedPortalPoints.getFrom().getZ() + 0.5);

        Portal portal = Portal.entityType.create(portalFrameTile.getWorld());
        portal.dimensionTo = portalFrameTile.getLinkedWorldKey();
        portal.width = portalFrameTile.getPortalState().getWidth() - 1;
        portal.height = portalFrameTile.getPortalState().getHeight() - 1;
        portal.destination = linkedCenter;

        if (portalFrameTile.getFacing().getAxis().isHorizontal())
        {
            portal.axisH = new Vec3d(getUnitVector(Direction.UP));
            portal.axisW = new Vec3d(getUnitVector(facing.rotateYCounterclockwise()));

            if (portalFrameTile.getLinkedFacing() == facing)
                portal.setOrientationRotation(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_Y.rotationDegrees(180)));

            else if (portalFrameTile.getLinkedFacing() == facing.getOpposite())
            {
                portal.setOrientationRotation(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_Y.rotationDegrees(0)));
            }
            else if (portalFrameTile.getLinkedFacing() == facing.rotateYClockwise())
            {
                portal.setOrientationRotation(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_Y.rotationDegrees(90)));
            }
            else if (portalFrameTile.getLinkedFacing() == facing.rotateYCounterclockwise())
            {
                portal.setOrientationRotation(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_Y.rotationDegrees(-90)));
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
                portal.setOrientationRotation(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_X.rotationDegrees(180)));
            else
                portal.setOrientationRotation(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_X.rotationDegrees(0)));

            var linkedState = linkedPortal.getPortalState();
            // Special case for a rotated horizontal portal. Like a 3x2 portal connected to a 2x3
            if (portalFrameTile.getPortalState().getWidth() != linkedState.getWidth())
                portal.setOrientationRotation(portal.getOrientationRotation().combine(DQuaternion.fromMcQuaternion(RotationAxis.POSITIVE_Y.rotationDegrees(90))));
        }

        Vec3d center = new Vec3d(
                (portalFrameTile.getPortalState().getTo().getX() - portalFrameTile.getPortalState().getFrom().getX()) / 2F,
                (portalFrameTile.getPortalState().getTo().getY() - portalFrameTile.getPortalState().getFrom().getY()) / 2F,
                (portalFrameTile.getPortalState().getTo().getZ() - portalFrameTile.getPortalState().getFrom().getZ()) / 2F
        ).add(portalFrameTile.getPortalState().getFrom().getX() + 0.5, portalFrameTile.getPortalState().getFrom().getY() + 0.5, portalFrameTile.getPortalState().getFrom().getZ() + 0.5);
        portal.updatePosition(center.x, center.y, center.z);
        portalFrameTile.getWorld().spawnEntity(portal);

        portalFrameTile.setPortalEntityID(portal.getUuid());
    }

    public static Vector3f getUnitVector(Direction direction)
    {
        return new Vector3f(direction.getOffsetX(), direction.getOffsetY(), direction.getOffsetZ());
    }
}
