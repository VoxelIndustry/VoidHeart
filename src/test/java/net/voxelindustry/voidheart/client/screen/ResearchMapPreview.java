package net.voxelindustry.voidheart.client.screen;

import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Research;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Draws the research map as the plan lays it out, to review it without starting a game. Run with {@code DUNGEON_PREVIEW=true}, images go to
 * build/research-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class ResearchMapPreview
{
    private static final int SCALE = 2;

    private static final Map<Constellation, Color> COLORS = Map.of(Constellation.RITES, new Color(0xE08A5E), Constellation.ARTIFICES, new Color(0xD880C8),
            Constellation.ARCHIVE, new Color(0xCDB36E), Constellation.TRAVEL, new Color(0x7FB2EA), Constellation.DEITIES, new Color(0xD8586A));

    @Test
    void drawMaps() throws IOException
    {
        var folder = new File("build/research-preview");
        folder.mkdirs();

        var shipped = ResearchTrees.shipped();
        var grown = new TreeMap<>(shipped);
        grown.put(ResearchTrees.id("void_lens"), ResearchTrees.research(Constellation.ARTIFICES, 3, "far_sight"));

        draw(shipped, new File(folder, "shipped.png"));
        draw(grown, new File(folder, "grown.png"));
    }

    /** The whole map at its one size, with what each screen size shows of it at rest outlined. */
    private static void draw(Map<Identifier, Research> tree, File file) throws IOException
    {
        var frame = ResearchTrees.frame();
        long start = System.nanoTime();
        var plan = ResearchMapPlan.plan(tree, frame);
        System.out.printf("%s: %.1f ms%n", file.getName(), (System.nanoTime() - start) / 1e6);

        var bounds = plan.bounds();
        int margin = 30;
        int width = bounds.width() + margin * 2;
        int height = bounds.height() + margin * 2;
        var image = new BufferedImage(width * SCALE, height * SCALE, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(SCALE, SCALE);
        g.setColor(new Color(0x0D0A14));
        g.fillRect(0, 0, width, height);
        g.translate(margin - bounds.minX(), margin - bounds.minY());
        g.setColor(new Color(0x2A2430));
        g.drawRect(bounds.minX(), bounds.minY(), bounds.width(), bounds.height());

        var views = new Color[] { new Color(0xFF5050), new Color(0xFFA040), new Color(0xE0E040), new Color(0x50D070), new Color(0x50A0FF), new Color(0xB070FF) };
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 7));
        for (int i = 0; i < ResearchTrees.SIZES.length; i++)
        {
            int[] size = ResearchTrees.SIZES[i];
            int[] view = ResearchTrees.view(size[0], size[1]);
            int[] across = ResearchMapPlan.eyeRange(bounds.minX(), bounds.maxX(), view[0], view[2], 10);
            int[] down = ResearchMapPlan.eyeRange(bounds.minY(), bounds.maxY(), view[1], view[3], 10);
            int eyeX = Math.clamp((view[0] + view[2]) / 2, across[0], across[1]);
            int eyeY = Math.clamp((view[1] + view[3]) / 2, down[0], down[1]);
            g.setColor(views[i]);
            g.setStroke(new BasicStroke(0.75F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] { 4, 2 }, 0));
            g.drawRect(view[0] - eyeX, view[1] - eyeY, view[2] - view[0], view[3] - view[1]);
            g.drawString(size[0] + "x" + size[1], view[0] - eyeX + 2, view[3] - eyeY - 2);
        }
        g.setStroke(new BasicStroke(1));

        g.setColor(new Color(0x3A2F4A));
        g.fill(new Ellipse2D.Double(-frame.eyeHalfWidth(), -frame.eyeHalfHeight(), frame.eyeHalfWidth() * 2, frame.eyeHalfHeight() * 2));
        double scleraW = frame.eyeHalfWidth() - 11;
        double scleraH = frame.eyeHalfHeight() - 11;
        g.setColor(new Color(0x2E7F78));
        g.fill(new Ellipse2D.Double(-scleraW, -scleraH, scleraW * 2, scleraH * 2));

        var nodes = plan.nodes();
        for (var link : plan.links())
        {
            boolean bridge = link.parent() != null && tree.get(link.parent()).constellation() != tree.get(link.child()).constellation();
            g.setColor(bridge ? new Color(0x8A7FA0) : new Color(0xC8C0D8));
            g.setStroke(bridge ? new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] { 3, 3 }, 0) : new BasicStroke(1));
            for (int i = 0; i < link.xs().length - 1; i++)
                g.draw(new java.awt.geom.Line2D.Float(link.xs()[i], link.ys()[i], link.xs()[i + 1], link.ys()[i + 1]));
        }
        g.setStroke(new BasicStroke(1));

        int half = frame.nodeSize() / 2;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 5));
        tree.forEach((id, research) ->
        {
            var at = nodes.get(id);
            var color = COLORS.get(research.constellation());
            g.setColor(new Color(0x241C30));
            g.fillOval(at.x() - half, at.y() - half, frame.nodeSize(), frame.nodeSize());
            g.setColor(color);
            g.drawOval(at.x() - half, at.y() - half, frame.nodeSize(), frame.nodeSize());
            g.setColor(new Color(0xE0D8EA));
            var label = shortName(id.getPath());
            g.drawString(label, at.x() - g.getFontMetrics().stringWidth(label) / 2F, at.y() + 2);
        });

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        plan.names().forEach((constellation, name) ->
        {
            var color = COLORS.get(constellation);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 60));
            g.fillRect(name.x(), name.y(), name.width(), name.height());
            g.setColor(color);
            g.drawRect(name.x() + 1, name.y() + 1, 7, 7);
            g.drawString(ResearchTrees.nameOf(constellation), name.x() + 11, name.y() + 9);
        });

        g.dispose();
        ImageIO.write(image, "png", file);
    }

    private static String shortName(String path)
    {
        var parts = path.split("_");
        return parts.length == 1 ? path.substring(0, Math.min(5, path.length())) : parts[0].substring(0, Math.min(4, parts[0].length())) + "." + parts[1].charAt(0);
    }
}
