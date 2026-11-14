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
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponentItem;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusMenu;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldStats;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.KNOWN_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.LACK_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.MUTED_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.RULE_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.TEXT_COLOR;

/**
 * The grid of an apparatus over the player's inventory, cut in a small slab of the knowledge repository's voidstone. The grid sits in a torn hole lit from
 * below, what the apparatus gives is on a tablet beside it, and one whisper between them and the inventory says what a click does.
 * <p>
 * A component is carried onto the grid like onto any slot: clicked there, or dragged there from the slot it was picked up from. Its shape follows the cursor,
 * turned with R, a right click or the wheel. A piece clicked in the grid comes back onto the cursor, to be dropped in a slot or dragged to another cell, and
 * comes straight back into the inventory with shift held. Each edit runs on the client at once and on the server through a menu button.
 * <p>
 * Per frame: one blit for the baked slab, 36 slot plates, up to 25 cells and pieces, and a few lines of text. The slab is baked once per resize.
 */
public class ApparatusScreen extends AbstractContainerScreen<ApparatusMenu>
{
    private static final int PANEL_W   = 248;
    private static final int PANEL_H   = 228;
    private static final int PAD       = 10;
    private static final int TITLE_Y   = 8;
    private static final int WELL_X    = PAD;
    private static final int WELL_Y    = 20;
    private static final int WELL      = 108;
    private static final int WELL_RIM  = 4;
    private static final int CELL      = 20;
    private static final int TABLET_X  = WELL_X + WELL + 8;
    private static final int TABLET_W  = PANEL_W - TABLET_X - PAD;
    private static final int WHISPER_Y = WELL_Y + WELL + 4;
    private static final int LINE      = 11;
    private static final int TEAR      = 3;

    private static final int   PLATE_COLOR   = 0xFF0B0810;
    private static final int   CELL_BACK     = 0xB00B0810;
    private static final int   CELL_LINE     = 0xFF2A2038;
    private static final int   SLOT_BACK     = 0xF40B0810;
    private static final int   OUTLINE_COLOR = 0xFF040208;
    private static final float PIECE_TINT    = 0.3F;
    private static final float GHOST_TINT    = 0.45F;
    private static final int   GHOST_ALPHA   = 0xC0;
    private static final int   FITS_COLOR    = 0xFF7CE8D6;
    private static final int   WARD_COLOR    = 0xFFE8C46A;
    private static final int   CORE_COLOR    = 0xFFC9B2F2;

    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(MODID, "dynamic/apparatus_panel");

    private int rotation;
    private @Nullable ApparatusComponent rotationFor;
    /** The cell a piece was taken from by the press still held, so letting go over another cell sets it there. */
    private int @Nullable [] pressedCell;
    /** Whether the press still held picked a stack up from a slot, so letting go over the grid sets one there. */
    private boolean pressedOnSlot;

    public ApparatusScreen(ApparatusMenu menu, Inventory inventory, Component title)
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

    private int size()
    {
        return menu.size();
    }

    private int gridX()
    {
        return leftPos + WELL_X + WELL_RIM + (WELL - 2 * WELL_RIM - size() * CELL) / 2;
    }

    private int gridY()
    {
        return topPos + WELL_Y + WELL_RIM + (WELL - 2 * WELL_RIM - size() * CELL) / 2;
    }

    private Bounds tablet()
    {
        return new Bounds(leftPos + TABLET_X, topPos + WELL_Y, TABLET_W, WELL);
    }

    // The slab, baked once: voidstone with a lip around its edge, and a torn hole for the grid, lit from within like the research map

