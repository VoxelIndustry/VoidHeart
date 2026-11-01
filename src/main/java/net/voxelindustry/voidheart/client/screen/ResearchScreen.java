package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeMath;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads.Action;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryMenu;
import net.voxelindustry.voidheart.common.content.research.Research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * A dashboard of what a player knows. The map in the middle shows the tree as far as it has been read, the ledger on
 * the left what there is to spend, and the frontier on the right everything that can be done next.
 */
public class ResearchScreen extends AbstractContainerScreen<KnowledgeRepositoryMenu>
{
    public static KnowledgePayloads.Snapshot snapshot = KnowledgePayloads.Snapshot.EMPTY;

    static final int KNOWN_RGB    = 0xD6C9F5;
    static final int FRONTIER_RGB = 0x36C5B0;
    static final int ACTIVE_RGB   = 0xF4D670;
    static final int REVEALED_RGB = 0x8E6BE8;

    static final int KNOWN_COLOR    = 0xFF000000 | KNOWN_RGB;
    static final int FRONTIER_COLOR = 0xFF7CE8D6;
    static final int ACTIVE_COLOR   = 0xFF000000 | ACTIVE_RGB;
    static final int TEXT_COLOR     = 0xFFEDE6FF;
    static final int MUTED_COLOR    = 0xFFA899BC;
    static final int LACK_COLOR     = 0xFFEF8A98;
    static final int LID_COLOR      = 0xFF8E6BE8;
    static final int LID_LOW_COLOR  = 0xFF4A3860;

    private static final int RULE_COLOR     = 0xFF4A3860;
    private static final int RULE_CUT_COLOR = 0xFF08050C;
    private static final int SPENT_COLOR    = 0xFF8A7A99;
    private static final int BAR_BACK_COLOR = 0xFF08050C;
    private static final int BAR_COLOR      = 0xFF8E6BE8;
    private static final int ROW_HOVER      = 0x40FFFFFF;
    private static final int ROW_SELECTED   = 0x3036C5B0;
    private static final int SPENT_SHADE    = 0xB0100A18;
    private static final int CARD_TINT      = 0x20F4D670;
    private static final int CARD_HOVER     = 0x38F4D670;

    private static final int LEFT_MIN  = 112;
    private static final int LEFT_MAX  = 170;
    private static final int RIGHT_MIN = 124;
    private static final int RIGHT_MAX = 200;
    private static final int PAD       = 8;
    private static final int LINE      = 10;
    private static final int ROW_H     = 22;
    private static final int CARD_H    = 42;
    private static final int BUTTON_H  = 18;
    private static final int INSPECTOR_MIN = 60;
    private static final int DONE_GAP  = 5;
    private static final int FED_PITCH = 18;
    private static final int FED_CELL  = 46;

    private static final long LEARNED_MILLIS = 7000;
    private static final long FADE_MILLIS    = 1200;

    private static final Identifier VOID_A = Identifier.fromNamespaceAndPath(MODID, "dynamic/research_void");
    private static final Identifier VOID_B = Identifier.fromNamespaceAndPath(MODID, "dynamic/research_void_previous");

    enum NodeState
    {
        HIDDEN,
        UNKNOWN,
        REVEALED,
        ACTIVE,
        DONE
    }

    private Map<Identifier, Research> tree = Map.of();
    private ResearchMapView           map;
    private ResearchButton            refineButton;
    private ResearchButton            actionButton;
    private List<Identifier>          frontier = List.of();

    private Identifier                 voidCurrent = VOID_A;
    private Identifier                 voidPrevious = VOID_B;
    private KnowledgePayloads.Snapshot seen;
    private Identifier                 selected;
    private Identifier                 lastClicked;
    private Identifier                 learned;
    private long                       learnedAt;
    private long                       snapshotAt;
    private long                       bakedAt;
    private boolean                    hasPrevious;
    private boolean                    descriptionCut;
    private int                        listScroll;
    private int                        fedScroll;
    private int                        left;
    private int                        right;

