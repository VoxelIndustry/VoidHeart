package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.render.ArmillaryPortalRender;
import net.voxelindustry.voidheart.client.screen.ResearchScreen.Bounds;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart.Phase;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryMenu;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads.ActionKind;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads.BubbleView;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads.View;
import net.voxelindustry.voidheart.common.content.armillary.VoidBubble;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The universe seen from the side, built like the knowledge repository: the map is the screen, a click only looks at a bubble, and everything more exact waits on a tablet beside it.
 * Everything drawn comes from the last view the server sent, and a search's roots from its bubble alone.
 */
public class ArmillaryScreen extends AbstractContainerScreen<ArmillaryMenu>
{
    private static final int RIM_MIN      = 20;
    private static final int RIM_MAX      = 40;
    private static final int PAD          = 8;
    private static final int LINE         = 10;
    private static final int BUTTON_H     = 18;
    private static final int QUIET_H      = 14;
    private static final int DETAIL_MIN   = 136;
    private static final int DETAIL_MAX   = 190;
    private static final int DETAIL_GAP   = 8;
    private static final int EDGE         = 6;
    private static final int GLYPH_SIZE   = 16;
    private static final int COST_HEAD    = 13;
    private static final int COST_ROW     = 17;
    private static final int SECTION_GAP  = 4;
    private static final int PLATE_GAP    = 3;
    private static final int ABANDON_GAP  = 11;
    private static final int CLOSED_WHISPER_TICKS = 20 * 20;
    private static final int READOUT_GAP  = 3;
    private static final int BAR_BACK     = 0xFF08050C;
    private static final int LEAVING_TICKS = 20 * 60;

    private static final long FADE_MILLIS = 1200;

    private static final Identifier VOID_A = Identifier.fromNamespaceAndPath(MODID, "dynamic/armillary_void");
    private static final Identifier VOID_B = Identifier.fromNamespaceAndPath(MODID, "dynamic/armillary_void_previous");

    private static View view = View.EMPTY;
    private static long viewReceivedAt;

    private record Line(FormattedCharSequence text, int color)
    {
    }

    private final ItemStack pearl = new ItemStack(VoidHeartItems.VOID_PEARL);

    private final List<String> tabletText = new ArrayList<>();

    private ArmillaryMapView map;
    private ResearchButton   primary;
    private ResearchButton   quiet;
    private int              left;
    private int              right;

    private Identifier    voidCurrent = VOID_A;
    private Identifier    voidPrevious = VOID_B;
    private boolean       hasPrevious;
    private long          bakedAt;
    private List<Integer> bakedKey;
    private View          seen = View.EMPTY;

    private int             selected = -1;
    private int             lastClicked = -1;
    private @Nullable Bounds tablet;
    private int             whisperHalf;
    private String          whisperText = "";

    public ArmillaryScreen(ArmillaryMenu menu, Inventory inventory, Component title)
    {
        super(menu, inventory, title, 176, 166);
    }

    public static void receive(View payload)
    {
        view = payload;
        viewReceivedAt = Util.getMillis();
    }

    @Override
    protected void init()
    {
        super.init();

        int rim = Math.clamp((int) (width * 0.045F), RIM_MIN, RIM_MAX);
        left = rim;
        right = width - rim;
        map = new ArmillaryMapView(font, left, right, height, seed());

        hasPrevious = false;
        bake(voidCurrent, map.rootStep(view, searchProgress()));
        bakedKey = map.lightKey(view, map.rootStep(view, searchProgress()));

        primary = addRenderableWidget(new ResearchButton(0, 0, DETAIL_MIN, BUTTON_H, pressed ->
        {
            setFocused(null);
            pressPrimary();
        }));
        primary.visible = false;
        quiet = addRenderableWidget(new ResearchButton(0, 0, DETAIL_MIN, QUIET_H, true, pressed ->
        {
            setFocused(null);
            send(ActionKind.ABANDON, -1);
        }));
        quiet.visible = false;
    }

    private long seed()
    {
        return minecraft.player == null ? 0 : minecraft.player.getUUID().getLeastSignificantBits();
    }

