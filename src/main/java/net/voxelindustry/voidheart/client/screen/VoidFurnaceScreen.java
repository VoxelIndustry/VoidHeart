package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceBalance;
import net.voxelindustry.voidheart.common.energy.furnace.VoidFurnaceMenu;
import net.voxelindustry.voidheart.common.energy.machine.MachinePower;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.KNOWN_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.LACK_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.MUTED_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.RULE_COLOR;

/**
 * The Void Furnace over the player's inventory, cut in a small slab of the knowledge repository's voidstone. Its input and output sit in a torn mouth
 * lit from within, joined by a seam that fills as an item cooks, violet on void energy and ember on Fiery. A segmented gauge above them shows its pace,
 * a thin one under them the Fiery held, and one word says what the furnace is at.
 * <p>
 * Per frame: one blit for the baked slab, 38 slot plates, two bars and a few lines of text. The slab is baked once per resize.
 */
public class VoidFurnaceScreen extends AbstractContainerScreen<VoidFurnaceMenu>
{
    private static final int PANEL_W   = 214;
    private static final int PANEL_H   = 194;
    private static final int PAD       = 10;
    private static final int TITLE_Y   = 8;
    private static final int MOUTH_X   = 42;
    private static final int MOUTH_Y   = 22;
    private static final int MOUTH_W   = 130;
    private static final int MOUTH_H   = 56;
    private static final int TEAR      = 3;
    private static final int WHISPER_Y = MOUTH_Y + MOUTH_H + 6;

    private static final int SEAM_X0 = VoidFurnaceMenu.INPUT_X + 22;
    private static final int SEAM_X1 = VoidFurnaceMenu.OUTPUT_X - 6;
    private static final int SEAM_Y  = VoidFurnaceMenu.SLOTS_Y + 7;
    private static final int GAUGE_X0 = VoidFurnaceMenu.INPUT_X - 1;
    private static final int GAUGE_X1 = VoidFurnaceMenu.OUTPUT_X + 17;
    private static final int GAUGE_Y  = VoidFurnaceMenu.SLOTS_Y + 22;
    /** The pace gauge: one segment for each times a furnace's pace, up to the most a hot furnace burning Fiery reaches. */
    private static final int SEGMENTS    = (int) Math.round(VoidFurnaceBalance.pace(1, true));
    private static final int SEGMENT_W   = 5;
    private static final int SEGMENT_GAP = 1;
    private static final int PACE_W      = SEGMENTS * (SEGMENT_W + SEGMENT_GAP) - SEGMENT_GAP;
    private static final int PACE_X      = MOUTH_X + (MOUTH_W - PACE_W) / 2;
    private static final int PACE_Y      = VoidFurnaceMenu.SLOTS_Y - 11;
    private static final int PACE_H      = 4;

    private static final int SLOT_BACK     = 0xF40B0810;
    private static final int CELL_LINE     = 0xFF2A2038;
    private static final int OUTLINE_COLOR = 0xFF040208;
    private static final int TRACK_COLOR   = 0xFF08050C;
    private static final int VOID_COLOR    = 0xFFB08CF5;
    private static final int FIERY_COLOR   = 0xFFF0A050;
    private static final int FIERY_DIM     = 0xFF7A4A28;
    private static final int MOUTH_RGB     = 0x4A2A6E;
    private static final int COLD_SEGMENT  = 0xFF6A4AA8;
    private static final int HOT_SEGMENT   = 0xFFE6DAFF;
    private static final int NOTCH_COLOR   = 0xFF4A3860;

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(MODID, "dynamic/void_furnace_panel");

