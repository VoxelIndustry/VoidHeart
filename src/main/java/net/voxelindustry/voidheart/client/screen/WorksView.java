package net.voxelindustry.voidheart.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.hoard.HoardMenu;
import net.voxelindustry.voidheart.common.content.works.StationKind;
import net.voxelindustry.voidheart.common.content.works.WorksGraph;
import net.voxelindustry.voidheart.common.content.works.WorksLayout;
import net.voxelindustry.voidheart.common.content.works.WorksPayloads;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.ACTIVE_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.FRONTIER_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.KNOWN_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.LACK_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.MUTED_COLOR;
import static net.voxelindustry.voidheart.client.screen.ResearchScreen.RULE_COLOR;

/**
 * The works tab of a Reckoning: what the owner's stations can make as plates, then the plan for a chosen item drawn as a graph flowing to the right,
 * raw kinds on the left and the item alone on the right. An order begun shows the same graph with its progress: teal waits, gold is being worked,
 * lilac is done or taken from the hoard, rose is lacking or waits on a station no Officiant serves, and motes run along the arcs into what is being
 * worked. The orders running stand in a panel left of the slab, each with what it makes, how far it has got and what it waits on.
 * <p>
 * Per frame: up to 72 plates in the list, or a graph of a few dozen plates and arcs sampled every few pixels, and a few order rows; the layout is
 * worked out only when the plan or order shown changes.
 */
final class WorksView
{
    static final int REGION_X = HoardScreen.PAD;
    static final int REGION_Y = 35;
    static final int REGION_W = HoardScreen.COLUMNS * HoardScreen.CELL + 6;
    static final int REGION_H = 146;
    static final int CONTROLS_Y = REGION_Y + REGION_H + 3;
    static final int WHISPER_Y  = CONTROLS_Y + 18;

    // The slab of orders left of the main one: the same stone, its own torn hole level with the main hole, the orders as plates in it
    static final int SIDE_PAD    = 6;
    static final int SIDE_W      = 106;
    static final int SIDE_SLAB_W = SIDE_W + 2 * SIDE_PAD + 4;
    static final int SIDE_GAP    = 3;
    static final int JOB_ROW     = 36;
    // An order is drawn as a research node: a disc for its state, filling as its crafts are done, glowing while it is made
    private static final int  DISC          = ResearchMapView.NODE_SIZE;
    private static final int  DISC_DONE     = 0;
    private static final int  DISC_WAITING  = 1;
    private static final int  DISC_RUNNING  = 2;
    private static final int  DISC_BLOCKED  = 3;
    private static final int  DISC_FILL     = 5;
    private static final int  DISC_GLOW     = 6;
    private static final long GLOW_MILLIS   = 2600;
    private static final int  THREAD_COLOR  = 0x704A3860;
    /** Laid over the icon of a blocked order, the closed disc's own dark, so the icon sinks into it. */
    private static final int  DIM_COLOR     = 0xB0221E28;
    private static final int  TEXT_X        = DISC + 6;
    static final int JOB_ROWS    = REGION_H / JOB_ROW;
    private static final int BAR_BACK   = 0xFF08050C;
    private static final int BAR_H      = 3;
    private static final int SCROLL_COLOR = 0xFF4A3860;

    private static final int CELL         = HoardScreen.CELL;
    private static final int LIST_ROWS    = REGION_H / CELL;
    private static final int MAX_COLUMN   = 30;
    private static final int MAX_ROW      = 22;
    private static final int ARC_SAMPLES  = 18;
    private static final long MOTE_MILLIS = 1400;

    private static final int PLATE_BACK = 0xB00B0810;
    private static final int LEAF_BACK  = 0xD0070509;
    private static final float MIN_ZOOM  = 0.5F;
    private static final float MAX_ZOOM  = 2.5F;
    private static final float ZOOM_STEP = 1.2F;
    private static final int ROSTER_ROW = 22;
    private static final int ROSTER_ROWS = REGION_H / ROSTER_ROW;
    private static final int ROSTER_ICONS = 4;
    private static final int ALTAR_PIP  = 0xFF8E6BE8;
    private static final int FORGE_PIP  = 0xFFE08A5E;
    private static final int TABLE_PIP  = 0xFF36C5B0;

    private final HoardMenu menu;
    /** Lays the slab's torn edge over what was drawn in the hole, set by the screen that baked it. */
    @Nullable
    java.util.function.Consumer<GuiGraphicsExtractor> rimOverlay;
    @Nullable
    ItemVariant chosen;
    long amount = 1;
    @Nullable
    Integer job;
    private int scroll;
    /** Whether the roster of Officiants is shown instead of what they can make. */
    boolean roster;
    private int rosterScroll;
    /** The first order shown in the panel of orders. */
    private int jobScroll;
    /** The order each row of the panel showed when last drawn, so a click picks what was seen even if the list changed since. */
    private final List<Integer> drawnRows = new ArrayList<>();
    // The graph shown as trimmed for display, kept while the graph it came from is the same
    @Nullable
    private WorksGraph trimmedFrom;
    @Nullable
    private WorksGraph trimmed;

    // The layout of the graph shown, worked out again when it changes, and how it is zoomed and panned
    @Nullable
    private WorksGraph laidOut;
    @Nullable
    private WorksLayout<Integer> layout;
    @Nullable
    private WorksGraph.Display display;
    float zoom = 1;
    float panX;
    float panY;

    WorksView(HoardMenu menu)
    {
        this.menu = menu;
    }

    // What is shown