    public ResearchScreen(KnowledgeRepositoryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init()
    {
        super.init();

        tree = Knowledge.tree(minecraft.level.registryAccess());
        left = Math.clamp((int) (width * 0.24F), LEFT_MIN, LEFT_MAX);
        right = width - Math.clamp((int) (width * 0.28F), RIGHT_MIN, RIGHT_MAX);
        map = new ResearchMapView(font, tree, left, right, height, seed());

        hasPrevious = false;
        bake(voidCurrent);
        rebuildFrontier();
        if (seen == null)
        {
            seen = snapshot;
            snapshotAt = Util.getMillis();
        }

        refineButton = addRenderableWidget(new ResearchButton(PAD, 0, left - PAD * 2, BUTTON_H, () -> snapshot.nextInsightCost() > 0 ? shownKnowledge() / snapshot.nextInsightCost() : 0,
                pressed ->
                {
                    setFocused(null);
                    send(Action.Kind.BUY_INSIGHT, null);
                }));
        actionButton = addRenderableWidget(new ResearchButton(right + PAD, height - PAD - BUTTON_H, width - right - PAD * 2, BUTTON_H, () -> 0, pressed ->
        {
            setFocused(null);
            act();
        }));
    }

    private long seed()
    {
        return minecraft.player == null ? 0 : minecraft.player.getUUID().getLeastSignificantBits();
    }

    private void bake(Identifier target)
    {
        ResearchVoid.bake(minecraft, target, width, height, left, right, map.lights(), map.rings(), seed());
    }

    private static void send(Action.Kind kind, Identifier research)
    {
        ClientPlayNetworking.send(new Action(kind, Optional.ofNullable(research)));
    }

    static NodeState stateOf(Identifier id, Research research)
    {
        if (snapshot.completed().contains(id))
            return NodeState.DONE;
        if (snapshot.active().filter(id::equals).isPresent())
            return NodeState.ACTIVE;
        if (snapshot.revealed().contains(id))
            return NodeState.REVEALED;
        if (research.requires().isEmpty() || research.requires().stream().anyMatch(snapshot.completed()::contains))
            return NodeState.UNKNOWN;
        return NodeState.HIDDEN;
    }

    static boolean canReveal(Research research)
    {
        return snapshot.insights() >= research.insightCost();
    }

    /**
     * What changed since the last frame gets its moment, and the light is redrawn once the tree has grown.
     */
    private void syncSnapshot()
    {
        if (snapshot == seen)
            return;

        var before = seen;
        seen = snapshot;
        snapshotAt = Util.getMillis();

        boolean grew = false;
        for (var id : snapshot.revealed())
        {
            if (!before.revealed().contains(id) && !before.completed().contains(id) && tree.containsKey(id))
            {
                map.bloom(id, false);
                selected = id;
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_CLICKING, 1.4F));
                grew = true;
            }
        }
        for (var id : snapshot.completed())
        {
            if (!before.completed().contains(id) && tree.containsKey(id))
            {
                map.bloom(id, true);
                learned = id;
                learnedAt = snapshotAt;
                grew = true;
            }
        }

