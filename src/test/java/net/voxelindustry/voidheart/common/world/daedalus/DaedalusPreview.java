package net.voxelindustry.voidheart.common.world.daedalus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Draws cuts through the Daedalus to review layouts without starting a game. Run with {@code DUNGEON_PREVIEW=true}, images go to build/daedalus-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class DaedalusPreview
{
    private static final long SEED = 1234L;
    private static final int TIER_SCALE = 2;
    private static final int COLUMNS = 4;
    private static final int GAP = 6;
    private static final int MAP_DISTRICTS = 4;
    private static final int SIDE_SCALE = 3;

    @Test
    void drawThemes() throws IOException
    {
        var folder = new File("build/daedalus-preview");
        folder.mkdirs();
        for (var theme : DaedalusTheme.values())
        {
            DistrictPos pos = null;
            for (int x = 0; pos == null; x++)
            {
                if (DaedalusTheme.at(new DistrictPos(x, 5)) == theme)
                    pos = new DistrictPos(x, 5);
            }
            var canvas = new DaedalusCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
            canvas.reset(pos.originX(), pos.originZ());
            DistrictPlans.paint(SEED, canvas);
            ImageIO.write(tiers(canvas), "png", new File(folder, theme.getSerializedName() + "_tiers.png"));
            ImageIO.write(sideCuts(canvas), "png", new File(folder, theme.getSerializedName() + "_side.png"));
        }
    }

    @Test
    void drawMap() throws IOException
    {
        var folder = new File("build/daedalus-preview");
        folder.mkdirs();
        int size = DistrictPos.PITCH * MAP_DISTRICTS;
        var canvas = new DaedalusCanvas(size, size);
        canvas.reset(0, 0);
        DistrictPlans.paint(SEED, canvas);
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++)
        {
            for (int x = 0; x < size; x++)
                image.setRGB(x, z, topDownColor(canvas, x, z));
        }
        ImageIO.write(image, "png", new File(folder, "map.png"));
    }

    private static int topDownColor(DaedalusCanvas canvas, int x, int z)
    {
        for (int y = DistrictPos.TOP_Y - 1; y > 0; y--)
        {
            var material = canvas.get(x, y, z);
            if (material == DaedalusMaterial.AIR)
            {
                if (DistrictPos.isInsideDistrict(x, z))
                    return shade(0xD8D8E0, y);
                continue;
            }
            if (material != DaedalusMaterial.FILL && material != DaedalusMaterial.WALL && !DistrictPos.isInsideDistrict(x, z))
                return color(material);
        }
        return DistrictPos.isInsideDistrict(x, z) ? 0x101018 : 0x000000;
    }

    private static int shade(int rgb, int y)
    {
        float light = 0.3F + 0.7F * y / DistrictPos.TOP_Y;
        int red = (int) (((rgb >> 16) & 0xFF) * light);
        int green = (int) (((rgb >> 8) & 0xFF) * light);
        int blue = (int) ((rgb & 0xFF) * light);
        return red << 16 | green << 8 | blue;
    }

    private static BufferedImage tiers(DaedalusCanvas canvas)
    {
        int panel = DistrictPos.PITCH * TIER_SCALE + GAP;
        var image = new BufferedImage(panel * COLUMNS, panel * (DistrictPos.TIERS / COLUMNS), BufferedImage.TYPE_INT_RGB);
        for (int tier = 0; tier < DistrictPos.TIERS; tier++)
        {
            int offsetX = (tier % COLUMNS) * panel;
            int offsetY = (tier / COLUMNS) * panel;
            int y = DistrictPos.floorY(tier) + 1;
            for (int z = 0; z < DistrictPos.PITCH; z++)
            {
                for (int x = 0; x < DistrictPos.PITCH; x++)
                    paint(image, offsetX + x * TIER_SCALE, offsetY + z * TIER_SCALE, TIER_SCALE, color(canvas.get(canvas.minX() + x, y, canvas.minZ() + z)));
            }
        }
        return image;
    }

    private static BufferedImage sideCuts(DaedalusCanvas canvas)
    {
        int rows = 3;
        int width = DistrictPos.PITCH * SIDE_SCALE;
        int rowHeight = DistrictPos.HEIGHT * SIDE_SCALE + GAP;
        var image = new BufferedImage(width, rowHeight * rows, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < rows; row++)
        {
            int z = canvas.minZ() + 3 + (row * DistrictPos.SIZE) / rows;
            for (int y = 0; y < DistrictPos.HEIGHT; y++)
            {
                for (int x = 0; x < DistrictPos.PITCH; x++)
                    paint(image, x * SIDE_SCALE, row * rowHeight + (DistrictPos.HEIGHT - 1 - y) * SIDE_SCALE, SIDE_SCALE, color(canvas.get(canvas.minX() + x, y, z)));
            }
        }
        return image;
    }

    private static void paint(BufferedImage image, int px, int py, int scale, int color)
    {
        for (int dy = 0; dy < scale; dy++)
        {
            for (int dx = 0; dx < scale; dx++)
                image.setRGB(px + dx, py + dy, color);
        }
    }

    private static int color(DaedalusMaterial material)
    {
        return switch (material)
        {
            case AIR -> 0xD8D8E0;
            case OPENING -> 0xF0B0B0;
            case FILL -> 0x101018;
            case BOUND -> 0x000000;
            case FLOOR, FLOOR_TRIM, FLOOR_INLAY -> 0x7A7488;
            case CARPET -> 0xA03050;
            case WALL, CEILING -> 0x3A3648;
            case PANEL, FACADE -> 0x4C4860;
            case TRIM, FACADE_TRIM -> 0xC0A040;
            case COLUMN -> 0x8A8498;
            case LAMP -> 0xFFF080;
            case WATER -> 0x3060C0;
            case SHELF -> 0x8A5A30;
            case RAIL_X, RAIL_Z, RAIL_POST -> 0xA0A0B0;
            case CHAIN -> 0x808090;
            case SLAB, SLAB_TOP -> 0x6A6478;
            case STAIRS_NORTH, STAIRS_EAST, STAIRS_SOUTH, STAIRS_WEST -> 0x40C080;
            case ARCH_NORTH, ARCH_EAST, ARCH_SOUTH, ARCH_WEST -> 0x60A0A0;
            case PROP -> 0xE06030;
            case CACHE -> 0xF0C030;
            case HATCH -> 0xE8E020;
            case ALTAR, PILLAR, SHRINE_LAMP -> 0xD040D0;
            case MONOLITH_ALONE, MONOLITH_BASE, MONOLITH_SHAFT, MONOLITH_TIP -> 0x60FF90;
            case ELDRITCH, OBSIDIAN -> 0x502070;
            case COBWEB -> 0xE0E0E0;
            case COPPER, COPPER_SLAB, VENT, PIPE_X, PIPE_Y, PIPE_Z -> 0x4FA08A;
        };
    }
}
