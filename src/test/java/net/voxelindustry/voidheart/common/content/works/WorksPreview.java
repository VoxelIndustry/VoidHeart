package net.voxelindustry.voidheart.common.content.works;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Draws plans for real late items as the works view will lay them out, to review the planner without starting a game. Run with
 * {@code DUNGEON_PREVIEW=true}, images go to build/works-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class WorksPreview
{
    private static final int COLUMN = 190;
    private static final int ROW    = 34;
    private static final int BOX_W  = 160;
    private static final int BOX_H  = 26;
    private static final int MARGIN = 30;

    private static final Map<StationKind, Color> STATION = Map.of(StationKind.ALTAR, new Color(0x8E6BE8), StationKind.FORGE, new Color(0xE08A5E),
            StationKind.TABLE, new Color(0x36C5B0));
    private static final Color RAW     = new Color(0xA899BC);
    private static final Color LACKING = new Color(0xEF8A98);

    @Test
    void drawPlans() throws IOException
    {
        var works = new JsonWorks();
        var folder = new File("build/works-preview");
        folder.mkdirs();

        draw(works, "voidheart:void_armor", 1, new File(folder, "void_armor.png"));
        draw(works, "voidheart:supreme_shielding_apparatus", 1, new File(folder, "supreme_apparatus.png"));
        draw(works, "voidheart:klein_star", 1, new File(folder, "klein_star.png"));
        draw(works, "voidheart:ravenous_storage_matrix", 2, new File(folder, "storage_matrix.png"));
    }

    private static void draw(JsonWorks works, String target, long count, File file) throws IOException
    {
        var plan = WorksPlan.plan(works::recipesFor, Map.of(), target, count);
        var layout = WorksLayout.of(plan);
        System.out.printf("%s: %d kinds, %d crafts, lacking %s, essence %s%n", file.getName(), plan.nodes().size(), plan.tasks().size(), plan.lacking(), plan.essence());

        int width = layout.columns() * COLUMN + MARGIN * 2;
        int height = layout.rows() * ROW + MARGIN * 2;
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0B0810));
        g.fillRect(0, 0, width, height);

        g.setStroke(new BasicStroke(1.5F));
        for (var edge : plan.edges())
        {
            var from = layout.spots().get(edge.ingredient());
            var to = layout.spots().get(edge.product());
            if (from == null || to == null)
                continue;
            float x1 = x(from) + BOX_W, y1 = y(from, layout) + BOX_H / 2F, x2 = x(to), y2 = y(to, layout) + BOX_H / 2F;
            g.setColor(new Color(0x4A3860));
            g.draw(new CubicCurve2D.Float(x1, y1, (x1 + x2) / 2, y1, (x1 + x2) / 2, y2, x2, y2));
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (var entry : layout.spots().entrySet())
        {
            var node = plan.nodes().get(entry.getKey());
            var spot = entry.getValue();
            var color = node.isMade() ? STATION.get(node.recipe().station()) : plan.lacking().containsKey(node.item()) ? LACKING : RAW;
            int x = x(spot), y = y(spot, layout);
            g.setColor(new Color(0x170E28));
            g.fillRoundRect(x, y, BOX_W, BOX_H, 6, 6);
            g.setColor(color);
            g.drawRoundRect(x, y, BOX_W, BOX_H, 6, 6);
            g.drawString(node.item().replace("voidheart:", "").replace("minecraft:", "mc:"), x + 6, y + 11);
            var counts = node.isMade() ? "x" + node.needed() + " (" + node.crafts() + " crafts)" : "x" + node.needed();
            g.setColor(new Color(0xEDE6FF));
            g.drawString(counts, x + 6, y + 23);
        }
        g.dispose();
        ImageIO.write(image, "png", file);
    }

    private static int x(WorksLayout.Spot spot)
    {
        return MARGIN + spot.column() * COLUMN;
    }

    private static int y(WorksLayout.Spot spot, WorksLayout<?> layout)
    {
        return MARGIN + Math.round((spot.y() + (layout.rows() - 1) / 2F) * ROW);
    }
}