    private void bakePanel()
    {
        var stone = ResearchVoid.readStone(minecraft);
        var image = new NativeImage(PANEL_W, PANEL_H, false);
        long seed = minecraft.player == null ? 0 : minecraft.player.getUUID().getLeastSignificantBits();
        var lights = List.of(new ResearchVoid.Light(WELL_X + WELL / 2D, WELL_Y + WELL / 2D, WELL * 0.7, 0.9, ResearchVoid.LIT_RGB));
        for (int y = 0; y < PANEL_H; y++)
            for (int x = 0; x < PANEL_W; x++)
            {
                int hole = holeDepth(x, y, seed);
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
     * How far inside the torn hole a pixel of the slab is, or minus how far outside it.
     */
    private static int holeDepth(int x, int y, long seed)
    {
        int left = WELL_X + tear(y, seed);
        int right = WELL_X + WELL - tear(y, seed + 91);
        int top = WELL_Y + tear(x, seed + 17);
        int bottom = WELL_Y + WELL - tear(x, seed + 29);
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

    private static @Nullable ApparatusComponent component(ItemStack stack)
    {
        return stack.getItem() instanceof ApparatusComponentItem item ? item.component() : null;
    }

    private @Nullable ApparatusComponent held()
    {
        var held = component(menu.getCarried());
        if (held != rotationFor)
        {
            rotationFor = held;
            rotation = 0;
        }
        return held;
    }

    private int cellAtX(double mouseX)
    {
        return (int) Math.floor((mouseX - gridX()) / CELL);
    }

    private int cellAtY(double mouseY)
    {
        return (int) Math.floor((mouseY - gridY()) / CELL);
    }

    private boolean overGrid(double mouseX, double mouseY)
    {
        return mouseX >= gridX() && mouseY >= gridY() && cellAtX(mouseX) < size() && cellAtY(mouseY) < size();
    }

    private ComponentShape shape(ApparatusComponent held)
    {
        return held.shape().rotated(rotation);
    }

    /**
     * Where the held piece's top left corner goes for the cell under the cursor, which it covers near its middle.
     */
    private int originX(ApparatusComponent held, double mouseX)
    {
        return cellAtX(mouseX) - (shape(held).width() - 1) / 2;
    }

    private int originY(ApparatusComponent held, double mouseY)
    {
        return cellAtY(mouseY) - (shape(held).height() - 1) / 2;
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
        if (!overGrid(event.x(), event.y()))
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
        int cellX = cellAtX(event.x());
        int cellY = cellAtY(event.y());
        if (held != null)
            press(ApparatusMenu.placeButton(originX(held, event.x()), originY(held, event.y()), rotation));
        else if (menu.getCarried().isEmpty() && menu.grid().at(cellX, cellY) != null)
        {
            press(ApparatusMenu.takeButton(cellX, cellY, event.hasShiftDown()));
            if (!event.hasShiftDown())
                pressedCell = new int[] { cellX, cellY };
        }
        // No slot lies under the grid, so this only resets vanilla's drag, letting go over a slot then drop what was taken there
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

        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overGrid(event.x(), event.y()))
        {
            var held = held();
            boolean moved = fromCell != null && (fromCell[0] != cellAtX(event.x()) || fromCell[1] != cellAtY(event.y()));
            if (held != null && (moved || fromSlot))
                press(ApparatusMenu.placeButton(originX(held, event.x()), originY(held, event.y()), rotation));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (overGrid(mouseX, mouseY) && held() != null && scrollY != 0)
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
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + 18, y + 18, SLOT_BACK);
            graphics.outline(x, y, 18, 18, CELL_LINE);
        }
        ResearchScreen.tablet(graphics, tablet());

        int gridX = gridX();
        int gridY = gridY();
        for (int y = 0; y < size(); y++)
            for (int x = 0; x < size(); x++)
            {
                int cellX = gridX + x * CELL;
                int cellY = gridY + y * CELL;
                graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1, cellY + CELL - 1, CELL_BACK);
                graphics.outline(cellX + 1, cellY + 1, CELL - 2, CELL - 2, CELL_LINE);
            }
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
        var grid = menu.grid();
        extractPieces(graphics, grid, mouseX, mouseY);
        extractStats(graphics, grid);
        extractWhisper(graphics, grid, mouseX, mouseY);
        extractHovered(graphics, grid, mouseX, mouseY);
    }

