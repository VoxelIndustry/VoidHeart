package net.voxelindustry.voidheart.client.armor;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.armor.ArmorHotbar;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorItem;
import net.voxelindustry.voidheart.common.content.armor.WornArmor;


import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The Void Armor's hotbar, in place of the player's while they wear it. Its racks are dark plates on the slots of the first four keys, with the selected
 * one rimmed in gold. Its sights follow on the next keys, rimmed teal while they can be switched on, gold while on and rose once the frame has cut them.
 * Keys with no sight fitted yet are bare sockets.
 * <p>
 * Its charge is a row of rubrum cell icons over the hunger bar, the way armor and the apparatus's shield sit over health: ten cells for the whole charge,
 * in halves, emptying from the left. They flicker once the frame runs low, and the empty sockets turn a dead red when it goes limp. No numbers anywhere.
 * <p>
 * Per frame: up to nine plates of a few fills each, the racks' items and sights' icons, and up to twenty 9x9 sprites for the charge. The hotbar takes the
 * vanilla hotbar's footprint, so the rows above it stay where they are.
 */
public final class ArmorHud
{
    private static final int SLOT_STEP = 20;
    private static final int PLATE     = 20;
    private static final int BAR_W     = 182;
    private static final int BAR_H     = 22;

    private static final Identifier CHARGE_FULL  = Identifier.fromNamespaceAndPath(MODID, "hud/armor_charge_full");
    private static final Identifier CHARGE_HALF  = Identifier.fromNamespaceAndPath(MODID, "hud/armor_charge_half");
    private static final Identifier CHARGE_EMPTY = Identifier.fromNamespaceAndPath(MODID, "hud/armor_charge_empty");
    private static final int   CHARGE_ICONS     = 10;
    private static final int   ICON_SIZE        = 9;
    private static final int   ICON_STEP        = 8;
    /** The hunger row's place from the bottom, as vanilla works it out, and the row above it. */
    private static final int   FOOD_FROM_BOTTOM = 39;
    private static final int   ROW_ABOVE        = 10;
    private static final float FULL_ALPHA       = 0.9F;
    private static final float EMPTY_ALPHA      = 0.55F;

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

    private static final int   CELL_FULL    = 0xD4415E;
    private static final int   CELL_SOCKET  = 0x8A5A68;
    private static final int   CELL_DEAD    = 0x6E2234;
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

    }

    /** The menpō's edge in first person: dark wedges at the corners of the view, faint so they frame it without closing it in. */
    private static final float MASK_CORNER = 0.16F;
    private static final int   MASK_SHADE  = 0x400B0810;
    private static final int   MASK_RIM    = 0x502A2038;

    /**
     * Frames a wearer's first-person view in the corners of the frame's mask.
     */
    public static void maskEdge(GuiGraphicsExtractor graphics, DeltaTracker delta)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || ArmorWear.worn(player) == null)
            return;
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int size = Math.round(Math.min(width, height) * MASK_CORNER);
        for (int i = 0; i < size; i++)
        {
            int length = size - i;
            graphics.fill(0, i, length, i + 1, MASK_SHADE);
            graphics.fill(width - length, i, width, i + 1, MASK_SHADE);
            graphics.fill(0, height - 1 - i, length, height - i, MASK_SHADE);
            graphics.fill(width - length, height - 1 - i, width, height - i, MASK_SHADE);
            graphics.fill(length - 1, i, length, i + 1, MASK_RIM);
            graphics.fill(width - length, i, width - length + 1, i + 1, MASK_RIM);
            graphics.fill(length - 1, height - 1 - i, length, height - i, MASK_RIM);
            graphics.fill(width - length, height - 1 - i, width - length + 1, height - i, MASK_RIM);
        }
    }

    private static void plate(GuiGraphicsExtractor graphics, int x, int y, int rim, int back)
    {
        graphics.fill(x, y, x + PLATE - 1, y + PLATE, rim);
        graphics.fill(x + 1, y + 1, x + PLATE - 2, y + PLATE - 1, back);
        if (rim != SOCKET_LINE)
            graphics.fill(x + 1, y + 1, x + PLATE - 2, y + 2, rim == PLATE_LINE ? PLATE_TOP : ARGB.multiplyAlpha(rim, 0.6F));
    }

    /**
     * The frame's charge over the hunger bar, a row above it when the air bubbles show there.
     */
    public static void charge(GuiGraphicsExtractor graphics, DeltaTracker delta)
    {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        var worn = player == null || player.isSpectator() ? null : ArmorWear.worn(player);
        if (worn == null || minecraft.gameMode == null || !minecraft.gameMode.canHurtPlayer())
            return;

        long capacity = VoidArmorItem.stats(worn.frame()).capacity();
        var stage = ArmorWear.stage(player);
        int halves = capacity <= 0 ? 0 : Mth.ceil(CHARGE_ICONS * 2 * Math.min(1, ArmorWear.charge(player) / (double) capacity));
        float flicker = stage.low() ? Mth.lerp(0.5F + 0.5F * Mth.sin(Util.getMillis() * FLICKER_RATE), FLICKER_LOW, 1) : 1;

        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - FOOD_FROM_BOTTOM - ROW_ABOVE;
        if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply())
            y -= ROW_ABOVE;
        int socket = stage.limp() ? CELL_DEAD : CELL_SOCKET;
        for (int icon = 0; icon < CHARGE_ICONS; icon++)
        {
            // Filled from the right, as the hunger row is, so the cells empty from the left
            int x = right - ICON_SIZE - icon * ICON_STEP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHARGE_EMPTY, x, y, ICON_SIZE, ICON_SIZE, ARGB.color(EMPTY_ALPHA, socket));
            int filled = halves - icon * 2;
            if (filled > 0)
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, filled >= 2 ? CHARGE_FULL : CHARGE_HALF, x, y, ICON_SIZE, ICON_SIZE,
                        ARGB.color(FULL_ALPHA * flicker, CELL_FULL));
        }
    }
}
