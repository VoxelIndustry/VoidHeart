package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.Bounds;
import net.voxelindustry.voidheart.common.content.hoard.HoardMenu;
import net.voxelindustry.voidheart.common.content.hoard.HoardPayloads;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.FRONTIER_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.KNOWN_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.LACK_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.MUTED_COLOR;

/**
 * The hoard over the player's inventory, cut in a slab of the knowledge repository's voidstone: each kind it holds as a plate in a torn hole, most held
 * first, searched by name, with how full it is under them and one whisper saying what a click does. A plate clicked gives a stack onto the cursor, or
 * into the inventory with shift held; whatever the cursor carries goes into the hoard when clicked over the hole.
 * <p>
 * Per frame: one blit for the baked slab, 36 slot plates, up to 45 item plates with their counts, a gauge and a few lines of text. The slab is baked
 * once per resize, and the listing is sorted and filtered only when it or the search changes.
 */
public class HoardScreen extends AbstractContainerScreen<HoardMenu>
{
    static final int PAD     = 8;
    static final int COLUMNS = 9;
    static final int ROWS    = 4;
    static final int CELL    = 18;

    private static final int PANEL_W   = COLUMNS * CELL + 2 * PAD + 6;
    private static final int TITLE_Y   = 8;
    private static final int SEARCH_Y  = 19;
    private static final int SEARCH_H  = 12;
    private static final int WELL_X    = PAD;
    private static final int WELL_Y    = 35;
    private static final int WELL_W    = COLUMNS * CELL;
    private static final int WELL_H    = ROWS * CELL;
    private static final int TEAR      = 3;
    private static final int GAUGE_Y   = WELL_Y + WELL_H + 5;
    private static final int GAUGE_H   = 3;
    private static final int COUNT_Y   = GAUGE_Y + 5;
    private static final int WHISPER_Y = COUNT_Y + 11;
    private static final float COUNT_SCALE = 0.5F;
    static final int   COUNT_COLOR = 0xFFEDE6FF;
    private static final int PANEL_H   = HoardMenu.INVENTORY_Y + 4 * CELL + HoardMenu.HOTBAR_GAP + PAD;

    private static final int CELL_BACK     = 0xB00B0810;
    private static final int CELL_LINE     = 0xFF2A2038;
    private static final int CELL_HOVER    = 0xFF7CE8D6;
    private static final int SLOT_BACK     = 0xF40B0810;
    private static final int OUTLINE_COLOR = 0xFF040208;
    private static final int GAUGE_BACK    = 0xFF08050C;
    private static final int SCROLL_COLOR  = 0xFF4A3860;

    private static final Identifier PANEL       = Identifier.fromNamespaceAndPath(MODID, "dynamic/hoard_panel");
    private static final Identifier WORKS_PANEL = Identifier.fromNamespaceAndPath(MODID, "dynamic/works_panel");
    private static final Identifier SIDE_PANEL  = Identifier.fromNamespaceAndPath(MODID, "dynamic/works_side_panel");
    /** The works slab with its hole left clear, laid over the graph so what is panned past the torn edge goes under the stone. */
    private static final Identifier WORKS_RIM   = Identifier.fromNamespaceAndPath(MODID, "dynamic/works_rim");
    private static final int        TAB_Y       = 5;
    private static final int        TAB_H       = 11;
    private static final int        BUTTON_H    = 12;

    @Nullable
    private final WorksView works;
    private boolean worksTab;
    private ResearchButton hoardTabButton;
    private ResearchButton worksTabButton;
    private ResearchButton backButton;
    private ResearchButton lessButton;
    private ResearchButton moreButton;
    private ResearchButton beginButton;
    private ResearchButton keepButton;
    private ResearchButton cancelButton;
    private ResearchButton stationsButton;
    /** The amount to plan, typed or stepped. */
    private EditBox amountBox;
    /** Whether the left button went down on a graph, so moving it pans the graph. */
    private boolean panning;

    private EditBox search;
    private List<HoardPayloads.Entry> shown = List.of();
    private int shownListing = -1;
    private String shownSearch = "";
    private int scroll;

