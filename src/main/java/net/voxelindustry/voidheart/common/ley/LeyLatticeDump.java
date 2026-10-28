package net.voxelindustry.voidheart.common.ley;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.HashSet;
import java.util.function.DoubleBinaryOperator;

/**
 * Top-down debug render of a lattice area: pressure as background, lines with flow arrows, nodes sized by power.
 */
public final class LeyLatticeDump
{
    private static final Color[] KIND_COLORS = {
            new Color(0xB8A9D9),
            new Color(0x6FCF97),
            new Color(0xF2C94C),
            new Color(0xEB5757)
    };

    private LeyLatticeDump()
    {
    }

    public static BufferedImage render(Collection<LeyRegion> regions, DoubleBinaryOperator pressure, int minX, int minZ, int sizeBlocks, int sizePixels)
    {
        var image = new BufferedImage(sizePixels, sizePixels, BufferedImage.TYPE_INT_RGB);
        double scale = sizePixels / (double) sizeBlocks;

        for (int px = 0; px < sizePixels; px += 2)
        {
            for (int pz = 0; pz < sizePixels; pz += 2)
            {
                float p = (float) pressure.applyAsDouble(minX + px / scale, minZ + pz / scale);
                int rgb = new Color(0.08F + 0.12F * p, 0.06F + 0.05F * p, 0.12F + 0.18F * p).getRGB();
                image.setRGB(px, pz, rgb);
                if (px + 1 < sizePixels)
                    image.setRGB(px + 1, pz, rgb);
                if (pz + 1 < sizePixels)
                    image.setRGB(px, pz + 1, rgb);
                if (px + 1 < sizePixels && pz + 1 < sizePixels)
                    image.setRGB(px + 1, pz + 1, rgb);
            }
        }

        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        var drawn = new HashSet<String>();
        graphics.setStroke(new BasicStroke(1.5F));
        for (var region : regions)
        {
            for (var edge : region.edges())
            {
                if (drawn.add(edge.from() + ":" + edge.to()))
                    drawEdge(graphics, edge, minX, minZ, scale);
            }
        }

        for (var region : regions)
        {
            for (var node : region.nodes())
            {
                double radius = 2 + Math.sqrt(node.power()) * 1.6;
                double x = (node.x() - minX) * scale;
                double z = (node.z() - minZ) * scale;
                graphics.setColor(KIND_COLORS[node.kind().ordinal()]);
                graphics.fill(new java.awt.geom.Ellipse2D.Double(x - radius, z - radius, radius * 2, radius * 2));
                graphics.setColor(Color.BLACK);
                graphics.drawString(Integer.toString(node.lines()), (float) (x - 3), (float) (z + 4));
            }
        }

        graphics.dispose();
        return image;
    }

    private static void drawEdge(Graphics2D graphics, LeyEdge edge, int minX, int minZ, double scale)
    {
        double x1 = (edge.fromX() - minX) * scale;
        double z1 = (edge.fromZ() - minZ) * scale;
        double x2 = (edge.toX() - minX) * scale;
        double z2 = (edge.toZ() - minZ) * scale;

        graphics.setColor(new Color(0x9B7BFF));
        graphics.draw(new java.awt.geom.Line2D.Double(x1, z1, x2, z2));

        double angle = Math.atan2(z2 - z1, x2 - x1);
        double midX = (x1 + x2) / 2;
        double midZ = (z1 + z2) / 2;
        double size = 6;

        var arrow = new Path2D.Double();
        arrow.moveTo(midX + Math.cos(angle) * size, midZ + Math.sin(angle) * size);
        arrow.lineTo(midX + Math.cos(angle + 2.5) * size, midZ + Math.sin(angle + 2.5) * size);
        arrow.lineTo(midX + Math.cos(angle - 2.5) * size, midZ + Math.sin(angle - 2.5) * size);
        arrow.closePath();
        graphics.setColor(new Color(0xD9CCFF));
        graphics.fill(arrow);
    }
}
