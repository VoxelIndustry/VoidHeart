package net.voxelindustry.voidheart.common.world.sanctum;

import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Draws a sanctum to review it without starting a game: the floor seen from above with what stands on it, and a cut down its middle. Run with
 * {@code DUNGEON_PREVIEW=true}, images go to build/sanctum-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class SanctumPreview
{
    private static final File FOLDER = new File("build/sanctum-preview");
    private static final long[] SEEDS = { 0, 42, 123456789L };
    private static final int SCALE = 8;
    private static final Map<SanctumMaterial, Integer> COLORS = new EnumMap<>(SanctumMaterial.class);

    static
    {
        COLORS.put(SanctumMaterial.AIR, 0x0B0910);
        COLORS.put(SanctumMaterial.FOUNDATION, 0x26202A);
        COLORS.put(SanctumMaterial.WALL, 0x3B353E);
        COLORS.put(SanctumMaterial.WALL_CRACKED, 0x332E36);
        COLORS.put(SanctumMaterial.PANEL, 0x45464B);
        COLORS.put(SanctumMaterial.TRIM, 0x56525C);
        COLORS.put(SanctumMaterial.COLUMN, 0x8A8494);
        COLORS.put(SanctumMaterial.PLINTH, 0x6E6878);
        COLORS.put(SanctumMaterial.CAPITAL, 0x9C96A6);
        COLORS.put(SanctumMaterial.FLOOR, 0x4A4550);
        COLORS.put(SanctumMaterial.INLAY, 0x5A5260);
        COLORS.put(SanctumMaterial.GILT, 0xC9A43A);
        COLORS.put(SanctumMaterial.CEILING, 0x3E3A44);
        COLORS.put(SanctumMaterial.COFFER, 0x5E4E6E);
        COLORS.put(SanctumMaterial.GATE_FRAME, 0x2E8C7A);
        COLORS.put(SanctumMaterial.FLESH, 0x72363F);
        COLORS.put(SanctumMaterial.WRITHING, 0x4A2A3A);
        COLORS.put(SanctumMaterial.WRITHING_MAW, 0x2A1218);
        COLORS.put(SanctumMaterial.WRITHING_BLOSSOM, 0x9A5A62);
    }

    @Test
    void draw() throws IOException
    {
        FOLDER.mkdirs();
        for (long seed : SEEDS)
        {
            var plan = SanctumPlan.generate(seed);
            ImageIO.write(floor(plan), "png", new File(FOLDER, "floor-" + seed + ".png"));
            ImageIO.write(section(plan), "png", new File(FOLDER, "section-" + seed + ".png"));
        }
    }

    /** From above: the topmost floor under the lowest air, with props as dots in their own colours and the posts as squares. */
    private static BufferedImage floor(SanctumPlan plan)
    {
        var image = new BufferedImage(SanctumPlan.WIDTH * SCALE, SanctumPlan.LENGTH * SCALE, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        for (int z = 0; z < SanctumPlan.LENGTH; z++)
        {
            for (int x = 0; x < SanctumPlan.WIDTH; x++)
            {
                int y = plan.standY(x, z);
                var material = plan.material(x, y, z) == SanctumMaterial.AIR ? plan.material(x, y - 1, z) : plan.material(x, y, z);
                graphics.setColor(new java.awt.Color(COLORS.get(material)));
                graphics.fillRect(x * SCALE, z * SCALE, SCALE, SCALE);
            }
        }
        for (var prop : plan.props())
        {
            graphics.setColor(new java.awt.Color(propColor(prop.prop())));
            graphics.fillOval(prop.x() * SCALE + 2, prop.z() * SCALE + 2, SCALE - 4, SCALE - 4);
        }
        for (var post : plan.posts())
        {
            graphics.setColor(post.kind() == SanctumPlan.PostKind.MASS ? java.awt.Color.RED : java.awt.Color.GREEN);
            int size = post.kind() == SanctumPlan.PostKind.MASS ? SCALE * 3 : SCALE;
            graphics.drawRect(post.x() * SCALE + SCALE / 2 - size / 2, post.z() * SCALE + SCALE / 2 - size / 2, size, size);
        }
        graphics.dispose();
        return image;
    }

    /** A cut along the middle of the nave, the porch on the left. */
    private static BufferedImage section(SanctumPlan plan)
    {
        var image = new BufferedImage(SanctumPlan.LENGTH * SCALE, SanctumPlan.HEIGHT * SCALE, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        int x = SanctumPlan.AXIS - 4;
        for (int y = 0; y < SanctumPlan.HEIGHT; y++)
        {
            for (int z = 0; z < SanctumPlan.LENGTH; z++)
            {
                graphics.setColor(new java.awt.Color(COLORS.get(plan.material(x, y, z))));
                graphics.fillRect(z * SCALE, (SanctumPlan.HEIGHT - 1 - y) * SCALE, SCALE, SCALE);
            }
        }
        for (var prop : plan.props())
        {
            if (prop.x() != x)
                continue;
            graphics.setColor(new java.awt.Color(propColor(prop.prop())));
            graphics.fillOval(prop.z() * SCALE + 2, (SanctumPlan.HEIGHT - 1 - prop.y()) * SCALE + 2, SCALE - 4, SCALE - 4);
        }
        graphics.dispose();
        return image;
    }

    private static int propColor(UnderwayProp prop)
    {
        return switch (prop)
        {
            case BRAZIER_LIT -> 0xF0A030;
            case BONE_OFFERING -> 0xD8CFC0;
            case TENDRIL_COIL, TENDRIL_COIL_CORNER -> 0xC0607A;
            case TENDRIL_ROOT, TENDRIL_HANG, TENDRIL_TIP -> 0xE08090;
            case FLESH_EYES -> 0xC8D040;
            default -> 0xA4626A;
        };
    }
}