    private void bake(Identifier target, int rootStep)
    {
        ArmillaryChartBackground.bake(minecraft, target, width, height, left, right, map.chartTop(), map.chartBottom(), map.scale(), map.lights(view, rootStep), seed());
    }

    private static void send(ActionKind kind, int bubble)
    {
        ClientPlayNetworking.send(new ArmillaryPayloads.Action(kind, bubble));
    }

    /**
     * What changed since the last frame gets its moment, and the light is redrawn once it has anywhere new to fall.
     */
    private void syncView()
    {
        var before = seen;
        boolean opening = before == View.EMPTY;
        if (view != before)
        {
            seen = view;
            if (!opening)
                celebrate(before);
            if (opening && view.phaseValue() == Phase.REACHED || before.phaseValue() != Phase.REACHED && view.phaseValue() == Phase.REACHED)
                selected = view.target();
            var looked = bubble(selected);
            if (looked == null || looked.broken())
                selected = -1;
        }

        int rootStep = map.rootStep(view, searchProgress());
        var key = map.lightKey(view, rootStep);
        if (key.equals(bakedKey))
            return;

        bakedKey = key;
        if (opening)
        {
            bake(voidCurrent, rootStep);
            return;
        }

        var fresh = voidPrevious;
        voidPrevious = voidCurrent;
        voidCurrent = fresh;
        bake(voidCurrent, rootStep);
        hasPrevious = true;
        bakedAt = Util.getMillis();
    }

    private void celebrate(View before)
    {
        for (var bubble : view.bubbles())
        {
            if (before.bubbles().stream().noneMatch(old -> old.id() == bubble.id()))
                map.bloom(bubble, false, ResearchScreen.FRONTIER_RGB);
        }

        for (var old : before.bubbles())
        {
            if (!old.broken() && view.bubbles().stream().noneMatch(bubble -> bubble.id() == old.id()))
                map.bloom(old, false, ResearchScreen.READOUT_COLOR & 0xFFFFFF);
        }

        var spent = ArmillaryMapView.target(before);
        if (before.phaseValue() == Phase.CONNECTED && view.phaseValue() != Phase.CONNECTED && spent != null)
            map.collapse(spent);

        var target = ArmillaryMapView.target(view);
        if (target == null || before.phaseValue() == view.phaseValue())
            return;
        var theme = DungeonTheme.byId(target.theme());
        if (theme != null && view.phaseValue() == Phase.REACHED)
            map.bloom(target, true, ArmillaryPortalRender.themeColor(theme));
        else if (view.phaseValue() == Phase.CONNECTED)
            map.bloom(target, true, ResearchScreen.ACTIVE_RGB);
    }

    private @Nullable BubbleView bubble(int id)
    {
        for (var bubble : view.bubbles())
        {
            if (bubble.id() == id)
                return bubble;
        }
        return null;
    }

    private float elapsedTicks()
    {
        return (Util.getMillis() - viewReceivedAt) / 50F;
    }

    /**
     * The view is only sent once per second, so the search carries on from it here for the roots to grow smoothly.
     */
    private float searchProgress()
    {
        if (view.phaseValue() != Phase.SEARCHING || view.searchDuration() <= 0)
            return view.phaseValue().ordinal() > Phase.SEARCHING.ordinal() ? 1 : 0;
        return Mth.clamp((view.searchTicks() + elapsedTicks()) / view.searchDuration(), 0, 1);
    }

    private float secondsOfSearchLeft()
    {
        return Math.max(0, view.searchDuration() - view.searchTicks() - elapsedTicks()) / 20;
    }

    private float secondsOfConnectionLeft()
    {
        return Math.max(0, view.connectionTicks() - elapsedTicks()) / 20;
    }

    private boolean canPay(BubbleView bubble)
    {
        return view.pearls() == View.FREE || view.pearls() >= VoidBubble.openCost(bubble.difficulty());
    }

