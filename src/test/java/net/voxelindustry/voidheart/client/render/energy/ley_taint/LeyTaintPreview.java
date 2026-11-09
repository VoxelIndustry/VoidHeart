package net.voxelindustry.voidheart.client.render.energy.ley_taint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws the taint veins around one node from above at each stage, on flat grass, to review them without starting a game.
 * Run with {@code DUNGEON_PREVIEW=true}; images go to build/ley-preview. Upstream lines run west and north, downstream east and south.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class LeyTaintPreview
{
    private static final int PIXELS_PER_BLOCK = 8;
    private static final int RADIUS = 36;
    private static final int PANEL = RADIUS * 2 * PIXELS_PER_BLOCK;
    private static final float[] TAINTS = { 25, 45, 60, 75, 90, 100 };
    private static final Color GROUND = new Color(0x5F7F3A);

    @Test
    void drawStages() throws IOException
    {
        var folder = new File("build/ley-preview");
        folder.mkdirs();
        var image = new BufferedImage(PANEL * TAINTS.length, PANEL + 24, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        var pieces = LeyTaintVeins.generate(LeyTaintVeinsTest.NODE, LeyTaintVeinsTest.X, LeyTaintVeinsTest.Z, LeyTaintVeinsTest.SPOKES);
        for (int panel = 0; panel < TAINTS.length; panel++)
        {
            float taint = TAINTS[panel];
            var shape = LeyTaintLook.shape(taint);
            int left = panel * PANEL;
            double originX = LeyTaintVeinsTest.X + 0.5 - RADIUS, originZ = LeyTaintVeinsTest.Z + 0.5 - RADIUS;

            g.setClip(left, 0, PANEL, PANEL);
            g.setColor(GROUND);
            g.fillRect(left, 0, PANEL, PANEL);
            drawDark(g, left, shape);
            drawLines(g, left);

            float fade = Math.max(shape.reach() * 0.25F, 0.5F);
            for (var piece : pieces)
            {
                if (piece.distance() > shape.reach())
                    continue;
                float width = shape.width() * piece.width() * shape.levelWeight(piece.level()) * Math.min(1, (float) (shape.reach() - piece.distance()) / fade);
                if (width < 0.008F)
                    continue;
                var line = new Line2D.Double(left + (piece.x0() - originX) * PIXELS_PER_BLOCK, (piece.z0() - originZ) * PIXELS_PER_BLOCK,
                        left + (piece.x1() - originX) * PIXELS_PER_BLOCK, (piece.z1() - originZ) * PIXELS_PER_BLOCK);
                g.setStroke(new BasicStroke(Math.max(1, width * PIXELS_PER_BLOCK), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(LeyTaintLook.LEY_MAUVE));
                g.draw(line);
                g.setStroke(new BasicStroke(Math.max(1, width * 0.5F * PIXELS_PER_BLOCK), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(LeyTaintLook.BRUISE));
                g.draw(line);
            }

            g.setClip(null);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.drawString(String.format("taint %.0f, reach %.1f", taint, shape.reach()), left + 8, PANEL + 17);
        }
        g.dispose();
        ImageIO.write(image, "png", new File(folder, "ley_taint.png"));
    }

    private static void drawLines(java.awt.Graphics2D g, int left)
    {
        g.setStroke(new BasicStroke(1));
        g.setColor(new Color(0x36C5B0));
        int center = RADIUS * PIXELS_PER_BLOCK;
        for (var spoke : LeyTaintVeinsTest.SPOKES)
        {
            double dx = spoke.x() - LeyTaintVeinsTest.X, dz = spoke.z() - LeyTaintVeinsTest.Z;
            double length = Math.hypot(dx, dz);
            g.draw(new Line2D.Double(left + center, center, left + center + dx / length * PANEL, center + dz / length * PANEL));
        }
    }

    private static void drawDark(java.awt.Graphics2D g, int left, LeyTaintLook.Shape shape)
    {
        if (shape.dark() <= 0)
            return;
        int center = RADIUS * PIXELS_PER_BLOCK;
        g.setColor(new Color(LeyTaintLook.SEEP_DARK >> 16 & 255, LeyTaintLook.SEEP_DARK >> 8 & 255, LeyTaintLook.SEEP_DARK & 255, (int) (shape.dark() * 255)));
        double disc = 3.5 * PIXELS_PER_BLOCK;
        g.fill(new Ellipse2D.Double(left + center - disc, center - disc, disc * 2, disc * 2));
        g.setStroke(new BasicStroke(5 * PIXELS_PER_BLOCK * 0.6F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (var spoke : LeyTaintVeinsTest.SPOKES)
        {
            double dx = spoke.x() - LeyTaintVeinsTest.X, dz = spoke.z() - LeyTaintVeinsTest.Z;
            double length = Math.hypot(dx, dz);
            double reach = shape.reach() * PIXELS_PER_BLOCK * 0.7;
            g.draw(new Line2D.Double(left + center, center, left + center + dx / length * reach, center + dz / length * reach));
        }
    }
}