    /**
     * The held component follows the cursor as its shape over the grid, so its stack is not drawn on top of it there.
     */
    @Override
    public void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (held() == null || !overGrid(mouseX, mouseY))
            super.extractCarriedItem(graphics, mouseX, mouseY);
    }

    private void extractPieces(GuiGraphicsExtractor graphics, ApparatusGrid grid, int mouseX, int mouseY)
    {
        var valid = grid.valid(size());
        for (var placement : grid.placements())
        {
            int rgb = placement.component().rgb();
            extractPiece(graphics, placement.shape(), placement.x(), placement.y(), ARGB.srgbLerp(PIECE_TINT, PLATE_COLOR, 0xFF000000 | rgb),
                    valid.contains(placement) ? 0xFF000000 | rgb : LACK_COLOR, stackOf(placement.component()));
        }

        if (!overGrid(mouseX, mouseY))
            return;
        var held = held();
        if (held != null)
        {
            boolean fits = grid.canPlace(size(), held, originX(held, mouseX), originY(held, mouseY), rotation);
            int fill = ARGB.color(GHOST_ALPHA, ARGB.srgbLerp(GHOST_TINT, PLATE_COLOR, 0xFF000000 | held.rgb()));
            extractPiece(graphics, shape(held), originX(held, mouseX), originY(held, mouseY), fill, fits ? FITS_COLOR : LACK_COLOR, stackOf(held));
        }
        else if (menu.getCarried().isEmpty())
        {
            var placement = grid.at(cellAtX(mouseX), cellAtY(mouseY));
            if (placement != null)
                extractOutline(graphics, placement.shape(), placement.x(), placement.y(), FITS_COLOR);
        }
    }

    /**
     * Fills the cells of a piece as one slab, ruling only its outer edges, with its item on the cell nearest its middle.
     */
    private void extractPiece(GuiGraphicsExtractor graphics, ComponentShape shape, int originX, int originY, int fill, int edge, ItemStack icon)
    {
        int gridX = gridX();
        int gridY = gridY();
        for (var cell : shape.cells())
        {
            int x = gridX + (originX + cell.x()) * CELL;
            int y = gridY + (originY + cell.y()) * CELL;
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, fill);
            if (has(shape, cell.x() + 1, cell.y()))
                graphics.fill(x + CELL - 1, y + 1, x + CELL + 1, y + CELL - 1, fill);
            if (has(shape, cell.x(), cell.y() + 1))
                graphics.fill(x + 1, y + CELL - 1, x + CELL - 1, y + CELL + 1, fill);
            if (has(shape, cell.x() + 1, cell.y()) && has(shape, cell.x(), cell.y() + 1) && has(shape, cell.x() + 1, cell.y() + 1))
                graphics.fill(x + CELL - 1, y + CELL - 1, x + CELL + 1, y + CELL + 1, fill);
        }
        extractOutline(graphics, shape, originX, originY, edge);

        var middle = middle(shape);
        graphics.item(icon, gridX + (originX + middle.x()) * CELL + (CELL - 16) / 2, gridY + (originY + middle.y()) * CELL + (CELL - 16) / 2);
    }

    private void extractOutline(GuiGraphicsExtractor graphics, ComponentShape shape, int originX, int originY, int color)
    {
        int gridX = gridX();
        int gridY = gridY();
        for (var cell : shape.cells())
        {
            int x = gridX + (originX + cell.x()) * CELL;
            int y = gridY + (originY + cell.y()) * CELL;
            if (!has(shape, cell.x(), cell.y() - 1))
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + 2, color);
            if (!has(shape, cell.x(), cell.y() + 1))
                graphics.fill(x + 1, y + CELL - 2, x + CELL - 1, y + CELL - 1, color);
            if (!has(shape, cell.x() - 1, cell.y()))
                graphics.fill(x + 1, y + 1, x + 2, y + CELL - 1, color);
            if (!has(shape, cell.x() + 1, cell.y()))
                graphics.fill(x + CELL - 2, y + 1, x + CELL - 1, y + CELL - 1, color);
        }
    }

    static boolean has(ComponentShape shape, int x, int y)
    {
        return shape.cells().stream().anyMatch(cell -> cell.x() == x && cell.y() == y);
    }

    static ComponentShape.Cell middle(ComponentShape shape)
    {
        float centerX = (float) shape.cells().stream().mapToInt(ComponentShape.Cell::x).average().orElse(0);
        float centerY = (float) shape.cells().stream().mapToInt(ComponentShape.Cell::y).average().orElse(0);
        ComponentShape.Cell best = shape.cells().getFirst();
        float bestDistance = Float.MAX_VALUE;
        for (var cell : shape.cells())
        {
            float distance = (cell.x() - centerX) * (cell.x() - centerX) + (cell.y() - centerY) * (cell.y() - centerY);
            if (distance < bestDistance)
            {
                best = cell;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static ItemStack stackOf(ApparatusComponent component)
    {
        return new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(component));
    }

    /**
     * What the apparatus gives as short rows, a name on the left and its value on the right; the full wording waits in the pieces' tooltips.
     */
    private void extractStats(GuiGraphicsExtractor graphics, ApparatusGrid grid)
    {
        var tablet = tablet();
        if (!(menu.apparatus().getItem() instanceof ShieldingApparatusItem item))
            return;
        ShieldStats stats = grid.stats(item.tier());

        int y = tablet.y() + 7;
        y = row(graphics, tablet, y, "capacity", ShieldingApparatusItem.number(stats.capacity() / 2) + " ♥", 0xFF000000 | ApparatusComponent.CELL.rgb());
        y = row(graphics, tablet, y, "regen", ShieldingApparatusItem.number(stats.regenPerTick() * 20 / 2) + " ♥/s", 0xFF000000 | ApparatusComponent.RESONATOR.rgb());
        y = row(graphics, tablet, y, "delay", ShieldingApparatusItem.number(stats.delayTicks() / 20F) + "s", 0xFF000000 | ApparatusComponent.QUICKENER.rgb());
        y += 3;
        graphics.fill(tablet.x() + 6, y - 2, tablet.x() + tablet.width() - 6, y - 1, RULE_COLOR);
        y += 2;
        for (var ward : stats.wards())
            y = row(graphics, tablet, y, "ward", Component.translatable("voidheart.apparatus_screen.ward." + ward.name().toLowerCase(Locale.ROOT)).getString(), WARD_COLOR);
        if (stats.breakEffect().isPresent())
            row(graphics, tablet, y, "core",
                    Component.translatable("voidheart.apparatus_screen.core." + stats.breakEffect().get().name().toLowerCase(Locale.ROOT)).getString(), CORE_COLOR);
        else if (stats.wards().isEmpty())
            graphics.text(font, Component.translatable("voidheart.apparatus_screen.no_extras"), tablet.x() + 7, y, MUTED_COLOR, false);
    }

    private int row(GuiGraphicsExtractor graphics, Bounds tablet, int y, String name, String value, int color)
    {
        if (y + LINE > tablet.y() + tablet.height())
            return y;
        graphics.text(font, Component.translatable("voidheart.apparatus_screen.stat." + name), tablet.x() + 7, y, MUTED_COLOR, false);
        graphics.text(font, value, tablet.x() + tablet.width() - 7 - font.width(value), y, color, false);
        return y + LINE;
    }

    private void extractWhisper(GuiGraphicsExtractor graphics, ApparatusGrid grid, int mouseX, int mouseY)
    {
        var held = held();
        Component text;
        int color = MUTED_COLOR;
        if (held != null && overGrid(mouseX, mouseY))
        {
            var refusal = grid.refusal(size(), held, originX(held, mouseX), originY(held, mouseY), rotation);
            if (refusal != null)
            {
                text = Component.translatable("voidheart.apparatus_screen.refusal." + refusal.name().toLowerCase(Locale.ROOT));
                color = LACK_COLOR;
            }
            else
                text = Component.translatable("voidheart.apparatus_screen.whisper.set");
        }
        else if (held != null)
            text = Component.translatable("voidheart.apparatus_screen.whisper.turn");
        else if (!grid.isEmpty() && overGrid(mouseX, mouseY))
            text = Component.translatable("voidheart.apparatus_screen.whisper.take");
        else
            text = Component.translatable("voidheart.apparatus_screen.whisper.pick");

        var line = font.plainSubstrByWidth(text.getString(), PANEL_W - 2 * PAD);
        graphics.text(font, line, leftPos + (PANEL_W - font.width(line)) / 2, topPos + WHISPER_Y, color, false);
    }

    private void extractHovered(GuiGraphicsExtractor graphics, ApparatusGrid grid, int mouseX, int mouseY)
    {
        if (!menu.getCarried().isEmpty() || !overGrid(mouseX, mouseY))
            return;
        var placement = grid.at(cellAtX(mouseX), cellAtY(mouseY));
        if (placement != null)
            graphics.setTooltipForNextFrame(font, stackOf(placement.component()), mouseX, mouseY);
    }

    /**
     * The middle of a grid cell on screen, for tests to aim at.
     */
    public int[] cellCenter(int cellX, int cellY)
    {
        return new int[] { gridX() + cellX * CELL + CELL / 2, gridY() + cellY * CELL + CELL / 2 };
    }

    /**
     * The middle of the slot holding an inventory index on screen, for tests to aim at.
     */
    public int[] slotCenter(int inventoryIndex)
    {
        for (var slot : menu.slots)
            if (slot.getContainerSlot() == inventoryIndex)
                return new int[] { leftPos + slot.x + 8, topPos + slot.y + 8 };
        throw new IllegalArgumentException("No slot for inventory index " + inventoryIndex);
    }
}
