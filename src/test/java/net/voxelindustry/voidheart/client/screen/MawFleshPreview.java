package net.voxelindustry.voidheart.client.screen;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Draws the mouth behind the research map at every stage, side by side, to review it without starting a game. Run with {@code DUNGEON_PREVIEW=true},
 * images go to build/research-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class MawFleshPreview
{
    private static final int SCALE = 3;
    private static final int PANEL = 320;
    private static final int TALL  = 220;

    @Test
    void drawStages() throws IOException
    {
        var folder = new File("build/research-preview");
        folder.mkdirs();
        var image = new BufferedImage(PANEL * (MawFlesh.STAGES + 1) * SCALE, TALL * SCALE, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(0x0D0A14));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());

        for (int stage = 0; stage < MawFlesh.STAGES; stage++)
        {
            var shape = MawFlesh.shape(stage, 1234L);
            int middleX = PANEL * stage + PANEL / 2;
            int middleY = TALL / 2;
            paint(image, shape.cells(), middleX, middleY);
            for (int root = 0; root < shape.roots().size(); root++)
                paint(image, MawFlesh.tendril(shape.roots().get(root), 30 + root * 9, 8, root * 1.7F, 1234L + root), middleX, middleY);
            paint(image, shape.teeth(), middleX, middleY);
            paint(image, MawFlesh.eye(shape.eyeRadius(), 0.6F, -0.3F, 1, 1234L), middleX, middleY + Math.round(shape.eyeY()));
        }
        paint(image, MawFlesh.sealed(1234L).cells(), PANEL * MawFlesh.STAGES + PANEL / 2, TALL / 2);
        g.dispose();
        ImageIO.write(image, "png", new File(folder, "maw_stages.png"));
    }

    private static void paint(BufferedImage image, List<MawFlesh.Cell> cells, int middleX, int middleY)
    {
        for (var cell : cells)
        {
            for (int dy = 0; dy < cell.size() * SCALE; dy++)
            {
                for (int dx = 0; dx < cell.size() * SCALE; dx++)
                {
                    int x = (middleX + cell.x()) * SCALE + dx;
                    int y = (middleY + cell.y()) * SCALE + dy;
                    if (x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight())
                        image.setRGB(x, y, cell.argb());
                }
            }
        }
    }
}