    private static Component pearls(int count)
    {
        return Component.translatable(count == 1 ? "voidheart.armillary.screen.pearl_one" : "voidheart.armillary.screen.pearl_many", count);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a)
    {
        syncView();

        graphics.blit(RenderPipelines.GUI_TEXTURED, voidCurrent, 0, 0, 0, 0, width, height, width, height);
        if (hasPrevious)
        {
            float fade = (Util.getMillis() - bakedAt) / (float) FADE_MILLIS;
            if (fade < 1)
                graphics.blit(RenderPipelines.GUI_TEXTURED, voidPrevious, 0, 0, 0, 0, width, height, width, height, ARGB.white(1 - fade));
            else
                hasPrevious = false;
        }

        var over = overTablet(mouseX, mouseY) ? null : map.bubbleAt(view, mouseX, mouseY);
        map.extract(graphics, view, searchProgress(), elapsedTicks(), bubble(selected), over, mouseX, mouseY);

        extractReadout(graphics);
        extractWhisper(graphics);
        extractTablet(graphics);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym)
    {
        // All is drawn with the background, relative to the screen rather than a centered panel
    }

    private boolean overTablet(double mouseX, double mouseY)
    {
        return tablet != null && tablet.contains(mouseX, mouseY);
    }

    private Bounds readoutBounds()
    {
        var count = Integer.toString(view.pearls());
        return new Bounds(left + PAD + 3, height - PAD - GLYPH_SIZE, GLYPH_SIZE + READOUT_GAP + font.width(count), GLYPH_SIZE);
    }

    /**
     * What the player could pay with, kept low in a corner like the knowledge held on the repository's screen. Nothing shows for whoever pays nothing.
     */
    private void extractReadout(GuiGraphicsExtractor graphics)
    {
        if (view.pearls() == View.FREE)
            return;

        var bounds = readoutBounds();
        graphics.item(pearl, bounds.x(), bounds.y());
        graphics.text(font, Integer.toString(view.pearls()), bounds.x() + GLYPH_SIZE + READOUT_GAP, bounds.y() + (GLYPH_SIZE - font.lineHeight) / 2 + 1, ResearchScreen.READOUT_COLOR, false);
    }

    /**
     * At most one quiet sentence, about the one thing worth knowing right now.
     */
    private void extractWhisper(GuiGraphicsExtractor graphics)
    {
        Component text = null;
        int color = ResearchScreen.MUTED_COLOR;

        switch (view.phaseValue())
        {
            case SCANNING ->
            {
                if (recentlyClosed())
                {
                    text = Component.translatable("voidheart.armillary.screen.closed");
                    color = ResearchScreen.LACK_COLOR;
                }
                else if (view.bubbles().stream().allMatch(BubbleView::broken))
                    text = Component.translatable("voidheart.armillary.screen.scanning");
                else if (selected == -1)
                    text = Component.translatable("voidheart.armillary.screen.found");
            }
            case SEARCHING -> text = Component.translatable("voidheart.armillary.screen.searching");
            case REACHED ->
            {
                text = Component.translatable("voidheart.armillary.screen.reached");
                color = ResearchScreen.FRONTIER_COLOR;
            }
            case CONNECTED ->
            {
                text = Component.translatable("voidheart.armillary.screen.connected." + view.stageValue().name().toLowerCase(Locale.ROOT));
                color = ArmillaryMapView.failing(view) ? ResearchScreen.LACK_COLOR : ResearchScreen.KNOWN_COLOR;
            }
        }

        whisperHalf = text == null ? 0 : font.width(text) / 2 + PAD;
        whisperText = text == null ? "" : text.getString();
        if (text != null)
            ResearchScreen.whisper(graphics, font, text, color, (left + right) / 2, height);
    }

    private boolean recentlyClosed()
    {
        return view.bubbles().stream().anyMatch(bubble -> bubble.broken() && ArmillaryChart.RUIN_TICKS - bubble.fadesIn() < CLOSED_WHISPER_TICKS);
    }

    private void split(List<Line> lines, Component text, int inner, int color)
    {
        font.split(text, inner).forEach(line -> lines.add(new Line(line, color)));
    }

