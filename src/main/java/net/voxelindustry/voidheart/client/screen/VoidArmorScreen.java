package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.Bounds;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGridKind;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorModule;
import net.voxelindustry.voidheart.common.content.armor.ArmorModuleItem;
import net.voxelindustry.voidheart.common.content.armor.ArmorStats;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorMenu;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorLayout;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.KNOWN_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.LACK_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.MUTED_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.RULE_COLOR;
import static net.voxelindustry.voidheart.common.content.armor.VoidArmorLayout.*;

/**
 * The docked frame's grids over the player's inventory, cut in a slab of the knowledge repository's voidstone like the apparatus grid: the helm, the core
 * and the limbs each in a torn hole lit from below, the apparatus socket at the core's heart, the racks under the limbs and the charge under the helm.
 * What the frame gives is on a tablet beside the inventory, and one whisper says what a click does.
 * <p>
 * A module is carried onto a grid like onto any slot, clicked there or dragged there from its slot, its shape following the cursor and turned with R, a
 * right click or the wheel. A piece clicked comes back onto the cursor, or straight into the inventory with shift held. Each edit runs on the client at once
 * and on the server through a menu button.
 * <p>
 * Per frame: one blit for the baked slab, 41 slot plates, 49 cells, up to 15 pieces, ten charge pips and a few lines of text. The slab is baked once per
 * resize.
 */
public class VoidArmorScreen extends AbstractContainerScreen<VoidArmorMenu>
{
    private static final int TITLE_Y = 8;
    private static final int LINE    = 11;
    private static final int TEAR    = 3;

    private static final int PIP_W      = 7;
    private static final int PIP_H      = 11;
    private static final int PIP_GAP    = 3;
    private static final int PIPS_ROW   = 6;

    private static final int   PLATE_COLOR    = 0xFF0B0810;
    private static final int   CELL_BACK      = 0xB00B0810;
    private static final int   CELL_LINE      = 0xFF2A2038;
    private static final int   SLOT_BACK      = 0xF40B0810;
    private static final int   OUTLINE_COLOR  = 0xFF040208;
    private static final int   SOCKET_RIM     = 0xFF8C6A2E;
    private static final int   SOCKET_BACK    = 0xFF050308;
    private static final int   PIP_EMPTY      = 0xFF1A0E16;
    private static final int   PIP_FULL       = 0xFFD4415E;
    private static final int   PIP_RIM        = 0xFF4A1E2C;
    private static final int   RACK_KEY_COLOR = 0x80A899BC;
    private static final float PIECE_TINT     = 0.3F;
    private static final float GHOST_TINT     = 0.45F;
    private static final int   GHOST_ALPHA    = 0xC0;
    private static final int   FITS_COLOR     = 0xFF7CE8D6;
    private static final int   VALUE_COLOR    = 0xFFEDE6FF;

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(MODID, "dynamic/void_armor_panel");

    private int rotation;
    private @Nullable ArmorModule rotationFor;
    /** The grid and cell a piece was taken from by the press still held, so letting go over another cell sets it there. */
    private @Nullable Cell pressedCell;
    /** Whether the press still held picked a stack up from a slot, so letting go over a grid sets one there. */
    private boolean pressedOnSlot;

    private record Cell(ArmorGridKind kind, int x, int y)
    {
    }

    public VoidArmorScreen(VoidArmorMenu menu, Inventory inventory, Component title)
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

    private Bounds tablet()
    {
        return new Bounds(leftPos + TABLET_X, topPos + TABLET_Y, TABLET_W, TABLET_H);
    }

    // The slab, baked once: voidstone with a lip around its edge, and a torn hole for each grid, lit from within like the research map

