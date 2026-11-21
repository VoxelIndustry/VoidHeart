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
 * lilac is done or taken from the hoard, rose is lacking, and motes run along the arcs into what is being worked.
 * <p>
 * Per frame: up to 72 plates in the list, or a graph of a few dozen plates and arcs sampled every few pixels; the layout is worked out only when the
 * plan or order shown changes.
 */
final class WorksView
{
    static final int REGION_X = HoardScreen.PAD;
    static final int REGION_Y = 35;
    static final int REGION_W = HoardScreen.COLUMNS * HoardScreen.CELL + 6;
    static final int REGION_H = 146;
    static final int CONTROLS_Y = REGION_Y + REGION_H + 3;
    static final int JOBS_Y     = CONTROLS_Y + 15;

    private static final int CELL         = HoardScreen.CELL;
    private static final int LIST_ROWS    = REGION_H / CELL;
    private static final int MAX_COLUMN   = 30;
    private static final int MAX_ROW      = 22;
    private static final int ARC_SAMPLES  = 18;
    private static final long MOTE_MILLIS = 1400;
    static final int JOB_PLATE_W  = 26;

    private static final int PLATE_BACK = 0xB00B0810;
    private static final int LEAF_BACK  = 0xD0070509;
    private static final float MIN_ZOOM  = 0.5F;
    private static final float MAX_ZOOM  = 2.5F;
    private static final float ZOOM_STEP = 1.2F;
    private static final int ROSTER_ROW = 20;
    private static final int ROSTER_ROWS = REGION_H / ROSTER_ROW;
    private static final int ROSTER_ICONS = 4;
    private static final int ALTAR_PIP  = 0xFF8E6BE8;
    private static final int FORGE_PIP  = 0xFFE08A5E;
    private static final int TABLE_PIP  = 0xFF36C5B0;

    private final HoardMenu menu;
    @Nullable
    ItemVariant chosen;
    long amount = 1;
    @Nullable
    Integer job;
    private int scroll;
    /** Whether the roster of Officiants is shown instead of what they can make. */
    boolean roster;
    private int rosterScroll;

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
            return shown.graph();
        var plan = menu.getPlan();
        return chosen != null && plan != null && plan.item().equals(chosen) ? plan.graph() : null;
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
        if (node.lacking() > 0)
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
            var pip = stationPip(station.station());
            graphics.fill(x + 2, y + 2, x + 5, y + ROSTER_ROW - 4, pip == 0 ? LACK_COLOR : pip);

            var name = station.station() < 0 ? Component.translatable(MODID + ".works.roster.none")
                    : Component.translatable(MODID + ".works.roster.station." + StationKind.values()[station.station()].name().toLowerCase(Locale.ROOT));
            var where = " " + station.pos().getX() + ", " + station.pos().getY() + ", " + station.pos().getZ();
            graphics.text(font, font.plainSubstrByWidth(name.getString() + where, REGION_W - 16), x + 8, y + 1, KNOWN_COLOR, false);

            var state = switch (station.status())
            {
                case WorksPayloads.Station.LAYING -> Component.translatable(MODID + ".works.roster.laying", station.making());
                case WorksPayloads.Station.WORKING -> Component.translatable(MODID + ".works.roster.working", station.making());
                default -> Component.translatable(MODID + ".works.roster.idle");
            };
            int iconsWidth = Math.min(ROSTER_ICONS, station.makes().size()) * 9;
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(x + 8, y + 10);
            pose.scale(0.75F, 0.75F);
            graphics.text(font, font.plainSubstrByWidth(state.getString(), (int) ((REGION_W - 18 - iconsWidth) / 0.75F)), 0, 0,
                    station.status() == WorksPayloads.Station.IDLE ? MUTED_COLOR : ACTIVE_COLOR, false);
            pose.popMatrix();