    public HoardScreen(HoardMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, PANEL_W, PANEL_H);
        works = menu.isWorks() ? new WorksView(menu) : null;
        worksTab = works != null;
    }

    @Override
    protected void init()
    {
        super.init();
        bakePanel(PANEL, WELL_X, WELL_Y, WELL_W, WELL_H);
        if (works != null)
        {
            bakePanel(WORKS_PANEL, WorksView.REGION_X, WorksView.REGION_Y, WorksView.REGION_W, WorksView.REGION_H);
            bakeRim(WORKS_RIM, WorksView.REGION_X, WorksView.REGION_Y, WorksView.REGION_W, WorksView.REGION_H);
            works.rimOverlay = graphics -> graphics.blit(RenderPipelines.GUI_TEXTURED, WORKS_RIM, leftPos + WorksView.REGION_X, topPos + WorksView.REGION_Y,
                    WorksView.REGION_X, WorksView.REGION_Y, WorksView.REGION_W, WorksView.REGION_H, PANEL_W, PANEL_H);
            // The slab of orders, its hole level with the works hole
            bakePanel(SIDE_PANEL, WorksView.SIDE_SLAB_W, PANEL_H, WorksView.SIDE_PAD, WorksView.REGION_Y, WorksView.SIDE_W + 4, WorksView.REGION_H);
            addWorksButtons();
        }
        search = new EditBox(font, leftPos + WELL_X + 2, topPos + SEARCH_Y + 2, WELL_W - 4, SEARCH_H - 2, Component.translatable(MODID + ".hoard.search"));
        search.setBordered(false);
        search.setTextColor(KNOWN_COLOR);
        search.setHint(Component.translatable(MODID + ".hoard.search").withColor(MUTED_COLOR));
        addRenderableWidget(search);
    }

    @Override
    public void removed()
    {
        minecraft.getTextureManager().release(PANEL);
        if (works != null)
        {
            minecraft.getTextureManager().release(WORKS_PANEL);
            minecraft.getTextureManager().release(SIDE_PANEL);
            minecraft.getTextureManager().release(WORKS_RIM);
        }
        super.removed();
    }

    // The slab, baked once: voidstone with a torn hole the hoard shows in, lit from within like the research map

    /**
     * The slab's stone around a hole, the hole itself clear but for the shadow its wall casts, as the void under it is shaded.
     */
    private void bakeRim(Identifier target, int wellX, int wellY, int wellW, int wellH)
    {
        var stone = ResearchVoid.readStone(minecraft);
        var image = new NativeImage(PANEL_W, PANEL_H, false);
        long seed = minecraft.player == null ? 0 : minecraft.player.getUUID().getMostSignificantBits();
        for (int y = 0; y < PANEL_H; y++)
            for (int x = 0; x < PANEL_W; x++)
            {
                int hole = ResearchVoid.tornHoleDepth(x, y, wellX, wellY, wellW, wellH, TEAR, seed);
                int rim = Math.min(Math.min(x, y), Math.min(PANEL_W - 1 - x, PANEL_H - 1 - y));
                int color;
                if (hole >= 0)
                {
                    float shadow = hole < ResearchVoid.INNER_SHADOW ? (1 - hole / ResearchVoid.INNER_SHADOW) * 0.85F : 0;
                    color = net.minecraft.util.ARGB.color((int) (shadow * 255), ResearchVoid.DEEP_COLOR);
                }
                else if (rim == 0)
                    color = OUTLINE_COLOR;
                else
                    color = ResearchVoid.stoneAt(stone, x, y, Math.min(-hole, rim), seed);
                image.setPixel(x, y, color);
            }
        if (stone != null)
            stone.close();
        ResearchVoid.upload(minecraft, target, image);
    }

    private void bakePanel(Identifier target, int wellX, int wellY, int wellW, int wellH)
    {
        bakePanel(target, PANEL_W, PANEL_H, wellX, wellY, wellW, wellH);
    }

    private void bakePanel(Identifier target, int panelW, int panelH, int wellX, int wellY, int wellW, int wellH)
    {
        var stone = ResearchVoid.readStone(minecraft);
        var image = new NativeImage(panelW, panelH, false);
        long seed = minecraft.player == null ? 0 : minecraft.player.getUUID().getMostSignificantBits();
        var lights = List.of(new ResearchVoid.Light(wellX + wellW / 2D, wellY + wellH / 2D, wellW * 0.7, 0.9, ResearchVoid.LIT_RGB));
        for (int y = 0; y < panelH; y++)
            for (int x = 0; x < panelW; x++)
            {
                int hole = ResearchVoid.tornHoleDepth(x, y, wellX, wellY, wellW, wellH, TEAR, seed);
                int rim = Math.min(Math.min(x, y), Math.min(panelW - 1 - x, panelH - 1 - y));
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
        ResearchVoid.upload(minecraft, target, image);
    }

    // The works tab of a Reckoning

    private ResearchButton worksButton(int x, int y, int width, int height, boolean quiet, String key, Button.OnPress onPress)
    {
        var button = new ResearchButton(leftPos + x, topPos + y, width, height, quiet, onPress);
        button.setMessage(Component.translatable(MODID + ".works.button." + key));
        addRenderableWidget(button);
        return button;
    }

    private void addWorksButtons()
    {
        hoardTabButton = worksButton(PANEL_W - PAD - 82, TAB_Y, 40, TAB_H, true, "hoard", button -> worksTab = false);
        worksTabButton = worksButton(PANEL_W - PAD - 40, TAB_Y, 40, TAB_H, true, "works", button -> worksTab = true);
        int y = WorksView.CONTROLS_Y;
        int x = WorksView.REGION_X;
        backButton = worksButton(x, y, 11, BUTTON_H, true, "back", button -> works.back());
        lessButton = worksButton(x + 13, y, 11, BUTTON_H, true, "less", button -> works.changeAmount(-1));
        moreButton = worksButton(x + 50, y, 11, BUTTON_H, true, "more", button -> works.changeAmount(1));
        beginButton = worksButton(x + 63, y, 76, BUTTON_H, false, "begin", button -> works.begin());
        keepButton = worksButton(x + 141, y, 27, BUTTON_H, true, "keep", button -> works.keep());
        cancelButton = worksButton(x + 63, y, 76, BUTTON_H, true, "cancel", button -> works.cancelShown());
        stationsButton = worksButton(x, y, 62, BUTTON_H, true, "stations", button -> works.roster = true);

        // The amount, typed or stepped, between its steps
        amountBox = new EditBox(font, leftPos + x + 28, topPos + y + 2, 20, BUTTON_H - 2, Component.translatable(MODID + ".works.amount"));
        amountBox.setBordered(false);
        amountBox.setMaxLength(4);
        amountBox.setTextColor(KNOWN_COLOR);
        amountBox.setValue(String.valueOf(works.amount));
        amountBox.setResponder(text ->
        {
            // Only digits stay
            var digits = text.replaceAll("[^0-9]", "");
            if (!digits.equals(text))
                amountBox.setValue(digits);
            else if (!digits.isEmpty())
                works.typeAmount(Long.parseLong(digits));
        });
        addRenderableWidget(amountBox);
        updateWorksButtons();
    }

    private void updateWorksButtons()
    {
        if (works == null)
            return;
        boolean graph = worksTab && works.shownGraph() != null;
        boolean job = worksTab && works.shownJob() != null;
        boolean roster = worksTab && !graph && works.roster;
        backButton.visible = graph || roster;
        stationsButton.visible = worksTab && !graph && !roster;
        lessButton.visible = moreButton.visible = beginButton.visible = keepButton.visible = amountBox.visible = graph && !job;
        cancelButton.visible = job;
        keepButton.setMessage(Component.translatable(MODID + ".works.button." + (works.isKept() ? "kept" : "keep")));
        // Begin turns rose when the plan lacks something: the order still waits for it, but not silently
        beginButton.warning = works.beginWarns();
        beginButton.setMessage(Component.translatable(MODID + ".works.button." + (beginButton.warning ? "queue_anyway" : "begin")));
        if (!amountBox.isFocused() && !amountBox.getValue().equals(String.valueOf(works.amount)))
            amountBox.setValue(String.valueOf(works.amount));
        hoardTabButton.selected = !worksTab;
        worksTabButton.selected = worksTab;
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (!worksTab)
            super.extractSlots(graphics, mouseX, mouseY);
    }

    @Override
    protected boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY)
    {
        return !worksTab && super.isHovering(x, y, width, height, mouseX, mouseY);
    }

    private String query()
    {
        return search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Shows the works tab or the hoard, for tests.
     */
    public void showWorks(boolean shown)
    {
        worksTab = shown && works != null;
    }

    /**
     * The middle of a listed or job plate, or of a control, for tests to aim at.
     */
    public int[] worksControl(String name)
    {
        if (name.equals("amount"))
            return new int[] { amountBox.getX() + amountBox.getWidth() / 2, amountBox.getY() + amountBox.getHeight() / 2 };
        var button = switch (name)
        {
            case "back" -> backButton;
            case "less" -> lessButton;
            case "more" -> moreButton;
            case "begin" -> beginButton;
            case "keep" -> keepButton;
            case "cancel" -> cancelButton;
            case "stations" -> stationsButton;
            case "hoard" -> hoardTabButton;
            default -> worksTabButton;
        };
        return new int[] { button.getX() + button.getWidth() / 2, button.getY() + button.getHeight() / 2 };
    }

    /**
     * @return the works tab's line under the controls, for tests
     */
    public String worksWhisper()
    {
        return works == null ? "" : works.whisper(query()).getString();
    }

    /**
     * @return what a control of the works tab reads, for tests
     */
    public String worksControlText(String name)
    {
        return switch (name)
        {
            case "begin" -> beginButton.getMessage().getString();
            case "stations" -> stationsButton.getMessage().getString();
            default -> amountBox.getValue();
        };
    }

    public boolean worksGraphShown()
    {
        return works != null && works.shownGraph() != null;
    }

    public int[] whisperCenter()
    {
        return new int[] { leftPos + PANEL_W / 2, topPos + WorksView.WHISPER_Y + font.lineHeight / 2 };
    }

    public int[] worksRegionCenter()
    {
        return new int[] { leftPos + WorksView.REGION_X + WorksView.REGION_W / 2, topPos + WorksView.REGION_Y + WorksView.REGION_H / 2 };
    }

    public float worksZoom()
    {
        return works == null ? 1 : works.zoom;
    }

    public float[] worksPan()
    {
        return works == null ? new float[2] : new float[] { works.panX, works.panY };
    }

    public int[] jobCenter(int index)
    {
        return works == null ? new int[2] : works.jobCenter(index, leftPos, topPos);
    }

    public @Nullable int[] makeableCenter(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant item)
    {
        return works == null ? null : works.listedCenter(item, query(), leftPos, topPos);
    }

    public @Nullable int[] nodeCenter(int node)
    {
        return works == null ? null : works.nodeCenter(node, leftPos, topPos);
    }

    public boolean worksShown()
    {
        return worksTab;
    }

    // The listing

    private List<HoardPayloads.Entry> shown()
    {
        var query = query();
        if (menu.getListing() != shownListing || !query.equals(shownSearch))
        {
            var entries = new ArrayList<HoardPayloads.Entry>();
            for (var entry : menu.getEntries())
            {
                if (query.isEmpty() || entry.variant().toStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                    entries.add(entry);
            }
            entries.sort(Comparator.comparingLong(HoardPayloads.Entry::count).reversed()
                    .thenComparing(entry -> entry.variant().toStack().getHoverName().getString()));
            shown = entries;
            shownListing = menu.getListing();
            shownSearch = query;
            scroll = Math.clamp(scroll, 0, maxScroll());
        }
        return shown;
    }

    private int maxScroll()
    {
        return Math.max(0, (shown.size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    private boolean overWell(double mouseX, double mouseY)
    {
        return mouseX >= leftPos + WELL_X && mouseX < leftPos + WELL_X + WELL_W && mouseY >= topPos + WELL_Y && mouseY < topPos + WELL_Y + WELL_H;
    }

    private @Nullable HoardPayloads.Entry entryAt(double mouseX, double mouseY)
    {
        if (!overWell(mouseX, mouseY))
            return null;
        int column = (int) (mouseX - leftPos - WELL_X) / CELL;
        int row = (int) (mouseY - topPos - WELL_Y) / CELL;
        int index = (scroll + row) * COLUMNS + column;
        var entries = shown();
        return index < entries.size() ? entries.get(index) : null;
    }

    /**
     * How many a plate shows: whole up to a thousand, then shortened.
     */
    static String countText(long count)
    {
        if (count < 1_000)
            return String.valueOf(count);
        if (count < 1_000_000)
            return shorten(count / 1_000D) + "k";
        return shorten(count / 1_000_000D) + "M";
    }

    private static String shorten(double value)
    {
        return value < 10 ? String.format(Locale.ROOT, "%.1f", Math.floor(value * 10) / 10) : String.valueOf((long) value);
    }

    // Input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        // The container screen takes every click, so the works view's plates go first: they lie clear of every button
        if (worksTab)
        {
            panning = event.button() == InputConstants.MOUSE_BUTTON_LEFT && works.graphAt(leftPos, topPos, event.x(), event.y());
            return event.button() == InputConstants.MOUSE_BUTTON_LEFT && works.clicked(query(), leftPos, topPos, event.x(), event.y())
                    || super.mouseClicked(event, doubleClick);
        }
        if (!overWell(event.x(), event.y()))
            return super.mouseClicked(event, doubleClick);
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT)
            return true;

        if (!menu.getCarried().isEmpty())
        {
            var entry = entryAt(event.x(), event.y());
            if (entry != null && entry.variant().matches(menu.getCarried()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT)
                ClientPlayNetworking.send(new HoardPayloads.Take(menu.containerId, entry.variant(), false));
            else
                ClientPlayNetworking.send(new HoardPayloads.Put(menu.containerId));
            return true;
        }

        var entry = entryAt(event.x(), event.y());
        if (entry != null)
            ClientPlayNetworking.send(new HoardPayloads.Take(menu.containerId, entry.variant(), event.hasShiftDown()));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if (worksTab && panning)
        {
            works.dragged(dragX, dragY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        panning = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (worksTab && scrollY != 0 && works.scrolled(leftPos, topPos, mouseX, mouseY, scrollY))
            return true;
        if (overWell(mouseX, mouseY) && scrollY != 0)
        {
            shown();
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
        if (worksTab)
        {
            // Enter begins the plan shown, Escape goes back a step before it closes the screen
            if (enter && beginButton.visible)
            {
                amountBox.setFocused(false);
                works.begin();
                return true;
            }
            if (event.key() == InputConstants.KEY_ESCAPE && (works.shownGraph() != null || works.roster))
            {
                amountBox.setFocused(false);
                works.back();
                return true;
            }
            if (amountBox.isFocused() && event.key() != InputConstants.KEY_ESCAPE)
                return amountBox.keyPressed(event) || true;
        }
        // Typing in the search never closes the screen or drops an item
        if (search.isFocused() && event.key() != InputConstants.KEY_ESCAPE)
            return search.keyPressed(event) || true;
        return super.keyPressed(event);
    }

    // Drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, worksTab ? WORKS_PANEL : PANEL, leftPos, topPos, 0, 0, PANEL_W, PANEL_H, PANEL_W, PANEL_H);
        if (worksTab)
            graphics.blit(RenderPipelines.GUI_TEXTURED, SIDE_PANEL, WorksView.sideSlab(leftPos), topPos, 0, 0, WorksView.SIDE_SLAB_W, PANEL_H,
                    WorksView.SIDE_SLAB_W, PANEL_H);
        for (var slot : worksTab ? List.<Slot>of() : menu.slots)
        {
            int x = leftPos + slot.x - 1;
            int y = topPos + slot.y - 1;
            graphics.fill(x, y, x + CELL, y + CELL, SLOT_BACK);
            graphics.outline(x, y, CELL, CELL, CELL_LINE);
        }
        ResearchScreen.tablet(graphics, new Bounds(leftPos + WELL_X, topPos + SEARCH_Y, WELL_W, SEARCH_H));
        if (amountBox != null && amountBox.visible)
            ResearchScreen.tablet(graphics, new Bounds(amountBox.getX() - 2, amountBox.getY() - 2, amountBox.getWidth() + 2, BUTTON_H));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym)
    {
        graphics.text(font, font.plainSubstrByWidth(title.getString(), PANEL_W - 2 * PAD), PAD, TITLE_Y, KNOWN_COLOR, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        updateWorksButtons();
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        if (worksTab)
        {
            works.extract(graphics, font, leftPos, topPos, query(), mouseX, mouseY);
            var whisper = works.whisper(query());
            var line = font.plainSubstrByWidth(whisper.getString(), PANEL_W - 2 * PAD);
            var color = whisper.getStyle().getColor() == null ? MUTED_COLOR : whisper.getStyle().getColor().getValue() | 0xFF000000;
            int lineX = leftPos + (PANEL_W - font.width(line)) / 2;
            int lineY = topPos + WorksView.WHISPER_Y;
            graphics.text(font, line, lineX, lineY, color, false);
            // Everything a plan lacks shows above its whisper, each with its icon, so the line stays readable
            var lacking = works.lackingRows();
            if (!lacking.isEmpty() && mouseX >= lineX && mouseX < lineX + font.width(line) && mouseY >= lineY - 1 && mouseY < lineY + font.lineHeight)
            {
                var header = Component.translatable(MODID + ".works.whisper.lacking").withColor(ResearchScreen.LACK_COLOR);
                net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner above = (screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight) ->
                        new org.joml.Vector2i(Math.clamp(x - tooltipWidth / 2, 4, Math.max(4, screenWidth - tooltipWidth - 4)), Math.max(4, lineY - tooltipHeight - 8));
                graphics.setTooltipForNextFrame(font, List.of(header.getVisualOrderText()), java.util.Optional.of(new ItemRowsTooltip(lacking)), above, mouseX, mouseY,
                        false, null);
            }
            return;
        }
        extractPlates(graphics, mouseX, mouseY);
        extractGauge(graphics);
        extractWhisper(graphics, mouseX, mouseY);
    }

    private void extractPlates(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        var entries = shown();
        var hovered = entryAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++)
            for (int column = 0; column < COLUMNS; column++)
            {
                int index = (scroll + row) * COLUMNS + column;
                int x = leftPos + WELL_X + column * CELL;
                int y = topPos + WELL_Y + row * CELL;
                if (index >= entries.size())
                    continue;
                var entry = entries.get(index);
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, CELL_BACK);
                graphics.outline(x + 1, y + 1, CELL - 2, CELL - 2, entry == hovered ? CELL_HOVER : CELL_LINE);
                var stack = entry.variant().toStack();
                graphics.item(stack, x + 1, y + 1);
                extractCount(graphics, font, countText(entry.count()), x + CELL - 1, y + CELL - 1);
            }

        if (maxScroll() > 0)
        {
            int trackX = leftPos + WELL_X + WELL_W + 2;
            int barH = Math.max(6, WELL_H * ROWS / (ROWS + maxScroll()));
            int barY = topPos + WELL_Y + (WELL_H - barH) * scroll / maxScroll();
            graphics.fill(trackX, barY, trackX + 2, barY + barH, SCROLL_COLOR);
        }

        if (hovered != null && menu.getCarried().isEmpty())
            graphics.setTooltipForNextFrame(font, hovered.variant().toStack(), mouseX, mouseY);
    }

    /**
     * A count in half-size text, its bottom right corner at x, y, so a long count stays inside its plate.
     */
    static void extractCount(GuiGraphicsExtractor graphics, Font font, String count, int x, int y)
    {
        extractCount(graphics, font, count, x, y, COUNT_COLOR);
    }

    static void extractCount(GuiGraphicsExtractor graphics, Font font, String count, int x, int y, int color)
    {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(COUNT_SCALE, COUNT_SCALE);
        graphics.text(font, count, -font.width(count), -font.lineHeight + 1, color, true);
        pose.popMatrix();
    }

    private void extractGauge(GuiGraphicsExtractor graphics)
    {
        int x = leftPos + WELL_X;
        int y = topPos + GAUGE_Y;
        long total = menu.getTotal();
        long capacity = menu.getCapacity();
        boolean over = total > capacity;
        graphics.fill(x, y, x + WELL_W, y + GAUGE_H, GAUGE_BACK);
        int filled = capacity <= 0 ? (total > 0 ? WELL_W : 0) : (int) Math.min(WELL_W, WELL_W * total / capacity);
        graphics.fill(x, y, x + filled, y + GAUGE_H, over ? LACK_COLOR : FRONTIER_COLOR);

        var count = Component.translatable(MODID + ".hoard.held", countText(total), countText(capacity)).getString();
        graphics.text(font, count, x + (WELL_W - font.width(count)) / 2, topPos + COUNT_Y, over ? LACK_COLOR : MUTED_COLOR, false);
    }

    private void extractWhisper(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        Component text;
        int color = MUTED_COLOR;
        if (menu.getTotal() > menu.getCapacity())
        {
            text = Component.translatable(MODID + ".hoard.whisper.over");
            color = LACK_COLOR;
        }
        else if (!menu.getCarried().isEmpty() && overWell(mouseX, mouseY))
            text = Component.translatable(MODID + ".hoard.whisper.put");
        else if (entryAt(mouseX, mouseY) != null)
            text = Component.translatable(MODID + ".hoard.whisper.take");
        else if (menu.getEntries().isEmpty() && menu.getCapacity() == 0)
            text = Component.translatable(MODID + ".hoard.whisper.no_room");
        else
            text = Component.translatable(MODID + ".hoard.whisper.shift");

        var line = font.plainSubstrByWidth(text.getString(), PANEL_W - 2 * PAD);
        graphics.text(font, line, leftPos + (PANEL_W - font.width(line)) / 2, topPos + WHISPER_Y, color, false);
    }

    /**
     * The middle of the plate in a column and row of the hole, for tests to aim at.
     */
    public int[] plateCenter(int column, int row)
    {
        return new int[] { leftPos + WELL_X + column * CELL + CELL / 2, topPos + WELL_Y + row * CELL + CELL / 2 };
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
