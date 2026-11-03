package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.Map;

/**
 * Where the eye and the islands around it fall on screen. A research's position is authored from -1 to 1 on each axis, with the eye at 0.
 */
public final class ResearchLayout
{
    public record Projection(double centerX, double centerY, double radiusX, double radiusY)
    {
        public double x(double at)
        {
            return centerX + at * radiusX;
        }

        public double y(double at)
        {
            return centerY + at * radiusY;
        }
    }

    /** Where a branch leaving the eye starts, in the same units. */
    public static final double EYE_REACH = 0.3;

    private static final double RADIUS_Y_MAX = 300;
    private static final double WIDEST       = 2;

    private ResearchLayout()
    {
    }

    public static Projection projection(int left, int right, int height)
    {
        double radiusY = Math.min((height - ResearchMapView.BOTTOM_BAND) / 2D - ResearchMapView.NODE_SIZE / 2D - 4, RADIUS_Y_MAX);
        double radiusX = Math.min((right - left) / 2D - ResearchMapView.NODE_SIZE / 2D - 8, radiusY * WIDEST);
        return new Projection((left + right) / 2D, (height - ResearchMapView.BOTTOM_BAND) / 2D, radiusX, radiusY);
    }

    public static Identifier parentOf(Research research, Map<Identifier, Research> tree)
    {
        return research.requires().stream().filter(tree::containsKey).findFirst().orElse(null);
    }
}