    List<ItemVariant> listed(String query)
    {
        var items = new ArrayList<ItemVariant>();
        for (var item : menu.getMakeable())
        {
            if (query.isEmpty() || item.toStack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query))
                items.add(item);
        }
        return items;
    }

    @Nullable
    WorksPayloads.Job shownJob()
    {
        if (job == null)
            return null;
        return menu.getJobs().stream().filter(candidate -> candidate.id() == job).findFirst().orElse(null);
    }

    @Nullable
    WorksGraph shownGraph()
    {
        var shown = shownJob();
        if (shown != null)
            return trim(shown.graph());
        var plan = menu.getPlan();
        return chosen != null && plan != null && plan.item().equals(chosen) ? trim(plan.graph()) : null;
    }

    private WorksGraph trim(WorksGraph graph)
    {
        if (graph != trimmedFrom)
        {
            trimmedFrom = graph;
            trimmed = graph.trimmed();
        }
        return trimmed;
    }

    /**
     * @return whether some loaded Officiant serves that kind of station, true for a kind taken from the hoard
     */
    private boolean served(int station)
    {
        return station < 0 || menu.getRoster().stream().anyMatch(officiant -> officiant.station() == station);
    }

    /**
     * @return the kinds of station a graph still needs that no Officiant serves; for an order only those with crafts left
     */
    private List<StationKind> unserved(WorksGraph graph, @Nullable WorksPayloads.Job shown)
    {
        return graph.nodes().stream().filter(node -> node.isMade() && !served(node.station()) && (shown == null || shown.left(node.recipe()) > 0))
                .map(node -> StationKind.values()[node.station()]).distinct().toList();
    }

    /**
     * @return whether a kind still waits on a station no Officiant serves: in a plan, or with crafts left in an order
     */
    private boolean waitsOnStation(WorksGraph.Node node, @Nullable WorksPayloads.Job shown)
    {
        return node.isMade() && !served(node.station()) && (shown == null || shown.left(node.recipe()) > 0);
    }

    private static Component unservedLine(StationKind kind)
    {
        return Component.translatable(MODID + ".works.unserved." + kind.name().toLowerCase(Locale.ROOT)).withColor(LACK_COLOR);
    }

    /**
     * @return the block a kind of station is, as its icon
     */
    static net.minecraft.world.item.ItemStack stationIcon(int station)
    {
        if (station < 0)
            return new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER);
        return switch (StationKind.values()[station])
        {
            case ALTAR -> new net.minecraft.world.item.ItemStack(net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.VOID_ALTAR);
            case FORGE -> new net.minecraft.world.item.ItemStack(net.voxelindustry.voidheart.common.setup.VoidHeartBlocks.SHATTER_FORGE);
            case TABLE -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
        };
    }

    private WorksLayout<Integer> layoutOf(WorksGraph graph)
    {
        if (graph != laidOut)
        {
            var sameShape = laidOut != null && laidOut.nodes().equals(graph.nodes()) && laidOut.drinks().equals(graph.drinks());
            laidOut = graph;
            display = graph.display();
            layout = WorksLayout.of(display.plan());
            // A new plan opens whole; the same plan updating keeps where it was looked at
            if (!sameShape)
            {
                zoom = 1;
                panX = 0;
                panY = 0;
            }
        }
        return layout;
    }

    /**
     * @return the top left of each node's plate, then of each drink's, before zooming and panning
     */
    private int[][] spots(WorksGraph graph, int left, int top)
    {
        var layout = layoutOf(graph);
        int columnStep = layout.columns() > 1 ? Math.min(MAX_COLUMN, Math.max(CELL + 4, (REGION_W - CELL) / (layout.columns() - 1))) : 0;
        int rowStep = layout.rows() > 1 ? Math.min(MAX_ROW, Math.max(CELL + 2, (REGION_H - CELL) / (layout.rows() - 1))) : 0;
        int width = (layout.columns() - 1) * columnStep + CELL;
        int x0 = left + REGION_X + (REGION_W - width) / 2;
        int yMiddle = top + REGION_Y + REGION_H / 2 - CELL / 2;
        var spots = new int[graph.nodes().size() + graph.drinks().size()][];
        for (var entry : layout.spots().entrySet())
            spots[entry.getKey()] = new int[] { x0 + entry.getValue().column() * columnStep, yMiddle + Math.round(entry.getValue().y() * rowStep) };
        return spots;
    }

    private float centerX(int left)
    {
        return left + REGION_X + REGION_W / 2F;
    }

    private float centerY(int top)
    {
        return top + REGION_Y + REGION_H / 2F;
    }

    /**
     * @return a point on screen in the graph's own space, before zooming and panning
     */
    private float[] toGraph(int left, int top, double mouseX, double mouseY)
    {
        return new float[] { (float) ((mouseX - centerX(left) - panX) / zoom + centerX(left)), (float) ((mouseY - centerY(top) - panY) / zoom + centerY(top)) };
    }

    private static boolean inRegion(int left, int top, double mouseX, double mouseY)
    {
        return mouseX >= left + REGION_X && mouseX < left + REGION_X + REGION_W && mouseY >= top + REGION_Y && mouseY < top + REGION_Y + REGION_H;
    }

    private int stateColor(WorksGraph.Node node, @Nullable WorksPayloads.Job shown)
    {
        if (node.lacking() > 0 || waitsOnStation(node, shown))
            return LACK_COLOR;
        if (!node.isMade())
            return KNOWN_COLOR;
        if (shown == null)
            return FRONTIER_COLOR;
        if (shown.working().getOrDefault(node.recipe(), 0L) > 0)
            return ACTIVE_COLOR;
        return shown.left(node.recipe()) > 0 ? FRONTIER_COLOR : KNOWN_COLOR;
    }

    private static int stationPip(int station)
    {
        if (station < 0)
            return 0;
        return switch (StationKind.values()[station])
        {
            case ALTAR -> ALTAR_PIP;
            case FORGE -> FORGE_PIP;
            case TABLE -> TABLE_PIP;
        };
    }

    // Drawing

    void extract(GuiGraphicsExtractor graphics, Font font, int left, int top, String query, int mouseX, int mouseY)
    {
        var graph = shownGraph();
        if (graph != null)
            extractGraph(graphics, font, graph, left, top, mouseX, mouseY);
        else if (roster)
            extractRoster(graphics, font, left, top, mouseX, mouseY);
        else
            extractList(graphics, font, left, top, query, mouseX, mouseY);
        extractJobs(graphics, font, left, top, mouseX, mouseY);
    }

    /**
     * Each loaded Officiant as a row: its station and where it stands, what it is doing, and for a forge what that build makes.
     */
    private void extractRoster(GuiGraphicsExtractor graphics, Font font, int left, int top, int mouseX, int mouseY)
    {
        var stations = menu.getRoster();
        rosterScroll = Math.clamp(rosterScroll, 0, Math.max(0, stations.size() - ROSTER_ROWS));
        ItemVariant hovered = null;
        for (int row = 0; row < ROSTER_ROWS && rosterScroll + row < stations.size(); row++)
        {
            var station = stations.get(rosterScroll + row);
            int x = left + REGION_X + 2;
            int y = top + REGION_Y + 2 + row * ROSTER_ROW;
            graphics.fill(x, y, x + REGION_W - 4, y + ROSTER_ROW - 2, PLATE_BACK);
            graphics.outline(x, y, REGION_W - 4, ROSTER_ROW - 2, RULE_COLOR);
            var stationIcon = stationIcon(station.station());
            graphics.item(stationIcon, x + 2, y + 2);

            var name = station.station() < 0 ? Component.translatable(MODID + ".works.roster.none") : stationIcon.getHoverName();
            var where = "  " + station.pos().getX() + ", " + station.pos().getY() + ", " + station.pos().getZ();
            graphics.text(font, font.plainSubstrByWidth(name.getString(), REGION_W - 26), x + 21, y + 2, KNOWN_COLOR, false);
            int nameWidth = font.width(font.plainSubstrByWidth(name.getString(), REGION_W - 26));
            if (21 + nameWidth + font.width(where) < REGION_W - 6)
                graphics.text(font, where, x + 21 + nameWidth, y + 2, MUTED_COLOR, false);

            var state = switch (station.status())
            {
                case WorksPayloads.Station.LAYING -> Component.translatable(MODID + ".works.roster.laying", station.making());
                case WorksPayloads.Station.WORKING -> Component.translatable(MODID + ".works.roster.working", station.making());
                case WorksPayloads.Station.GATHERING -> Component.translatable(MODID + ".works.roster.gathering");
                case WorksPayloads.Station.NO_FIELD -> Component.translatable(MODID + ".works.roster.no_field");
                default -> Component.translatable(MODID + ".works.roster.idle");
            };
            int iconsWidth = Math.min(ROSTER_ICONS, station.makes().size()) * 9;
            var pose = graphics.pose();
            int stateColor = switch (station.status())
            {
                case WorksPayloads.Station.IDLE -> MUTED_COLOR;
                case WorksPayloads.Station.GATHERING -> FRONTIER_COLOR;
                case WorksPayloads.Station.NO_FIELD -> LACK_COLOR;
                default -> ACTIVE_COLOR;
            };
            graphics.text(font, font.plainSubstrByWidth(state.getString(), REGION_W - 28 - iconsWidth), x + 21, y + 11, stateColor, false);

            // What a forge's build makes, as small icons at the row's end
            for (int icon = 0; icon < Math.min(ROSTER_ICONS, station.makes().size()); icon++)
            {
                int iconX = x + REGION_W - 6 - (icon + 1) * 9;
                pose.pushMatrix();
                pose.translate(iconX, y + 11);
                pose.scale(0.5F, 0.5F);
                graphics.item(station.makes().get(icon).toStack(), 0, 0);
                pose.popMatrix();
                if (mouseX >= iconX && mouseX < iconX + 8 && mouseY >= y + 11 && mouseY < y + 19)
                    hovered = station.makes().get(icon);
            }
        }
        if (hovered != null)
            graphics.setTooltipForNextFrame(font, hovered.toStack(), mouseX, mouseY);
    }

    private void extractList(GuiGraphicsExtractor graphics, Font font, int left, int top, String query, int mouseX, int mouseY)
    {
        var items = listed(query);
        scroll = Math.clamp(scroll, 0, Math.max(0, (items.size() + HoardScreen.COLUMNS - 1) / HoardScreen.COLUMNS - LIST_ROWS));
        var hovered = listedAt(query, left, top, mouseX, mouseY);
        for (int row = 0; row < LIST_ROWS; row++)
            for (int column = 0; column < HoardScreen.COLUMNS; column++)
            {
                int index = (scroll + row) * HoardScreen.COLUMNS + column;
                if (index >= items.size())
                    continue;
                int x = left + REGION_X + column * CELL;
                int y = top + REGION_Y + row * CELL;
                var item = items.get(index);
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, PLATE_BACK);
                graphics.outline(x + 1, y + 1, CELL - 2, CELL - 2, item.equals(hovered) ? FRONTIER_COLOR : RULE_COLOR);
                graphics.item(item.toStack(), x + 1, y + 1);
            }
        int rows = (items.size() + HoardScreen.COLUMNS - 1) / HoardScreen.COLUMNS;
        if (rows > LIST_ROWS)
        {
            int trackX = left + REGION_X + HoardScreen.COLUMNS * CELL + 1;
            int trackH = LIST_ROWS * CELL;
            int barH = Math.max(6, trackH * LIST_ROWS / rows);
            int barY = top + REGION_Y + (trackH - barH) * scroll / (rows - LIST_ROWS);
            graphics.fill(trackX, top + REGION_Y, trackX + 2, top + REGION_Y + trackH, BAR_BACK);
            graphics.fill(trackX, barY, trackX + 2, barY + barH, SCROLL_COLOR);
        }
        if (hovered != null)
            graphics.setTooltipForNextFrame(font, hovered.toStack(), mouseX, mouseY);
    }

    private void extractGraph(GuiGraphicsExtractor graphics, Font font, WorksGraph graph, int left, int top, int mouseX, int mouseY)
    {
        var shown = shownJob();
        var spots = spots(graph, left, top);
        var mote = (Util.getMillis() % MOTE_MILLIS) / (float) MOTE_MILLIS;
        var reserve = menu.getPlan() == null || shown != null ? Map.<String, Long>of() : menu.getPlan().reserve();

        // Everything is drawn in the graph's own space, zoomed and panned about the hole's middle and kept inside it
        graphics.enableScissor(left + REGION_X, top + REGION_Y, left + REGION_X + REGION_W, top + REGION_Y + REGION_H);
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(centerX(left) + panX, centerY(top) + panY);
        pose.scale(zoom, zoom);
        pose.translate(-centerX(left), -centerY(top));
        var mouse = inRegion(left, top, mouseX, mouseY) ? toGraph(left, top, mouseX, mouseY) : new float[] { Float.NaN, Float.NaN };

        for (var edge : graph.edges())
            extractArc(graphics, font, spots[edge.ingredient()], spots[edge.product()], edge.count(), RULE_COLOR,
                    shown != null && stateColor(graph.nodes().get(edge.product()), shown) == ACTIVE_COLOR ? mote : -1);
        for (int leaf = 0; leaf < graph.drinks().size(); leaf++)
        {
            var drink = graph.drinks().get(leaf);
            extractArc(graphics, font, spots[graph.nodes().size() + leaf], spots[drink.node()], 0, aspectColor(drink.aspect()) & 0x80FFFFFF | 0x80000000, -1);
        }

        Object hovered = null;
        for (int index = 0; index < graph.nodes().size(); index++)
        {
            var spot = spots[index];
            if (spot == null)
                continue;
            var node = graph.nodes().get(index);
            graphics.fill(spot[0] + 1, spot[1] + 1, spot[0] + CELL - 1, spot[1] + CELL - 1, PLATE_BACK);
            graphics.outline(spot[0] + 1, spot[1] + 1, CELL - 2, CELL - 2, stateColor(node, shown));
            var pip = stationPip(node.station());
            if (pip != 0)
                graphics.fill(spot[0] + 1, spot[1] + 1, spot[0] + 3, spot[1] + 3, pip);
            graphics.item(node.item().toStack(), spot[0] + 1, spot[1] + 1);
            boolean wanting = node.lacking() > 0 || waitsOnStation(node, shown);
            HoardScreen.extractCount(graphics, font, HoardScreen.countText(node.needed()), spot[0] + CELL - 1, spot[1] + CELL - 1,
                    wanting ? LACK_COLOR : HoardScreen.COUNT_COLOR);
            if (wanting)
                extractWant(graphics, spot[0] + 1, spot[1] + 1);
            if (mouse[0] >= spot[0] && mouse[0] < spot[0] + CELL && mouse[1] >= spot[1] && mouse[1] < spot[1] + CELL)
                hovered = node;
        }

        // The essence each kind's crafts drink, as leaves in the aspect's colour
        for (int leaf = 0; leaf < graph.drinks().size(); leaf++)
        {
            var spot = spots[graph.nodes().size() + leaf];
            if (spot == null)
                continue;
            var drink = graph.drinks().get(leaf);
            int color = aspectColor(drink.aspect());
            graphics.fill(spot[0] + 1, spot[1] + 1, spot[0] + CELL - 1, spot[1] + CELL - 1, LEAF_BACK);
            boolean short_ = shown == null && reserveShort(graph, reserve, drink.aspect());
            graphics.outline(spot[0] + 1, spot[1] + 1, CELL - 2, CELL - 2, short_ ? LACK_COLOR : KNOWN_COLOR);
            graphics.fill(spot[0] + 5, spot[1] + 4, spot[0] + CELL - 5, spot[1] + CELL - 4, color);
            graphics.fill(spot[0] + 4, spot[1] + 5, spot[0] + CELL - 4, spot[1] + CELL - 5, color);
            HoardScreen.extractCount(graphics, font, HoardScreen.countText(drink.amount()), spot[0] + CELL - 1, spot[1] + CELL - 1,
                    short_ ? LACK_COLOR : HoardScreen.COUNT_COLOR);
            if (short_)
                extractWant(graphics, spot[0] + 1, spot[1] + 1);
            if (mouse[0] >= spot[0] && mouse[0] < spot[0] + CELL && mouse[1] >= spot[1] && mouse[1] < spot[1] + CELL)
                hovered = drink;
        }
        pose.popMatrix();
        // What is panned or zoomed past the torn edge goes under the stone, not over it
        if (rimOverlay != null)
            rimOverlay.accept(graphics);
        graphics.disableScissor();

        if (hovered instanceof WorksGraph.Node node)
        {
            var lines = tooltip(node, shown);
            if (waitsOnStation(node, shown))
                lines.add(unservedLine(StationKind.values()[node.station()]));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
        else if (hovered instanceof WorksGraph.Drink drink)
        {
            var lines = new ArrayList<Component>();
            lines.add(aspectName(drink.aspect()));
            lines.add(Component.translatable(MODID + ".works.node.drinks", drink.amount()).withColor(MUTED_COLOR));
            if (reserve.containsKey(drink.aspect()))
                lines.add(Component.translatable(MODID + ".works.node.reserve", reserve.get(drink.aspect())).withColor(KNOWN_COLOR));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    /**
     * A small rose notch in a plate's top left corner, so what is wanting reads without telling colours apart.
     */
    private static void extractWant(GuiGraphicsExtractor graphics, int x, int y)
    {
        graphics.fill(x, y, x + 4, y + 1, LACK_COLOR);
        graphics.fill(x, y, x + 1, y + 4, LACK_COLOR);
        graphics.fill(x + 1, y + 1, x + 3, y + 2, LACK_COLOR);
        graphics.fill(x + 1, y + 1, x + 2, y + 3, LACK_COLOR);
    }

    /**
     * A curve from one plate's right to another's left, its count at its middle, and a mote along it if one runs.
     *
     * @param mote how far along the mote is, or negative for none
     */
    private void extractArc(GuiGraphicsExtractor graphics, Font font, int @Nullable [] from, int @Nullable [] to, long count, int color, float mote)
    {
        if (from == null || to == null)
            return;
        float x1 = from[0] + CELL, y1 = from[1] + CELL / 2F, x2 = to[0], y2 = to[1] + CELL / 2F;
        for (int i = 0; i <= ARC_SAMPLES; i++)
        {
            var point = arc(x1, y1, x2, y2, i / (float) ARC_SAMPLES);
            graphics.fill((int) point[0], (int) point[1], (int) point[0] + 1, (int) point[1] + 1, color);
        }
        if (count > 1)
        {
            var middle = arc(x1, y1, x2, y2, 0.5F);
            var text = HoardScreen.countText(count);
            HoardScreen.extractCount(graphics, font, text, (int) middle[0] + font.width(text) / 4 + 1, (int) middle[1] - 1);
        }
        if (mote >= 0)
        {
            var point = arc(x1, y1, x2, y2, mote);
            graphics.fill((int) point[0] - 1, (int) point[1] - 1, (int) point[0] + 1, (int) point[1] + 1, ACTIVE_COLOR);
        }
    }

    private static int aspectColor(String aspect)
    {
        var key = net.minecraft.resources.Identifier.tryParse(aspect);
        if (key == null || !EssenceRegistry.ESSENCE_REGISTRY.containsKey(key))
            return MUTED_COLOR;
        return 0xFF000000 | net.voxelindustry.voidheart.client.render.VoidBeamRender.aspectColor(EssenceRegistry.ESSENCE_REGISTRY.getValue(key));
    }

    /**
     * @return a row for each kind the plan shown lacks, its icon, how many and its name, for the whisper's tooltip
     */
    List<ItemRowsTooltip.Row> lackingRows()
    {
        var graph = shownGraph();
        if (graph == null || shownJob() != null)
            return List.of();
        return graph.nodes().stream().filter(node -> node.lacking() > 0)
                .map(node -> new ItemRowsTooltip.Row(node.item().toStack(), node.lacking(), LACK_COLOR)).toList();
    }

    private static ArrayList<Component> tooltip(WorksGraph.Node node, @Nullable WorksPayloads.Job shown)
    {
        var lines = new ArrayList<Component>();
        lines.add(node.item().toStack().getHoverName());
        lines.add(Component.translatable(MODID + ".works.node.needs", node.needed()).withColor(MUTED_COLOR));
        if (node.fromStock() > 0)
            lines.add(Component.translatable(MODID + ".works.node.from_hoard", node.fromStock()).withColor(KNOWN_COLOR));
        if (node.isMade())
        {
            var station = Component.translatable(MODID + ".works.station." + StationKind.values()[node.station()].name().toLowerCase(Locale.ROOT));
            lines.add(Component.translatable(MODID + ".works.node.crafts", node.crafts(), station).withColor(FRONTIER_COLOR));
            if (shown != null)
                lines.add(Component.translatable(MODID + ".works.node.left", shown.left(node.recipe())).withColor(ACTIVE_COLOR));
        }
        if (node.lacking() > 0)
            lines.add(Component.translatable(MODID + ".works.node.lacking", node.lacking()).withColor(LACK_COLOR));
        return lines;
    }

    private static float[] arc(float x1, float y1, float x2, float y2, float t)
    {
        float middle = (x1 + x2) / 2;
        float u = 1 - t;
        float x = u * u * u * x1 + 3 * u * u * t * middle + 3 * u * t * t * middle + t * t * t * x2;
        float y = u * u * u * y1 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y2;
        return new float[] { x, y };
    }

    /**
     * @return the left of the slab of orders
     */
    static int sideSlab(int left)
    {
        return left - SIDE_SLAB_W - SIDE_GAP;
    }

    private static int sideLeft(int left)
    {
        return sideSlab(left) + SIDE_PAD + 2;
    }

    private static int rowTop(int top, int row)
    {
        return top + REGION_Y + 1 + row * JOB_ROW;
    }

    /**
     * Each order running as a row of the panel left of the slab: an icon of what it makes, how many, a bar of the crafts done, and what it waits on.
     */
    private void extractJobs(GuiGraphicsExtractor graphics, Font font, int left, int top, int mouseX, int mouseY)
    {
        var jobs = menu.getJobs();
        jobScroll = Math.clamp(jobScroll, 0, Math.max(0, jobs.size() - JOB_ROWS));
        int x = sideLeft(left);
        // Titled as the main slab is, the count of orders where the main slab has its search
        int titleX = sideSlab(left) + SIDE_PAD + 2;
        graphics.text(font, Component.translatable(MODID + ".works.side.title"), titleX, top + 8, KNOWN_COLOR, false);
        if (!jobs.isEmpty())
        {
            var count = jobs.size() > JOB_ROWS ? (jobScroll + 1) + "-" + Math.min(jobs.size(), jobScroll + JOB_ROWS) + " / " + jobs.size()
                    : String.valueOf(jobs.size());
            graphics.text(font, count, titleX, top + 21, MUTED_COLOR, false);
        }
        if (jobs.isEmpty())
        {
            var empty = Component.translatable(MODID + ".works.side.empty").getString();
            graphics.text(font, empty, x + (SIDE_W - font.width(empty)) / 2, top + REGION_Y + REGION_H / 2 - 4, MUTED_COLOR, false);
            return;
        }

        drawnRows.clear();
        for (int row = 0; row < JOB_ROWS && jobScroll + row < jobs.size(); row++)
        {
            var job = jobs.get(jobScroll + row);
            drawnRows.add(job.id());
            int y = rowTop(top, row);
            boolean over = mouseX >= x && mouseX < x + SIDE_W && mouseY >= y && mouseY < y + JOB_ROW - 2;
            boolean selected = this.job != null && this.job == job.id();
            long total = job.graph().nodes().stream().filter(WorksGraph.Node::isMade).mapToLong(WorksGraph.Node::crafts).sum();
            long done = Math.max(0, total - job.left());
            boolean working = job.working().values().stream().anyMatch(count -> count > 0);
            boolean blocked = !unserved(job.graph(), job).isEmpty() || !working && job.graph().nodes().stream().anyMatch(node -> node.lacking() > 0);
            int stateRgb = working ? ResearchScreen.ACTIVE_RGB : ResearchScreen.FRONTIER_RGB;

            // The queue as one branch: a thread from each order down to the next
            if (row + 1 < JOB_ROWS && jobScroll + row + 1 < jobs.size())
                graphics.fill(x + DISC / 2, y + DISC, x + DISC / 2 + 1, y + JOB_ROW, THREAD_COLOR);

            // Glows as an active research does while it is made, steady when chosen, faintly when the cursor is on it
            int glow = 0;
            if (selected)
                glow = 200;
            else if (working)
                glow = (int) (40 + 150 * (0.5F + 0.5F * (float) Math.sin(Util.getMillis() * Math.PI * 2 / GLOW_MILLIS + job.id())));
            else if (over)
                glow = 110;
            if (glow > 0)
                ResearchMapView.sprite(graphics, DISC_GLOW, x, y, net.minecraft.util.ARGB.color(glow, selected ? ResearchScreen.ACTIVE_RGB : stateRgb));

            int disc = blocked ? DISC_BLOCKED : working ? DISC_RUNNING : total > 0 && done >= total ? DISC_DONE : DISC_WAITING;
            ResearchMapView.sprite(graphics, disc, x, y, 0xFFFFFFFF);
            if (total > 0 && done > 0 && done < total)
            {
                int filled = (int) Math.ceil(DISC * done / (double) total);
                graphics.enableScissor(x, y + DISC - filled, x + DISC, y + DISC);
                ResearchMapView.sprite(graphics, DISC_FILL, x, y, net.minecraft.util.ARGB.color(working ? 170 : 90, stateRgb));
                graphics.disableScissor();
            }
            if (!job.graph().nodes().isEmpty())
            {
                int iconX = x + (DISC - 16) / 2;
                int iconY = y + (DISC - 16) / 2;
                graphics.item(job.graph().nodes().getFirst().item().toStack(), iconX, iconY);
                // A blocked order's icon sinks into its disc, its corners rounded off to stay inside the rim
                if (blocked)
                {
                    graphics.fill(iconX, iconY + 1, iconX + 16, iconY + 15, DIM_COLOR);
                    graphics.fill(iconX + 1, iconY, iconX + 15, iconY + 1, DIM_COLOR);
                    graphics.fill(iconX + 1, iconY + 15, iconX + 15, iconY + 16, DIM_COLOR);
                }
            }

            // Its name, a thread lit as far as its crafts have got, and what it waits on, straight on the void as research names are
            // Kept clear of the hole's torn edge
            int textW = SIDE_W - TEXT_X - 5;
            var name = job.count() + " " + job.what();
            graphics.text(font, font.plainSubstrByWidth(name, textW), x + TEXT_X, y + 2, selected ? ACTIVE_COLOR : KNOWN_COLOR, false);
            graphics.fill(x + TEXT_X, y + 12, x + TEXT_X + textW, y + 13, RULE_COLOR);
            if (total > 0)
                graphics.fill(x + TEXT_X, y + 12, x + TEXT_X + (int) (textW * done / total), y + 13, 0xFF000000 | stateRgb);

            var status = jobStatus(job, working);
            var color = status.getStyle().getColor() == null ? MUTED_COLOR : status.getStyle().getColor().getValue() | 0xFF000000;
            var lines = font.split(status, textW);
            for (int line = 0; line < Math.min(2, lines.size()); line++)
                graphics.text(font, lines.get(line), x + TEXT_X, y + 16 + line * (font.lineHeight + 1), color, false);
            // A name cut short by the hole's edge reads whole under the cursor
            if (over)
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.literal(name), status, Component.translatable(MODID + ".works.side.crafts", done, total)
                        .withColor(MUTED_COLOR)), mouseX, mouseY);
        }
    }

    /**
     * @return what an order is at: waiting on a station no Officiant serves, being made, waiting for ingredients, or waiting its turn
     */
    private Component jobStatus(WorksPayloads.Job job, boolean working)
    {
        var unserved = unserved(job.graph(), job);
        if (!unserved.isEmpty())
            return unservedLine(unserved.getFirst());
        if (working)
            return Component.translatable(MODID + ".works.job.working").withColor(ACTIVE_COLOR);
        if (job.graph().nodes().stream().anyMatch(node -> node.lacking() > 0))
            return Component.translatable(MODID + ".works.job.lacking").withColor(LACK_COLOR);
        // What the Officiants serving its stations are at, when none of them is free to take it
        var kinds = job.graph().nodes().stream().filter(node -> node.isMade() && job.left(node.recipe()) > 0).map(WorksGraph.Node::station).distinct().toList();
        var serving = menu.getRoster().stream().filter(station -> kinds.contains(station.station())).toList();
        if (!serving.isEmpty() && serving.stream().allMatch(station -> station.status() == WorksPayloads.Station.NO_FIELD))
            return Component.translatable(MODID + ".works.job.no_field").withColor(LACK_COLOR);
        // A station working another order is busy; only Officiants still gathering energy say so
        boolean busy = serving.stream().anyMatch(station -> station.status() == WorksPayloads.Station.WORKING || station.status() == WorksPayloads.Station.LAYING);
        boolean idle = serving.stream().anyMatch(station -> station.status() == WorksPayloads.Station.IDLE);
        if (!busy && !idle && serving.stream().anyMatch(station -> station.status() == WorksPayloads.Station.GATHERING))
            return Component.translatable(MODID + ".works.job.gathering").withColor(FRONTIER_COLOR);
        return Component.translatable(MODID + ".works.job.waiting").withColor(MUTED_COLOR);
    }

    /**
     * @return the line under the controls: what a click does here, or what the plan shown needs
     */
    Component whisper(String query)
    {
        var shown = shownJob();
        if (shown != null)
        {
            var left = shown.remaining().values().stream().mapToLong(Long::longValue).sum() + shown.working().values().stream().mapToLong(Long::longValue).sum();
            return Component.translatable(MODID + ".works.whisper.job", shown.count(), shown.what(), left);
        }
        var graph = shownGraph();
        if (graph != null)
        {
            var unserved = unserved(graph, null);
            if (!unserved.isEmpty())
                return unservedLine(unserved.getFirst());
            if (graph.nodes().stream().anyMatch(node -> node.lacking() > 0))
                return Component.translatable(MODID + ".works.whisper.lacking").withColor(LACK_COLOR);
            if (!graph.essence().isEmpty())
            {
                var drink = graph.essence().entrySet().iterator().next();
                var plan = menu.getPlan();
                var reserve = plan == null || shownJob() != null ? null : plan.reserve().get(drink.getKey());
                if (reserve != null && reserve > 0)
                    return Component.translatable(MODID + ".works.whisper.drinks_reserve", drink.getValue(), aspectName(drink.getKey()), HoardScreen.countText(reserve));
                return Component.translatable(MODID + ".works.whisper.drinks", drink.getValue(), aspectName(drink.getKey()));
            }
            return Component.translatable(MODID + ".works.whisper.ready");
        }
        if (roster)
            return menu.getRoster().isEmpty() ? Component.translatable(MODID + ".works.whisper.no_officiant")
                    : Component.translatable(MODID + ".works.whisper.roster", menu.getRoster().size());
        if (menu.getMakeable().isEmpty())
            return Component.translatable(MODID + ".works.whisper.no_station");
        return Component.translatable(MODID + ".works.whisper.choose");
    }

    private static Component aspectName(String id)
    {
        var key = net.minecraft.resources.Identifier.tryParse(id);
        if (key != null && EssenceRegistry.ESSENCE_REGISTRY.containsKey(key))
            return Component.translatable(EssenceRegistry.ESSENCE_REGISTRY.getValue(key).translatableName());
        return Component.literal(id);
    }

    // Input

    @Nullable
    ItemVariant listedAt(String query, int left, int top, double mouseX, double mouseY)
    {
        if (shownGraph() != null || roster || mouseX < left + REGION_X || mouseX >= left + REGION_X + HoardScreen.COLUMNS * CELL
                || mouseY < top + REGION_Y || mouseY >= top + REGION_Y + LIST_ROWS * CELL)
            return null;
        int column = (int) (mouseX - left - REGION_X) / CELL;
        int row = (int) (mouseY - top - REGION_Y) / CELL;
        var items = listed(query);
        int index = (scroll + row) * HoardScreen.COLUMNS + column;
        return index < items.size() ? items.get(index) : null;
    }

    /**
     * @return whether the click was on the works view
     */
    boolean clicked(String query, int left, int top, double mouseX, double mouseY)
    {
        var item = listedAt(query, left, top, mouseX, mouseY);
        if (item != null)
        {
            choose(item);
            return true;
        }
        int x = sideLeft(left);
        for (int row = 0; row < drawnRows.size(); row++)
        {
            int y = rowTop(top, row);
            if (mouseX < x || mouseX >= x + SIDE_W || mouseY < y || mouseY >= y + JOB_ROW - 2)
                continue;
            int id = drawnRows.get(row);
            job = job != null && job == id ? null : id;
            return true;
        }
        return false;
    }

    void choose(ItemVariant item)
    {
        chosen = item;
        job = null;
        ask();
    }

    void ask()
    {
        if (chosen != null)
            ClientPlayNetworking.send(new WorksPayloads.AskPlan(menu.containerId, chosen, amount));
    }

    void back()
    {
        if (job != null)
            job = null;
        else if (chosen == null && roster)
            roster = false;
        else
        {
            chosen = null;
            menu.clearPlan();
        }
    }

    void changeAmount(int sign)
    {
        var step = Minecraft.getInstance().hasShiftDown() ? 16 : 1;
        amount = Math.clamp(amount + (long) sign * step, 1, 4096);
        ask();
    }

    void typeAmount(long typed)
    {
        var clamped = Math.clamp(typed, 1, 4096);
        if (clamped == amount)
            return;
        amount = clamped;
        ask();
    }

    /**
     * @return whether the plan shown lacks something to begin: an ingredient, a station no Officiant serves, or essence the reserve can't pay
     */
    boolean beginWarns()
    {
        var graph = shownGraph();
        if (graph == null || shownJob() != null)
            return false;
        var reserve = menu.getPlan() == null ? Map.<String, Long>of() : menu.getPlan().reserve();
        return graph.nodes().stream().anyMatch(node -> node.lacking() > 0) || !unserved(graph, null).isEmpty()
                || graph.essence().keySet().stream().anyMatch(aspect -> reserveShort(graph, reserve, aspect));
    }

    /**
     * @return whether the reserve holds less of an aspect than all the plan's crafts drink of it together
     */
    private static boolean reserveShort(WorksGraph graph, Map<String, Long> reserve, String aspect)
    {
        return reserve.getOrDefault(aspect, 0L) < graph.essence().getOrDefault(aspect, 0L);
    }

    /**
     * @return whether the plan shown is the one for the item and amount chosen, so beginning or keeping it asks for what is shown
     */
    boolean planCurrent()
    {
        var plan = menu.getPlan();
        return chosen != null && plan != null && plan.item().equals(chosen) && plan.count() == amount;
    }

    void begin()
    {
        if (!planCurrent())
            return;
        ClientPlayNetworking.send(new WorksPayloads.Begin(menu.containerId, chosen, amount));
        chosen = null;
        menu.clearPlan();
    }

    void keep()
    {
        if (!planCurrent())
            return;
        var kept = menu.getKeeps().stream().anyMatch(keep -> keep.item().equals(chosen) && keep.level() == amount);
        ClientPlayNetworking.send(new WorksPayloads.Keep(menu.containerId, chosen, kept ? 0 : amount));
    }

    void cancelShown()
    {
        if (job != null)
            ClientPlayNetworking.send(new WorksPayloads.Cancel(menu.containerId, job));
        job = null;
    }

    boolean isKept()
    {
        return chosen != null && menu.getKeeps().stream().anyMatch(keep -> keep.item().equals(chosen) && keep.level() == amount);
    }

    /**
     * The wheel scrolls the panel of orders under it, zooms a graph about the hole's middle, and scrolls the lists.
     */
    boolean scrolled(int left, int top, double mouseX, double mouseY, double scrollY)
    {
        if (mouseX >= sideLeft(left) && mouseX < sideLeft(left) + SIDE_W && mouseY >= top + REGION_Y && mouseY < rowTop(top, JOB_ROWS))
        {
            jobScroll = Math.max(0, jobScroll - (int) Math.signum(scrollY));
            return true;
        }
        if (shownGraph() != null)
        {
            var before = zoom;
            zoom = Math.clamp(scrollY > 0 ? zoom * ZOOM_STEP : zoom / ZOOM_STEP, MIN_ZOOM, MAX_ZOOM);
            panX *= zoom / before;
            panY *= zoom / before;
            return true;
        }
        if (roster)
            rosterScroll = Math.max(0, rosterScroll - (int) Math.signum(scrollY));
        else
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
        return true;
    }

    /**
     * Dragging a graph pans it, never so far that it leaves the hole.
     */
    void dragged(double dragX, double dragY)
    {
        float reachX = REGION_W * zoom;
        float reachY = REGION_H * zoom;
        panX = Math.clamp(panX + (float) dragX, -reachX, reachX);
        panY = Math.clamp(panY + (float) dragY, -reachY, reachY);
    }

    boolean graphAt(int left, int top, double mouseX, double mouseY)
    {
        return shownGraph() != null && inRegion(left, top, mouseX, mouseY);
    }

    // For tests to aim at

    /**
     * @return the middle of an order's row in the panel, scrolling it into view
     */
    int[] jobCenter(int index, int left, int top)
    {
        if (index < jobScroll)
            jobScroll = index;
        else if (index >= jobScroll + JOB_ROWS)
            jobScroll = index - JOB_ROWS + 1;
        return new int[] { sideLeft(left) + SIDE_W / 2, rowTop(top, index - jobScroll) + (JOB_ROW - 2) / 2 };
    }

    @Nullable
    int[] listedCenter(ItemVariant item, String query, int left, int top)
    {
        int index = listed(query).indexOf(item);
        if (index < 0)
            return null;
        int row = index / HoardScreen.COLUMNS;
        if (row < scroll)
            scroll = row;
        else if (row >= scroll + LIST_ROWS)
            scroll = row - LIST_ROWS + 1;
        return new int[] { left + REGION_X + index % HoardScreen.COLUMNS * CELL + CELL / 2, top + REGION_Y + (row - scroll) * CELL + CELL / 2 };
    }

    @Nullable
    int[] nodeCenter(int node, int left, int top)
    {
        var graph = shownGraph();
        if (graph == null || node >= graph.nodes().size() + graph.drinks().size())
            return null;
        var spot = spots(graph, left, top)[node];
        if (spot == null)
            return null;
        // From the graph's own space to the screen, through the zoom and pan
        float x = (spot[0] + CELL / 2F - centerX(left)) * zoom + centerX(left) + panX;
        float y = (spot[1] + CELL / 2F - centerY(top)) * zoom + centerY(top) + panY;
        return new int[] { Math.round(x), Math.round(y) };
    }
}
