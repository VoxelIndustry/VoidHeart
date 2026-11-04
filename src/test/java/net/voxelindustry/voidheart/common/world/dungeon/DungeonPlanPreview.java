package net.voxelindustry.voidheart.common.world.dungeon;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static net.voxelindustry.voidheart.common.world.dungeon.DungeonMaterial.*;

/**
 * Draws cuts through generated dungeons to review layouts without starting a game. Run with {@code DUNGEON_PREVIEW=true}, images go to build/dungeon-preview.
 */
@EnabledIfEnvironmentVariable(named = "DUNGEON_PREVIEW", matches = "true")
class DungeonPlanPreview
{
    private static final int SCALE = 3;

    @Test
    void drawCuts() throws IOException
    {
        var folder = new File("build/dungeon-preview");
        folder.mkdirs();
        for (var size : DungeonSize.values())
        {
            for (var theme : DungeonTheme.values())
            {
                for (var layout : DungeonLayout.values())
                {
                    long start = System.nanoTime();
                    var plan = DungeonPlan.generate(new DungeonBlueprint(size, theme, 3, layout, 1234L + theme.ordinal()));
                    System.out.printf("%s %s %s: %d ms, %d rooms, %d chests%n", size, theme, layout, (System.nanoTime() - start) / 1_000_000, plan.rooms().size(), plan.chests().size());
                    if (size != DungeonSize.LARGE || theme == DungeonTheme.TEMPLE || theme == DungeonTheme.FOREST)
                        draw(plan, new File(folder, size.getSerializedName() + "_" + theme.getSerializedName() + "_" + layout.name().toLowerCase() + ".png"));
                }
            }
        }
    }

    private static void draw(DungeonPlan plan, File file) throws IOException
    {
        var grid = plan.grid();
        int edge = grid.width;
        int panel = edge * SCALE + 6;
        var image = new BufferedImage(panel * 3, panel, BufferedImage.TYPE_INT_RGB);

        int[] entry = plan.entry();
        drawSideCut(image, grid, entry[2], 0, SCALE);
        drawSideCut(image, grid, edge / 2, panel, SCALE);
        drawTopCut(image, grid, entry[1] + 2, panel * 2, SCALE);
        ImageIO.write(image, "png", file);
    }

    private static void drawSideCut(BufferedImage image, DungeonGrid grid, int z, int offsetX, int scale)
    {
        for (int y = 0; y < grid.height; y++)
        {
            for (int x = 0; x < grid.width; x++)
                paint(image, offsetX + x * scale, (grid.height - 1 - y) * scale, scale, color(grid.material(x, y, z)));
        }
    }

    private static void drawTopCut(BufferedImage image, DungeonGrid grid, int y, int offsetX, int scale)
    {
        for (int z = 0; z < grid.depth; z++)
        {
            for (int x = 0; x < grid.width; x++)
                paint(image, offsetX + x * scale, z * scale, scale, color(grid.material(x, y, z)));
        }
    }

    private static void paint(BufferedImage image, int px, int py, int scale, int color)
    {
        for (int dy = 0; dy < scale; dy++)
        {
            for (int dx = 0; dx < scale; dx++)
                image.setRGB(px + dx, py + dy, color);
        }
    }

    private static int color(DungeonMaterial material)
    {
        if (material.isCrystal())
        {
            return switch (material.crystalKind())
            {
                case AZURIUM -> 0x33AAFF;
                case RUBRUM -> 0xFF3344;
                case MEMORIAM -> 0xFFEE66;
            };
        }
        if (material == UNSET || material == AIR)
            return 0x0B0B12;
        if (material == POCKET_WALL)
            return 0x000000;
        if (material == ROCK)
            return 0x2A2A38;
        if (material == ROCK_NATIVE)
            return 0x4A4638;
        if (material == VEIN || material == VEIN_WRITHING || material == VEIN_BLOSSOM || material == VEIN_MAW)
            return 0x6A2A9A;
        if (material == TEMPLATE)
            return 0x7A5AC8;
        if (material == FLUID)
            return 0x2FBF6F;
        if (material == CHEST)
            return 0xFF9900;
        if (material == LANTERN || material == GLOW || material == CANDLE || material == CAMPFIRE)
            return 0xFFDD88;
        if (material.name().startsWith("METAL"))
            return 0xD8C050;
        if (material == LEAVES || material == PLANT || material == TALL_PLANT || material == HANGING || material == SOIL)
            return 0x4C8A3A;
        if (material == LOG || material == STEM || material == PLANKS || material == FRAME || material == ROOF_STAIRS)
            return 0x8A5A30;
        if (material == CAP || material == CAP_GILL || material == CAP_GLOW)
            return 0xC83A20;
        return 0x9A9AA6;
    }
}
