package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
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
import net.voxelindustry.voidheart.client.scene.ResearchScenes;
import net.voxelindustry.voidheart.common.content.oil.Elixirs;
import net.voxelindustry.voidheart.common.content.oil.OilType;
import net.voxelindustry.voidheart.common.content.research.Constellation;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.KnowledgeMath;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads;
import net.voxelindustry.voidheart.common.content.research.KnowledgePayloads.Action;
import net.voxelindustry.voidheart.common.content.research.KnowledgeRepositoryMenu;
import net.voxelindustry.voidheart.common.content.research.RecipeSheet;
import net.voxelindustry.voidheart.common.content.research.Research;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import org.joml.Vector2i;

import java.util.ArrayDeque;
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
    public static List<RecipeSheet> recipeSheets = List.of();

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

    static final int         RULE_COLOR     = 0xFF4A3860;
    private static final int BAR_BACK_COLOR = 0xFF08050C;
    private static final int TABLET_COLOR   = 0xF40B0810;
    private static final int SPENT_SHADE    = 0xB0100A18;
    static final int         READOUT_COLOR  = 0xFF8A7A99;
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
    private static final int GROOVE        = 5;
    private static final int TOOLTIP_GAP   = 7;
    private static final int TOOLTIP_EDGE  = 4;
    private static final int UNLOCK_ROW    = 17;
    private static final int UNLOCK_PITCH  = 18;
    private static final int UNLOCK_HEAD   = 13;
    private static final int UNLOCK_GAP    = 4;
    private static final int UNLOCK_HOVER  = 0x40;

    private static final double EMPTY_WARNING_SECONDS = 60;

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

    private record Unlock(Identifier item, ItemStack stack, Bounds bounds, boolean named)
    {
    }

    record Bounds(int x, int y, int width, int height)
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
    private ResearchSceneView          sceneView;
    private final List<Unlock>         unlockSlots = new ArrayList<>();
    private final List<ResearchText.Placed> detailLines = new ArrayList<>();
    private final ArrayDeque<ResearchItemPage> pages = new ArrayDeque<>();

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
                () -> KnowledgeMath.affordableInsights(shownKnowledge(), snapshot.nextInsightCost(), snapshot.insightCostGrowth(), ResearchInsightButton.DASHES_MAX),
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
        ResearchVoid.bake(minecraft, target, width, height, left, right, map.lights(), seed());
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
        return String.format(Locale.ROOT, "%.0f", value);
    }

    static String duration(double seconds)
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

    static void tablet(GuiGraphicsExtractor graphics, Bounds bounds)
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
        map.extract(graphics, mouseX, mouseY, voidCurrent, width, height, fullness, !starving(), canRefine(), selected, modal() ? null : hoveredNode(mouseX, mouseY));

        insightButton.active = canRefine();
        insightButton.visible = !modal();

        extractFlight(graphics);
        extractReadout(graphics, mouseX, mouseY);
        if (!modal())
            extractWhisper(graphics);
        extractFedGlyph(graphics, mouseX, mouseY);
        extractDetail(graphics, mouseX, mouseY);
        extractFed(graphics);
        extractCard(graphics, mouseX, mouseY);

        int windowBottom = insightButton.getY() + ResearchInsightButton.HEIGHT;
        if (sceneView != null)
            sceneView.extract(graphics, mouseX, mouseY, width, height, left, right, windowBottom);
        else if (!pages.isEmpty())
            extractPage(graphics, mouseX, mouseY, windowBottom);
    }

    private boolean modal()
    {
        return sceneView != null || !pages.isEmpty();
    }

    private void extractPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int windowBottom)
    {
        var scene = sceneOfPage();
        var at = pages.peek().extract(graphics, mouseX, mouseY, width, left, right, windowBottom, scene.isPresent());
        if (scene.isEmpty())
            return;

        actionButton.setX(at.x());
        actionButton.setY(at.y());
        actionButton.setWidth(at.width());
        actionButton.visible = true;
        actionButton.active = true;
        actionButton.setMessage(Component.translatable("voidheart.research.see_scene"));
    }

    /**
     * A page offers its scene once the research that scene belongs to is done.
     */
    private Optional<Identifier> sceneOfPage()
    {
        return ResearchScenes.forItem(pages.peek().item()).filter(snapshot.completed()::contains);
    }

    private Optional<Identifier> ownerOf(Identifier item)
    {
        return tree.entrySet().stream()
                .filter(entry -> entry.getValue().unlocks().contains(item))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst();
    }

    private boolean hasPage(Identifier item)
    {
        return ownerOf(item).filter(owner -> stateOf(owner, tree.get(owner)) != NodeState.HIDDEN).isPresent();
    }

    private Component described(String key)
    {
        return ResearchText.of(key, new ResearchText.Knowing(tree, id -> stateOf(id, tree.get(id)), this::ownerOf, this::textValue));
    }

    private String textValue(String name)
    {
        var columns = snapshot.columns();
        return switch (name)
        {
            case "column_radius" -> wholeNumber(columns.radius());
            case "column_capacity" -> wholeNumber(columns.capacity());
            case "column_count" -> wholeNumber(columns.count());
            case "column_total" -> wholeNumber(columns.total());
            case "searing_charges" -> wholeNumber(OilType.SEARING.charges());
            case "ember_minutes" -> wholeNumber(Elixirs.EMBER_DRAUGHT_TICKS / 1200D);
            case "stillwater_minutes" -> wholeNumber(Elixirs.STILLWATER_TICKS / 1200D);
            case "bulwark_seconds" -> wholeNumber(Elixirs.BULWARK_TICKS / 20D);
            case "bulwark_health" -> wholeNumber(4 * (Elixirs.BULWARK_AMPLIFIER + 1));
            case "insight_minutes" -> wholeNumber(Elixirs.INSIGHT_TICKS / 1200D);
            case "clarity_minutes" -> wholeNumber(Elixirs.CLARITY_TICKS / 1200D);
            default -> name;
        };
    }

    private Optional<Constellation> concealedIn(Identifier item)
    {
        return ownerOf(item).filter(owner -> stateOf(owner, tree.get(owner)) == NodeState.HIDDEN).map(owner -> tree.get(owner).constellation());
    }

    private void follow(ResearchText.Link link)
    {
        if (link.item())
            openPage(link.id());
        else
            showOnMap(link.id());
    }

    /**
     * Back to the map, on the research that was named, with the same light it had when it was first found.
     */
    private void showOnMap(Identifier research)
    {
        sceneView = null;
        pages.clear();
        fedOpen = false;
        lastClicked = null;
        selected = research;
        map.bloom(research, stateOf(research, tree.get(research)) == NodeState.DONE);
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_CLICKING, 1.4F));
    }

    private void openPage(Identifier item)
    {
        ownerOf(item).ifPresent(owner ->
        {
            pages.push(new ResearchItemPage(font, item, owner, tree.get(owner).constellation(), recipeSheets, snapshot.completed(), this::hasPage,
                    () -> described(ResearchItemPage.pageKey(item)), this::concealedIn));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.1F));
        });
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
        var feed = Component.translatable("voidheart.research.card.feed");
        int widest = rows.stream().filter(row -> row != null).mapToInt(row -> font.width(row.label()) + 10 + font.width(row.value())).max().orElse(0);
        int boxWidth = Math.max(CARD_MIN, Math.max(Math.max(widest, font.width(feed)), PARCHMENT_W + PARCHMENT_GAP + font.width(title)) + PAD * 2);
        int boxHeight = PAD * 2 + CARD_HEADER + rows.stream().mapToInt(row -> row == null ? GROOVE : CARD_ROW).sum() + GROOVE + font.lineHeight;

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
        graphics.text(font, title, x + PAD + PARCHMENT_W + PARCHMENT_GAP, y + 2, TEXT_COLOR);
        y += CARD_HEADER;
        for (var row : rows)
        {
            if (row == null)
            {
                graphics.fill(x + PAD, y + 1, x + boxWidth - PAD, y + 2, RULE_COLOR);
                y += GROOVE;
                continue;
            }
            graphics.text(font, row.label(), x + PAD, y, MUTED_COLOR);
            graphics.text(font, row.value(), x + boxWidth - PAD - font.width(row.value()), y, row.valueColor());
            y += CARD_ROW;
        }
        graphics.fill(x + PAD, y + 1, x + boxWidth - PAD, y + 2, RULE_COLOR);
        graphics.text(font, feed, x + PAD, y + GROOVE, READOUT_COLOR);
        pose.popMatrix();
    }

    /**
     * The figures are what the card is opened for, so they outshine their names, and what needs a response is colored. A null row is a groove between the two groups.
     */
    private List<CardRow> cardRows()
    {
        double knowledge = shownKnowledge();
        double change = knowledgeChange();
        double cost = snapshot.nextInsightCost();

        var rows = new ArrayList<CardRow>();
        rows.add(new CardRow(Component.translatable("voidheart.research.card.gathering"), String.format(Locale.ROOT, "%.2f/s", snapshot.rate()), TEXT_COLOR));
        activeResearch().ifPresent(research -> rows.add(new CardRow(Component.translatable("voidheart.research.card.burning"), String.format(Locale.ROOT, "%.2f/s", research.knowledgePerSecond()), TEXT_COLOR)));
        rows.add(new CardRow(Component.translatable("voidheart.research.card.holds"), wholeNumber(snapshot.cap()), TEXT_COLOR));
        rows.add(new CardRow(Component.translatable("voidheart.research.card.columns"), wholeNumber(snapshot.columns().count()), TEXT_COLOR));

        if (knowledge >= snapshot.cap() && change >= 0)
            rows.add(new CardRow(Component.translatable("voidheart.research.card.full"), Component.translatable("voidheart.research.card.full_lost").getString(), LACK_COLOR));
        else if (change > 0)
            rows.add(new CardRow(Component.translatable("voidheart.research.card.full_in"), duration((snapshot.cap() - knowledge) / change), TEXT_COLOR));
        else if (change < 0 && knowledge > 0)
            rows.add(new CardRow(Component.translatable("voidheart.research.card.empty_in"), duration(knowledge / -change), knowledge / -change < EMPTY_WARNING_SECONDS ? LACK_COLOR : TEXT_COLOR));

        rows.add(null);
        rows.add(new CardRow(Component.translatable("voidheart.research.card.next"), nextInsightText(knowledge, cost, change), knowledge >= cost ? FRONTIER_COLOR : TEXT_COLOR));
        rows.add(new CardRow(Component.translatable("voidheart.research.card.cost"), wholeNumber(Math.ceil(cost)), TEXT_COLOR));
        return rows;
    }

    private String nextInsightText(double knowledge, double cost, double change)
    {
        if (knowledge >= cost)
            return Component.translatable("voidheart.research.card.next_now").getString();
        if (cost > snapshot.cap())
            return Component.translatable("voidheart.research.card.next_capped").getString();

        double wait = KnowledgeMath.secondsToReach(knowledge, cost, change);
        if (Double.isFinite(wait))
            return duration(wait);
        return Component.translatable(activeResearch().isPresent() ? "voidheart.research.card.next_blocked" : "voidheart.research.card.next_stalled").getString();
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

        whisper(graphics, font, text, color, (left + right) / 2, height);
    }

    static void whisper(GuiGraphicsExtractor graphics, Font font, Component text, int color, int center, int screenHeight)
    {
        int y = screenHeight - PAD - LINE + 1;
        int half = font.width(text) / 2 + 3;
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
    private void extractDetail(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        detailBounds = null;
        actionButton.visible = false;
        unlockSlots.clear();
        detailLines.clear();
        if (modal() || selected == null || !tree.containsKey(selected))
            return;

        var research = tree.get(selected);
        var state = stateOf(selected, research);
        boolean unknown = state == NodeState.UNKNOWN;
        int boxWidth = Math.clamp((int) (width * 0.3F), DETAIL_MIN, DETAIL_MAX);
        int inner = boxWidth - PAD * 2;

        var lines = new ArrayList<Line>();
        int nameColor = unknown ? MUTED_COLOR : state == NodeState.DONE ? KNOWN_COLOR : state == NodeState.ACTIVE ? ACTIVE_COLOR : FRONTIER_COLOR;
        font.split(unknown ? Component.translatable("voidheart.research.unread") : Component.translatable(Research.nameKey(selected)), inner).forEach(line -> lines.add(new Line(line, nameColor)));
        int sigilSpace = ResearchMapView.SIGIL_SIZE + ResearchMapView.SIGIL_GAP;
        int constellationColor = 0xFF000000 | ConstellationLook.of(research.constellation()).rgb();
        font.split(Component.translatable(research.constellation().nameKey()), inner - sigilSpace).forEach(line -> lines.add(new Line(line, constellationColor)));
        int spaced = lines.size();

        font.split(unknown ? Component.translatable("voidheart.research.unread_desc") : described(Research.descriptionKey(selected)), inner).forEach(line -> lines.add(new Line(line, TEXT_COLOR)));
        int unlocksAt = lines.size();
        if (!unknown)
            effects(research).forEach(effect -> font.split(effect, inner).forEach(line -> lines.add(new Line(line, FRONTIER_COLOR))));
        statusOf(selected, research, state).ifPresent(status -> font.split(status, inner).forEach(line -> lines.add(new Line(line, starving() && state == NodeState.ACTIVE ? LACK_COLOR : MUTED_COLOR))));

        double progress = state == NodeState.DONE || unknown ? 0 : shownProgress(selected, research) / research.knowledgeCost();
        boolean hasButton = state != NodeState.DONE || ResearchScenes.has(selected);
        int bodyHeight = PAD * 2 + lines.size() * LINE + 3 + (progress > 0 ? 5 : 0) + (hasButton ? BUTTON_H + 4 : 0);

        var unlocks = unknown ? List.<Identifier>of() : unlocksOf(research);
        int columns = Math.max(1, inner / UNLOCK_PITCH);
        boolean named = bodyHeight + unlockSpace(unlocks.size(), UNLOCK_ROW, 1) <= height - 12;
        int unlockHeight = named ? unlockSpace(unlocks.size(), UNLOCK_ROW, 1) : unlockSpace(unlocks.size(), UNLOCK_PITCH, columns);
        int boxHeight = bodyHeight + unlockHeight;

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
            if (index == unlocksAt)
                textY = extractUnlocks(graphics, unlocks, named, columns, x + PAD, textY, inner, mouseX, mouseY, constellationColor);
            boolean besideSigil = index == spaced - 1;
            if (besideSigil)
                ResearchMapView.sigil(graphics, research.constellation(), x + PAD, textY - 1, 255);
            graphics.text(font, lines.get(index).text(), x + PAD + (besideSigil ? sigilSpace : 0), textY, lines.get(index).color());
            detailLines.add(new ResearchText.Placed(lines.get(index).text(), x + PAD + (besideSigil ? sigilSpace : 0), textY));
            textY += LINE;
        }

        if (unlocksAt == lines.size())
            textY = extractUnlocks(graphics, unlocks, named, columns, x + PAD, textY, inner, mouseX, mouseY, constellationColor);

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

    private static List<Identifier> unlocksOf(Research research)
    {
        return research.unlocks().stream().filter(BuiltInRegistries.ITEM::containsKey).toList();
    }

    private static int unlockSpace(int count, int pitch, int columns)
    {
        return count == 0 ? 0 : UNLOCK_GAP + UNLOCK_HEAD + (count + columns - 1) / columns * pitch;
    }

    /**
     * Each thing by its name where the tablet has the height for it, otherwise just the icons, whose names wait under the cursor. Each opens its page.
     */
    private int extractUnlocks(GuiGraphicsExtractor graphics, List<Identifier> unlocks, boolean named, int columns, int x, int y, int inner, int mouseX, int mouseY, int tint)
    {
        if (unlocks.isEmpty())
            return y;

        y += UNLOCK_GAP;
        graphics.text(font, Component.translatable("voidheart.research.unlocks"), x, y, MUTED_COLOR);
        y += UNLOCK_HEAD;
        for (int index = 0; index < unlocks.size(); index++)
        {
            var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(unlocks.get(index)));
            int itemX = named ? x : x + index % columns * UNLOCK_PITCH;
            int itemY = named ? y + index * UNLOCK_ROW : y + index / columns * UNLOCK_PITCH;
            var bounds = new Bounds(itemX, itemY, named ? inner : GLYPH_SIZE, GLYPH_SIZE);
            boolean hovered = bounds.contains(mouseX, mouseY);
            graphics.fill(itemX, itemY, itemX + GLYPH_SIZE, itemY + GLYPH_SIZE, ResearchItemPage.SLOT_PLATE);
            if (hovered)
                graphics.fill(bounds.x() - 1, bounds.y(), bounds.x() + bounds.width() + 1, bounds.y() + bounds.height(), (UNLOCK_HOVER << 24) | (tint & 0xFFFFFF));
            graphics.item(stack, itemX, itemY);
            if (named)
                graphics.text(font, stack.getHoverName(), itemX + GLYPH_SIZE + 4, itemY + 4, hovered ? TEXT_COLOR : FRONTIER_COLOR);
            unlockSlots.add(new Unlock(unlocks.get(index), stack, bounds, named));
        }
        return y + (named ? unlocks.size() * UNLOCK_ROW : (unlocks.size() + columns - 1) / columns * UNLOCK_PITCH);
    }

    private static List<Component> effects(Research research)
    {
        var effects = new ArrayList<Component>();
        if (research.passiveBonus() > 0)
            effects.add(Component.translatable("voidheart.research.passive_bonus", String.format(Locale.ROOT, "%.2f", research.passiveBonus())));
        if (research.columnCapacity() > 0)
            effects.add(Component.translatable("voidheart.research.column_capacity", wholeNumber(research.columnCapacity())));
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
                    yield Optional.of(Component.translatable("voidheart.research.row_paused", String.format(Locale.ROOT, "%.0f%%", 100 * done / research.knowledgeCost())));
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
            case DONE -> Component.translatable("voidheart.research.see_scene");
            default -> Component.translatable(snapshot.progress().getOrDefault(selected, 0D) > 0 ? "voidheart.research.resume" : "voidheart.research.begin");
        });
    }

    private void act()
    {
        if (!pages.isEmpty())
        {
            sceneOfPage().ifPresent(this::openScene);
            return;
        }
        if (selected == null || !tree.containsKey(selected))
            return;

        var state = stateOf(selected, tree.get(selected));
        if (state == NodeState.UNKNOWN)
            send(Action.Kind.REVEAL, selected);
        else if (state == NodeState.REVEALED || state == NodeState.ACTIVE)
            send(Action.Kind.TOGGLE, selected);
        else if (state == NodeState.DONE)
            openScene(selected);
    }

    private void openScene(Identifier research)
    {
        ResearchScenes.of(research).ifPresent(scene ->
        {
            sceneView = new ResearchSceneView(font, scene, ConstellationLook.of(tree.get(research).constellation()));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1F));
        });
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

        if (sceneView != null)
        {
            var hint = sceneView.tooltip(mouseX, mouseY);
            if (hint != null)
                graphics.setComponentTooltipForNextFrame(font, List.of(hint), mouseX, mouseY);
            return;
        }

        if (!pages.isEmpty())
        {
            var lines = pages.peek().tooltip(mouseX, mouseY);
            if (!lines.isEmpty())
                graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }

        var mention = ResearchText.linkAt(font, detailLines, mouseX, mouseY);
        if (mention.isPresent())
        {
            graphics.setComponentTooltipForNextFrame(font, List.of(mention.get().tooltip().copy().withColor(MUTED_COLOR)), mouseX, mouseY);
            return;
        }

        unlockSlots.stream().filter(slot -> slot.bounds().contains(mouseX, mouseY)).findFirst()
                .ifPresent(slot -> graphics.setComponentTooltipForNextFrame(font, slot.named()
                        ? List.of(Component.translatable("voidheart.page.read").withColor(MUTED_COLOR))
                        : List.of(slot.stack().getHoverName(), Component.translatable("voidheart.page.read").withColor(MUTED_COLOR)), mouseX, mouseY));

        var fed = fedOpen ? fedAt(mouseX, mouseY) : null;
        if (fed != null)
            graphics.setComponentTooltipForNextFrame(font, fedTooltip(fed.getKey()), mouseX, mouseY);
        else if (!snapshot.fed().isEmpty() && fedGlyph().contains(mouseX, mouseY))
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("voidheart.research.extracted")), mouseX, mouseY);
        else if (mouseX >= insightButton.getX() && mouseX < insightButton.getX() + insightButton.getWidth() && mouseY >= insightButton.getY() && mouseY < insightButton.getY() + insightButton.getHeight())
            extractInsightTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Held above the plate rather than at the cursor, where it would cover the count it is there to explain.
     */
    private void extractInsightTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        int center = insightButton.getX() + insightButton.getWidth() / 2;
        int top = insightButton.getY();
        graphics.setTooltipForNextFrame(font, insightTooltip().stream().map(Component::getVisualOrderText).toList(), Optional.empty(),
                (screenWidth, screenHeight, x, y, tooltipWidth, tooltipHeight) -> new Vector2i(Math.clamp(center - tooltipWidth / 2, TOOLTIP_EDGE, Math.max(TOOLTIP_EDGE, screenWidth - tooltipWidth - TOOLTIP_EDGE)), top - tooltipHeight - TOOLTIP_GAP),
                mouseX, mouseY, false, null);
    }

    private List<Component> insightTooltip()
    {
        var cost = wholeNumber(Math.ceil(snapshot.nextInsightCost()));
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("voidheart.research.craft"));
        lines.add(Component.translatable("voidheart.research.craft.kept", snapshot.insights()).withColor(MUTED_COLOR));

        if (canRefine())
        {
            lines.add(Component.translatable("voidheart.research.craft.cost", cost).withColor(FRONTIER_COLOR));
            lines.add(Component.translatable("voidheart.research.craft.key").withColor(READOUT_COLOR));
        }
        else if (snapshot.nextInsightCost() > snapshot.cap())
            lines.add(Component.translatable("voidheart.research.craft.capped", cost).withColor(LACK_COLOR));
        else
        {
            double wait = KnowledgeMath.secondsToReach(shownKnowledge(), snapshot.nextInsightCost(), knowledgeChange());
            if (Double.isFinite(wait))
                lines.add(Component.translatable("voidheart.research.craft.wait", cost, duration(wait)).withColor(MUTED_COLOR));
            else
                lines.add(Component.translatable(activeResearch().isPresent() ? "voidheart.research.craft.burning" : "voidheart.research.craft.stalled", cost).withColor(LACK_COLOR));
        }
        return lines;
    }

    private List<Component> fedTooltip(Identifier id)
    {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        var lines = new ArrayList<Component>();
        lines.add(stack.getHoverName());
        appendKnowledgeLines(stack, lines);
        return lines;
    }

    /**
     * One step back through what was opened: a scene, then the pages in the order they were read, then the tablet on the map.
     *
     * @return false when nothing is left to go back from
     */
    private boolean stepBack()
    {
        if (sceneView != null)
            sceneView = null;
        else if (!pages.isEmpty())
            pages.pop();
        else if (selected != null || fedOpen)
        {
            selected = null;
            fedOpen = false;
        }
        else
            return false;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (event.button() == InputConstants.MOUSE_BUTTON_4)
        {
            stepBack();
            return true;
        }

        if (sceneView != null)
        {
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && sceneView.mouseClicked(event.x(), event.y()))
                sceneView = null;
            return true;
        }

        if (!pages.isEmpty())
        {
            if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
                return true;
            if (actionButton.visible && actionButton.isMouseOver(event.x(), event.y()))
                return super.mouseClicked(event, doubleClick);
            var mentioned = pages.peek().mentionAt(event.x(), event.y());
            if (mentioned.isPresent())
                follow(mentioned.get());
            else if (pages.peek().overClose(event.x(), event.y()))
                pages.pop();
            else
                pages.peek().linkAt(event.x(), event.y()).ifPresent(this::openPage);
            return true;
        }

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
            return super.mouseClicked(event, doubleClick);

        var unlock = unlockSlots.stream().filter(slot -> slot.bounds().contains(event.x(), event.y())).findFirst();
        if (unlock.isPresent())
        {
            lastClicked = null;
            openPage(unlock.get().item());
            return true;
        }

        var mentioned = ResearchText.linkAt(font, detailLines, event.x(), event.y());
        if (mentioned.isPresent())
        {
            follow(mentioned.get());
            return true;
        }

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
        if (sceneView != null)
        {
            if (event.key() == InputConstants.KEY_ESCAPE)
                stepBack();
            else
                sceneView.keyPressed(event.key());
            return true;
        }

        if (!pages.isEmpty())
        {
            if (event.key() == InputConstants.KEY_ESCAPE)
                stepBack();
            else if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)
                act();
            return true;
        }

        switch (event.key())
        {
            case InputConstants.KEY_ESCAPE ->
            {
                if (stepBack())
                    return true;
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

    public int[] scenePosition(String part, int step)
    {
        return switch (part)
        {
            case "expand" -> sceneView.expandPosition();
            case "close" -> sceneView.closePosition();
            case "play" -> sceneView.playPosition();
            case "mark" -> sceneView.markPosition(step);
            default -> sceneView.stageCenter();
        };
    }

    public int[] unlockPosition(int index)
    {
        var bounds = unlockSlots.get(index).bounds();
        return new int[] { bounds.x() + 8, bounds.y() + 8 };
    }

    public Optional<int[]> pageMentionPosition(Identifier research)
    {
        return pages.peek().mentionPosition(new ResearchText.Link(false, research));
    }

    public Optional<int[]> pageItemMentionPosition(Identifier item)
    {
        return pages.peek().mentionPosition(new ResearchText.Link(true, item));
    }

    public Identifier pageItem()
    {
        return pages.isEmpty() ? null : pages.peek().item();
    }

    public int[] pageSlotPosition(Identifier item)
    {
        return pages.peek().slotPosition(item);
    }

    public List<int[]> pageRiteIconPositions()
    {
        return pages.peek().riteIconPositions();
    }

    public int[] pageClosePosition()
    {
        return pages.peek().closePosition();
    }

    public boolean actionVisible()
    {
        return actionButton.visible;
    }

    public boolean sceneOpen()
    {
        return sceneView != null;
    }

    public boolean sceneFullScreen()
    {
        return sceneView != null && sceneView.fullScreen();
    }

    public boolean scenePaused()
    {
        return sceneView != null && sceneView.paused();
    }

    public int sceneStep()
    {
        return sceneView == null ? -1 : sceneView.currentStep();
    }

    public int sceneSteps()
    {
        return sceneView == null ? 0 : sceneView.steps();
    }

    /** Holds the scene still on the end of a step, so that a test can look at it. */
    public void holdScene(int step)
    {
        sceneView.holdAtEndOf(step);
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
        lines.add(Component.translatable("voidheart.knowledge.yield", yield == Math.rint(yield) ? wholeNumber(yield) : String.format(Locale.ROOT, "%.1f", yield)).withStyle(ChatFormatting.DARK_AQUA));
        lines.add(state.withStyle(ChatFormatting.GRAY));
    }
}