            // What a forge's build makes, as small icons at the row's end
            for (int icon = 0; icon < Math.min(ROSTER_ICONS, station.makes().size()); icon++)
            {
                int iconX = x + REGION_W - 6 - (icon + 1) * 9;
                pose.pushMatrix();
                pose.translate(iconX, y + 9);
                pose.scale(0.5F, 0.5F);
                graphics.item(station.makes().get(icon).toStack(), 0, 0);
                pose.popMatrix();
                if (mouseX >= iconX && mouseX < iconX + 8 && mouseY >= y + 9 && mouseY < y + 17)
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
            HoardScreen.extractCount(graphics, font, HoardScreen.countText(node.needed()), spot[0] + CELL - 1, spot[1] + CELL - 1);
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
            graphics.outline(spot[0] + 1, spot[1] + 1, CELL - 2, CELL - 2, reserve.getOrDefault(drink.aspect(), 0L) >= drink.amount() ? KNOWN_COLOR : RULE_COLOR);
            graphics.fill(spot[0] + 5, spot[1] + 4, spot[0] + CELL - 5, spot[1] + CELL - 4, color);
            graphics.fill(spot[0] + 4, spot[1] + 5, spot[0] + CELL - 4, spot[1] + CELL - 5, color);
            HoardScreen.extractCount(graphics, font, HoardScreen.countText(drink.amount()), spot[0] + CELL - 1, spot[1] + CELL - 1);
            if (mouse[0] >= spot[0] && mouse[0] < spot[0] + CELL && mouse[1] >= spot[1] && mouse[1] < spot[1] + CELL)
                hovered = drink;
        }
        pose.popMatrix();
        graphics.disableScissor();

        if (hovered instanceof WorksGraph.Node node)
            graphics.setComponentTooltipForNextFrame(font, tooltip(node, shown), mouseX, mouseY);
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
     * @return a line for each kind the plan shown lacks, for the whisper's tooltip
     */
    List<Component> lackingLines()
    {
        var graph = shownGraph();
        if (graph == null || shownJob() != null)
            return List.of();
        return graph.nodes().stream().filter(node -> node.lacking() > 0)
                .map(node -> (Component) Component.translatable(MODID + ".works.lacking_line", node.lacking(), node.item().toStack().getHoverName()).withColor(LACK_COLOR))
                .toList();
    }

    private static List<Component> tooltip(WorksGraph.Node node, @Nullable WorksPayloads.Job shown)
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

    private void extractJobs(GuiGraphicsExtractor graphics, Font font, int left, int top, int mouseX, int mouseY)
    {
        var jobs = menu.getJobs();
        for (int index = 0; index < jobs.size() && index * (JOB_PLATE_W + 2) + JOB_PLATE_W <= REGION_W; index++)
        {
            var shown = jobs.get(index);
            int x = left + REGION_X + index * (JOB_PLATE_W + 2);
            int y = top + JOBS_Y;
            boolean selected = job != null && job == shown.id();
            boolean over = mouseX >= x && mouseX < x + JOB_PLATE_W && mouseY >= y && mouseY < y + 12;
            graphics.fill(x, y, x + JOB_PLATE_W, y + 12, PLATE_BACK);
            graphics.outline(x, y, JOB_PLATE_W, 12, selected ? ACTIVE_COLOR : over ? FRONTIER_COLOR : RULE_COLOR);
            var label = "#" + shown.id();
            graphics.text(font, label, x + (JOB_PLATE_W - font.width(label)) / 2, y + 2, selected ? ACTIVE_COLOR : MUTED_COLOR, false);
        }
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
            var lacking = graph.nodes().stream().filter(node -> node.lacking() > 0).toList();
            if (lacking.size() == 1)
                return Component.translatable(MODID + ".works.whisper.lacking_one", lacking.getFirst().lacking(), lacking.getFirst().item().toStack().getHoverName())
                        .withColor(LACK_COLOR);
            if (!lacking.isEmpty())
                return Component.translatable(MODID + ".works.whisper.lacking", lacking.getFirst().lacking(), lacking.getFirst().item().toStack().getHoverName(),
                        lacking.size() - 1).withColor(LACK_COLOR);
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
        var jobs = menu.getJobs();
        for (int index = 0; index < jobs.size(); index++)
        {
            int x = left + REGION_X + index * (JOB_PLATE_W + 2);
            int y = top + JOBS_Y;
            if (mouseX >= x && mouseX < x + JOB_PLATE_W && mouseY >= y && mouseY < y + 12)
            {
                job = job != null && job == jobs.get(index).id() ? null : jobs.get(index).id();
                return true;
            }
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

    void begin()
    {
        if (chosen == null)
            return;
        ClientPlayNetworking.send(new WorksPayloads.Begin(menu.containerId, chosen, amount));
        chosen = null;
        menu.clearPlan();
    }

    void keep()
    {
        if (chosen == null)
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
     * The wheel zooms a graph about the hole's middle, and scrolls the lists.
     */
    boolean scrolled(double scrollY)
    {
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