        if (grew)
        {
            tree.forEach((id, research) ->
            {
                if (stateOf(id, research) == NodeState.UNKNOWN && research.requires().stream().anyMatch(requirement -> snapshot.completed().contains(requirement) && !before.completed().contains(requirement)))
                    map.appear(id);
            });
            swapVoids();
        }
        rebuildFrontier();
    }

    /**
     * The new light goes into the other texture, and the old one stays around to fade out over it.
     */
    private void swapVoids()
    {
        var fresh = voidPrevious;
        voidPrevious = voidCurrent;
        voidCurrent = fresh;
        bake(voidCurrent);
        hasPrevious = true;
        bakedAt = snapshotAt;
    }

    private void rebuildFrontier()
    {
        frontier = tree.entrySet().stream()
                .filter(entry -> stateOf(entry.getKey(), entry.getValue()) == NodeState.REVEALED || stateOf(entry.getKey(), entry.getValue()) == NodeState.UNKNOWN)
                .sorted(Comparator.<Map.Entry<Identifier, Research>>comparingInt(entry -> stateOf(entry.getKey(), entry.getValue()) == NodeState.REVEALED ? 0 : 1)
                        .thenComparingDouble(entry -> -snapshot.progress().getOrDefault(entry.getKey(), 0D) / entry.getValue().knowledgeCost())
                        .thenComparingInt(entry -> entry.getValue().insightCost())
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getKey)
                .toList();

        if (selected == null || !tree.containsKey(selected) || stateOf(selected, tree.get(selected)) == NodeState.HIDDEN)
            selected = snapshot.active().orElse(frontier.isEmpty() ? null : frontier.getFirst());
    }

    private double secondsSinceSnapshot()
    {
        return Math.min(1.5, (Util.getMillis() - snapshotAt) / 1000D);
    }

    private Optional<Research> activeResearch()
    {
        return snapshot.active().map(tree::get);
    }

    private double knowledgeChange()
    {
        var research = activeResearch();
        return research.isPresent() ? snapshot.rate() - research.get().knowledgePerSecond() : snapshot.rate();
    }

    private boolean starving()
    {
        return activeResearch().isPresent() && knowledgeChange() < 0 && shownKnowledge() <= 0.01;
    }

    private double shownKnowledge()
    {
        return Math.clamp(snapshot.knowledge() + knowledgeChange() * secondsSinceSnapshot(), 0, Math.max(snapshot.cap(), snapshot.knowledge()));
    }

    private double shownProgress(Identifier id, Research research)
    {
        double progress = snapshot.progress().getOrDefault(id, 0D);
        if (snapshot.active().filter(id::equals).isEmpty())
            return progress;
        return Math.min(research.knowledgeCost(), progress + (starving() ? snapshot.rate() : research.knowledgePerSecond()) * secondsSinceSnapshot());
    }

    private double secondsToFinish(Identifier id, Research research)
    {
        return KnowledgeMath.secondsToFinish(shownKnowledge(), snapshot.rate(), research.knowledgeCost() - shownProgress(id, research), research.knowledgePerSecond());
    }

    private static String number(double value)
    {
        return value >= 100 || Math.abs(value - Math.rint(value)) < 0.05 ? String.format("%.0f", value) : String.format("%.1f", value);
    }

    private static String wholeNumber(double value)
    {
        return String.format("%.0f", value);
    }

    private static String duration(double seconds)
    {
        long whole = (long) Math.ceil(seconds);
        if (whole < 60)
            return whole + "s";
        if (whole < 3600)
            return String.format("%dm %02ds", whole / 60, whole % 60);
        return String.format("%dh %02dm", whole / 3600, whole / 60 % 60);
    }

    private static Component insightCount(int count)
    {
        return Component.translatable(count == 1 ? "voidheart.research.insight_one" : "voidheart.research.insight_many", count);
    }

    private static Component ringOf(Research research)
    {
        return Component.translatableWithFallback("voidheart.research.ring." + research.tier(), "Ring " + research.tier());
    }

    private String fit(String text, int maxWidth)
    {
        if (font.width(text) <= maxWidth)
            return text;
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")).stripTrailing() + "...";
    }

    private void bigNumber(GuiGraphicsExtractor graphics, String text, int x, int y, int color)
    {
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(2);
        graphics.text(font, text, 0, 0, color);
        pose.popMatrix();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick)
    {
        syncSnapshot();

        graphics.blit(RenderPipelines.GUI_TEXTURED, voidCurrent, 0, 0, 0, 0, width, height, width, height);
        if (hasPrevious)
        {
            float fade = (Util.getMillis() - bakedAt) / (float) FADE_MILLIS;
            if (fade < 1)
                graphics.blit(RenderPipelines.GUI_TEXTURED, voidPrevious, 0, 0, 0, 0, width, height, width, height, ARGB.white(1 - fade));
            else
                hasPrevious = false;
        }

        float fullness = snapshot.cap() > 0 ? (float) Math.min(1, shownKnowledge() / snapshot.cap()) : 0;
        map.extract(graphics, mouseX, mouseY, voidCurrent, width, height, fullness, !starving(), selected, hoveredNode(mouseX, mouseY));

        extractLedger(graphics, mouseX, mouseY);
        extractFrontier(graphics, mouseX, mouseY);
        extractInspector(graphics);
    }

    private Identifier hoveredNode(int mouseX, int mouseY)
    {
        var row = rowAt(mouseX, mouseY);
        return row != null ? row : mouseX >= left && mouseX < right ? map.nodeAt(mouseX, mouseY).orElse(null) : null;
    }

    private void extractLedger(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        int width = left - PAD * 2;
        int y = PAD;

        graphics.text(font, Component.translatable("voidheart.research.knowledge"), PAD, y, MUTED_COLOR);
        y += LINE + 1;

        double knowledge = shownKnowledge();
        var amount = number(knowledge);
        bigNumber(graphics, amount, PAD, y, TEXT_COLOR);
        var cap = "/ " + wholeNumber(snapshot.cap());
        if (PAD + font.width(amount) * 2 + 3 + font.width(cap) <= left - PAD)
            graphics.text(font, cap, PAD + font.width(amount) * 2 + 3, y + 8, MUTED_COLOR);
        y += 19;

        double fill = snapshot.cap() > 0 ? Math.min(1, knowledge / snapshot.cap()) : 0;
        graphics.fill(PAD, y, PAD + width, y + 3, BAR_BACK_COLOR);
        graphics.fill(PAD, y, PAD + (int) (width * fill), y + 3, BAR_COLOR);
        y += 6;

        double change = knowledgeChange();
        boolean full = knowledge >= snapshot.cap() && change >= 0;
        graphics.text(font, (change >= 0 ? "+" : "") + String.format("%.2f", change) + "/s", PAD, y, change >= 0 ? FRONTIER_COLOR : ACTIVE_COLOR);
        y += LINE;
        if (full)
            graphics.text(font, fit(Component.translatable("voidheart.research.full").getString(), width), PAD, y, LACK_COLOR);
        else if (change > 0)
            graphics.text(font, fit(Component.translatable("voidheart.research.full_in", duration((snapshot.cap() - knowledge) / change)).getString(), width), PAD, y, MUTED_COLOR);
        else if (change < 0 && knowledge > 0)
            graphics.text(font, fit(Component.translatable("voidheart.research.empty_in", duration(knowledge / -change)).getString(), width), PAD, y, MUTED_COLOR);
        y += LINE + 4;

        rule(graphics, PAD, y, width);
        y += 5;

        graphics.text(font, Component.translatable("voidheart.research.insights"), PAD, y, MUTED_COLOR);
        y += LINE + 1;
        bigNumber(graphics, Integer.toString(snapshot.insights()), PAD, y, snapshot.insights() > 0 ? FRONTIER_COLOR : TEXT_COLOR);
        y += 20;

        extractRefine(graphics, y, width, knowledge);
        y += BUTTON_H + 2 + LINE + 3;

        rule(graphics, PAD, y, width);
        y += 5;

        extractExtracted(graphics, y, width, mouseX, mouseY);
    }

    /**
     * A groove cut in the stone: its dark side, then the lit lip under it.
     */
    private static void rule(GuiGraphicsExtractor graphics, int x, int y, int width)
    {
        graphics.fill(x, y - 1, x + width, y, RULE_CUT_COLOR);
        graphics.fill(x, y, x + width, y + 1, RULE_COLOR);
    }

    private void extractRefine(GuiGraphicsExtractor graphics, int y, int width, double knowledge)
    {
        double cost = snapshot.nextInsightCost();
        var label = Component.translatable("voidheart.research.refine", number(cost));
        if (font.width(label) > width - 6)
            label = Component.translatable("voidheart.research.refine_short", number(cost));

        refineButton.setY(y);
        refineButton.setMessage(label);
        refineButton.active = knowledge >= cost;

        Component status;
        int color = MUTED_COLOR;
        if (knowledge >= cost)
        {
            status = Component.translatable("voidheart.research.refine_ready");
            color = FRONTIER_COLOR;
        }
        else if (cost > snapshot.cap())
        {
            status = Component.translatable("voidheart.research.refine_capped");
            color = LACK_COLOR;
        }
        else
        {
            double wait = KnowledgeMath.secondsToReach(knowledge, cost, knowledgeChange());
            status = Double.isInfinite(wait) ? Component.translatable("voidheart.research.refine_blocked") : Component.translatable("voidheart.research.refine_wait", duration(wait));
        }
        graphics.text(font, fit(status.getString(), width), PAD, y + BUTTON_H + 3, color);
    }

    private List<Map.Entry<Identifier, Integer>> fedItems()
    {
        return snapshot.fed().entrySet().stream()
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    private int fedColumns()
    {
        return Math.max(1, (left - PAD * 2) / FED_CELL);
    }

    private int fedTop()
    {
        return refineButton.getY() + BUTTON_H + 2 + LINE + 3 + 5 + LINE + 1;
    }

    private void extractExtracted(GuiGraphicsExtractor graphics, int y, int width, int mouseX, int mouseY)
    {
        graphics.text(font, Component.translatable("voidheart.research.extracted"), PAD, y, MUTED_COLOR);
        var fed = fedItems();
        if (!fed.isEmpty())
        {
            var legend = Component.translatable("voidheart.research.next_yield");
            graphics.text(font, legend, PAD + width - font.width(legend), y, SPENT_COLOR);
        }
        y += LINE + 1;

        if (fed.isEmpty())
        {
            var lines = font.split(Component.translatable("voidheart.research.feed_hint"), width);
            for (int line = 0; line < lines.size() && y + LINE <= height - PAD; line++, y += LINE)
                graphics.text(font, lines.get(line), PAD, y, MUTED_COLOR);
            return;
        }

        int columns = fedColumns();
        int rows = (height - PAD - y) / FED_PITCH;
        int totalRows = (fed.size() + columns - 1) / columns;
        fedScroll = Math.clamp(fedScroll, 0, Math.max(0, totalRows - rows));

        for (int index = fedScroll * columns; index < fed.size(); index++)
        {
            int row = index / columns - fedScroll;
            if (row >= rows)
                break;

            int x = PAD + index % columns * (width / columns);
            int cellY = y + row * FED_PITCH;
            int count = fed.get(index).getValue();
            var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(fed.get(index).getKey()));
            double next = KnowledgeMath.feedMultiplier(count);

            graphics.item(stack, x, cellY);
            if (next <= 0)
                graphics.fill(x, cellY, x + 16, cellY + 16, SPENT_SHADE);
            if (next > 0)
                graphics.text(font, String.format("%.0f%%", next * 100), x + 18, cellY + 4, next >= 0.3 ? FRONTIER_COLOR : TEXT_COLOR);
            else
                graphics.text(font, Component.translatable("voidheart.research.fed_spent_short"), x + 18, cellY + 4, SPENT_COLOR);
        }
    }

    private int listTop()
    {
        return PAD + LINE + 2 + (snapshot.active().isPresent() ? CARD_H + 2 : 0);
    }

    /**
     * Only as tall as what it says, so that the frontier keeps the rest of the rail, while the frontier never drops below two rows.
     */
    private int inspectorHeight()
    {
        return Math.min(inspectorWanted(), height - PAD - listTop() - 2 - Math.min(frontier.size(), 2) * ROW_H);
    }

    private int inspectorWanted()
    {
        if (selected == null || !tree.containsKey(selected))
            return INSPECTOR_MIN;

        int width = this.width - right - PAD * 2;
        var research = tree.get(selected);
        var state = stateOf(selected, research);
        boolean unknown = state == NodeState.UNKNOWN;

        int lines = 1 + font.split(unknown ? Component.translatable("voidheart.research.unread") : Component.translatable(Research.nameKey(selected)), width).size();
        lines += font.split(unknown ? Component.translatable("voidheart.research.unread_desc") : Component.translatable(Research.descriptionKey(selected)), width).size();
        if (!unknown)
            lines += effects(research).stream().mapToInt(effect -> font.split(effect, width).size()).sum();
        return Math.max(INSPECTOR_MIN, 7 + lines * LINE + 2 + (state == NodeState.DONE ? LINE + DONE_GAP : BUTTON_H + 3));
    }

    private static List<Component> effects(Research research)
    {
        var effects = new ArrayList<Component>();
        if (research.passiveBonus() > 0)
            effects.add(Component.translatable("voidheart.research.passive_bonus", String.format("%.2f", research.passiveBonus())));
        if (research.capBonus() > 0)
            effects.add(Component.translatable("voidheart.research.cap_bonus", wholeNumber(research.capBonus())));
        return effects;
    }

    private int listBottom()
    {
        return height - PAD - inspectorHeight();
    }

    private void extractFrontier(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        int x = right + PAD;
        int width = this.width - right - PAD * 2;
        long now = Util.getMillis();

        if (learned != null && now - learnedAt < LEARNED_MILLIS)
            graphics.text(font, fit(Component.translatable("voidheart.research.learned", Component.translatable(Research.nameKey(learned))).getString(), width), x, PAD, KNOWN_COLOR);
        else
            graphics.text(font, Component.translatable("voidheart.research.frontier"), x, PAD, MUTED_COLOR);

        activeResearch().ifPresent(research -> extractRunning(graphics, snapshot.active().orElseThrow(), research, x, PAD + LINE + 2, width, mouseX, mouseY));

        int top = listTop();
        int bottom = listBottom();
        int visible = (bottom - top) / ROW_H;
        listScroll = Math.clamp(listScroll, 0, Math.max(0, frontier.size() - visible));

        if (frontier.isEmpty() && activeResearch().isEmpty())
        {
            var lines = font.split(Component.translatable("voidheart.research.none"), width);
            for (int line = 0; line < lines.size(); line++)
                graphics.text(font, lines.get(line), x, top + line * LINE, MUTED_COLOR);
        }

        for (int index = listScroll; index < frontier.size() && index - listScroll < visible; index++)
            extractRow(graphics, frontier.get(index), x, top + (index - listScroll) * ROW_H, width, mouseX, mouseY);

        if (listScroll > 0)
            graphics.fill(x, top - 1, x + width, top, FRONTIER_COLOR);
        if (listScroll + visible < frontier.size())
            graphics.fill(x, top + visible * ROW_H, x + width, top + visible * ROW_H + 1, FRONTIER_COLOR);
    }

    private void extractRunning(GuiGraphicsExtractor graphics, Identifier id, Research research, int x, int y, int width, int mouseX, int mouseY)
    {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + CARD_H;
        graphics.fill(x - 3, y, x + width + 3, y + CARD_H, hovered ? CARD_HOVER : CARD_TINT);
        graphics.outline(x - 3, y, width + 6, CARD_H, ACTIVE_COLOR);

        graphics.item(ResearchMapView.iconOf(research), x, y + 3);
        graphics.text(font, fit(Component.translatable(Research.nameKey(id)).getString(), width - 20), x + 19, y + 4, ACTIVE_COLOR);

        double progress = shownProgress(id, research);
        graphics.text(font, fit(number(progress) + " / " + wholeNumber(research.knowledgeCost()), width - 20), x + 19, y + 13, TEXT_COLOR);

        graphics.fill(x, y + 22, x + width, y + 26, BAR_BACK_COLOR);
        graphics.fill(x, y + 22, x + (int) (width * Math.min(1, progress / research.knowledgeCost())), y + 26, ACTIVE_COLOR);

        double left = secondsToFinish(id, research);
        Component status;
        int color = TEXT_COLOR;
        if (starving())
        {
            status = Component.translatable("voidheart.research.starving", String.format("%.2f", snapshot.rate()));
            color = LACK_COLOR;
        }
        else
            status = Double.isInfinite(left) ? Component.translatable("voidheart.research.stalled") : Component.translatable("voidheart.research.left", duration(left));
        graphics.text(font, fit(status.getString(), width), x, y + 30, color);
    }

    private void extractRow(GuiGraphicsExtractor graphics, Identifier id, int x, int y, int width, int mouseX, int mouseY)
    {
        var research = tree.get(id);
        var state = stateOf(id, research);
        boolean hovered = mouseX >= x - 3 && mouseX < x + width + 3 && mouseY >= y && mouseY < y + ROW_H - 1;

        if (id.equals(selected))
            graphics.fill(x - 3, y, x + width + 3, y + ROW_H - 1, ROW_SELECTED);
        else if (hovered)
            graphics.fill(x - 3, y, x + width + 3, y + ROW_H - 1, ROW_HOVER);

        String name;
        Component detail;
        int nameColor;
        int detailColor = MUTED_COLOR;

        if (state == NodeState.UNKNOWN)
        {
            boolean affordable = canReveal(research);
            ResearchMapView.glyph(graphics, affordable, x, y + 2);
            var parent = ResearchLayout.parentOf(research, tree);
            name = parent != null
                    ? Component.translatable("voidheart.research.past", Component.translatable(Research.nameKey(parent))).getString()
                    : Component.translatable(map.nodeY(id) < height / 2 ? "voidheart.research.above_eye" : "voidheart.research.below_eye").getString();
            nameColor = affordable ? FRONTIER_COLOR : MUTED_COLOR;
            detail = Component.translatable("voidheart.research.row_unread", research.insightCost());
            detailColor = affordable ? TEXT_COLOR : MUTED_COLOR;
        }
        else
        {
            graphics.item(ResearchMapView.iconOf(research), x, y + 2);
            name = Component.translatable(Research.nameKey(id)).getString();
            nameColor = FRONTIER_COLOR;
            double done = snapshot.progress().getOrDefault(id, 0D);
            double seconds = secondsToFinish(id, research);
            detail = done > 0
                    ? Component.translatable("voidheart.research.row_paused", String.format("%.0f%%", 100 * done / research.knowledgeCost()))
                    : Double.isInfinite(seconds)
                    ? Component.translatable("voidheart.research.row_cost", wholeNumber(research.knowledgeCost()))
                    : Component.translatable("voidheart.research.row_ready", wholeNumber(research.knowledgeCost()), duration(seconds));
        }

        graphics.text(font, fit(name, width - 19), x + 19, y + 2, nameColor);
        graphics.text(font, fit(detail.getString(), width - 19), x + 19, y + 11, detailColor);
    }

    private void extractInspector(GuiGraphicsExtractor graphics)
    {
        int x = right + PAD;
        int width = this.width - right - PAD * 2;
        int top = listBottom() + 2;

        rule(graphics, x, top, width);
        int y = top + 5;
        int bottom = height - PAD - (selected != null && tree.containsKey(selected) && stateOf(selected, tree.get(selected)) == NodeState.DONE ? LINE + DONE_GAP : BUTTON_H + 3);
        descriptionCut = false;

        actionButton.visible = false;
        if (selected == null || !tree.containsKey(selected))
            return;

        var research = tree.get(selected);
        var state = stateOf(selected, research);
        boolean unknown = state == NodeState.UNKNOWN;

        var name = unknown ? Component.translatable("voidheart.research.unread") : Component.translatable(Research.nameKey(selected));
        for (var line : font.split(name, width))
        {
            graphics.text(font, line, x, y, unknown ? MUTED_COLOR : state == NodeState.DONE ? KNOWN_COLOR : state == NodeState.ACTIVE ? ACTIVE_COLOR : FRONTIER_COLOR);
            y += LINE;
        }

        graphics.text(font, fit(ringOf(research).getString(), width), x, y, MUTED_COLOR);
        y += LINE + 2;

        if (!unknown)
        {
            for (var effect : effects(research))
            {
                for (var line : font.split(effect, width))
                {
                    graphics.text(font, line, x, y, FRONTIER_COLOR);
                    y += LINE;
                }
            }
        }

        var description = unknown ? Component.translatable("voidheart.research.unread_desc") : Component.translatable(Research.descriptionKey(selected));
        var lines = font.split(description, width);
        int room = Math.max(0, (bottom - y) / LINE);
        descriptionCut = lines.size() > room;
        for (int line = 0; line < Math.min(room, lines.size()); line++, y += LINE)
            graphics.text(font, lines.get(line), x, y, descriptionCut && line == room - 1 ? MUTED_COLOR : TEXT_COLOR);
        if (descriptionCut && room > 0)
            graphics.text(font, "...", x + width - font.width("..."), y - LINE, MUTED_COLOR);

        extractAction(graphics, research, state);
    }

    private void extractAction(GuiGraphicsExtractor graphics, Research research, NodeState state)
    {
        switch (state)
        {
            case UNKNOWN ->
            {
                boolean affordable = canReveal(research);
                actionButton.visible = true;
                actionButton.active = affordable;
                actionButton.setMessage(affordable
                        ? Component.translatable("voidheart.research.reveal", insightCount(research.insightCost()))
                        : Component.translatable("voidheart.research.needs", insightCount(research.insightCost() - snapshot.insights())));
            }
            case REVEALED ->
            {
                actionButton.visible = true;
                actionButton.active = true;
                actionButton.setMessage(Component.translatable(snapshot.progress().getOrDefault(selected, 0D) > 0 ? "voidheart.research.resume" : "voidheart.research.begin"));
            }
            case ACTIVE ->
            {
                actionButton.visible = true;
                actionButton.active = true;
                actionButton.setMessage(Component.translatable("voidheart.research.pause"));
            }
            case DONE -> graphics.text(font, Component.translatable("voidheart.research.done"), right + PAD, height - PAD - font.lineHeight, KNOWN_COLOR);
            default ->
            {
            }
        }
    }

    private void act()
    {
        if (selected == null || !tree.containsKey(selected))
            return;

        var state = stateOf(selected, tree.get(selected));
        if (state == NodeState.UNKNOWN)
            send(Action.Kind.REVEAL, selected);
        else if (state == NodeState.REVEALED || state == NodeState.ACTIVE)
            send(Action.Kind.TOGGLE, selected);
    }

    private Identifier rowAt(double mouseX, double mouseY)
    {
        int x = right + PAD;
        int width = this.width - right - PAD * 2;
        if (mouseX < x - 3 || mouseX >= x + width + 3)
            return null;

        if (snapshot.active().isPresent() && mouseY >= PAD + LINE + 2 && mouseY < PAD + LINE + 2 + CARD_H)
            return snapshot.active().get();

        int top = listTop();
        int visible = (listBottom() - top) / ROW_H;
        if (mouseY < top || mouseY >= top + visible * ROW_H)
            return null;

        int index = listScroll + (int) ((mouseY - top) / ROW_H);
        return index < frontier.size() ? frontier.get(index) : null;
    }

    private Map.Entry<Identifier, Integer> fedAt(double mouseX, double mouseY)
    {
        var fed = fedItems();
        int columns = fedColumns();
        int top = fedTop();
        int cell = (left - PAD * 2) / columns;
        if (mouseX < PAD || mouseX >= PAD + columns * cell || mouseY < top || mouseY >= height - PAD)
            return null;

        int index = ((int) (mouseY - top) / FED_PITCH + fedScroll) * columns + (int) (mouseX - PAD) / cell;
        return index < fed.size() ? fed.get(index) : null;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym)
    {
        // All is drawn with the background, relative to the screen rather than a centered panel
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        super.extractTooltip(graphics, mouseX, mouseY);

        if (mouseX >= left && mouseX < right)
        {
            var node = map.nodeAt(mouseX, mouseY);
            if (node.isPresent())
                graphics.setComponentTooltipForNextFrame(font, nodeTooltip(node.get(), tree.get(node.get())), mouseX, mouseY);
            else if (map.overEye(mouseX, mouseY))
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("voidheart.research.eye", number(shownKnowledge()), wholeNumber(snapshot.cap()))), mouseX, mouseY);
            return;
        }

        if (descriptionCut && mouseX >= right && mouseY > listBottom() && mouseY < height - PAD - BUTTON_H && tree.containsKey(selected))
        {
            var state = stateOf(selected, tree.get(selected));
            graphics.setComponentTooltipForNextFrame(font, List.of(state == NodeState.UNKNOWN ? Component.translatable("voidheart.research.unread_desc") : Component.translatable(Research.descriptionKey(selected))), mouseX, mouseY);
            return;
        }

        var fed = fedAt(mouseX, mouseY);
        if (fed != null)
            graphics.setComponentTooltipForNextFrame(font, fedTooltip(fed.getKey(), fed.getValue()), mouseX, mouseY);
        else if (mouseX < left && mouseY < PAD + 62)
            graphics.setComponentTooltipForNextFrame(font, economyTooltip(), mouseX, mouseY);
    }

    private List<Component> nodeTooltip(Identifier id, Research research)
    {
        var state = stateOf(id, research);
        var lines = new ArrayList<Component>();
        if (state == NodeState.UNKNOWN)
        {
            lines.add(Component.translatable("voidheart.research.unread"));
            lines.add(Component.translatable("voidheart.research.row_unread", insightCount(research.insightCost())).withStyle(canReveal(research) ? ChatFormatting.AQUA : ChatFormatting.RED));
            return lines;
        }

        lines.add(Component.translatable(Research.nameKey(id)));
        lines.add(state == NodeState.DONE
                ? Component.translatable("voidheart.research.done").withStyle(ChatFormatting.GRAY)
                : Component.translatable("voidheart.research.row_progress", number(shownProgress(id, research)), wholeNumber(research.knowledgeCost()), String.format("%.2f", research.knowledgePerSecond())).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    private List<Component> fedTooltip(Identifier id, int count)
    {
        var item = BuiltInRegistries.ITEM.getValue(id);
        double next = KnowledgeMath.feedMultiplier(count);
        var lines = new ArrayList<Component>();
        lines.add(item.getName(new ItemStack(item)));
        lines.add(Component.translatable("voidheart.research.fed", count).withStyle(ChatFormatting.GRAY));
        lines.add(next > 0
                ? Component.translatable("voidheart.research.fed_next", String.format("%.0f%%", next * 100)).withStyle(ChatFormatting.AQUA)
                : Component.translatable("voidheart.research.fed_spent").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private List<Component> economyTooltip()
    {
        var lines = new ArrayList<Component>();
        double bonusRate = 0;
        double bonusCap = 0;
        for (var id : snapshot.completed())
        {
            var research = tree.get(id);
            if (research == null)
                continue;
            bonusRate += research.passiveBonus();
            bonusCap += research.capBonus();
        }
        lines.add(Component.translatable("voidheart.research.economy_rate", String.format("%.2f", snapshot.rate()), String.format("%.2f", bonusRate)));
        lines.add(Component.translatable("voidheart.research.economy_cap", wholeNumber(snapshot.cap()), wholeNumber(bonusCap)));
        activeResearch().ifPresent(research -> lines.add(Component.translatable("voidheart.research.economy_drain", String.format("%.2f", research.knowledgePerSecond())).withStyle(ChatFormatting.GOLD)));
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT)
        {
            var row = rowAt(event.x(), event.y());
            var node = row != null ? Optional.of(row) : event.x() >= left && event.x() < right ? map.nodeAt(event.x(), event.y()) : Optional.<Identifier>empty();
            if (node.isPresent())
            {
                // The game calls any two quick clicks a double click, wherever they landed
                boolean again = node.get().equals(lastClicked);
                lastClicked = node.get();
                selected = node.get();
                if (doubleClick && again)
                    act();
                return true;
            }
        }
        lastClicked = null;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (mouseX >= right)
        {
            listScroll -= (int) Math.signum(scrollY);
            return true;
        }
        if (mouseX < left)
        {
            fedScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Up and down walk the frontier, Enter acts on what is selected and R refines an insight, so the mouse is never needed.
     */
    @Override
    public boolean keyPressed(KeyEvent event)
    {
        switch (event.key())
        {
            case InputConstants.KEY_UP ->
            {
                moveSelection(-1);
                return true;
            }
            case InputConstants.KEY_DOWN ->
            {
                moveSelection(1);
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER ->
            {
                if (getFocused() == null)
                {
                    act();
                    return true;
                }
            }
            case InputConstants.KEY_R ->
            {
                if (refineButton.active)
                    send(Action.Kind.BUY_INSIGHT, null);
                return true;
            }
            default ->
            {
            }
        }
        return super.keyPressed(event);
    }

    public int[] nodePosition(Identifier id)
    {
        return new int[] { map.nodeX(id), map.nodeY(id) };
    }

    public int[] refinePosition()
    {
        return new int[] { refineButton.getX() + refineButton.getWidth() / 2, refineButton.getY() + refineButton.getHeight() / 2 };
    }

    public int[] actionPosition()
    {
        return new int[] { actionButton.getX() + actionButton.getWidth() / 2, actionButton.getY() + actionButton.getHeight() / 2 };
    }

    public Identifier selected()
    {
        return selected;
    }

    private void moveSelection(int by)
    {
        var order = new ArrayList<Identifier>();
        snapshot.active().ifPresent(order::add);
        order.addAll(frontier);
        if (order.isEmpty())
            return;

        int next = Math.clamp(order.indexOf(selected) + by, 0, order.size() - 1);
        selected = order.get(next);

        int index = frontier.indexOf(selected);
        int visible = Math.max(1, (listBottom() - listTop()) / ROW_H);
        if (index >= 0)
            listScroll = Math.clamp(listScroll, index - visible + 1, index);
    }

    @Override
    public void removed()
    {
        minecraft.getTextureManager().release(VOID_A);
        minecraft.getTextureManager().release(VOID_B);
        super.removed();
    }

    /**
     * Points out the items a repository would feed on.
     */
    public static void appendKnowledgeTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines)
    {
        if (Knowledge.valueOf(stack) > 0)
            lines.add(Component.translatable("voidheart.knowledge.embedded").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
