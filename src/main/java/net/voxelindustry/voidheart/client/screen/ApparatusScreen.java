package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.Bounds;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponent;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusComponentItem;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusEdits;
import net.voxelindustry.voidheart.common.content.apparatus.ApparatusGrid;
import net.voxelindustry.voidheart.common.content.apparatus.ComponentShape;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldPayloads;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatus;
import net.voxelindustry.voidheart.common.content.apparatus.ShieldingApparatusItem;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.KNOWN_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.LACK_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.MUTED_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.RULE_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.TEXT_COLOR;

/**
 * The grid of the apparatus in hand, set in the same slab as the knowledge repository. Components carried wait on a tablet to the left: one is picked there,
 * turned with R or a right click and set by clicking the grid, and clicking a piece in the grid takes it back. What the apparatus gives is on the tablet to the right.
 */
public class ApparatusScreen extends Screen
{
    private static final int RIM_MIN    = 20;
    private static final int RIM_MAX    = 40;
    private static final int PAD        = 8;
    private static final int LINE       = 11;
    private static final int CELL       = 22;
    private static final int TABLET_W   = 132;
    private static final int TABLET_GAP = 18;
    private static final int ROW        = 20;
    private static final int HEAD       = 16;
    private static final int GRID_LIFT  = 6;

    private static final int PLATE_COLOR      = 0xFF0B0810;
    private static final int CELL_LINE        = 0xFF2A2038;
    private static final float PIECE_TINT     = 0.3F;
    private static final float GHOST_TINT     = 0.45F;
    private static final int FITS_COLOR       = 0xFF7CE8D6;
    private static final int SELECTED_BACK    = 0xFF16463F;
    private static final int HOVER_BACK       = 0xFF1A1424;
    private static final int WHISPER_BACK     = 0xA0050308;

    private static final int CAPACITY_RGB   = 0x36C5B0;
    private static final int REGEN_RGB      = 0x8E6BE8;
    private static final int QUICKEN_RGB    = 0xF4D670;
    private static final int WARD_RGB       = 0xD6C9F5;
    private static final int CORE_RGB       = 0xF2A65A;

    private static final Identifier VOID = Identifier.fromNamespaceAndPath(MODID, "dynamic/apparatus_void");

    private int left;
    private int right;
    private int gridX;
    private int gridY;
    private int size;
    private Bounds tray;
    private Bounds stats;

    private @Nullable ApparatusComponent held;
    private int rotation;

    public ApparatusScreen()
    {
        super(Component.translatable("voidheart.apparatus_screen.title"));
    }