    /**
     * A tablet beside the selected bubble on the side away from the rings, or on the other side when there is no room there. Nothing about a bubble shows until it is asked about.
     */
    private void extractTablet(GuiGraphicsExtractor graphics)
    {
        tablet = null;
        primary.visible = false;
        quiet.visible = false;
        tabletText.clear();

        var bubble = bubble(selected);
        if (bubble == null)
            return;

        var phase = view.phaseValue();
        boolean isTarget = bubble.id() == view.target();
        boolean connected = phase == Phase.CONNECTED;
        boolean showCost = !connected;
        boolean hasPrimary = !connected && (!isTarget || phase == Phase.REACHED);
        boolean hasQuiet = isTarget && !connected;

        int boxWidth = Math.clamp((int) (width * 0.3F), DETAIL_MIN, DETAIL_MAX);
        int inner = boxWidth - PAD * 2;
        int nameColor = ARGB.opaque(ArmillaryMapView.stateRgb(view, bubble));
        var theme = DungeonTheme.byId(bubble.theme());

        var head = new ArrayList<Line>();
        split(head, Component.translatable("voidheart.armillary.bubble.depth", ArmillaryMapView.numeral(bubble.difficulty())), inner, nameColor);
        if (theme == null)
            split(head, Component.translatable("voidheart.armillary.bubble.theme.unknown"), inner, ResearchScreen.MUTED_COLOR);
        else
            split(head, Component.translatable("voidheart.armillary.bubble.theme." + theme.getSerializedName()), inner, ARGB.opaque(ArmillaryPortalRender.themeColor(theme)));

        var body = new ArrayList<Line>();
        split(body, Component.translatable("voidheart.armillary.bubble.danger." + bubble.difficulty()), inner, ResearchScreen.TEXT_COLOR);
        split(body, Component.translatable("voidheart.armillary.bubble.size." + DungeonSize.values()[bubble.size()].getSerializedName()), inner, ResearchScreen.MUTED_COLOR);

        var status = new ArrayList<Line>();
        if (!isTarget && !connected)
            split(status, Component.translatable("voidheart.armillary.screen.search_takes", ResearchScreen.duration(bubble.searchDuration() / 20D)), inner, ResearchScreen.MUTED_COLOR);
        float bar = -1;
        int barColor = ResearchScreen.ACTIVE_COLOR;
        if (isTarget && phase == Phase.SEARCHING)
        {
            split(status, Component.translatable("voidheart.research.left", ResearchScreen.duration(secondsOfSearchLeft())), inner, ResearchScreen.MUTED_COLOR);
            bar = searchProgress();
        }
        else if (isTarget && connected)
        {
            split(status, Component.translatable("voidheart.research.left", ResearchScreen.duration(secondsOfConnectionLeft())), inner, ArmillaryMapView.failing(view) ? ResearchScreen.LACK_COLOR : ResearchScreen.MUTED_COLOR);
            bar = view.connectionDuration() > 0 ? Math.min(1, secondsOfConnectionLeft() * 20 / view.connectionDuration()) : 0;
            barColor = ArmillaryMapView.failing(view) ? ResearchScreen.LACK_COLOR : ResearchScreen.ACTIVE_COLOR;
        }
        else if (connected)
            split(status, Component.translatable("voidheart.armillary.screen.busy"), inner, ResearchScreen.MUTED_COLOR);
        else if (!isTarget && phase == Phase.SEARCHING)
            split(status, Component.translatable("voidheart.armillary.screen.lose_search"), inner, ResearchScreen.LACK_COLOR);
        else if (!isTarget && phase == Phase.REACHED)
            split(status, Component.translatable("voidheart.armillary.screen.lose_path"), inner, ResearchScreen.LACK_COLOR);
        if (bubble.fadesIn() != BubbleView.NEVER && bubble.fadesIn() < LEAVING_TICKS)
            split(status, Component.translatable("voidheart.armillary.bubble.fading"), inner, ResearchScreen.LACK_COLOR);

        int boxHeight = PAD * 2 + (head.size() + body.size() + status.size()) * LINE + PLATE_GAP
                + (showCost ? SECTION_GAP + COST_HEAD + COST_ROW : 0)
                + (bar >= 0 ? 5 : 0)
                + (hasPrimary ? BUTTON_H + SECTION_GAP : 0)
                + (hasQuiet ? QUIET_H + (hasPrimary ? ABANDON_GAP : SECTION_GAP) : 0);

        int bubbleX = map.bubbleX(bubble);
        int reach = map.radius(bubble) + DETAIL_GAP;
        boolean onRight = bubbleX >= (left + right) / 2;
        int x = onRight ? bubbleX + reach : bubbleX - reach - boxWidth;
        if (x < left + EDGE || x + boxWidth > right - EDGE)
            x = onRight ? bubbleX - reach - boxWidth : bubbleX + reach;
        x = Math.clamp(x, left + EDGE, Math.max(left + EDGE, right - EDGE - boxWidth));
        int center = (left + right) / 2;
        boolean overWhisper = whisperHalf > 0 && x < center + whisperHalf && x + boxWidth > center - whisperHalf;
        int lowest = (overWhisper ? height - PAD - LINE - SECTION_GAP : height - EDGE) - boxHeight;
        int y = Math.clamp(map.bubbleY(bubble) - boxHeight / 2, EDGE, Math.max(EDGE, lowest));

        tablet = new Bounds(x, y, boxWidth, boxHeight);
        ResearchScreen.tablet(graphics, tablet);

        int textX = x + PAD;
        int textY = y + PAD;
        textY = extractLines(graphics, head, textX, textY) + PLATE_GAP;
        textY = extractLines(graphics, body, textX, textY);
        if (showCost)
            textY = extractCost(graphics, bubble, textX, textY, inner);
        textY = extractLines(graphics, status, textX, textY);

        if (bar >= 0)
        {
            graphics.fill(textX, textY + 1, textX + inner, textY + 3, BAR_BACK);
            graphics.fill(textX, textY + 1, textX + (int) (inner * bar), textY + 3, barColor);
            textY += 5;
        }

        if (hasPrimary)
        {
            place(primary, textX, textY + SECTION_GAP, inner);
            primary.setMessage(primaryText(bubble));
            primary.active = !isTarget || canPay(bubble);
            textY += SECTION_GAP + BUTTON_H;
        }
        if (hasQuiet)
        {
            // Set well apart, as a slip off the plate above would cost minutes of searching
            if (hasPrimary)
                graphics.fill(textX, textY + ABANDON_GAP / 2, textX + inner, textY + ABANDON_GAP / 2 + 1, ResearchScreen.RULE_COLOR);
            place(quiet, textX, textY + (hasPrimary ? ABANDON_GAP : SECTION_GAP), inner);
            quiet.setMessage(Component.translatable(phase == Phase.REACHED ? "voidheart.armillary.screen.abandon_path" : "voidheart.armillary.screen.abandon_search"));
        }
    }