    private void bakePanel()
    {
        var stone = ResearchVoid.readStone(minecraft);
        var image = new NativeImage(PANEL_W, PANEL_H, false);
        long seed = minecraft.player == null ? 0 : minecraft.player.getUUID().getLeastSignificantBits();
        var lights = new ArrayList<ResearchVoid.Light>();
        for (var kind : ArmorGridKind.values())
            lights.add(new ResearchVoid.Light(wellX(kind) + wellW(kind) / 2D, WELL_Y + wellH(kind) / 2D, wellW(kind) * 0.7, 0.9, ResearchVoid.LIT_RGB));
        for (int y = 0; y < PANEL_H; y++)
            for (int x = 0; x < PANEL_W; x++)
            {
                int hole = Integer.MIN_VALUE;
                for (var kind : ArmorGridKind.values())
                    hole = Math.max(hole, holeDepth(kind, x, y, seed + kind.ordinal() * 131L));
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

    /**
     * How far inside a grid's torn hole a pixel of the slab is, or minus how far outside it.
     */
    private static int holeDepth(ArmorGridKind kind, int x, int y, long seed)
    {
        int left = wellX(kind) + tear(y, seed);
        int right = wellX(kind) + wellW(kind) - tear(y, seed + 91);
        int top = WELL_Y + tear(x, seed + 17);
        int bottom = WELL_Y + wellH(kind) - tear(x, seed + 29);
        int inX = Math.min(x - left, right - 1 - x);
        int inY = Math.min(y - top, bottom - 1 - y);
        if (inX >= 0 && inY >= 0)
            return Math.min(inX, inY);
        return -Math.max(Math.max(-inX, 0), Math.max(-inY, 0));
    }

    private static int tear(int along, long seed)
    {
        return Math.round(ResearchVoid.smooth(along * 0.13F, 3.1F, seed) * TEAR);
    }

    // Input

    private static @Nullable ArmorModule module(ItemStack stack)
    {
        return stack.getItem() instanceof ArmorModuleItem item ? item.module() : null;
    }

    private @Nullable ArmorModule held()
    {
        var held = module(menu.getCarried());
        if (held != rotationFor)
        {
            rotationFor = held;
            rotation = 0;
        }
        return held;
    }

    private int screenGridX(ArmorGridKind kind)
    {
        return leftPos + gridX(kind);
    }

    private int screenGridY(ArmorGridKind kind)
    {
        return topPos + gridY(kind);
    }

    /**
     * The grid cell under the cursor, or null off the grids and on the apparatus socket, which is a slot.
     */
    private @Nullable Cell cellAt(double mouseX, double mouseY)
    {
        for (var kind : ArmorGridKind.values())
        {
            int x = (int) Math.floor((mouseX - screenGridX(kind)) / CELL);
            int y = (int) Math.floor((mouseY - screenGridY(kind)) / CELL);
            if (mouseX >= screenGridX(kind) && mouseY >= screenGridY(kind) && kind.contains(x, y))
                return kind.isSocket(x, y) ? null : new Cell(kind, x, y);
        }
        return null;
    }

    private ComponentShape shape(ArmorModule held)
    {
        return held.shape().rotated(rotation);
    }

    /**
     * Where the held piece's top left corner goes for the cell under the cursor, which it covers near its middle.
     */
    private int originX(ArmorModule held, Cell cell)
    {
        return cell.x() - (shape(held).width() - 1) / 2;
    }

    private int originY(ArmorModule held, Cell cell)
    {
        return cell.y() - (shape(held).height() - 1) / 2;
    }

    /**
     * Runs a grid edit at once on the client and asks the server for the same.
     */
    private void press(int button)
    {
        if (menu.clickMenuButton(minecraft.player, button))
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void turn(int quarters)
    {
        if (held() != null)
            rotation = (rotation + quarters) & 3;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        pressedCell = null;
        pressedOnSlot = false;
        var cell = cellAt(event.x(), event.y());
        if (cell == null)
        {
            var slot = hoveredSlot;
            pressedOnSlot = event.button() == InputConstants.MOUSE_BUTTON_LEFT && slot != null && slot.hasItem() && menu.getCarried().isEmpty();
            return super.mouseClicked(event, doubleClick);
        }

        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
        {
            turn(1);
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
            return true;

        var held = held();
        if (held != null)
            press(VoidArmorMenu.placeButton(cell.kind(), originX(held, cell), originY(held, cell), rotation));
        else if (menu.getCarried().isEmpty() && menu.grids().at(cell.kind(), cell.x(), cell.y()) != null)
        {
            press(VoidArmorMenu.takeButton(cell.kind(), cell.x(), cell.y(), event.hasShiftDown()));
            if (!event.hasShiftDown())
                pressedCell = cell;
        }
        // No slot lies under a grid cell, so this only resets vanilla's drag, letting go over a slot then drop what was taken there
        super.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        var fromCell = pressedCell;
        boolean fromSlot = pressedOnSlot;
        pressedCell = null;
        pressedOnSlot = false;

        var cell = cellAt(event.x(), event.y());
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && cell != null)
        {
            var held = held();
            boolean moved = fromCell != null && !fromCell.equals(cell);
            if (held != null && (moved || fromSlot))
                press(VoidArmorMenu.placeButton(cell.kind(), originX(held, cell), originY(held, cell), rotation));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (cellAt(mouseX, mouseY) != null && held() != null && scrollY != 0)
        {
            turn(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (event.key() == InputConstants.KEY_R && held() != null)
        {
            turn(1);
            return true;
        }
        return super.keyPressed(event);
    }

    // Drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        for (var slot : menu.slots)
        {
            if (slot.index == VoidArmorMenu.SOCKET)
                continue;
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + SLOT, y + SLOT, SLOT_BACK);
            graphics.outline(x, y, SLOT, SLOT, CELL_LINE);
        }
        ResearchScreen.tablet(graphics, tablet());

        for (var kind : ArmorGridKind.values())
        {
            int gridX = screenGridX(kind);
            int gridY = screenGridY(kind);
            for (int y = 0; y < kind.height(); y++)
                for (int x = 0; x < kind.width(); x++)
                {
                    int cellX = gridX + x * CELL;
                    int cellY = gridY + y * CELL;
                    if (kind.isSocket(x, y))
                    {
                        graphics.fill(cellX, cellY, cellX + CELL, cellY + CELL, SOCKET_RIM);
                        graphics.fill(cellX + 2, cellY + 2, cellX + CELL - 2, cellY + CELL - 2, SOCKET_BACK);
                        continue;
                    }
                    graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1, cellY + CELL - 1, CELL_BACK);
                    graphics.outline(cellX + 1, cellY + 1, CELL - 2, CELL - 2, CELL_LINE);
                }
        }

        for (int rack = 0; rack < ArmorBalance.RACKS; rack++)
            if (!menu.slots.get(VoidArmorMenu.FIRST_RACK + rack).hasItem())
            {
                var key = String.valueOf(rack + 1);
                graphics.text(font, key, leftPos + RACK_X + rack * SLOT + (16 - font.width(key)) / 2 + 1, topPos + RACK_Y + 4, RACK_KEY_COLOR, false);
            }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym)
    {
        graphics.text(font, font.plainSubstrByWidth(title.getString(), PANEL_W - 2 * PAD), PAD, TITLE_Y, KNOWN_COLOR, false);
        for (var kind : ArmorGridKind.values())
        {
            var label = Component.translatable(MODID + ".void_armor_screen.grid." + kind.key()).getString();
            graphics.text(font, label, wellX(kind) + (wellW(kind) - font.width(label)) / 2, LABEL_Y, MUTED_COLOR, false);
        }
        var charge = Component.translatable(MODID + ".void_armor_screen.charge").getString();
        graphics.text(font, charge, HELM_X + (wellW(ArmorGridKind.HELM) - font.width(charge)) / 2, CHARGE_LABEL_Y, MUTED_COLOR, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        var grids = menu.grids();
        var stats = grids.stats();
        extractPieces(graphics, grids, mouseX, mouseY);
        extractCharge(graphics, stats);
        extractStats(graphics, stats);
        extractWhisper(graphics, grids, mouseX, mouseY);
        extractHovered(graphics, grids, mouseX, mouseY);
    }

    /**
     * The held module follows the cursor as its shape over a grid, so its stack is not drawn on top of it there.
     */
    @Override
    public void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (held() == null || cellAt(mouseX, mouseY) == null)
            super.extractCarriedItem(graphics, mouseX, mouseY);
    }

    private void extractPieces(GuiGraphicsExtractor graphics, ArmorGrids grids, int mouseX, int mouseY)
    {
        var valid = grids.valid();
        for (var kind : ArmorGridKind.values())
            for (var placement : grids.placements(kind))
            {
                int rgb = 0xFF000000 | placement.module().rgb();
                extractPiece(graphics, kind, placement.shape(), placement.x(), placement.y(), ARGB.srgbLerp(PIECE_TINT, PLATE_COLOR, rgb),
                        valid.get(kind).contains(placement) ? rgb : LACK_COLOR, stackOf(placement.module()));
            }

        var cell = cellAt(mouseX, mouseY);
        if (cell == null)
            return;
        var held = held();
        if (held != null)
        {
            int x = originX(held, cell);
            int y = originY(held, cell);
            boolean fits = grids.canPlace(cell.kind(), held, x, y, rotation);
            int fill = ARGB.color(GHOST_ALPHA, ARGB.srgbLerp(GHOST_TINT, PLATE_COLOR, 0xFF000000 | held.rgb()));
            extractPiece(graphics, cell.kind(), shape(held), x, y, fill, fits ? FITS_COLOR : LACK_COLOR, stackOf(held));
        }
        else if (menu.getCarried().isEmpty())
        {
            var placement = grids.at(cell.kind(), cell.x(), cell.y());
            if (placement != null)
                extractOutline(graphics, cell.kind(), placement.shape(), placement.x(), placement.y(), FITS_COLOR);
        }
    }

    /**
     * Fills the cells of a piece as one slab, ruling only its outer edges, with its item on the cell nearest its middle. Cells off the grid are left out,
     * so a ghost hanging over the edge shows only where it would land.
     */
    private void extractPiece(GuiGraphicsExtractor graphics, ArmorGridKind kind, ComponentShape shape, int originX, int originY, int fill, int edge, ItemStack icon)
    {
        int gridX = screenGridX(kind);
        int gridY = screenGridY(kind);
        for (var cell : shape.cells())
        {
            int x = gridX + (originX + cell.x()) * CELL;
            int y = gridY + (originY + cell.y()) * CELL;
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, fill);
            if (ApparatusScreen.has(shape, cell.x() + 1, cell.y()))
                graphics.fill(x + CELL - 1, y + 1, x + CELL + 1, y + CELL - 1, fill);
            if (ApparatusScreen.has(shape, cell.x(), cell.y() + 1))
                graphics.fill(x + 1, y + CELL - 1, x + CELL - 1, y + CELL + 1, fill);
            if (ApparatusScreen.has(shape, cell.x() + 1, cell.y()) && ApparatusScreen.has(shape, cell.x(), cell.y() + 1)
                    && ApparatusScreen.has(shape, cell.x() + 1, cell.y() + 1))
                graphics.fill(x + CELL - 1, y + CELL - 1, x + CELL + 1, y + CELL + 1, fill);
        }
        extractOutline(graphics, kind, shape, originX, originY, edge);

        var middle = ApparatusScreen.middle(shape);
        graphics.item(icon, gridX + (originX + middle.x()) * CELL + (CELL - 16) / 2, gridY + (originY + middle.y()) * CELL + (CELL - 16) / 2);
    }

    private void extractOutline(GuiGraphicsExtractor graphics, ArmorGridKind kind, ComponentShape shape, int originX, int originY, int color)
    {
        int gridX = screenGridX(kind);
        int gridY = screenGridY(kind);
        for (var cell : shape.cells())
        {
            int x = gridX + (originX + cell.x()) * CELL;
            int y = gridY + (originY + cell.y()) * CELL;
            if (!ApparatusScreen.has(shape, cell.x(), cell.y() - 1))
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + 2, color);
            if (!ApparatusScreen.has(shape, cell.x(), cell.y() + 1))
                graphics.fill(x + 1, y + CELL - 2, x + CELL - 1, y + CELL - 1, color);
            if (!ApparatusScreen.has(shape, cell.x() - 1, cell.y()))
                graphics.fill(x + 1, y + 1, x + 2, y + CELL - 1, color);
            if (!ApparatusScreen.has(shape, cell.x() + 1, cell.y()))
                graphics.fill(x + CELL - 2, y + 1, x + CELL - 1, y + CELL - 1, color);
        }
    }

    private static ItemStack stackOf(ArmorModule module)
    {
        return new ItemStack(VoidArmorContent.MODULES.get(module));
    }

    /**
     * One rubrum pip for the frame's own battery and one for each working battery cell, filled in that order from the charge held. No number: the pips
     * are what the hotbar will show while worn.
     */
    private void extractCharge(GuiGraphicsExtractor graphics, ArmorStats stats)
    {
        var shares = new ArrayList<Long>();
        shares.add(ArmorBalance.FRAME_CAPACITY);
        for (int i = 0; i < stats.batteries(); i++)
            shares.add(ArmorBalance.BATTERY_CAPACITY);

        long left = menu.charge();
        int perRow = Math.min(PIPS_ROW, shares.size());
        int rowWidth = perRow * PIP_W + (perRow - 1) * PIP_GAP;
        int startX = leftPos + HELM_X + (wellW(ArmorGridKind.HELM) - rowWidth) / 2;
        for (int i = 0; i < shares.size(); i++)
        {
            int x = startX + i % PIPS_ROW * (PIP_W + PIP_GAP);
            int y = topPos + CHARGE_Y + i / PIPS_ROW * (PIP_H + PIP_GAP);
            long share = shares.get(i);
            float fill = Math.clamp(left / (float) share, 0, 1);
            left = Math.max(0, left - share);

            graphics.fill(x, y, x + PIP_W, y + PIP_H, PIP_RIM);
            graphics.fill(x + 1, y + 1, x + PIP_W - 1, y + PIP_H - 1, PIP_EMPTY);
            int height = Math.round((PIP_H - 2) * fill);
            if (height > 0)
                graphics.fill(x + 1, y + PIP_H - 1 - height, x + PIP_W - 1, y + PIP_H - 1, PIP_FULL);
        }
    }

    /**
     * What the frame gives as short rows, a name on the left and its value on the right; the full wording waits in the modules' tooltips.
     */
    private void extractStats(GuiGraphicsExtractor graphics, ArmorStats stats)
    {
        var tablet = tablet();
        int y = tablet.y() + 7;
        y = row(graphics, tablet, y, "sortie", Component.translatable(MODID + ".void_armor_screen.minutes", number(stats.sortieMinutes())).getString(), VALUE_COLOR);
        y = row(graphics, tablet, y, "speed", "+" + Math.round(stats.speedBonus() * 100) + "%", VALUE_COLOR);
        y = row(graphics, tablet, y, "jump", Component.translatable(MODID + ".void_armor_screen.blocks", number(stats.jumpBlocks())).getString(), VALUE_COLOR);
        y = row(graphics, tablet, y, "reach", "+" + number(ArmorBalance.ENTITY_REACH), VALUE_COLOR);
        y = row(graphics, tablet, y, "hit", stats.hitBonus() > 0 ? "+" + number(stats.hitBonus()) : "-", stats.hitBonus() > 0 ? VALUE_COLOR : MUTED_COLOR);
        String sight = stats.spectralLens() ? "spectral" : stats.nightVision() ? "night" : "none";
        row(graphics, tablet, y, "sight", Component.translatable(MODID + ".void_armor_screen.sight." + sight).getString(),
                0xFF000000 | (stats.spectralLens() ? ArmorModule.SPECTRAL_LENS.rgb() : stats.nightVision() ? ArmorModule.NIGHT_VISION.rgb() : 0xA899BC));
    }

    private static String number(float value)
    {
        return value == Math.round(value) ? String.valueOf(Math.round(value)) : String.format(Locale.ROOT, "%.1f", value);
    }

    private int row(GuiGraphicsExtractor graphics, Bounds tablet, int y, String name, String value, int color)
    {
        if (y + LINE > tablet.y() + tablet.height())
            return y;
        graphics.text(font, Component.translatable(MODID + ".void_armor_screen.stat." + name), tablet.x() + 7, y, MUTED_COLOR, false);
        graphics.text(font, value, tablet.x() + tablet.width() - 7 - font.width(value), y, color, false);
        return y + LINE;
    }

    private void extractWhisper(GuiGraphicsExtractor graphics, ArmorGrids grids, int mouseX, int mouseY)
    {
        var held = held();
        var cell = cellAt(mouseX, mouseY);
        Component text;
        int color = MUTED_COLOR;
        if (held != null && cell != null)
        {
            var refusal = grids.refusal(cell.kind(), held, originX(held, cell), originY(held, cell), rotation);
            if (refusal != null)
            {
                text = refusal == ArmorGrids.Refusal.WRONG_GRID
                       ? Component.translatable(MODID + ".void_armor_screen.refusal.wrong_grid",
                        Component.translatable(MODID + ".void_armor_screen.grid." + held.grids().iterator().next().key()))
                       : Component.translatable(MODID + ".void_armor_screen.refusal." + refusal.name().toLowerCase(Locale.ROOT));
                color = LACK_COLOR;
            }
            else
                text = Component.translatable(MODID + ".void_armor_screen.whisper.set");
        }
        else if (held != null)
            text = Component.translatable(MODID + ".void_armor_screen.whisper.turn");
        else if (cell != null && grids.at(cell.kind(), cell.x(), cell.y()) != null)
            text = Component.translatable(MODID + ".void_armor_screen.whisper.take");
        else if (cell != null)
            text = Component.translatable(MODID + ".void_armor_screen.whisper.grid." + cell.kind().key());
        else if (hoveredSlot != null && hoveredSlot.index == VoidArmorMenu.SOCKET)
            text = Component.translatable(MODID + ".void_armor_screen.whisper.socket");
        else if (hoveredSlot != null && hoveredSlot.index >= VoidArmorMenu.FIRST_RACK)
            text = Component.translatable(MODID + ".void_armor_screen.whisper.rack");
        else
            text = Component.translatable(MODID + ".void_armor_screen.whisper.pick");

        var line = font.plainSubstrByWidth(text.getString(), PANEL_W - 2 * PAD);
        graphics.text(font, line, leftPos + (PANEL_W - font.width(line)) / 2, topPos + WHISPER_Y, color, false);
    }

    private void extractHovered(GuiGraphicsExtractor graphics, ArmorGrids grids, int mouseX, int mouseY)
    {
        var cell = cellAt(mouseX, mouseY);
        if (!menu.getCarried().isEmpty() || cell == null)
            return;
        var placement = grids.at(cell.kind(), cell.x(), cell.y());
        if (placement != null)
            graphics.setTooltipForNextFrame(font, stackOf(placement.module()), mouseX, mouseY);
    }

    /**
     * The middle of a grid cell on screen, for tests to aim at.
     */
    public int[] cellCenter(ArmorGridKind kind, int cellX, int cellY)
    {
        return new int[] { screenGridX(kind) + cellX * CELL + CELL / 2, screenGridY(kind) + cellY * CELL + CELL / 2 };
    }

    /**
     * The middle of a menu slot on screen, for tests to aim at.
     */
    public int[] slotCenter(int menuSlot)
    {
        var slot = menu.slots.get(menuSlot);
        return new int[] { leftPos + slot.x + 8, topPos + slot.y + 8 };
    }
}