    private ItemStack apparatus()
    {
        var hand = minecraft.player == null ? null : ApparatusEdits.handOf(minecraft.player);
        return hand == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    private ApparatusGrid grid()
    {
        return ShieldingApparatus.grid(apparatus());
    }

    @Override
    protected void init()
    {
        super.init();
        var stack = apparatus();
        size = stack.getItem() instanceof ShieldingApparatusItem item ? item.tier().size() : 3;

        int rim = Math.clamp((int) (width * 0.045F), RIM_MIN, RIM_MAX);
        left = rim;
        right = width - rim;
        int gridSize = size * CELL;
        gridX = (width - gridSize) / 2;
        gridY = (height - gridSize) / 2 - GRID_LIFT;

        int tabletH = Math.max(gridSize + 2 * PAD, HEAD + 4 * ROW);
        int tabletY = (height - tabletH) / 2 - GRID_LIFT;
        tray = new Bounds(Math.max(left + PAD, gridX - TABLET_GAP - TABLET_W), tabletY, TABLET_W, tabletH);
        stats = new Bounds(Math.min(right - PAD - TABLET_W, gridX + gridSize + TABLET_GAP), tabletY, TABLET_W, tabletH);

        var lights = List.of(new ResearchVoid.Light(width / 2D, height / 2D - GRID_LIFT, gridSize * 1.1, 0.9, ResearchVoid.LIT_RGB));
        ResearchVoid.bake(minecraft, VOID, width, height, left, right, lights, minecraft.player == null ? 0 : minecraft.player.getUUID().getLeastSignificantBits());
    }

    @Override
    public void tick()
    {
        if (apparatus().isEmpty())
            onClose();
        else if (held != null && count(held) == 0)
            held = null;
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    @Override
    public void removed()
    {
        minecraft.getTextureManager().release(VOID);
        super.removed();
    }

    private Map<ApparatusComponent, Integer> carried()
    {
        var counts = new EnumMap<ApparatusComponent, Integer>(ApparatusComponent.class);
        var inventory = minecraft.player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            var stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ApparatusComponentItem item)
                counts.merge(item.component(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private int count(ApparatusComponent component)
    {
        return carried().getOrDefault(component, 0);
    }

    private static int rgb(ApparatusComponent component)
    {
        if (component.breakEffect() != null)
            return CORE_RGB;
        if (component.ward() != null)
            return WARD_RGB;
        if (component.quickening() > 0)
            return QUICKEN_RGB;
        if (component.regen() > 0)
            return REGEN_RGB;
        return CAPACITY_RGB;
    }

    private static ItemStack stackOf(ApparatusComponent component)
    {
        return new ItemStack(VoidHeartItems.APPARATUS_COMPONENTS.get(component));
    }

    private int cellAtX(double mouseX)
    {
        return (int) Math.floor((mouseX - gridX) / CELL);
    }

    private int cellAtY(double mouseY)
    {
        return (int) Math.floor((mouseY - gridY) / CELL);
    }

    private boolean overGrid(double mouseX, double mouseY)
    {
        int x = cellAtX(mouseX);
        int y = cellAtY(mouseY);
        return mouseX >= gridX && mouseY >= gridY && x < size && y < size;
    }

    /**
     * Where the held piece's top left corner goes for the cell under the cursor, which it covers near its middle.
     */
    private int originX(double mouseX)
    {
        return cellAtX(mouseX) - (shape().width() - 1) / 2;
    }

    private int originY(double mouseY)
    {
        return cellAtY(mouseY) - (shape().height() - 1) / 2;
    }

    private ComponentShape shape()
    {
        return held == null ? ComponentShape.of("#") : held.shape().rotated(rotation);
    }

    private List<ApparatusComponent> trayRows()
    {
        return new ArrayList<>(carried().keySet());
    }

    private @Nullable ApparatusComponent trayAt(double mouseX, double mouseY)
    {
        if (!tray.contains(mouseX, mouseY))
            return null;
        int row = (int) ((mouseY - tray.y() - HEAD) / ROW);
        var rows = trayRows();
        if (mouseY < tray.y() + HEAD || row < 0 || row >= rows.size())
            return null;
        return rows.get(row);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        graphics.blit(RenderPipelines.GUI_TEXTURED, VOID, 0, 0, 0, 0, width, height, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (minecraft.player == null)
            return;

        var grid = grid();
        extractGrid(graphics, grid, mouseX, mouseY);
        extractTray(graphics, mouseX, mouseY);
        extractStats(graphics);
        extractWhisper(graphics, grid, mouseX, mouseY);
        extractHovered(graphics, grid, mouseX, mouseY);
    }

    private void extractGrid(GuiGraphicsExtractor graphics, ApparatusGrid grid, int mouseX, int mouseY)
    {
        int gridSize = size * CELL;
        ResearchScreen.tablet(graphics, new Bounds(gridX - 4, gridY - 4, gridSize + 8, gridSize + 8));
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
            {
                int cellX = gridX + x * CELL;
                int cellY = gridY + y * CELL;
                graphics.fill(cellX + 1, cellY + 1, cellX + CELL - 1, cellY + CELL - 1, PLATE_COLOR);
                graphics.outline(cellX + 1, cellY + 1, CELL - 2, CELL - 2, CELL_LINE);
            }

        var valid = grid.valid(size);
        for (var placement : grid.placements())
            extractPiece(graphics, placement.shape(), placement.x(), placement.y(), rgb(placement.component()), PIECE_TINT, valid.contains(placement) ? 0xFF000000 | rgb(placement.component()) : LACK_COLOR,
                    stackOf(placement.component()));

        if (held != null && overGrid(mouseX, mouseY))
        {
            boolean fits = grid.canPlace(size, held, originX(mouseX), originY(mouseY), rotation);
            extractPiece(graphics, shape(), originX(mouseX), originY(mouseY), rgb(held), GHOST_TINT, fits ? FITS_COLOR : LACK_COLOR, stackOf(held));
        }
        else if (held == null && overGrid(mouseX, mouseY))
        {
            var placement = grid.at(cellAtX(mouseX), cellAtY(mouseY));
            if (placement != null)
                extractOutline(graphics, placement.shape(), placement.x(), placement.y(), FITS_COLOR);
        }
    }

    /**
     * Fills the cells of a piece as one slab, ruling only its outer edges, with its item on the cell nearest its middle.
     */
    private void extractPiece(GuiGraphicsExtractor graphics, ComponentShape shape, int originX, int originY, int rgb, float tint, int edge, ItemStack icon)
    {
        int fill = ARGB.srgbLerp(tint, PLATE_COLOR, 0xFF000000 | rgb);
        for (var cell : shape.cells())
        {
            int x = gridX + (originX + cell.x()) * CELL;
            int y = gridY + (originY + cell.y()) * CELL;
            graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, fill);
            if (has(shape, cell.x() + 1, cell.y()))
                graphics.fill(x + CELL - 1, y + 1, x + CELL + 1, y + CELL - 1, fill);
            if (has(shape, cell.x(), cell.y() + 1))
                graphics.fill(x + 1, y + CELL - 1, x + CELL - 1, y + CELL + 1, fill);
        }
        extractOutline(graphics, shape, originX, originY, edge);

        var middle = middle(shape);
        int iconX = gridX + (originX + middle.x()) * CELL + (CELL - 16) / 2;
        int iconY = gridY + (originY + middle.y()) * CELL + (CELL - 16) / 2;
        graphics.item(icon, iconX, iconY);
    }

    private void extractOutline(GuiGraphicsExtractor graphics, ComponentShape shape, int originX, int originY, int color)
    {
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

    private static boolean has(ComponentShape shape, int x, int y)
    {
        return shape.cells().stream().anyMatch(cell -> cell.x() == x && cell.y() == y);
    }

    private static ComponentShape.Cell middle(ComponentShape shape)
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

    private void extractTray(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        ResearchScreen.tablet(graphics, tray);
        graphics.text(font, Component.translatable("voidheart.apparatus_screen.carried"), tray.x() + PAD, tray.y() + PAD - 2, KNOWN_COLOR);

        var counts = carried();
        if (counts.isEmpty())
        {
            var lines = font.split(Component.translatable("voidheart.apparatus_screen.none_carried"), tray.width() - 2 * PAD);
            for (int i = 0; i < lines.size(); i++)
                graphics.text(font, lines.get(i), tray.x() + PAD, tray.y() + HEAD + 4 + i * LINE, MUTED_COLOR);
            return;
        }

        var hovered = trayAt(mouseX, mouseY);
        int row = 0;
        for (var entry : counts.entrySet())
        {
            int y = tray.y() + HEAD + row * ROW;
            if (y + ROW > tray.y() + tray.height())
                break;
            var component = entry.getKey();
            if (component == held)
                graphics.fill(tray.x() + 2, y, tray.x() + tray.width() - 2, y + ROW - 1, SELECTED_BACK);
            else if (component == hovered)
                graphics.fill(tray.x() + 2, y, tray.x() + tray.width() - 2, y + ROW - 1, HOVER_BACK);
            graphics.fill(tray.x() + 2, y + 2, tray.x() + 4, y + ROW - 3, 0xFF000000 | rgb(component));

            graphics.item(stackOf(component), tray.x() + PAD, y + 1);
            var name = font.plainSubstrByWidth(stackOf(component).getHoverName().getString(), tray.width() - 2 * PAD - 18 - 16);
            graphics.text(font, name, tray.x() + PAD + 19, y + (ROW - font.lineHeight) / 2 + 1, component == held ? TEXT_COLOR : MUTED_COLOR);
            var count = "×" + entry.getValue();
            graphics.text(font, count, tray.x() + tray.width() - PAD - font.width(count), y + (ROW - font.lineHeight) / 2 + 1, MUTED_COLOR);
            row++;
        }
    }

    private void extractStats(GuiGraphicsExtractor graphics)
    {
        ResearchScreen.tablet(graphics, stats);
        var stack = apparatus();
        if (!(stack.getItem() instanceof ShieldingApparatusItem item))
            return;

        int x = stats.x() + PAD;
        int y = stats.y() + PAD - 2;
        for (var part : font.split(stack.getHoverName(), stats.width() - 2 * PAD))
        {
            graphics.text(font, part, x, y, KNOWN_COLOR);
            y += LINE;
        }
        y += HEAD - LINE;

        var lines = new ArrayList<Component>();
        ShieldingApparatusItem.appendStats(grid().stats(item.tier()), lines::add);
        for (var line : lines)
        {
            for (var part : font.split(line, stats.width() - 2 * PAD))
            {
                if (y + LINE > stats.y() + stats.height())
                    return;
                var color = line.getStyle().getColor();
                graphics.text(font, part, x, y, color == null ? TEXT_COLOR : 0xFF000000 | color.getValue());
                y += LINE;
            }
            y += 2;
        }
    }

    private void extractWhisper(GuiGraphicsExtractor graphics, ApparatusGrid grid, int mouseX, int mouseY)
    {
        Component text;
        int color = MUTED_COLOR;
        if (held != null && overGrid(mouseX, mouseY))
        {
            var refusal = grid.refusal(size, held, originX(mouseX), originY(mouseY), rotation);
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
        else if (!grid.isEmpty())
            text = Component.translatable("voidheart.apparatus_screen.whisper.take");
        else
            text = Component.translatable("voidheart.apparatus_screen.whisper.pick");

        int textWidth = font.width(text);
        int x = (width - textWidth) / 2;
        int y = height - PAD - LINE - 2;
        graphics.fill(x - 6, y - 3, x + textWidth + 6, y + font.lineHeight + 2, WHISPER_BACK);
        graphics.text(font, text, x, y, color);
    }

    private void extractHovered(GuiGraphicsExtractor graphics, ApparatusGrid grid, int mouseX, int mouseY)
    {
        if (held != null)
            return;
        var component = trayAt(mouseX, mouseY);
        if (component == null && overGrid(mouseX, mouseY))
        {
            var placement = grid.at(cellAtX(mouseX), cellAtY(mouseY));
            component = placement == null ? null : placement.component();
        }
        if (component != null)
            graphics.setTooltipForNextFrame(font, stackOf(component), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT)
        {
            if (held != null)
                rotation = (rotation + 1) & 3;
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
            return super.mouseClicked(event, doubleClick);

        var picked = trayAt(event.x(), event.y());
        if (picked != null)
        {
            held = picked == held ? null : picked;
            return true;
        }

        if (overGrid(event.x(), event.y()))
        {
            if (held != null)
            {
                int x = originX(event.x());
                int y = originY(event.y());
                if (grid().canPlace(size, held, x, y, rotation))
                    ClientPlayNetworking.send(new ShieldPayloads.Edit(false, held, x, y, rotation));
            }
            else if (grid().at(cellAtX(event.x()), cellAtY(event.y())) != null)
                ClientPlayNetworking.send(new ShieldPayloads.Edit(true, ApparatusComponent.CELL, cellAtX(event.x()), cellAtY(event.y()), 0));
            return true;
        }

        held = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (event.key() == InputConstants.KEY_R && held != null)
        {
            rotation = (rotation + 1) & 3;
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE && held != null)
        {
            held = null;
            return true;
        }
        return super.keyPressed(event);
    }
}
