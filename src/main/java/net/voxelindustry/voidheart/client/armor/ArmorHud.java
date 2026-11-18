package net.voxelindustry.voidheart.client.armor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.LoadShedding;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;

import java.util.ArrayList;

/**
 * The Void Armor's hotbar, in place of the player's while they wear it. Its racks are dark plates on the slots of the first four keys, with the selected
 * one rimmed in gold. Its sights follow on the next keys, rimmed teal while they can be switched on, gold while on and rose once the frame has cut them.
 * Keys with no sight fitted yet are bare sockets. Its charge is a row of rubrum cells beside the bar, one for the frame and one for each battery,
 * emptying in turn and flickering once the frame runs low.
 * No numbers anywhere.
 * <p>
 * Per frame: up to nine plates of a few fills each, the racks' items and sights' icons, and up to a dozen cells. It takes the vanilla hotbar's footprint,
 * so the rows above it stay where they are.
 */
public final class ArmorHud
{
    private static final int SLOT_STEP = 20;
    private static final int PLATE     = 20;
    private static final int BAR_W     = 182;
    private static final int BAR_H     = 22;

    private static final int CELL_W    = 5;
    private static final int CELL_H    = 11;
    private static final int CELL_GAP  = 2;
    private static final int CELLS_GAP = 6;

    private static final int PLATE_BACK    = 0xE00B0810;
    private static final int PLATE_LINE    = 0xFF2A2038;
    private static final int PLATE_TOP     = 0xFF3A2E4A;
    private static final int SELECTED_RIM  = 0xFFC99A45;
    private static final int SELECTED_BACK = 0xE81A1424;
    private static final int KEY_COLOR     = 0x80A899BC;
    private static final int READY_RIM     = 0xFF36C5B0;
    private static final int ON_RIM        = 0xFFE8C46A;
    private static final int SHED_RIM      = 0xFFD4415E;
    private static final int SHED_VEIL     = 0xB00B0810;
    /** Keys after the racks with no sight fitted to them yet show as bare sockets, room the frame has to grow into. */
    private static final int SOCKET_BACK   = 0x700B0810;
    private static final int SOCKET_LINE   = 0x902A2038;

    private static final int   CELL_EMPTY   = 0xFF1A0E16;
    private static final int   CELL_FULL    = 0xFFD4415E;
    private static final int   CELL_RIM     = 0xFF4A1E2C;
    private static final int   CELL_DEAD    = 0xFF6E2234;
    private static final float FLICKER_LOW  = 0.35F;
    private static final float FLICKER_RATE = 0.018F;

    private ArmorHud()
    {
    }

    /**
     * Wraps the vanilla hotbar, drawing the frame's in its place while it is worn.
     */
    public static HudElement replacing(HudElement hotbar)
    {
        return (graphics, delta) ->
        {
            var player = Minecraft.getInstance().player;
            var worn = player == null || player.isSpectator() ? null : ArmorWear.worn(player);
            if (worn == null)
                hotbar.extractRenderState(graphics, delta);
            else
                extract(graphics, delta, worn);
        };
    }

    private static void extract(GuiGraphicsExtractor graphics, DeltaTracker delta, WornArmor worn)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        int left = graphics.guiWidth() / 2 - BAR_W / 2;
        int top = graphics.guiHeight() - BAR_H;
        var inventory = player.getInventory();

        for (int i = 0; i < ArmorHotbar.RACKS; i++)
        {
            int x = left + i * SLOT_STEP + 1;
            boolean selected = inventory.getSelectedSlot() == i;
            plate(graphics, x, top + 1, selected ? SELECTED_RIM : PLATE_LINE, selected ? SELECTED_BACK : PLATE_BACK);
            var stack = inventory.getItem(i);
            if (stack.isEmpty())
            {
                var key = String.valueOf(i + 1);
                graphics.text(minecraft.font, key, x + (PLATE - minecraft.font.width(key)) / 2, top + 7, KEY_COLOR, false);
            }
            else
            {
                graphics.item(player, stack, x + 2, top + 3, i + 1);
                graphics.itemDecorations(minecraft.font, stack, x + 2, top + 3);
            }
        }

        var stage = ArmorWear.stage(player);
        var capabilities = ArmorHotbar.capabilities(worn.frame());
        for (int j = 0; ArmorHotbar.RACKS + j < 9; j++)
        {
            int x = left + (ArmorHotbar.RACKS + j) * SLOT_STEP + 1;
            if (j >= capabilities.size())
            {
                plate(graphics, x, top + 1, SOCKET_LINE, SOCKET_BACK);
                continue;
            }
            var module = capabilities.get(j);
            boolean shed = !stage.keeps(module);
            int rim = shed ? SHED_RIM : worn.on().contains(module) ? ON_RIM : READY_RIM;
            plate(graphics, x, top + 1, rim, PLATE_BACK);
            graphics.item(new ItemStack(VoidArmorContent.MODULES.get(module)), x + 2, top + 3);
            if (shed)
                graphics.fill(x + 2, top + 3, x + PLATE - 2, top + PLATE, SHED_VEIL);
        }

        cells(graphics, left + BAR_W + CELLS_GAP, top + (BAR_H - CELL_H) / 2, worn, stage);
    }

    private static void plate(GuiGraphicsExtractor graphics, int x, int y, int rim, int back)
    {
        graphics.fill(x, y, x + PLATE - 1, y + PLATE, rim);
        graphics.fill(x + 1, y + 1, x + PLATE - 2, y + PLATE - 1, back);
        if (rim != SOCKET_LINE)
            graphics.fill(x + 1, y + 1, x + PLATE - 2, y + 2, rim == PLATE_LINE ? PLATE_TOP : ARGB.multiplyAlpha(rim, 0.6F));
    }

    /**
     * One rubrum cell for the frame's own battery, then one for each battery cell, filled in that order from the charge left, as on the base's screen.
     */
    private static void cells(GuiGraphicsExtractor graphics, int x, int y, WornArmor worn, LoadShedding.Stage stage)
    {
        var stats = VoidArmorItem.stats(worn.frame());
        var shares = new ArrayList<Long>();
        shares.add(ArmorBalance.FRAME_CAPACITY);
        for (int i = 0; i < stats.batteries(); i++)
            shares.add(ArmorBalance.BATTERY_CAPACITY);

        float flicker = stage.low() ? Mth.lerp(0.5F + 0.5F * Mth.sin(Util.getMillis() * FLICKER_RATE), FLICKER_LOW, 1) : 1;
        long left = ArmorWear.charge(Minecraft.getInstance().player);
        for (long share : shares)
        {
            float fill = Math.clamp(left / (float) share, 0, 1);
            left = Math.max(0, left - share);
            graphics.fill(x, y, x + CELL_W, y + CELL_H, stage.limp() ? CELL_DEAD : CELL_RIM);
            graphics.fill(x + 1, y + 1, x + CELL_W - 1, y + CELL_H - 1, CELL_EMPTY);
            int height = Math.round((CELL_H - 2) * fill);
            if (height > 0)
                graphics.fill(x + 1, y + CELL_H - 1 - height, x + CELL_W - 1, y + CELL_H - 1, ARGB.multiplyAlpha(CELL_FULL, flicker));
            x += CELL_W + CELL_GAP;
        }
    }
}
