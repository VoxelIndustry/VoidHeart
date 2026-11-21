package net.voxelindustry.voidheart.common.energy.storage;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The socket wall's four niches, shared by its model, its hit test and its renderer. Pixels are given for a wall facing
 * north, its front at z 0; the blockstate turns the model and {@link #slotAt} and {@link #cellCenter} turn the rest.
 * Slots run 0 top left, 1 top right, 2 bottom left, 3 bottom right, as seen from the front.
 */
public final class SocketWallGeometry
{
    public static final int SLOTS = 4;

    /** How deep each niche is cut into the front, in pixels. */
    public static final int NICHE_DEPTH = 4;
    /** How far the cell stands behind the front, in pixels: halfway into its niche. */
    public static final float CELL_DEPTH = 2;

    /** A niche's opening, in pixels on the north-facing model. */
    public record Niche(int x0, int y0, int x1, int y1)
    {
        /** The cell stands on the gold ledge along the niche's floor, in the room left above it. */
        public float cellMiddleY()
        {
            return (y0 + 1 + y1) / 2F;
        }

        public float middleX()
        {
            return (x0 + x1) / 2F;
        }
    }

    /**
     * Someone facing a north-facing wall looks south, with east on their left, so top left is at high x.
     */
    public static final List<Niche> NICHES = List.of(
            new Niche(9, 9, 15, 15),
            new Niche(1, 9, 7, 15),
            new Niche(9, 1, 15, 7),
            new Niche(1, 1, 7, 7));

    private SocketWallGeometry()
    {
    }

    /**
     * Someone facing the wall looks opposite to it, so their left is the wall's facing turned clockwise. It is also
     * where the model's +x turns to.
     */
    public static Direction viewersLeft(Direction facing)
    {
        return facing.getClockWise();
    }

    /**
     * @param facing where the front looks
     * @param local  the hit point relative to the block's lower north-west corner
     * @return the slot whose half of the front was hit
     */
    public static int slotAt(Direction facing, Vec3 local)
    {
        var left = viewersLeft(facing);
        var across = (local.x - 0.5) * left.getStepX() + (local.z - 0.5) * left.getStepZ();
        var column = across > 0 ? 0 : 1;
        var row = local.y >= 0.5 ? 0 : 1;
        return row * 2 + column;
    }

    /**
     * Where a slot's cell stands, relative to the block's lower north-west corner.
     */
    public static Vec3 cellCenter(Direction facing, int slot)
    {
        var niche = NICHES.get(slot);
        var left = viewersLeft(facing);
        // On the north-facing model, the viewer's left is toward high x
        var across = (niche.middleX() - 8) / 16;
        var out = 0.5 - CELL_DEPTH / 16;
        return new Vec3(
                0.5 + left.getStepX() * across + facing.getStepX() * out,
                niche.cellMiddleY() / 16,
                0.5 + left.getStepZ() * across + facing.getStepZ() * out);
    }
}