    public VoidFurnaceScreen(VoidFurnaceMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, PANEL_W, PANEL_H);
    }

    @Override
    protected void init()
    {
        super.init();
        bakePanel();
    }

    @Override
    public void removed()
    {
        minecraft.getTextureManager().release(PANEL);
        super.removed();
    }

    // The slab, baked once: voidstone with a lip around its edge, and a torn mouth for the slots, lit from within like the research map

    private void bakePanel()
    {
        var stone = ResearchVoid.readStone(minecraft);
        var image = new NativeImage(PANEL_W, PANEL_H, false);
        long seed = minecraft.player == null ? 0 : minecraft.player.getUUID().getLeastSignificantBits() ^ 0x5F0;
        var lights = List.of(new ResearchVoid.Light(MOUTH_X + MOUTH_W / 2D, MOUTH_Y + MOUTH_H * 0.7, MOUTH_W * 0.45, 0.9, MOUTH_RGB));
        for (int y = 0; y < PANEL_H; y++)
            for (int x = 0; x < PANEL_W; x++)
            {
                int hole = ResearchVoid.tornHoleDepth(x, y, MOUTH_X, MOUTH_Y, MOUTH_W, MOUTH_H, TEAR, seed);
                int rim = Math.min(Math.min(x, y), Math.min(PANEL_W - 1 - x, PANEL_H - 1 - y));
                int color;
                if (hole >= 0)
                    color = ResearchVoid.voidAt(x, y, hole, 0, lights, seed);
                else if (rim == 0)
                    color = OUTLINE_COLOR;
                else
                    color = ResearchVoid.stoneAt(stone, x, y, Math.min(-hole, rim), seed);
                image.setPixel(x, y, color);
            }
        if (stone != null)
            stone.close();
        ResearchVoid.upload(minecraft, PANEL, image);
    }

    // Drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        for (var slot : menu.slots)
        {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BACK);
            graphics.outline(x, y, 18, 18, CELL_LINE);
        }

        // The seam between input and output, filling as the item cooks
        int seamX0 = leftPos + SEAM_X0;
        int seamX1 = leftPos + SEAM_X1;
        int seamY = topPos + SEAM_Y;
        graphics.fill(seamX0, seamY, seamX1, seamY + 3, TRACK_COLOR);
        int filled = Math.round((seamX1 - seamX0) * menu.progress());
        if (filled > 0)
            graphics.fill(seamX0, seamY, seamX0 + filled, seamY + 3, menu.fast() ? FIERY_COLOR : VOID_COLOR);
        // An arrowhead at its end, lit once the item is done
        graphics.fill(seamX1, seamY - 2, seamX1 + 1, seamY + 5, filled >= seamX1 - seamX0 && filled > 0 ? KNOWN_COLOR : RULE_COLOR);
        graphics.fill(seamX1 + 1, seamY - 1, seamX1 + 2, seamY + 4, RULE_COLOR);
        graphics.fill(seamX1 + 2, seamY, seamX1 + 3, seamY + 3, RULE_COLOR);

        extractPaceGauge(graphics);

        // The Fiery held
        int gaugeX0 = leftPos + GAUGE_X0;
        int gaugeX1 = leftPos + GAUGE_X1;
        int gaugeY = topPos + GAUGE_Y;
        graphics.fill(gaugeX0, gaugeY, gaugeX1, gaugeY + 2, TRACK_COLOR);
        int held = Math.round((gaugeX1 - gaugeX0) * Math.min(1F, menu.fiery() / (float) VoidFurnaceBalance.FIERY_TANK));
        if (held > 0)
            graphics.fill(gaugeX0, gaugeY, gaugeX0 + held, gaugeY + 2, VoidFurnaceBalance.canBurnFiery(menu.fiery()) ? FIERY_COLOR : FIERY_DIM);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym)
    {
        graphics.text(font, font.plainSubstrByWidth(title.getString(), PANEL_W - 2 * PAD), PAD, TITLE_Y, KNOWN_COLOR, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        extractWhisper(graphics);
        if (overBar(mouseX, mouseY, GAUGE_Y) && menu.getCarried().isEmpty())
            graphics.setTooltipForNextFrame(font, Component.translatable(MODID + ".void_furnace.fiery_gauge", menu.fiery(), VoidFurnaceBalance.FIERY_TANK,
                    VoidFurnaceBalance.FIERY_PER_ITEM), mouseX, mouseY);
        else if (overPace(mouseX, mouseY) && menu.getCarried().isEmpty())
            graphics.setTooltipForNextFrame(font, Component.translatable(MODID + ".void_furnace.heat." + VoidFurnaceBalance.heatStage(menu.heat()),
                    VoidFurnaceBalance.paceText(VoidFurnaceBalance.pace(menu.heat(), false)),
                    VoidFurnaceBalance.paceText(VoidFurnaceBalance.pace(menu.heat(), true))), mouseX, mouseY);
    }

    /**
     * One segment for each times a furnace's pace. The first ones, a cold furnace's, glow dim violet; those heat adds brighten toward white; those Fiery
     * adds burn ember. While nothing cooks it shows the pace its heat holds, dimmed, and notches mark the cold and hot paces.
     */
    private void extractPaceGauge(GuiGraphicsExtractor graphics)
    {
        int x0 = leftPos + PACE_X;
        int y0 = topPos + PACE_Y;
        var live = menu.cooking() && menu.power() != MachinePower.UNPOWERED && menu.power() != MachinePower.IDLE;
        var pace = live ? menu.pace() : VoidFurnaceBalance.pace(menu.heat(), false);
        var cold = VoidFurnaceBalance.COLD_SPEED;
        var hot = VoidFurnaceBalance.HOT_SPEED;
        for (int segment = 0; segment < SEGMENTS; segment++)
        {
            int x = x0 + segment * (SEGMENT_W + SEGMENT_GAP);
            graphics.fill(x, y0, x + SEGMENT_W, y0 + PACE_H, TRACK_COLOR);
            var lit = Math.clamp(pace - segment, 0, 1);
            if (lit <= 0)
                continue;
            int color;
            if (segment < cold)
                color = COLD_SEGMENT;
            else if (segment < hot)
                color = ARGB.srgbLerp((float) ((segment + 1 - cold) / (hot - cold)), COLD_SEGMENT, HOT_SEGMENT);
            else
                color = FIERY_COLOR;
            if (!live)
                color = ARGB.srgbLerp(0.55F, color, TRACK_COLOR);
            graphics.fill(x, y0, x + (int) Math.max(1, Math.round(SEGMENT_W * lit)), y0 + PACE_H, color);
        }
        for (var notch : new double[] { cold, hot })
        {
            int x = x0 + (int) notch * (SEGMENT_W + SEGMENT_GAP) - SEGMENT_GAP;
            graphics.fill(x, y0 + PACE_H + 1, x + SEGMENT_GAP, y0 + PACE_H + 3, NOTCH_COLOR);
        }
    }

    private boolean overPace(int mouseX, int mouseY)
    {
        return mouseX >= leftPos + PACE_X && mouseX < leftPos + PACE_X + PACE_W && mouseY >= topPos + PACE_Y - 2 && mouseY < topPos + PACE_Y + PACE_H + 3;
    }

    private int gaugeX0()
    {
        return leftPos + GAUGE_X0;
    }

    private int gaugeX1()
    {
        return leftPos + GAUGE_X1;
    }

    private boolean overBar(int mouseX, int mouseY, int barY)
    {
        return mouseX >= gaugeX0() && mouseX < gaugeX1() && mouseY >= topPos + barY - 2 && mouseY < topPos + barY + 4;
    }

    private void extractWhisper(GuiGraphicsExtractor graphics)
    {
        var input = menu.slots.getFirst().getItem();
        var power = menu.power();
        String key;
        int color = MUTED_COLOR;
        if (input.isEmpty())
            key = "empty";
        else if (!menu.cooking())
        {
            key = "stuck";
            color = LACK_COLOR;
        }
        else if (power == MachinePower.UNPOWERED)
        {
            key = "unpowered";
            color = LACK_COLOR;
        }
        else if (power == MachinePower.WEAK)
            key = "weak";
        else if (menu.fast())
        {
            key = "fiery";
            color = FIERY_COLOR;
        }
        else
            key = "void";

        var text = Component.translatable(MODID + ".void_furnace.whisper." + key);
        var line = font.plainSubstrByWidth(text.getString(), PANEL_W - 2 * PAD);
        graphics.text(font, line, leftPos + (PANEL_W - font.width(line)) / 2, topPos + WHISPER_Y, color, false);
    }
}
