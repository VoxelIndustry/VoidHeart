package net.voxelindustry.voidheart.common.content.armillary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws every root a search grows, each at its fullest, to review their shapes without starting a game. Run with {@code DUNGEON_PREVIEW=true}, images go to
 * build/armillary-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class ArmillaryRootsPreview
{
    private static final int   WIDTH  = 580;
    private static final int   HEIGHT = 400;
    private static final float MOUNT  = 20;

    private static final float[][] TARGETS = { { 0.8F, 0.7F, 0.055F }, { 0.15F, 0.25F, 0.035F }, { 0.55F, 0.12F, 0.08F }, { 0.92F, 0.45F, 0.035F }, { 0.3F, ArmillaryChart.STRATA_TOP, 0 } };

    @Test
    void drawRoots() throws IOException
    {
        var folder = new File("build/armillary-preview");
        folder.mkdirs();
        for (int index = 0; index < TARGETS.length; index++)
        {
            for (long seed = 0; seed < 3; seed++)
                draw(TARGETS[index], seed * 7919 + index, new File(folder, "roots-" + index + "-" + seed + ".png"));
        }
    }

    private static void draw(float[] target, long seed, File file) throws IOException
    {
        var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x0D0A14));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setColor(new Color(0x2A2430));
        g.fillRect(0, Math.round(ArmillaryChart.STRATA_TOP * HEIGHT), WIDTH, HEIGHT);

        float radius = target[2] * HEIGHT;
        g.setColor(new Color(0x6A5A8A));
        g.fill(new Ellipse2D.Float(target[0] * WIDTH - radius, target[1] * HEIGHT - radius, radius * 2, radius * 2));

        var branches = ArmillaryRoots.grow(seed, 0.5F, MOUNT / WIDTH, MOUNT / HEIGHT, target[0], target[1], radius / HEIGHT);
        for (var branch : branches)
        {
            var path = new Path2D.Float();
            path.moveTo(branch.xs()[0] * WIDTH, branch.ys()[0] * HEIGHT);
            for (int point = 1; point < branch.xs().length; point++)
                path.lineTo(branch.xs()[point] * WIDTH, branch.ys()[point] * HEIGHT);
            g.setStroke(new BasicStroke(branch.main() ? 2 : 1));
            g.setColor(branch.main() ? new Color(0xF2C14E) : new Color(0x9C8FB0));
            g.draw(path);
        }

        g.setColor(new Color(0x050308));
        g.fill(new Ellipse2D.Float(WIDTH / 2F - MOUNT + 1, -MOUNT + 1, MOUNT * 2 - 2, MOUNT * 2 - 2));
        g.setColor(new Color(0x2E2340));
        g.draw(new Ellipse2D.Float(WIDTH / 2F - MOUNT + 1, -MOUNT + 1, MOUNT * 2 - 2, MOUNT * 2 - 2));
        g.dispose();
        ImageIO.write(image, "png", file);
    }
}
