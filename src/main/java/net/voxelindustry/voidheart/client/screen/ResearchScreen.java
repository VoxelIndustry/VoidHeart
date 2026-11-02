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
import net.minecraft.util.FormattedCharSequence;
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
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The map is the screen: the eye shows how much is held, the motes around it how many insights, the light what has been reached.
 * Everything more exact waits to be asked for: details beside a selected research, figures on the knowledge card, feed history behind its glyph.
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
    private static final int BAR_BACK_COLOR = 0xFF08050C;
    private static final int TABLET_COLOR   = 0xF40B0810;
    private static final int SPENT_SHADE    = 0xB0100A18;
    private static final int READOUT_COLOR  = 0xFF8A7A99;
    private static final int GLYPH_DIM      = 0x58040208;
    private static final int WHISPER_BACK   = 0xA0050308;

    private static final int RIM_MIN       = 20;
    private static final int RIM_MAX       = 40;
    private static final int PAD           = 8;
    private static final int LINE          = 10;
    private static final int BUTTON_H      = 18;
    private static final int FED_PITCH     = 18;
    private static final int FED_COLUMNS   = 6;
    private static final int FED_ROWS      = 5;
    private static final int DETAIL_MIN    = 136;
    private static final int DETAIL_MAX    = 190;
    private static final int DETAIL_GAP    = 18;
    private static final int GLYPH_SIZE    = 16;
    private static final int CARD_MIN      = 118;
    private static final int CARD_ROW      = 11;
    private static final int READOUT_HOVER = 3;
    private static final int CARD_HEADER   = 15;
    private static final int PARCHMENT_U   = 92;
    private static final int PARCHMENT_V   = 96;
    private static final int PARCHMENT_W   = 10;
    private static final int PARCHMENT_H   = 11;
    private static final int PARCHMENT_GAP = 3;
    private static final int PARCHMENT_DIM = 0xFFB4ABBE;

    private static final long LEARNED_MILLIS = 7000;
    private static final long FADE_MILLIS    = 1200;
    private static final long CARD_GROW      = 230;
    private static final long CARD_SHRINK    = 110;
    private static final float CARD_FROM     = 0.35F;
    private static final long FLIGHT_MILLIS  = 850;
    private static final int  FLIGHT_TRAIL   = 5;

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

    private record Line(FormattedCharSequence text, int color)
    {
    }

    private record CardRow(Component label, String value, int valueColor)
    {
    }

    private record Bounds(int x, int y, int width, int height)
    {
        boolean contains(double px, double py)
        {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    private Map<Identifier, Research> tree = Map.of();
    private ResearchMapView           map;
    private ResearchButton            actionButton;
    private ResearchInsightButton     insightButton;
    private List<Identifier>          frontier = List.of();

    private Identifier                 voidCurrent = VOID_A;
    private Identifier                 voidPrevious = VOID_B;
    private KnowledgePayloads.Snapshot seen;
    private Identifier                 selected;
    private Identifier                 lastClicked;
    private Identifier                 learned;
    private Bounds                     detailBounds;
    private Bounds                     fedBounds;
    private Bounds                     cardBounds;
    private float                      cardOpen;
    private long                       cardTickedAt;
    private long                       flightAt = -1;
    private boolean                    fedOpen;
    private long                       learnedAt;
    private long                       snapshotAt;
    private long                       bakedAt;
    private boolean                    hasPrevious;
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
        int rim = Math.clamp((int) (width * 0.045F), RIM_MIN, RIM_MAX);
        left = rim;
        right = width - rim;
        map = new ResearchMapView(font, tree, left, right, height, seed());

        hasPrevious = false;
        bake(voidCurrent);
        rebuildFrontier();
        if (seen == null)
        {
            seen = snapshot;
            snapshotAt = Util.getMillis();
        }

        actionButton = addRenderableWidget(new ResearchButton(0, 0, DETAIL_MIN, BUTTON_H, pressed ->
        {
            setFocused(null);
            act();
        }));
        actionButton.visible = false;

        insightButton = addRenderableWidget(new ResearchInsightButton((left + right - ResearchInsightButton.WIDTH) / 2, height - PAD - LINE - 3 - ResearchInsightButton.HEIGHT,
                Component.translatable("voidheart.research.craft"),
                () -> snapshot.insights(),
                () -> snapshot.nextInsightCost() > 0 ? shownKnowledge() / snapshot.nextInsightCost() : 0,
                pressed ->
                {
                    setFocused(null);
                    refine();
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

        if (before.nextInsightCost() > 0 && snapshot.nextInsightCost() > before.nextInsightCost())
            onInsightCrafted();

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

    private void onInsightCrafted()
    {
        flightAt = snapshotAt;
        insightButton.crafted();
        map.startle(snapshotAt + FLIGHT_MILLIS);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F));
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

        if (selected != null && (!tree.containsKey(selected) || stateOf(selected, tree.get(selected)) == NodeState.HIDDEN))
            selected = null;
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

    private boolean canRefine()
    {
        return snapshot.nextInsightCost() > 0 && shownKnowledge() >= snapshot.nextInsightCost();
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

    private static void tablet(GuiGraphicsExtractor graphics, Bounds bounds)
    {
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), TABLET_COLOR);
        graphics.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), RULE_COLOR);
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
        map.extract(graphics, mouseX, mouseY, voidCurrent, width, height, fullness, !starving(), canRefine(), selected, hoveredNode(mouseX, mouseY));

        insightButton.active = canRefine();

        extractFlight(graphics);
        extractReadout(graphics, mouseX, mouseY);
        extractWhisper(graphics);
        extractFedGlyph(graphics, mouseX, mouseY);
        extractDetail(graphics);
        extractFed(graphics);
        extractCard(graphics, mouseX, mouseY);
    }

    /**
     * The insight just crafted, rising from the lantern to join the motes around the eye.
     */
    private void extractFlight(GuiGraphicsExtractor graphics)
    {
        if (flightAt < 0)
            return;

        float t = (Util.getMillis() - flightAt) / (float) FLIGHT_MILLIS;
        if (t >= 1.2F)
        {
            flightAt = -1;
            return;
        }

        for (int trail = 0; trail < FLIGHT_TRAIL; trail++)
        {
            float at = t - trail * 0.035F;
            if (at <= 0 || at >= 1)
                continue;

            float eased = at * at * (3 - 2 * at);
            float sway = (float) Math.sin(at * Math.PI) * 26;
            int x = (int) (insightButton.lanternX() + ((left + right) / 2F - insightButton.lanternX()) * eased + sway);
            int y = (int) (insightButton.lanternY() + (map.orbitY() - insightButton.lanternY()) * eased);
            int size = trail == 0 ? 3 : 2;
            graphics.fill(x, y, x + size, y + size, ARGB.color(240 - trail * 44, FRONTIER_RGB));
        }
    }

    private boolean overTablet(double mouseX, double mouseY)
    {
        return detailBounds != null && detailBounds.contains(mouseX, mouseY) || fedOpen && fedBounds != null && fedBounds.contains(mouseX, mouseY);
    }

    private Identifier hoveredNode(int mouseX, int mouseY)
    {
        return overTablet(mouseX, mouseY) ? null : map.nodeAt(mouseX, mouseY).orElse(null);
    }

    /**
     * The only figures on screen at rest, kept low in a corner and whole so that they do not tick.
     */
    private void extractReadout(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        var bounds = readoutBounds();
        boolean lit = bounds.contains(mouseX, mouseY) || cardOpen > 0;
        extractParchment(graphics, bounds.x() + READOUT_HOVER, bounds.y() + 2, lit ? 0xFFFFFFFF : PARCHMENT_DIM);
        graphics.text(font, readoutText(), bounds.x() + READOUT_HOVER + PARCHMENT_W + PARCHMENT_GAP, bounds.y() + READOUT_HOVER, lit ? MUTED_COLOR : READOUT_COLOR, false);
    }

    private static void extractParchment(GuiGraphicsExtractor graphics, int x, int y, int tint)
    {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ResearchMapView.SPRITES, x, y, PARCHMENT_U, PARCHMENT_V, PARCHMENT_W, PARCHMENT_H, ResearchMapView.ATLAS, ResearchMapView.ATLAS, tint);
    }

    private String readoutText()
    {
        return wholeNumber(Math.floor(shownKnowledge())) + " / " + wholeNumber(snapshot.cap());
    }

    private Bounds readoutBounds()
    {
        return new Bounds(left + PAD + 3, height - PAD - font.lineHeight - READOUT_HOVER, PARCHMENT_W + PARCHMENT_GAP + font.width(readoutText()) + READOUT_HOVER * 2, font.lineHeight + READOUT_HOVER * 2);
    }

    /**
     * Grows out of the readout while the cursor is on either of them, and shrinks back into it afterwards.
     */
    private void extractCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        long now = Util.getMillis();
        boolean wanted = readoutBounds().contains(mouseX, mouseY) || cardOpen > 0.5F && cardBounds != null && cardBounds.contains(mouseX, mouseY);
        float step = (now - cardTickedAt) / (float) (wanted ? CARD_GROW : CARD_SHRINK);
        cardTickedAt = now;
        cardOpen = Math.clamp(cardOpen + (wanted ? step : -step), 0, 1);

        if (cardOpen <= 0)
        {
            cardBounds = null;
            return;
        }

        var title = Component.translatable("voidheart.research.card.title");
        var rows = cardRows();
        int widest = rows.stream().filter(row -> row != null).mapToInt(row -> font.width(row.label()) + 10 + font.width(row.value())).max().orElse(0);
        int boxWidth = Math.max(CARD_MIN, Math.max(widest, PARCHMENT_W + PARCHMENT_GAP + font.width(title)) + PAD * 2);
        int boxHeight = PAD * 2 + CARD_HEADER + rows.stream().mapToInt(row -> row == null ? 5 : CARD_ROW).sum();

        var readout = readoutBounds();
        int x = readout.x();
        int bottom = readout.y() - 2;
        cardBounds = new Bounds(x, bottom - boxHeight, boxWidth, boxHeight);

        float eased = 1 - (1 - cardOpen) * (1 - cardOpen) * (1 - cardOpen);
        float scale = CARD_FROM + (1 - CARD_FROM) * eased;

        var pose = graphics.pose();
        pose.pushMatrix();
        if (cardOpen < 1)
        {
            pose.translate(x, bottom);
            pose.scale(scale);
            pose.translate(-x, -bottom);
        }

        tablet(graphics, cardBounds);
        int y = cardBounds.y() + PAD;
        extractParchment(graphics, x + PAD, y, 0xFFFFFFFF);
        graphics.text(font, title, x + PAD + PARCHMENT_W + PARCHMENT_GAP, y + 2, TEXT_COLOR, false);
        y += CARD_HEADER;
        for (var row : rows)
        {
            if (row == null)
            {
                graphics.fill(x + PAD, y + 1, x + boxWidth - PAD, y + 2, RULE_COLOR);
                y += 5;
                continue;
            }
            graphics.text(font, row.label(), x + PAD, y, MUTED_COLOR, false);
            graphics.text(font, row.value(), x + boxWidth - PAD - font.width(row.value()), y, row.valueColor(), false);
            y += CARD_ROW;
        }
        pose.popMatrix();
    }

    /**
     * Names read first and figures stay dim. A null row is a groove between the two groups.
     */
    private List<CardRow> cardRows()
    {
        double knowledge = shownKnowledge();
        double change = knowledgeChange();
        double cost = snapshot.nextInsightCost();

        var rows = new ArrayList<CardRow>();
        rows.add(new CardRow(Component.translatable("voidheart.research.card.gathering"), String.format("%.2f/s", snapshot.rate()), READOUT_COLOR));
        activeResearch().ifPresent(research -> rows.add(new CardRow(Component.translatable("voidheart.research.card.burning"), String.format("%.2f/s", research.knowledgePerSecond()), READOUT_COLOR)));
        rows.add(new CardRow(Component.translatable("voidheart.research.card.holds"), wholeNumber(snapshot.cap()), READOUT_COLOR));

        if (knowledge >= snapshot.cap() && change >= 0)
            rows.add(new CardRow(Component.translatable("voidheart.research.card.full"), "", READOUT_COLOR));
        else if (change > 0)
            rows.add(new CardRow(Component.translatable("voidheart.research.card.full_in"), duration((snapshot.cap() - knowledge) / change), READOUT_COLOR));
        else if (change < 0 && knowledge > 0)
            rows.add(new CardRow(Component.translatable("voidheart.research.card.empty_in"), duration(knowledge / -change), READOUT_COLOR));

        rows.add(null);
        rows.add(new CardRow(Component.translatable("voidheart.research.card.next"), nextInsightText(knowledge, cost, change), knowledge >= cost ? FRONTIER_COLOR : READOUT_COLOR));
        rows.add(new CardRow(Component.translatable("voidheart.research.card.cost"), wholeNumber(Math.ceil(cost)), READOUT_COLOR));
        return rows;
    }

    private String nextInsightText(double knowledge, double cost, double change)
    {
        if (knowledge >= cost)
            return Component.translatable("voidheart.research.card.next_now").getString();
        if (cost > snapshot.cap())
            return Component.translatable("voidheart.research.card.next_capped").getString();

        double wait = KnowledgeMath.secondsToReach(knowledge, cost, change);
        return Double.isInfinite(wait) ? Component.translatable("voidheart.research.card.next_blocked").getString() : duration(wait);
    }

    /**
     * At most one quiet sentence, about the one thing worth knowing right now.
     */
    private void extractWhisper(GuiGraphicsExtractor graphics)
    {
        Component text = null;
        int color = MUTED_COLOR;

        if (learned != null && Util.getMillis() - learnedAt < LEARNED_MILLIS)
        {
            text = Component.translatable("voidheart.research.learned", Component.translatable(Research.nameKey(learned)));
            color = KNOWN_COLOR;
        }
        else if (starving())
        {
            text = Component.translatable("voidheart.research.whisper.starving");
            color = LACK_COLOR;
        }
        else if (selected == null)
        {
            int cheapest = frontier.stream().filter(id -> stateOf(id, tree.get(id)) == NodeState.UNKNOWN).mapToInt(id -> tree.get(id).insightCost()).min().orElse(Integer.MAX_VALUE);
            boolean idle = snapshot.active().isEmpty();

            if (shownKnowledge() >= snapshot.cap() && knowledgeChange() >= 0 && snapshot.cap() > 0)
            {
                text = Component.translatable("voidheart.research.full");
                color = LACK_COLOR;
            }
            else if (idle && frontier.stream().anyMatch(id -> stateOf(id, tree.get(id)) == NodeState.REVEALED))
                text = Component.translatable("voidheart.research.whisper.begin");
            else if (snapshot.insights() >= cheapest && idle)
                text = Component.translatable("voidheart.research.whisper.read");
            else if (canRefine() && snapshot.insights() < cheapest)
                text = Component.translatable("voidheart.research.whisper.refine");
            else if (snapshot.fed().isEmpty() && snapshot.insights() == 0)
                text = Component.translatable("voidheart.research.feed_hint");
            else if (frontier.isEmpty() && idle)
                text = Component.translatable("voidheart.research.none");
        }

        if (text == null)
            return;

        int y = height - PAD - LINE + 1;
        int half = font.width(text) / 2 + 3;
        int center = (left + right) / 2;
        graphics.fill(center - half, y - 1, center + half, y + LINE - 1, WHISPER_BACK);
        graphics.centeredText(font, text, center, y, color);
    }

    private Bounds fedGlyph()
    {
        return new Bounds(right - PAD - 6 - GLYPH_SIZE, height - PAD - GLYPH_SIZE, GLYPH_SIZE, GLYPH_SIZE);
    }

    private void extractFedGlyph(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        if (snapshot.fed().isEmpty())
            return;

        var glyph = fedGlyph();
        graphics.item(new ItemStack(VoidHeartItems.KNOWLEDGE_TABLET), glyph.x(), glyph.y());
        if (!fedOpen && !glyph.contains(mouseX, mouseY))
            graphics.fill(glyph.x(), glyph.y(), glyph.x() + GLYPH_SIZE, glyph.y() + GLYPH_SIZE, GLYPH_DIM);
    }

    private List<Map.Entry<Identifier, Integer>> fedItems()
    {
        return snapshot.fed().entrySet().stream()
                .filter(entry -> BuiltInRegistries.ITEM.containsKey(entry.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .toList();
    }

    /**
     * What has been fed, by how much it still has to give: two pips, one, or a darkened icon. The figures are on hover.
     */
    private void extractFed(GuiGraphicsExtractor graphics)
    {
        fedBounds = null;
        if (!fedOpen)
            return;

        var fed = fedItems();
        int columns = Math.clamp(fed.size(), 4, FED_COLUMNS);
        int rows = Math.min(FED_ROWS, (fed.size() + columns - 1) / columns);
        int totalRows = (fed.size() + columns - 1) / columns;
        fedScroll = Math.clamp(fedScroll, 0, Math.max(0, totalRows - rows));

        int boxWidth = columns * FED_PITCH + PAD * 2;
        int boxHeight = LINE + 3 + rows * FED_PITCH + PAD * 2;
        var glyph = fedGlyph();
        fedBounds = new Bounds(right - PAD - boxWidth, glyph.y() - 4 - boxHeight, boxWidth, boxHeight);
        tablet(graphics, fedBounds);

        graphics.text(font, Component.translatable("voidheart.research.extracted"), fedBounds.x() + PAD, fedBounds.y() + PAD, MUTED_COLOR);
        int top = fedBounds.y() + PAD + LINE + 3;
        for (int index = fedScroll * columns; index < fed.size() && index / columns - fedScroll < rows; index++)
        {
            int x = fedBounds.x() + PAD + index % columns * FED_PITCH;
            int y = top + (index / columns - fedScroll) * FED_PITCH;
            double next = KnowledgeMath.feedMultiplier(fed.get(index).getValue());

            graphics.item(new ItemStack(BuiltInRegistries.ITEM.getValue(fed.get(index).getKey())), x, y);
            if (next <= 0)
                graphics.fill(x, y, x + 16, y + 16, SPENT_SHADE);
            int pips = next >= 0.3 ? 2 : next > 0 ? 1 : 0;
            for (int pip = 0; pip < pips; pip++)
                graphics.fill(x + 13 - pip * 3, y + 14, x + 15 - pip * 3, y + 16, 0xFF000000 | FRONTIER_RGB);
        }
    }

    private Map.Entry<Identifier, Integer> fedAt(double mouseX, double mouseY)
    {
        if (fedBounds == null)
            return null;

        var fed = fedItems();
        int columns = Math.clamp(fed.size(), 4, FED_COLUMNS);
        int x = (int) (mouseX - fedBounds.x() - PAD);
        int y = (int) (mouseY - fedBounds.y() - PAD - LINE - 3);
        if (x < 0 || y < 0 || x >= columns * FED_PITCH)
            return null;

        int index = (y / FED_PITCH + fedScroll) * columns + x / FED_PITCH;
        return index < fed.size() ? fed.get(index) : null;
    }

    /**
     * A tablet beside the selected research on the side away from the eye, or against the far edge when there is no room there,
     * so that it hides neither the eye nor what it talks about. Nothing about a research shows until it is asked about.
     */
    private void extractDetail(GuiGraphicsExtractor graphics)
    {
        detailBounds = null;
        actionButton.visible = false;
        if (selected == null || !tree.containsKey(selected))
            return;

        var research = tree.get(selected);
        var state = stateOf(selected, research);
        boolean unknown = state == NodeState.UNKNOWN;
        int boxWidth = Math.clamp((int) (width * 0.3F), DETAIL_MIN, DETAIL_MAX);
        int inner = boxWidth - PAD * 2;

        var lines = new ArrayList<Line>();
        int nameColor = unknown ? MUTED_COLOR : state == NodeState.DONE ? KNOWN_COLOR : state == NodeState.ACTIVE ? ACTIVE_COLOR : FRONTIER_COLOR;
        font.split(unknown ? Component.translatable("voidheart.research.unread") : Component.translatable(Research.nameKey(selected)), inner).forEach(line -> lines.add(new Line(line, nameColor)));
        font.split(ringOf(research), inner).forEach(line -> lines.add(new Line(line, MUTED_COLOR)));
        int spaced = lines.size();

        font.split(unknown ? Component.translatable("voidheart.research.unread_desc") : Component.translatable(Research.descriptionKey(selected)), inner).forEach(line -> lines.add(new Line(line, TEXT_COLOR)));
        if (!unknown)
            effects(research).forEach(effect -> font.split(effect, inner).forEach(line -> lines.add(new Line(line, FRONTIER_COLOR))));
        statusOf(selected, research, state).ifPresent(status -> font.split(status, inner).forEach(line -> lines.add(new Line(line, starving() && state == NodeState.ACTIVE ? LACK_COLOR : MUTED_COLOR))));

        double progress = state == NodeState.DONE || unknown ? 0 : shownProgress(selected, research) / research.knowledgeCost();
        boolean hasButton = state != NodeState.DONE;
        int boxHeight = PAD * 2 + lines.size() * LINE + 3 + (progress > 0 ? 5 : 0) + (hasButton ? BUTTON_H + 4 : 0);

        int nodeX = map.nodeX(selected);
        boolean onRight = nodeX >= (left + right) / 2;
        int x = onRight ? nodeX + DETAIL_GAP : nodeX - DETAIL_GAP - boxWidth;
        if (x < left + 6 || x + boxWidth > right - 6)
            x = onRight ? left + 6 : right - 6 - boxWidth;
        boolean overButton = x < insightButton.getX() + insightButton.getWidth() + 4 && x + boxWidth > insightButton.getX() - 4;
        int lowest = (overButton ? insightButton.getY() - 4 : height - 6) - boxHeight;
        int y = Math.clamp(map.nodeY(selected) - boxHeight / 2, 6, Math.max(6, lowest));

        detailBounds = new Bounds(x, y, boxWidth, boxHeight);
        tablet(graphics, detailBounds);

        int textY = y + PAD;
        for (int index = 0; index < lines.size(); index++)
        {
            if (index == spaced)
                textY += 3;
            graphics.text(font, lines.get(index).text(), x + PAD, textY, lines.get(index).color());
            textY += LINE;
        }

        if (progress > 0)
        {
            int barColor = state == NodeState.ACTIVE ? ACTIVE_COLOR : 0xFF000000 | FRONTIER_RGB;
            graphics.fill(x + PAD, textY + 1, x + PAD + inner, textY + 3, BAR_BACK_COLOR);
            graphics.fill(x + PAD, textY + 1, x + PAD + (int) (inner * Math.min(1, progress)), textY + 3, barColor);
            textY += 5;
        }

        if (hasButton)
            placeAction(research, state, x + PAD, textY + 3, inner);
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

    private Optional<Component> statusOf(Identifier id, Research research, NodeState state)
    {
        double seconds = secondsToFinish(id, research);
        return switch (state)
        {
            case ACTIVE ->
            {
                if (starving())
                    yield Optional.of(Component.translatable("voidheart.research.starving"));
                yield Optional.of(Double.isInfinite(seconds) ? Component.translatable("voidheart.research.stalled") : Component.translatable("voidheart.research.left", duration(seconds)));
            }
            case REVEALED ->
            {
                double done = snapshot.progress().getOrDefault(id, 0D);
                if (done > 0)
                    yield Optional.of(Component.translatable("voidheart.research.row_paused", String.format("%.0f%%", 100 * done / research.knowledgeCost())));
                yield Optional.of(Double.isInfinite(seconds)
                        ? Component.translatable("voidheart.research.row_cost", wholeNumber(research.knowledgeCost()))
                        : Component.translatable("voidheart.research.row_ready", wholeNumber(research.knowledgeCost()), duration(seconds)));
            }
            default -> Optional.empty();
        };
    }

    private void placeAction(Research research, NodeState state, int x, int y, int buttonWidth)
    {
        actionButton.setX(x);
        actionButton.setY(y);
        actionButton.setWidth(buttonWidth);
        actionButton.visible = true;
        actionButton.active = state != NodeState.UNKNOWN || canReveal(research);
        actionButton.setMessage(switch (state)
        {
            case UNKNOWN -> canReveal(research)
                    ? Component.translatable("voidheart.research.reveal", insightCount(research.insightCost()))
                    : Component.translatable("voidheart.research.needs", insightCount(research.insightCost() - snapshot.insights()));
            case ACTIVE -> Component.translatable("voidheart.research.pause");
            default -> Component.translatable(snapshot.progress().getOrDefault(selected, 0D) > 0 ? "voidheart.research.resume" : "voidheart.research.begin");
        });
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

    private void refine()
    {
        if (canRefine())
            send(Action.Kind.BUY_INSIGHT, null);
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

        var fed = fedOpen ? fedAt(mouseX, mouseY) : null;
        if (fed != null)
            graphics.setComponentTooltipForNextFrame(font, fedTooltip(fed.getKey()), mouseX, mouseY);
        else if (!snapshot.fed().isEmpty() && fedGlyph().contains(mouseX, mouseY))
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("voidheart.research.extracted")), mouseX, mouseY);
        else if (mouseX >= insightButton.getX() && mouseX < insightButton.getX() + insightButton.getWidth() && mouseY >= insightButton.getY() && mouseY < insightButton.getY() + insightButton.getHeight())
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("voidheart.research.craft")), mouseX, mouseY);
    }

    private List<Component> fedTooltip(Identifier id)
    {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        var lines = new ArrayList<Component>();
        lines.add(stack.getHoverName());
        appendKnowledgeLines(stack, lines);
        return lines;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
            return super.mouseClicked(event, doubleClick);

        if (actionButton.visible && actionButton.isMouseOver(event.x(), event.y()))
        {
            lastClicked = null;
            return super.mouseClicked(event, doubleClick);
        }
        if (overTablet(event.x(), event.y()))
            return true;

        if (!snapshot.fed().isEmpty() && fedGlyph().contains(event.x(), event.y()))
        {
            fedOpen = !fedOpen;
            selected = null;
            lastClicked = null;
            return true;
        }

        var node = map.nodeAt(event.x(), event.y());
        if (node.isPresent())
        {
            // The game calls any two quick clicks a double click, wherever they landed
            boolean again = node.get().equals(lastClicked);
            lastClicked = node.get();
            selected = node.get();
            fedOpen = false;
            if (doubleClick && again)
                act();
            return true;
        }

        lastClicked = null;
        if (insightButton.isMouseOver(event.x(), event.y()))
            return super.mouseClicked(event, doubleClick);

        selected = null;
        fedOpen = false;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (fedOpen && fedBounds != null && fedBounds.contains(mouseX, mouseY))
        {
            fedScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Up and down walk the frontier, Enter acts on what is selected, R refines an insight, and Escape lets go of a tablet before it leaves the screen.
     */
    @Override
    public boolean keyPressed(KeyEvent event)
    {
        switch (event.key())
        {
            case InputConstants.KEY_ESCAPE ->
            {
                if (selected != null || fedOpen)
                {
                    selected = null;
                    fedOpen = false;
                    return true;
                }
            }
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
                refine();
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

    public int[] insightPosition()
    {
        return new int[] { insightButton.getX() + insightButton.getWidth() / 2, insightButton.getY() + insightButton.getHeight() / 2 };
    }

    public int[] readoutPosition()
    {
        var readout = readoutBounds();
        return new int[] { readout.x() + readout.width() / 2, readout.y() + readout.height() / 2 };
    }

    public int[] eyePosition()
    {
        return new int[] { (int) map.projection().centerX(), (int) map.projection().centerY() };
    }

    public int[] actionPosition()
    {
        return new int[] { actionButton.getX() + actionButton.getWidth() / 2, actionButton.getY() + actionButton.getHeight() / 2 };
    }

    public int[] extractedPosition()
    {
        var glyph = fedGlyph();
        return new int[] { glyph.x() + GLYPH_SIZE / 2, glyph.y() + GLYPH_SIZE / 2 };
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

        fedOpen = false;
        selected = order.get(Math.clamp(order.indexOf(selected) + by, 0, order.size() - 1));
    }

    @Override
    public void removed()
    {
        minecraft.getTextureManager().release(VOID_A);
        minecraft.getTextureManager().release(VOID_B);
        super.removed();
    }

    /**
     * Points out the items a repository would feed on, what the next one would yield and how drained that kind of item is.
     */
    public static void appendKnowledgeTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines)
    {
        appendKnowledgeLines(stack, lines);
    }

    private static void appendKnowledgeLines(ItemStack stack, List<Component> lines)
    {
        double value = Knowledge.valueOf(stack);
        if (value <= 0)
            return;

        int fed = snapshot.fed().getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), 0);
        var exhaustion = KnowledgeMath.exhaustion(fed);
        var state = Component.translatable("voidheart.knowledge.exhaustion." + exhaustion.name().toLowerCase(Locale.ROOT));
        if (exhaustion == KnowledgeMath.Exhaustion.SPENT)
        {
            lines.add(state.withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        double yield = Math.round(value * KnowledgeMath.feedMultiplier(fed) * 10) / 10D;
        lines.add(Component.translatable("voidheart.knowledge.yield", yield == Math.rint(yield) ? wholeNumber(yield) : String.format("%.1f", yield)).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(state.withStyle(ChatFormatting.GRAY));
    }
}
