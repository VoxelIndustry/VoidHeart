package net.voxelindustry.voidheart.common.world.underway;

import net.voxelindustry.voidheart.common.world.daedalus.Compass;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

/**
 * Draws the Underway to review it without starting a game. Run with {@code DUNGEON_PREVIEW=true}, images go to build/underway-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class UnderwayPreview
{
    private static final long SEED = 1234L;
    private static final File FOLDER = new File("build/underway-preview");
    private static final int DISTRICTS = 40;
    private static final int PITCH = 30;
    private static final int DISTRICT = 24;
    private static final int LEGEND = 44;
    private static final float GOLDEN_TURN = 0.618034F;
    private static final int SEARCH = 80;
    private static final int FORT_SCALE = 4;
    private static final int GAP = 10;
    private static final int LOOK_DOWN = 4;
    private static final int LEGEND_ROWS = 12;
    private static final int[] FLOORS = { UnderwayLevels.ENTRY_FLOOR, UnderwayLevels.LIVING_FLOOR, UnderwayLevels.PLATFORM_FLOOR };
    private static final String[] LEVELS = { "entry level", "living level", "rail level" };
    private static final EnumSet<UnderwayRoomKind> UNLABELLED = EnumSet.of(UnderwayRoomKind.ENTRY_GALLERY, UnderwayRoomKind.PIT, UnderwayRoomKind.MAGAZINE, UnderwayRoomKind.CROSSING);

    @Test
    void drawNetwork() throws IOException
    {
        FOLDER.mkdirs();
        var sectors = new UnderwaySectors(SEED, 0, DISTRICTS);
        var image = new BufferedImage(DISTRICTS * PITCH, DISTRICTS * PITCH + LEGEND, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (int z = 0; z < DISTRICTS; z++)
        {
            for (int x = 0; x < DISTRICTS; x++)
            {
                graphics.setColor(new Color(ruinColor(UnderwayNetwork.ruinAt(SEED, new DistrictPos(x, z)))));
                graphics.fillRect(x * PITCH, z * PITCH, DISTRICT, DISTRICT);
            }
        }
        for (int z = 0; z < DISTRICTS; z++)
        {
            for (int x = 0; x < DISTRICTS; x++)
                drawGallery(graphics, new DistrictPos(x, z));
        }
        for (int z = 0; z < DISTRICTS; z++)
        {
            for (int x = 0; x < DISTRICTS; x++)
            {
                drawTrunk(graphics, new DistrictPos(x, z), Compass.EAST);
                drawTrunk(graphics, new DistrictPos(x, z), Compass.SOUTH);
            }
        }
        for (int z = 0; z < DISTRICTS; z++)
        {
            for (int x = 0; x < DISTRICTS; x++)
                drawNode(graphics, sectors, new DistrictPos(x, z));
        }
        drawLegend(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", new File(FOLDER, "network.png"));

        var sizes = sectors.sizes().values().stream().mapToInt(Integer::intValue).sorted().toArray();
        System.out.println("Sectors: " + sizes.length + ", median " + sizes[sizes.length / 2] + " districts, largest " + sizes[sizes.length - 1]);
    }

    @Test
    void drawForts() throws IOException
    {
        FOLDER.mkdirs();
        for (var ruin : UnderwayRuin.values())
            drawFort("station_" + ruin.name().toLowerCase(), find(UnderwayNode.STATION, ruin, true));
        drawFort("station_north_south", find(UnderwayNode.STATION, UnderwayRuin.DRY, false));
        drawFort("hub", find(UnderwayNode.HUB, UnderwayRuin.DRY, true));
        drawFort("hub_flooded", find(UnderwayNode.HUB, UnderwayRuin.FLOODED, true));
        drawFort("post", find(UnderwayNode.POST, UnderwayRuin.DRY, true));
        drawFort("post_collapsed", find(UnderwayNode.POST, UnderwayRuin.COLLAPSED, true));
    }

    /** One whole region of six districts a side, a block to a pixel, to see the forts joined by their trunks and galleries. */
    @Test
    void drawRegion() throws IOException
    {
        FOLDER.mkdirs();
        int size = DistrictPos.PITCH * UnderwayNetwork.REGION;
        var canvas = new UnderwayCanvas(size, size);
        canvas.reset(0, 0);
        UnderwayPlans.paint(SEED, canvas);
        for (int level = 1; level < FLOORS.length; level++)
        {
            var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int z = 0; z < size; z++)
            {
                for (int x = 0; x < size; x++)
                    image.setRGB(x, z, fromAbove(canvas, x, FLOORS[level] + 1, z));
            }
            ImageIO.write(image, "png", new File(FOLDER, "region_" + LEVELS[level].replace(' ', '_') + ".png"));
        }
    }

    private static DistrictPos find(UnderwayNode node, UnderwayRuin ruin, boolean alongX)
    {
        for (int z = 0; z < SEARCH; z++)
        {
            for (int x = 0; x < SEARCH; x++)
            {
                var pos = new DistrictPos(x, z);
                if (UnderwayNetwork.nodeAt(SEED, pos) == node && UnderwayNetwork.ruinAt(SEED, pos) == ruin && UnderwayPlans.fort(SEED, pos).frame().alongX() == alongX)
                    return pos;
            }
        }
        throw new IllegalStateException("No " + ruin + " " + node + " within " + SEARCH + " districts");
    }

    private static void drawFort(String name, DistrictPos pos) throws IOException
    {
        var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
        canvas.reset(pos.originX(), pos.originZ());
        UnderwayPlans.paint(SEED, canvas);
        var fort = UnderwayPlans.fort(SEED, pos);
        ImageIO.write(levels(canvas, fort), "png", new File(FOLDER, name + "_levels.png"));
        ImageIO.write(cuts(canvas, fort), "png", new File(FOLDER, name + "_cuts.png"));
    }

    /** Each level seen from above at head height, down to whatever lies under the air, in the world's own directions. */
    private static BufferedImage levels(UnderwayCanvas canvas, UnderwayFort fort)
    {
        int panel = DistrictPos.PITCH * FORT_SCALE;
        var image = new BufferedImage(panel * 2 + GAP, panel * 2 + GAP, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (int level = 0; level < FLOORS.length; level++)
        {
            int left = (level % 2) * (panel + GAP);
            int top = (level / 2) * (panel + GAP);
            for (int z = 0; z < DistrictPos.PITCH; z++)
            {
                for (int x = 0; x < DistrictPos.PITCH; x++)
                {
                    graphics.setColor(new Color(fromAbove(canvas, canvas.minX() + x, FLOORS[level] + 1, canvas.minZ() + z)));
                    graphics.fillRect(left + x * FORT_SCALE, top + z * FORT_SCALE, FORT_SCALE, FORT_SCALE);
                }
            }
            for (var room : fort.rooms())
            {
                if (room.floorY() == FLOORS[level] && !UNLABELLED.contains(room.kind()))
                    label(graphics, fort, room, left, top);
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(LEVELS[level], left + 6, top + 14);
        }
        graphics.drawString(fort.ruin().name().toLowerCase() + " " + fort.node().name().toLowerCase() + " under district " + fort.pos().x() + ", " + fort.pos().z(), panel + GAP + 16, panel + GAP + 20);
        legend(graphics, panel + GAP + 16, panel + GAP + 40);
        graphics.drawString("props: brown stands on the floor, teal hangs on a wall, yellow hangs from the ceiling", panel + GAP + 16, panel + GAP + 330);
        graphics.dispose();
        return image;
    }

    private static int fromAbove(UnderwayCanvas canvas, int x, int y, int z)
    {
        for (int height = 2; height >= 0; height--)
        {
            var prop = canvas.propAt(x, y + height, z);
            if (prop != null)
                return prop.mount() == UnderwayProp.Mount.WALL ? 0x20B0A0 : (prop.mount() == UnderwayProp.Mount.CEILING ? 0xE0D040 : 0xB06A30);
        }
        for (int depth = 0; depth <= LOOK_DOWN; depth++)
        {
            var material = canvas.get(x, y - depth, z);
            if (material != UnderwayMaterial.AIR)
                return color(material);
        }
        return 0;
    }

    private static void label(Graphics2D graphics, UnderwayFort fort, UnderwayRoom room, int left, int top)
    {
        var frame = fort.frame();
        int x = left + (frame.x(room.centreAlong(), room.centreAcross()) - fort.pos().originX()) * FORT_SCALE;
        int z = top + (frame.z(room.centreAlong(), room.centreAcross()) - fort.pos().originZ()) * FORT_SCALE;
        var words = room.kind().name().toLowerCase().split("_");
        graphics.setColor(Color.BLACK);
        for (int line = 0; line < words.length; line++)
            graphics.drawString(words[line], x - graphics.getFontMetrics().stringWidth(words[line]) / 2, z + 4 + (line * 2 - words.length + 1) * 5);
    }

    private static void legend(Graphics2D graphics, int left, int top)
    {
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        int row = 0;
        for (var material : UnderwayMaterial.values())
        {
            if (material.name().endsWith("_EAST") || material.name().endsWith("_SOUTH") || material.name().endsWith("_WEST") || material.name().endsWith("_Z"))
                continue;
            int x = left + (row / LEGEND_ROWS) * 220;
            int y = top + (row % LEGEND_ROWS) * 22;
            graphics.setColor(new Color(color(material)));
            graphics.fillRect(x, y, 16, 16);
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x, y, 16, 16);
            graphics.drawString(material.name().toLowerCase().replace("_north", "").replace("_x", "").replace('_', ' '), x + 24, y + 13);
            row++;
        }
    }

    /** Cuts in the fort's own frame, along it and across it, through its middle and through its core. */
    private static BufferedImage cuts(UnderwayCanvas canvas, UnderwayFort fort)
    {
        int width = DistrictPos.PITCH * FORT_SCALE;
        int height = UnderwayLevels.HEIGHT * FORT_SCALE;
        var image = new BufferedImage(width * 2 + GAP, height * 2 + GAP, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        var brush = new UnderwayBrush(canvas, fort.frame());
        var core = fort.shafts().getFirst();
        int[] fixed = { TrunkPainter.CENTRE, core.c0() + 1, core.a0() + 1, TrunkPainter.CENTRE };
        String[] names = { "along, through the middle", "along, through the core", "across, through the core", "across, through the middle" };
        for (int cut = 0; cut < fixed.length; cut++)
        {
            int left = (cut % 2) * (width + GAP);
            int top = (cut / 2) * (height + GAP);
            for (int y = UnderwayLevels.MIN_Y; y <= UnderwayLevels.TOP_Y; y++)
            {
                for (int run = 0; run < DistrictPos.PITCH; run++)
                {
                    var material = cut < 2 ? brush.get(run, y, fixed[cut]) : brush.get(fixed[cut], y, run);
                    graphics.setColor(new Color(material == UnderwayMaterial.AIR ? 0xD8D8E0 : color(material)));
                    graphics.fillRect(left + run * FORT_SCALE, top + (UnderwayLevels.TOP_Y - y) * FORT_SCALE, FORT_SCALE, FORT_SCALE);
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(names[cut], left + 6, top + height - 8);
        }
        graphics.dispose();
        return image;
    }

    private static int color(UnderwayMaterial material)
    {
        return switch (material)
        {
            case ROCK -> 0x0C0C10;
            case AIR -> 0x000000;
            case SHELL -> 0x34343C;
            case FLOOR -> 0x9A9AA2;
            case PLATFORM -> 0xC8BE9E;
            case BALLAST -> 0x6A5E50;
            case STEEL, COLUMN -> 0x3C6C98;
            case HAZARD, HAZARD_X, HAZARD_Z -> 0xC8A030;
            case GRATING -> 0x78A0C0;
            case STAIRS_NORTH, STAIRS_EAST, STAIRS_SOUTH, STAIRS_WEST -> 0x30C070;
            case TRUNK_RAIL_X, TRUNK_RAIL_Z -> 0xF4F4FF;
            case FEEDER_RAIL_X, FEEDER_RAIL_Z -> 0xD8A040;
            case DOOR -> 0xF07020;
            case GATE -> 0xFF2020;
            case EMBRASURE -> 0xFF50C0;
            case LAMP -> 0xFFF080;
            case EMERGENCY_LAMP -> 0xC02848;
            case RUBBLE -> 0x8A5A28;
            case WATER -> 0x3060C0;
            case VOID -> 0x8020C0;
        };
    }

    private static void drawGallery(Graphics2D graphics, DistrictPos pos)
    {
        if (UnderwayNetwork.nodeAt(SEED, pos).isOnLine())
            return;
        var station = UnderwayNetwork.stationOf(SEED, pos);
        graphics.setColor(new Color(0x8890A0));
        graphics.setStroke(new BasicStroke(1.5F));
        graphics.drawLine(centre(pos.x()), centre(pos.z()), centre(station.x()), centre(station.z()));
    }

    private static void drawTrunk(Graphics2D graphics, DistrictPos pos, int direction)
    {
        var link = UnderwayNetwork.linkFrom(SEED, pos, direction);
        if (link == UnderwayLink.NONE)
            return;
        int fromX = centre(pos.x());
        int fromZ = centre(pos.z());
        int toX = fromX + Compass.dx(direction) * PITCH;
        int toZ = fromZ + Compass.dz(direction) * PITCH;
        graphics.setColor(new Color(0xD8D8E0));
        graphics.setStroke(new BasicStroke(4));
        graphics.drawLine(fromX, fromZ, toX, toZ);

        int midX = fromX + Compass.dx(direction) * (DISTRICT / 2 + (PITCH - DISTRICT) / 2);
        int midZ = fromZ + Compass.dz(direction) * (DISTRICT / 2 + (PITCH - DISTRICT) / 2);
        if (link == UnderwayLink.GATED)
        {
            graphics.setColor(new Color(0xFF3030));
            graphics.setStroke(new BasicStroke(3));
            graphics.drawLine(midX - Compass.dz(direction) * 6, midZ - Compass.dx(direction) * 6, midX + Compass.dz(direction) * 6, midZ + Compass.dx(direction) * 6);
        }
        else if (link == UnderwayLink.COLLAPSED)
        {
            graphics.setColor(new Color(0xFF9020));
            graphics.fillOval(midX - 4, midZ - 4, 8, 8);
        }
    }

    private static void drawNode(Graphics2D graphics, UnderwaySectors sectors, DistrictPos pos)
    {
        var sector = sectors.sectorOf(pos);
        var color = sector == null ? Color.GRAY : Color.getHSBColor(sector * GOLDEN_TURN, 0.75F, 1);
        int half = switch (UnderwayNetwork.nodeAt(SEED, pos))
        {
            case HUB -> 8;
            case STATION -> 5;
            case POST -> 2;
        };
        if (half > 2)
        {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(centre(pos.x()) - half - 1, centre(pos.z()) - half - 1, half * 2 + 2, half * 2 + 2);
        }
        graphics.setColor(color);
        graphics.fillRect(centre(pos.x()) - half, centre(pos.z()) - half, half * 2, half * 2);
    }

    private static void drawLegend(Graphics2D graphics)
    {
        int top = DISTRICTS * PITCH + 6;
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        int x = 8;
        for (var ruin : UnderwayRuin.values())
        {
            graphics.setColor(new Color(ruinColor(ruin)));
            graphics.fillRect(x, top, 14, 14);
            graphics.setColor(Color.WHITE);
            graphics.drawRect(x, top, 14, 14);
            graphics.drawString(ruin.name().toLowerCase(), x + 20, top + 12);
            x += 110;
        }
        graphics.drawString("big square: hub   square: station   dot: post, thin line: its gallery   red bar: blast gate   orange: cave-in   node colour: sector", 8, top + 32);
    }

    private static int centre(int district)
    {
        return district * PITCH + DISTRICT / 2;
    }

    private static int ruinColor(UnderwayRuin ruin)
    {
        return switch (ruin)
        {
            case DRY -> 0x2C2C34;
            case FLOODED -> 0x1C3A66;
            case COLLAPSED -> 0x54402A;
            case BREACHED -> 0x6A2090;
            case SEALED -> 0x5C6A2C;
        };
    }
}
