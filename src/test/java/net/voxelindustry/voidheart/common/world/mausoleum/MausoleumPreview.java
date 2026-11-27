package net.voxelindustry.voidheart.common.world.mausoleum;

import net.voxelindustry.voidheart.common.world.daedalus.ShroudGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a mausoleum to review it without starting a game: the floor seen from above with its orbs, chambers, gate and rider, a cut down its
 * middle, and a cut across the hall through a pair of burial chambers. Run with {@code DUNGEON_PREVIEW=true}, images go to build/mausoleum-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class MausoleumPreview
{
    private static final File FOLDER = new File("build/mausoleum-preview");
    private static final long[] SEEDS = { 0, 42 };
    private static final int SCALE = 8;
    private static final Map<MausoleumMaterial, Integer> COLORS = new EnumMap<>(MausoleumMaterial.class);

    static
    {
        COLORS.put(MausoleumMaterial.AIR, 0x0B0910);
        COLORS.put(MausoleumMaterial.FOUNDATION, 0x26202A);
        COLORS.put(MausoleumMaterial.WALL, 0x3B353E);
        COLORS.put(MausoleumMaterial.WALL_CRACKED, 0x332E36);
        COLORS.put(MausoleumMaterial.GLYPHS, 0x5C5648);
        COLORS.put(MausoleumMaterial.PANEL, 0x45464B);
        COLORS.put(MausoleumMaterial.TRIM, 0x56525C);
        COLORS.put(MausoleumMaterial.CORNICE, 0x6A6472);
        COLORS.put(MausoleumMaterial.COLUMN, 0x8A8494);
        COLORS.put(MausoleumMaterial.BAND, 0xB0863A);
        COLORS.put(MausoleumMaterial.CAPITAL, 0x9C96A6);
        COLORS.put(MausoleumMaterial.FLOOR, 0x4A4550);
        COLORS.put(MausoleumMaterial.INLAY, 0x5A5260);
        COLORS.put(MausoleumMaterial.GILT, 0xC9A43A);
        COLORS.put(MausoleumMaterial.CEILING, 0x2A2840);
        COLORS.put(MausoleumMaterial.DAIS, 0x605A68);
        COLORS.put(MausoleumMaterial.FALSE_DOOR, 0x7A5AA0);
    }

    @Test
    void draw() throws IOException
    {
        FOLDER.mkdirs();
        for (long seed : SEEDS)
        {
            var plan = MausoleumPlan.generate(seed);
            ImageIO.write(floor(plan), "png", new File(FOLDER, "floor-" + seed + ".png"));
            ImageIO.write(section(plan), "png", new File(FOLDER, "section-" + seed + ".png"));
            ImageIO.write(cross(plan, MausoleumPlan.CHAMBER_ZS[1] + 1), "png", new File(FOLDER, "cross-" + seed + ".png"));
        }
    }

    /** From above: what one walks on, the lowest air's floor, with orbs as green dots, chambers outlined, the gate in violet and the rider in red. */
    private static BufferedImage floor(MausoleumPlan plan)
    {
        var image = new BufferedImage(MausoleumPlan.WIDTH * SCALE, MausoleumPlan.LENGTH * SCALE, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        for (int z = 0; z < MausoleumPlan.LENGTH; z++)
        {
            for (int x = 0; x < MausoleumPlan.WIDTH; x++)
            {
                int y = MausoleumPlan.FLOOR_Y;
                while (y < MausoleumPlan.HEIGHT - 1 && plan.material(x, y, z) != MausoleumMaterial.AIR)
                    y++;
                var material = y >= MausoleumPlan.HEIGHT - 1 ? plan.material(x, MausoleumPlan.FLOOR_Y, z) : plan.material(x, y - 1, z);
                graphics.setColor(new Color(COLORS.get(material)));
                graphics.fillRect(x * SCALE, z * SCALE, SCALE, SCALE);
            }
        }
        var gate = MausoleumPlan.GATE;
        graphics.setColor(new Color(0x9A40F0));
        gate.forEach((x, y, z, part) ->
        {
            if (y == gate.y() || part == ShroudGate.Part.SEAL)
                graphics.fillRect(x * SCALE + 1, z * SCALE + 1, SCALE - 2, SCALE - 2);
        });
        graphics.setColor(new Color(0x8CFF40));
        for (var orb : plan.orbs())
            graphics.fillOval(orb.x() * SCALE + 2, orb.z() * SCALE + 2, SCALE - 4, SCALE - 4);
        graphics.setColor(new Color(0x40C0FF));
        for (var chamber : plan.chambers())
            graphics.drawRect((chamber.x() - 2) * SCALE, (chamber.z() - 2) * SCALE, 4 * SCALE, 4 * SCALE);
        graphics.setColor(new Color(0xE0C080));
        for (int offset : MausoleumPlan.sarcophagusParts())
            graphics.fillRect(MausoleumPlan.SARCOPHAGUS_X * SCALE + 1, (MausoleumPlan.SARCOPHAGUS_Z + offset) * SCALE + 1, SCALE - 2, SCALE - 2);
        graphics.setColor(Color.RED);
        graphics.drawRect((MausoleumPlan.RIDER_X - 1) * SCALE, (MausoleumPlan.RIDER_Z - 1) * SCALE, SCALE * 3, SCALE * 3);
        graphics.dispose();
        return image;
    }

    /** A cut along the axis, the gate on the left. */
    private static BufferedImage section(MausoleumPlan plan)
    {
        var image = new BufferedImage(MausoleumPlan.LENGTH * SCALE, MausoleumPlan.HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        int x = MausoleumPlan.AXIS - 2;
        for (int y = 0; y < MausoleumPlan.HEIGHT; y++)
        {
            for (int z = 0; z < MausoleumPlan.LENGTH; z++)
            {
                graphics.setColor(new Color(COLORS.get(plan.material(x, y, z))));
                graphics.fillRect(z * SCALE, (MausoleumPlan.HEIGHT - 1 - y) * SCALE, SCALE, SCALE);
            }
        }
        graphics.setColor(new Color(0x8CFF40));
        for (var orb : plan.orbs())
        {
            if (Math.abs(orb.x() - x) <= 2)
                graphics.fillOval(orb.z() * SCALE + 2, (MausoleumPlan.HEIGHT - 1 - orb.y()) * SCALE + 2, SCALE - 4, SCALE - 4);
        }
        graphics.dispose();
        return image;
    }

    /** A cut across, west on the left, through the hall at a given depth. */
    private static BufferedImage cross(MausoleumPlan plan, int z)
    {
        var image = new BufferedImage(MausoleumPlan.WIDTH * SCALE, MausoleumPlan.HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        for (int y = 0; y < MausoleumPlan.HEIGHT; y++)
        {
            for (int x = 0; x < MausoleumPlan.WIDTH; x++)
            {
                graphics.setColor(new Color(COLORS.get(plan.material(x, y, z))));
                graphics.fillRect(x * SCALE, (MausoleumPlan.HEIGHT - 1 - y) * SCALE, SCALE, SCALE);
            }
        }
        graphics.dispose();
        return image;
    }
}