    private static void place(ResearchButton button, int x, int y, int width)
    {
        button.setX(x);
        button.setY(y);
        button.setWidth(width);
        button.visible = true;
    }

    private Component primaryText(BubbleView bubble)
    {
        if (bubble.id() != view.target())
            return Component.translatable(view.phaseValue() == Phase.SCANNING ? "voidheart.armillary.screen.search" : "voidheart.armillary.screen.search_instead");

        int cost = VoidBubble.openCost(bubble.difficulty());
        if (canPay(bubble))
            return Component.translatable("voidheart.armillary.screen.open", pearls(cost));
        return Component.translatable("voidheart.armillary.screen.short", pearls(cost - view.pearls()));
    }

    private int extractLines(GuiGraphicsExtractor graphics, List<Line> lines, int x, int y)
    {
        for (var line : lines)
        {
            graphics.text(font, line.text(), x, y, line.color());
            tabletText.add(plain(line.text()));
            y += LINE;
        }
        return y;
    }

    private static String plain(FormattedCharSequence text)
    {
        var plain = new StringBuilder();
        text.accept((index, style, codePoint) ->
        {
            plain.appendCodePoint(codePoint);
            return true;
        });
        return plain.toString();
    }

    /**
     * The price is known from the first look, long before a search has been waited for. It goes red for whoever could not pay it yet.
     */
    private int extractCost(GuiGraphicsExtractor graphics, BubbleView bubble, int x, int y, int inner)
    {
        y += SECTION_GAP;
        var heading = Component.translatable("voidheart.armillary.screen.to_open");
        graphics.text(font, heading, x, y, ResearchScreen.MUTED_COLOR);
        tabletText.add(heading.getString());
        y += COST_HEAD;

        var cost = pearls(VoidBubble.openCost(bubble.difficulty()));
        graphics.fill(x, y, x + GLYPH_SIZE, y + GLYPH_SIZE, ResearchItemPage.SLOT_PLATE);
        graphics.item(pearl, x, y);
        graphics.text(font, cost, x + GLYPH_SIZE + SECTION_GAP, y + SECTION_GAP, canPay(bubble) ? ResearchScreen.FRONTIER_COLOR : ResearchScreen.LACK_COLOR);
        tabletText.add(cost.getString());
        return y + COST_ROW;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        super.extractTooltip(graphics, mouseX, mouseY);

        if (view.pearls() != View.FREE && readoutBounds().contains(mouseX, mouseY))
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("voidheart.armillary.screen.pearls_held").withColor(ResearchScreen.MUTED_COLOR)), mouseX, mouseY);
    }

    private void pressPrimary()
    {
        var bubble = bubble(selected);
        if (bubble == null || view.phaseValue() == Phase.CONNECTED)
            return;

        if (bubble.id() != view.target())
            send(ActionKind.TARGET, bubble.id());
        else if (view.phaseValue() == Phase.REACHED && canPay(bubble))
            send(ActionKind.OPEN, -1);
    }

    /**
     * Lets go of the tablet before leaving the screen.
     */
    private boolean stepBack()
    {
        if (selected == -1)
            return false;

        selected = -1;
        lastClicked = -1;
        return true;
    }

    private void moveSelection(int by)
    {
        var order = view.bubbles().stream().filter(bubble -> !bubble.broken()).sorted(Comparator.comparingInt(map::bubbleY)).map(BubbleView::id).toList();
        if (order.isEmpty())
            return;

        selected = order.get(Math.clamp(order.indexOf(selected) + by, 0, order.size() - 1));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)
    {
        if (event.button() == InputConstants.MOUSE_BUTTON_4)
        {
            stepBack();
            return true;
        }

        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT)
            return super.mouseClicked(event, doubleClick);

        if (primary.visible && primary.isMouseOver(event.x(), event.y()) || quiet.visible && quiet.isMouseOver(event.x(), event.y()))
        {
            lastClicked = -1;
            return super.mouseClicked(event, doubleClick);
        }
        if (overTablet(event.x(), event.y()))
            return true;

        var bubble = map.bubbleAt(view, event.x(), event.y());
        if (bubble != null)
        {
            // The game calls any two quick clicks a double click, wherever they landed
            boolean again = bubble.id() == lastClicked;
            lastClicked = bubble.id();
            selected = bubble.id();
            // Never at the price of a path already found, which takes reading the tablet first
            if (doubleClick && again && (view.target() == -1 || view.target() == bubble.id()))
                pressPrimary();
            return true;
        }

        stepBack();
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Up and down walk the bubbles from the shallowest, Enter acts on the selected one, and Escape lets go of it before it leaves the screen.
     */
    @Override
    public boolean keyPressed(KeyEvent event)
    {
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
                    pressPrimary();
                    return true;
                }
            }
            default ->
            {
            }
        }
        return super.keyPressed(event);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top)
    {
        return false;
    }

    @Override
    public void removed()
    {
        minecraft.getTextureManager().release(VOID_A);
        minecraft.getTextureManager().release(VOID_B);
        view = View.EMPTY;
        super.removed();
    }

    public float[] bubbleCenter(int id)
    {
        var bubble = bubble(id);
        return bubble == null ? null : new float[] { map.bubbleX(bubble), map.bubbleY(bubble) };
    }

    public int[] primaryCenter()
    {
        return new int[] { primary.getX() + primary.getWidth() / 2, primary.getY() + primary.getHeight() / 2 };
    }

    public int[] quietCenter()
    {
        return new int[] { quiet.getX() + quiet.getWidth() / 2, quiet.getY() + quiet.getHeight() / 2 };
    }

    public boolean primaryVisible()
    {
        return primary.visible;
    }

    public boolean primaryActive()
    {
        return primary.visible && primary.active;
    }

    public String primaryLabel()
    {
        return primary.getMessage().getString();
    }

    public int plateGap()
    {
        return quiet.getY() - primary.getY() - primary.getHeight();
    }

    public String whisper()
    {
        return whisperText;
    }

    public int selected()
    {
        return selected;
    }

    public List<String> tabletText()
    {
        return List.copyOf(tabletText);
    }
}
