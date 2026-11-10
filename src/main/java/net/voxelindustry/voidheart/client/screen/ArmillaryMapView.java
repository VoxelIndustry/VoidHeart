package net.voxelindustry.voidheart.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.client.render.ArmillaryPortalRender;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryChart.Phase;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads.BubbleView;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryPayloads.View;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryRoots;
import net.voxelindustry.voidheart.common.content.armillary.ArmillaryTile;
import net.voxelindustry.voidheart.common.content.armillary.ConnectionTimeline;
import net.voxelindustry.voidheart.common.content.armillary.VoidBubble;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusTheme;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * The hole in the slab: the rings at the top feeling their way down, the bubbles they have found, the dark between them, and the Daedalus under it all.
 * Colour follows the knowledge repository: teal can be acted on, gold is running, lilac rests, and a bubble's theme only shows in its own light.
 */
final class ArmillaryMapView
{
    private static final Identifier SPRITES = Identifier.fromNamespaceAndPath(MODID, "textures/gui/armillary.png");
    private static final int        ATLAS   = 128;

    private static final int[] BUBBLE_SIZE = { 16, 24, 32 };
    private static final int[] CORE_U      = { 0, 16, 40 };
    private static final int   CORE_V      = 0;
    private static final int   RIM_V       = 32;
    private static final int[] HALO_SIZE   = { 24, 32, 40 };
    private static final int[] HALO_U      = { 0, 24, 56 };
    private static final int   HALO_V      = 64;

    private static final int LARGE_MIN       = 450;
    private static final int CLEARANCE       = 18;
    private static final int CHART_GAP       = 2;
    private static final int BOTTOM_BAND     = 30;
    private static final int MIN_HOVER       = 7;
    private static final int LABEL_DROP      = 4;
    private static final int GLYPH_SIZE      = 16;
    private static final int BRACKET_GAP     = 2;

    private static final int   CORE_ALPHA     = 225;
    private static final int   IDLE_CORE_RGB  = 0x3A3046;
    private static final int   IDLE_RIM_ALPHA = 190;
    private static final float THEME_CORE     = 0.62F;
    private static final float DISTANT_FADE   = 0.4F;
    private static final int   FOUND_FADE     = 40;
    private static final int   LEAVING_TICKS  = 20 * 60;
    private static final long  BREATH_MILLIS  = 2600;

    private static final float SOURCE_X         = 0.5F;
    private static final float MAIN_CORE_WIDTH  = 1F;
    private static final float MAIN_TIP_WIDTH   = 0.6F;
    private static final float MAIN_GLOW_WIDTH  = 1.9F;
    private static final int   MAIN_GLOW_ALPHA  = 44;
    private static final float DEAD_END_WIDTH   = 0.6F;
    // Any thinner and a piece can fall between pixels at the smallest gui scale
    private static final float DEAD_END_TIP     = 0.5F;
    private static final int   DEAD_END_ALPHA   = 205;
    private static final int   WITHERED_RGB     = 0x5E4F66;
    private static final float WITHER_RECEDE    = 0.45F;
    private static final float MOTE_SPACING     = 24;
    private static final float MOTE_SPEED       = 22;
    private static final int   MOTE_ALPHA       = 230;
    // Even a straight joint overlaps a little, so that no row of pixels between two pieces is missed
    private static final float JOINT_OVERLAP    = 0.2F;

    private static final float[] RING_RADII      = { 14, 10.5F, 7 };
    private static final int[]   RING_NEAR       = { 0xFFEDE26A, 0xFF8F8499, 0xFFE9E4FF };
    private static final int[]   RING_FAR        = { 0xFF787238, 0xFF4A4352, 0xFF7E7AA0 };
    private static final float   RING_PITCH      = 0.32F;
    private static final float   RING_FALTER     = 1.6F;
    private static final float   SPIN_EASE       = 0.02F;
    private static final int     PORTAL_RADIUS   = 3;
    private static final int     PORTAL_COLOR    = 0xFF040208;
    private static final int     MOUNT_RADIUS    = 18;
    private static final int     MOUNT_BACK      = 0xFF050308;
    private static final int     GAUGE_REST      = 0xFF2E2340;
    private static final float   SWEEP_LENGTH    = 0.14F;
    private static final float   SWEEP_MILLIS    = 5200;
    private static final float   LOOK_REACH      = 3;
    private static final float   LOOK_EASE       = 0.12F;
    private static final float   LOOK_DISTANCE   = 150;

    private static final int  WATCHERS       = 6;
    private static final int  WATCHER_FLINCH = 46;
    private static final int  EMBERS         = 7;

    private static final long  BLOOM_MILLIS  = 1100;
    private static final long  FINISH_MILLIS = 1800;
    private static final float BLOOM_REACH   = 30;
    private static final float FINISH_REACH  = 90;
    private static final int   BLOOM_DOTS    = 32;

    private static final float LIGHT_REACH_SHARE = 0.25F;
    private static final float LIGHT_MIN_REACH   = 38;
    private static final float THEME_LIGHT       = 0.42F;
    static final int           ROOT_LIGHTS       = 6;
    private static final float ROOT_LIGHT_RADIUS = 20;
    private static final float ROOT_LIGHT        = 0.42F;
    private static final float IDLE_LIGHT_SHARE  = 1.2F;
    private static final float IDLE_LIGHT_EXTRA  = 4;
    private static final float IDLE_LIGHT        = 0.34F;

    private static final int   LACK_RGB       = ResearchScreen.LACK_COLOR & 0xFFFFFF;
    private static final float CRACK_GAP      = -0.45F;
    private static final float SHARD_DRIFT    = 9;
    private static final float SHARD_JAG      = 1.7F;
    private static final float SHARD_INWARD   = 0.86F;
    private static final float SHARD_INWARD_SHARE = 0.68F;
    private static final int   HOLLOW_ALPHA   = 130;
    private static final long  DYING_MILLIS   = 2600;

    private static final String[] NUMERALS = { "I", "II", "III", "IV", "V" };

    // The door is the rings' portal torn into the strata's lip, and the reached district's light rises from inside it
    private static final int   DOOR_RADIUS         = 3;
    private static final int   DOOR_SINK           = 1;
    private static final float STRATA_SEARCH_LIGHT = 0.7F;
    private static final float DOOR_LIGHT_REACH    = 26;
    private static final float DISTRICT_LIGHT_REACH = 38;
    private static final float DISTRICT_LIGHT_DEPTH = 0.55F;
    private static final int   WATCHER_CLEARANCE   = 8;
    private static final int   LIP_HOVER           = 2;
    private static final long  REVEAL_MILLIS       = 2400;
    private static final int   REVEAL_RISE         = 6;

    private record Root(ArmillaryRoots.Branch branch, float[] xs, float[] ys, float length)
    {
    }

    private record Bloom(int x, int y, int radius, long start, boolean finished, int rgb)
    {
    }

    private record Spot(int x, int y, float phase)
    {
    }

    private final Font font;
    private final int  left;
    private final int  right;
    private final int  holeLeft;
    private final int  holeRight;
    private final int  chartTop;
    private final int  chartBottom;
    private final int  scale;

    private final List<Spot>  watchers = new ArrayList<>();
    private final List<Spot>  embers   = new ArrayList<>();
    private final List<Bloom> blooms   = new ArrayList<>();
    private final float[]     angles   = new float[ArmillaryTile.RINGS];

    private List<Root> roots = List.of();
    private long       rootsSeed;
    private int        rootsTarget = -1;
    private float      spin = 1;
    private long       lastFrame = Util.getMillis();
    private float      lookX;
    private float      lookY;
    private @Nullable Root dying;
    private long       dyingAt;
    private final long seed;
    // Halved so that subtracting it from the time cannot overflow
    private long       revealAt = Long.MIN_VALUE / 2;

    ArmillaryMapView(Font font, int left, int right, int height, long seed)
    {
        this.font = font;
        this.left = left;
        this.right = right;
        this.holeLeft = left + CLEARANCE;
        this.holeRight = right - CLEARANCE;
        this.scale = height >= LARGE_MIN ? 2 : 1;
        this.chartTop = ArmillaryChartBackground.surfaceBottom(scale) + CHART_GAP;
        this.chartBottom = height - BOTTOM_BAND;
        this.seed = seed;

        var random = new Random(seed);
        int depths = Math.max(2, strataTop() - WATCHER_CLEARANCE * scale - chartTop);
        for (int index = 0; index < WATCHERS; index++)
            watchers.add(new Spot(holeLeft + random.nextInt(holeRight - holeLeft), chartTop + depths / 2 + random.nextInt(depths / 2), random.nextFloat()));
        for (int index = 0; index < EMBERS; index++)
            embers.add(new Spot(holeLeft + random.nextInt(holeRight - holeLeft), chartTop + random.nextInt(depths), random.nextFloat()));
    }

    /**
     * Where the Daedalus begins, across the whole hole.
     */
    int strataTop()
    {
        return Math.round(screenY(ArmillaryChart.STRATA_TOP));
    }

    int holeLeft()
    {
        return holeLeft;
    }

    static boolean targetsStrata(View view)
    {
        return view.targetsStrata();
    }

    /**
     * Where the root comes down on the strata. Only means something while the strata is the target.
     */
    int strataX(View view)
    {
        return Math.round(screenX(view.strata().x()));
    }

    static @Nullable DaedalusTheme strataTheme(View view)
    {
        int theme = view.strata().theme();
        return theme < 0 || theme >= DaedalusTheme.values().length ? null : DaedalusTheme.values()[theme];
    }

    /**
     * Where the strata's torn lip is above a column of the screen, matching the bake.
     */
    int lipY(int x)
    {
        return ArmillaryChartBackground.lipY(x, left, strataTop(), scale, seed);
    }

    boolean isOverStrata(double mouseX, double mouseY)
    {
        return mouseX >= holeLeft && mouseX < holeRight && mouseY >= lipY((int) mouseX) - LIP_HOVER * scale;
    }

    ArmillaryChartBackground.Strata strata(View view)
    {
        return new ArmillaryChartBackground.Strata(view.strata().known(), strataTop());
    }

    int doorY(View view)
    {
        return lipY(strataX(view)) + DOOR_SINK * scale;
    }

    int doorReach()
    {
        return (DOOR_RADIUS + 1) * scale;
    }

    /**
     * The strata coming into focus: a light running out along its lip from the middle while the studied look fades in.
     */
    void revealStrata()
    {
        revealAt = Util.getMillis();
    }

    boolean isRevealing()
    {
        return Util.getMillis() - revealAt < REVEAL_MILLIS;
    }

    int scale()
    {
        return scale;
    }

    int chartTop()
    {
        return chartTop;
    }

    int chartBottom()
    {
        return chartBottom;
    }

    private int chartHeight()
    {
        return chartBottom - chartTop;
    }

    private float screenX(float x)
    {
        return holeLeft + x * (holeRight - holeLeft);
    }

    private float screenY(float y)
    {
        return chartTop + y * chartHeight();
    }

    int bubbleX(BubbleView bubble)
    {
        return Math.round(screenX(bubble.x()));
    }

    int bubbleY(BubbleView bubble)
    {
        return Math.round(screenY(VoidBubble.y(bubble.difficulty(), bubble.seed())));
    }

    int radius(BubbleView bubble)
    {
        return BUBBLE_SIZE[bubble.size()] * scale / 2;
    }

    @Nullable BubbleView bubbleAt(View view, double mouseX, double mouseY)
    {
        for (var bubble : view.bubbles())
        {
            if (bubble.broken())
                continue;

            double reach = Math.max(MIN_HOVER, radius(bubble));
            double dx = mouseX - bubbleX(bubble);
            double dy = mouseY - bubbleY(bubble);
            if (dx * dx + dy * dy <= reach * reach)
                return bubble;
        }
        return null;
    }

    static @Nullable BubbleView target(View view)
    {
        for (var bubble : view.bubbles())
        {
            if (bubble.id() == view.target())
                return bubble;
        }
        return null;
    }

    static String numeral(int difficulty)
    {
        return NUMERALS[difficulty - 1];
    }

    static int strataStateRgb(View view)
    {
        if (!targetsStrata(view))
            return view.phaseValue() == Phase.SCANNING && view.strata().known() ? ResearchScreen.FRONTIER_RGB : ResearchScreen.KNOWN_RGB;
        return switch (view.phaseValue())
        {
            case REACHED -> ResearchScreen.FRONTIER_RGB;
            case CONNECTED -> failing(view) ? LACK_RGB : ResearchScreen.ACTIVE_RGB;
            default -> ResearchScreen.ACTIVE_RGB;
        };
    }

    /**
     * The state a bubble is in, as a colour: only what can be acted on is teal, only what is running is gold.
     */
    static int stateRgb(View view, BubbleView bubble)
    {
        var phase = view.phaseValue();
        if (bubble.id() == view.target())
        {
            return switch (phase)
            {
                case REACHED -> ResearchScreen.FRONTIER_RGB;
                case CONNECTED -> failing(view) ? LACK_RGB : ResearchScreen.ACTIVE_RGB;
                default -> ResearchScreen.ACTIVE_RGB;
            };
        }
        return phase == Phase.SCANNING ? ResearchScreen.FRONTIER_RGB : ResearchScreen.KNOWN_RGB;
    }

    static boolean failing(View view)
    {
        return view.stageValue().ordinal() >= ConnectionTimeline.Stage.FAILING.ordinal();
    }

    private static int themeRgb(DungeonTheme theme)
    {
        return ArmillaryPortalRender.themeColor(theme);
    }

    private static int themeLight(int rgb)
    {
        return ARGB.color(0, (int) (ARGB.red(rgb) * THEME_LIGHT), (int) (ARGB.green(rgb) * THEME_LIGHT), (int) (ARGB.blue(rgb) * THEME_LIGHT));
    }

    private void prepareRoots(BubbleView target)
    {
        prepareRoots(target.id(), target.seed(), target.x(), VoidBubble.y(target.difficulty(), target.seed()), (float) radius(target) / chartHeight());
    }

    /**
     * @return whether there is anything to grow roots towards
     */
    private boolean prepareRoots(View view)
    {
        if (targetsStrata(view))
        {
            prepareRoots(ArmillaryChart.STRATA, view.strata().seed(), view.strata().x(), ArmillaryChart.STRATA_TOP, 0);
            return true;
        }

        var target = target(view);
        if (target == null)
            return false;
        prepareRoots(target);
        return true;
    }

    private void prepareRoots(int id, long seed, float x, float y, float radius)
    {
        if (rootsTarget == id && rootsSeed == seed)
            return;

        rootsTarget = id;
        rootsSeed = seed;
        // Roots leave from just outside the gauge's shaded lip, so none ever shows under it
        float mount = (MOUNT_RADIUS + 1) * scale + 1;
        var grown = ArmillaryRoots.grow(seed, SOURCE_X, mount / (holeRight - holeLeft), mount / chartHeight(), x, y, radius);

        var prepared = new ArrayList<Root>();
        for (var branch : grown)
        {
            int points = branch.xs().length;
            var xs = new float[points];
            var ys = new float[points];
            float length = 0;
            for (int point = 0; point < points; point++)
            {
                xs[point] = screenX(branch.xs()[point]);
                ys[point] = screenY(branch.ys()[point]);
                if (point > 0)
                    length += (float) Math.hypot(xs[point] - xs[point - 1], ys[point] - ys[point - 1]);
            }
            prepared.add(new Root(branch, xs, ys, length));
        }
        roots = prepared;
    }

    private @Nullable Root mainRoot()
    {
        for (var root : roots)
        {
            if (root.branch().main())
                return root;
        }
        return null;
    }

    /**
     * How much of the path has been lit so far. The light only changes between these steps, as it has to be baked again.
     */
    int rootStep(View view, float progress)
    {
        if (!prepareRoots(view))
            return 0;

        var main = mainRoot();
        return main == null ? 0 : Mth.floor(Math.min(1, main.branch().grown(progress)) * ROOT_LIGHTS);
    }

    /**
     * Where the dark gives way: under the rings, a little around each bubble, along the path, and in the bubble's own hue once a root has reached it.
     */
    List<ResearchVoid.Light> lights(View view, int rootStep)
    {
        var lights = new ArrayList<ResearchVoid.Light>();
        float reach = Math.max(LIGHT_MIN_REACH, chartHeight() * LIGHT_REACH_SHARE);
        lights.add(new ResearchVoid.Light(screenX(SOURCE_X), chartTop, reach, 0.95, ResearchVoid.LIT_RGB));

        var targetTheme = target(view) == null ? null : DungeonTheme.byId(target(view).theme());
        int pathRgb = targetTheme == null ? ResearchVoid.LIT_RGB : themeLight(themeRgb(targetTheme));
        var daedalusTheme = targetsStrata(view) ? strataTheme(view) : null;
        if (daedalusTheme != null)
            pathRgb = themeLight(daedalusTheme.color());
        if (targetsStrata(view) && daedalusTheme == null)
            lights.add(new ResearchVoid.Light(strataX(view), doorY(view), DOOR_LIGHT_REACH * scale, STRATA_SEARCH_LIGHT, ResearchVoid.LIT_RGB));
        else if (daedalusTheme != null)
        {
            // Through the door and up out of the district below, the only colour the strata ever takes
            int screenBottom = chartBottom + BOTTOM_BAND;
            lights.add(new ResearchVoid.Light(strataX(view), doorY(view), DOOR_LIGHT_REACH * 1.6 * scale, 1, pathRgb));
            lights.add(new ResearchVoid.Light(strataX(view), strataTop() + (screenBottom - strataTop()) * DISTRICT_LIGHT_DEPTH, DISTRICT_LIGHT_REACH * scale, 1, pathRgb));
        }

        for (var bubble : view.bubbles())
        {
            if (bubble.broken())
                continue;

            float radius = radius(bubble);
            // Too small to meet: a shared light means a shared group on the repository's map, and these have nothing to do with each other
            if (bubble.id() != view.target())
                lights.add(new ResearchVoid.Light(bubbleX(bubble), bubbleY(bubble), radius * IDLE_LIGHT_SHARE + IDLE_LIGHT_EXTRA * scale, IDLE_LIGHT, ResearchVoid.LIT_RGB));
            else if (targetTheme == null)
                lights.add(new ResearchVoid.Light(bubbleX(bubble), bubbleY(bubble), radius * 2.6 + 18, 0.7, ResearchVoid.LIT_RGB));
            else
                lights.add(new ResearchVoid.Light(bubbleX(bubble), bubbleY(bubble), radius * 3.4 + 36, 1, pathRgb));
        }

        var main = target(view) == null && !targetsStrata(view) ? null : mainRoot();
        if (main != null)
        {
            int segments = main.branch().segments();
            for (int step = 1; step <= rootStep; step++)
            {
                int point = Math.min(segments, step * segments / ROOT_LIGHTS);
                lights.add(new ResearchVoid.Light(main.xs()[point], main.ys()[point], ROOT_LIGHT_RADIUS * scale, ROOT_LIGHT, pathRgb));
            }
        }
        return lights;
    }

    /**
     * What the light depends on, to tell when it has to be baked again.
     */
    List<Integer> lightKey(View view, int rootStep)
    {
        var key = new ArrayList<Integer>();
        key.add(rootStep);
        key.add(view.target());
        key.add(view.strata().known() ? 1 : 0);
        key.add(view.strata().theme());
        key.add(Float.floatToIntBits(view.strata().x()));
        for (var bubble : view.bubbles())
        {
            key.add(bubble.id());
            key.add(bubble.theme());
            key.add(bubble.broken() ? 1 : 0);
        }
        return key;
    }

    void bloom(BubbleView bubble, boolean finished, int rgb)
    {
        blooms.add(new Bloom(bubbleX(bubble), bubbleY(bubble), radius(bubble), Util.getMillis(), finished, rgb));
    }

    void bloomStrata(View view, boolean finished, int rgb)
    {
        blooms.add(new Bloom(strataX(view), doorY(view), doorReach(), Util.getMillis(), finished, rgb));
    }

    /**
     * The way into the Daedalus closed: the door on the strata goes dark and the root dies back.
     */
    void collapseStrata(View before)
    {
        bloomStrata(before, true, LACK_RGB);
        if (rootsTarget == ArmillaryChart.STRATA)
        {
            dying = mainRoot();
            dyingAt = Util.getMillis();
        }
    }

    /**
     * Its light goes out, and the path it stood on dies back to the rings.
     */
    void collapse(BubbleView bubble)
    {
        bloom(bubble, true, LACK_RGB);
        if (rootsTarget == bubble.id())
        {
            dying = mainRoot();
            dyingAt = Util.getMillis();
        }
    }

    /**
     * @param strataLooked whether the strata is selected or under the cursor, rather than a bubble
     * @param strataLookX  where along it, when it is not the target
     */
    void extract(GuiGraphicsExtractor graphics, View view, float progress, float elapsedTicks, @Nullable BubbleView selected, @Nullable BubbleView hovered, boolean strataLooked,
                 int strataLookX, int mouseX, int mouseY)
    {
        long now = Util.getMillis();
        var target = target(view);

        extractWatchers(graphics, mouseX, mouseY, now);
        if (view.phaseValue() == Phase.SCANNING)
            extractEmbers(graphics, now);
        if (prepareRoots(view))
            extractRoots(graphics, view, progress, now);
        else
            rootsTarget = -1;
        extractDying(graphics, now);
        extractDoor(graphics, view, now);

        for (var bubble : view.bubbles())
        {
            if (bubble.broken())
                extractBroken(graphics, bubble, elapsedTicks);
            else
                extractBubble(graphics, view, bubble, now);
        }
        var looked = selected != null ? selected : hovered;
        int lookX = looked != null ? bubbleX(looked) : strataLooked ? strataLookX : mouseX;
        int lookY = looked != null ? bubbleY(looked) : strataLooked ? lipY(lookX) : mouseY;
        extractRings(graphics, view, progress, elapsedTicks, lookX, lookY, looked != null || strataLooked, now);

        if (selected != null)
            ResearchMapView.brackets(graphics, bubbleX(selected), bubbleY(selected), radius(selected) + BRACKET_GAP, ResearchScreen.TEXT_COLOR);
        if (hovered != null && hovered != selected)
            ResearchMapView.brackets(graphics, bubbleX(hovered), bubbleY(hovered), radius(hovered) + BRACKET_GAP, ResearchScreen.MUTED_COLOR);
        if (strataLooked && targetsStrata(view))
            ResearchMapView.brackets(graphics, strataX(view), doorY(view), doorReach() + BRACKET_GAP, isStrataSelected ? ResearchScreen.TEXT_COLOR : ResearchScreen.MUTED_COLOR);

        extractNumerals(graphics, looked);
        extractLabel(graphics, view, target, selected, hovered);
        extractStrataLabel(graphics, view, strataLooked, strataLookX, selected, hovered);
        extractBlooms(graphics, now);
        extractReveal(graphics, now);
        lastFrame = now;
    }

    /**
     * A light running out along the lip from the middle, fading behind itself, the moment the strata comes into focus.
     */
    private void extractReveal(GuiGraphicsExtractor graphics, long now)
    {
        float t = (now - revealAt) / (float) REVEAL_MILLIS;
        if (t < 0 || t >= 1)
            return;

        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        int middle = (holeLeft + holeRight) / 2;
        float front = eased * (holeRight - holeLeft) / 2F;
        float trail = Math.max(1, front * 0.6F);
        for (int x = holeLeft; x < holeRight; x += scale)
        {
            float behind = front - Math.abs(x - middle);
            if (behind < 0)
                continue;

            int alpha = (int) (255 * Math.max(0, 1 - behind / trail) * (1 - t));
            if (alpha <= 0)
                continue;
            int y = lipY(x) - scale;
            graphics.fill(x, y, x + scale, y + scale, ARGB.color(alpha, ResearchScreen.REVEALED_RGB));
            if (behind < REVEAL_RISE * scale)
                graphics.fill(x, y - scale * 2, x + scale, y - scale, ARGB.color(alpha / 3, ResearchScreen.KNOWN_RGB));
        }
    }

    /**
     * Where the root came down, the same torn hole as the one inside the rings opens in the lip, rimmed in the district's colour. It breathes until it is opened.
     */
    private void extractDoor(GuiGraphicsExtractor graphics, View view, long now)
    {
        if (!targetsStrata(view) || view.phaseValue().ordinal() < Phase.REACHED.ordinal())
            return;

        var theme = strataTheme(view);
        int rim = theme == null ? ARGB.opaque(strataStateRgb(view)) : ARGB.opaque(theme.color());
        if (failing(view))
            rim = ARGB.opaque(LACK_RGB);
        if (view.phaseValue() == Phase.REACHED)
        {
            float breath = 0.5F + 0.5F * (float) Math.sin(now * Math.PI * 2 / BREATH_MILLIS);
            rim = ARGB.srgbLerp(0.35F + 0.65F * breath, ARGB.opaque(IDLE_CORE_RGB), rim);
        }

        int x = strataX(view);
        int y = doorY(view);
        disc(graphics, x, y, DOOR_RADIUS, PORTAL_COLOR);
        arc(graphics, x, y, DOOR_RADIUS + 1, 0, 1, rim);
    }

    /**
     * A name for the strata, wherever it is being looked at or the root is reaching. Someone who has not studied it only knows there is something.
     */
    private void extractStrataLabel(GuiGraphicsExtractor graphics, View view, boolean strataLooked, int strataLookX, @Nullable BubbleView selected, @Nullable BubbleView hovered)
    {
        boolean targeted = targetsStrata(view);
        if (!targeted && !strataLooked || isStrataSelected)
            return;

        var text = Component.translatable(view.strata().known() ? "voidheart.armillary.strata.name" : "voidheart.armillary.strata.unknown");
        int half = font.width(text) / 2 + 2;
        int anchor = targeted ? strataX(view) : strataLookX;
        int centerX = Math.clamp(anchor, holeLeft + half, Math.max(holeLeft + half, holeRight - half));
        int y = (targeted ? doorY(view) - doorReach() : lipY(centerX)) - LABEL_DROP - font.lineHeight;

        graphics.fill(centerX - half, y - 1, centerX + half, y + font.lineHeight - 1, ResearchMapView.LABEL_BACK_COLOR);
        graphics.centeredText(font, text, centerX, y, ARGB.opaque(view.strata().known() ? strataStateRgb(view) : ResearchScreen.MUTED_COLOR));
    }

    private boolean isStrataSelected;

    void setStrataSelected(boolean selected)
    {
        isStrataSelected = selected;
    }

    /**
     * Pairs of eyes in the deep. They close when the cursor comes near.
     */
    private void extractWatchers(GuiGraphicsExtractor graphics, int mouseX, int mouseY, long now)
    {
        for (var watcher : watchers)
        {
            float wave = (float) Math.sin((now / 7000D + watcher.phase()) * Math.PI * 2);
            if (wave < 0.35F || Math.hypot(mouseX - watcher.x(), mouseY - watcher.y()) < WATCHER_FLINCH)
                continue;

            int color = ARGB.color((int) (50 + 110 * (wave - 0.35F) / 0.65F), ResearchScreen.KNOWN_RGB);
            graphics.fill(watcher.x(), watcher.y(), watcher.x() + 1, watcher.y() + 3, color);
            graphics.fill(watcher.x() + 4, watcher.y(), watcher.x() + 5, watcher.y() + 3, color);
        }
    }

    /**
     * Something out there the rings have not made out yet, keeping an empty chart from being a dead one.
     */
    private void extractEmbers(GuiGraphicsExtractor graphics, long now)
    {
        for (var ember : embers)
        {
            float wave = 0.5F + 0.5F * (float) Math.sin(now / 1900D + ember.phase() * 40);
            int x = ember.x();
            int y = ember.y();
            graphics.fill(x - 1, y - 1, x + 1, y + 1, ARGB.color((int) (50 + 80 * wave), ResearchScreen.REVEALED_RGB));
            graphics.fill(x - 3, y, x + 3, y + 1, ARGB.color((int) (14 + 22 * wave), ResearchScreen.REVEALED_RGB));
            graphics.fill(x, y - 3, x + 1, y + 3, ARGB.color((int) (14 + 22 * wave), ResearchScreen.REVEALED_RGB));
        }
    }

    private void extractRoots(GuiGraphicsExtractor graphics, View view, float progress, long now)
    {
        for (var root : roots)
        {
            if (!root.branch().main())
                extractDeadEnd(graphics, root, progress);
        }

        var main = mainRoot();
        if (main == null)
            return;

        var target = target(view);
        int rgb = target == null ? strataStateRgb(view) : stateRgb(view, target);
        float grown = Math.min(1, main.branch().grown(progress));
        forGrown(main, grown, (x1, y1, x2, y2, along, bend) -> stroke(graphics, x1, y1, x2, y2, MAIN_GLOW_WIDTH, bend, ARGB.color(MAIN_GLOW_ALPHA, rgb)));
        forGrown(main, grown, (x1, y1, x2, y2, along, bend) -> stroke(graphics, x1, y1, x2, y2, Mth.lerp(along, MAIN_CORE_WIDTH, MAIN_TIP_WIDTH), bend, ARGB.opaque(rgb)));
        extractMotes(graphics, main, grown, rgb, now);
    }

    private interface Piece
    {
        /**
         * @param bend how far the piece must reach back over its start to close the corner it turns there, per unit of half width
         */
        void draw(float x1, float y1, float x2, float y2, float along, float bend);
    }

    /**
     * Stops part way along a piece, so that a root creeps rather than jumping a piece at a time.
     */
    private static void forGrown(Root root, float grown, Piece piece)
    {
        int segments = root.branch().segments();
        float tip = grown * segments;
        float[] xs = root.xs();
        float[] ys = root.ys();
        for (int segment = 0; segment < segments && segment < tip; segment++)
        {
            float part = Math.min(1, tip - segment);
            float x2 = Mth.lerp(part, xs[segment], xs[segment + 1]);
            float y2 = Mth.lerp(part, ys[segment], ys[segment + 1]);
            float bend = 0;
            if (segment > 0)
            {
                float turn = (float) (Math.atan2(ys[segment + 1] - ys[segment], xs[segment + 1] - xs[segment]) - Math.atan2(ys[segment] - ys[segment - 1], xs[segment] - xs[segment - 1]));
                turn = Mth.wrapDegrees(turn * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
                bend = Math.min(1, Math.abs((float) Math.tan(turn / 2)) + JOINT_OVERLAP);
            }
            piece.draw(xs[segment], ys[segment], x2, y2, segment / (float) segments, bend);
        }
    }

    /**
     * A piece of a root, reaching back just far enough over the corner at its start to leave no notch, and no more, so a translucent root doesn't bead at its joints.
     */
    private static void stroke(GuiGraphicsExtractor graphics, float x1, float y1, float x2, float y2, float halfWidth, float bend, int color)
    {
        float length = (float) Math.hypot(x2 - x1, y2 - y1);
        if (length <= 0)
            return;

        float back = halfWidth * bend / length;
        TravelMapScreen.line(graphics, x1 - (x2 - x1) * back, y1 - (y2 - y1) * back, x2, y2, halfWidth, color);
    }

    /**
     * Dies back from its tip, greying as it goes.
     */
    private void extractDeadEnd(GuiGraphicsExtractor graphics, Root root, float progress)
    {
        float grown = root.branch().grown(progress);
        float withered = root.branch().withered(progress);
        if (grown <= 0 || withered >= 1)
            return;

        int rgb = ARGB.srgbLerp(Math.min(1, withered * 2.5F), ARGB.opaque(ResearchScreen.REVEALED_RGB), ARGB.opaque(WITHERED_RGB));
        forGrown(root, grown * (1 - withered * WITHER_RECEDE), (x1, y1, x2, y2, along, bend) ->
                stroke(graphics, x1, y1, x2, y2, Mth.lerp(along, DEAD_END_WIDTH, DEAD_END_TIP), bend, ARGB.color((int) (DEAD_END_ALPHA * (1 - withered) * (1 - along * 0.35F)), rgb)));
    }

    /**
     * Always flowing away from the rings, the way players go.
     */
    private void extractMotes(GuiGraphicsExtractor graphics, Root main, float grown, int rgb, long now)
    {
        int segments = main.branch().segments();
        float reached = main.length() * grown;
        float drift = (now % 10_000_000L) / 1000F * MOTE_SPEED;
        for (float along = drift % MOTE_SPACING; along < reached; along += MOTE_SPACING)
        {
            float at = along / main.length() * segments;
            int piece = Math.min(segments - 1, (int) at);
            float within = at - piece;
            float x = Mth.lerp(within, main.xs()[piece], main.xs()[piece + 1]);
            float y = Mth.lerp(within, main.ys()[piece], main.ys()[piece + 1]);
            float angle = (float) Math.atan2(main.ys()[piece + 1] - main.ys()[piece], main.xs()[piece + 1] - main.xs()[piece]);
            float ends = Math.min(1, Math.min(along, reached - along) / 8);
            ResearchMapView.mote(graphics, x, y, angle, ARGB.srgbLerp(0.45F, ARGB.opaque(rgb), 0xFFFFFFFF) & 0xFFFFFF, (int) (MOTE_ALPHA * ends));
        }
    }

    private void extractBubble(GuiGraphicsExtractor graphics, View view, BubbleView bubble, long now)
    {
        float fade = Math.min(1, bubble.age() / (float) FOUND_FADE);
        if (bubble.fadesIn() != BubbleView.NEVER)
            fade *= Math.min(1, bubble.fadesIn() / (float) LEAVING_TICKS);

        boolean isTarget = bubble.id() == view.target();
        if (view.phaseValue() == Phase.CONNECTED && !isTarget)
            fade *= DISTANT_FADE;

        int size = bubble.size();
        int sprite = BUBBLE_SIZE[size];
        int shown = sprite * scale;
        int x = bubbleX(bubble) - shown / 2;
        int y = bubbleY(bubble) - shown / 2;
        int stateRgb = stateRgb(view, bubble);
        boolean invited = isTarget || view.phaseValue() == Phase.SCANNING;

        if (invited)
        {
            float breath = 0.5F + 0.5F * (float) Math.sin(now * Math.PI * 2 / BREATH_MILLIS + bubble.seed());
            int halo = HALO_SIZE[size];
            int alpha = (int) (fade * (isTarget ? 60 + 130 * breath : 20 + 70 * breath));
            graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, bubbleX(bubble) - halo * scale / 2, bubbleY(bubble) - halo * scale / 2, HALO_U[size], HALO_V, halo * scale, halo * scale, halo, halo, ATLAS, ATLAS, ARGB.color(alpha, stateRgb));
        }

        var theme = DungeonTheme.byId(bubble.theme());
        int coreRgb = theme == null ? IDLE_CORE_RGB : ARGB.srgbLerp(THEME_CORE, ARGB.opaque(IDLE_CORE_RGB), ARGB.opaque(themeRgb(theme))) & 0xFFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, CORE_U[size], CORE_V, shown, shown, sprite, sprite, ATLAS, ATLAS, ARGB.color((int) (fade * CORE_ALPHA), coreRgb));
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, x, y, CORE_U[size], RIM_V, shown, shown, sprite, sprite, ATLAS, ATLAS, ARGB.color((int) (fade * (invited ? 255 : IDLE_RIM_ALPHA)), stateRgb));

        if (theme == null)
            ResearchMapView.glyph(graphics, false, bubbleX(bubble) - GLYPH_SIZE * scale / 2, bubbleY(bubble) - GLYPH_SIZE * scale / 2, scale, ARGB.white(fade));
    }

    /**
     * All that is left of a bubble whose connection gave way: a hollow shell, already cracked, losing what is left of itself one piece at a time as the pieces drift down.
     */
    private void extractBroken(GuiGraphicsExtractor graphics, BubbleView bubble, float elapsedTicks)
    {
        float gone = Math.clamp(1 - (bubble.fadesIn() - elapsedTicks) / ArmillaryChart.RUIN_TICKS, 0, 1);
        int size = bubble.size();
        int sprite = BUBBLE_SIZE[size];
        int centerX = bubbleX(bubble);
        int centerY = bubbleY(bubble);

        int shown = sprite * scale;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SPRITES, centerX - shown / 2, centerY - shown / 2, CORE_U[size], CORE_V, shown, shown, sprite, sprite, ATLAS, ATLAS, ARGB.color((int) (HOLLOW_ALPHA * (1 - gone)), 0));

        float radius = sprite / 2F;
        int samples = Math.round(radius * Mth.TWO_PI * 1.3F);
        int color = ARGB.color((int) (255 * (1 - gone * gone * gone)), ARGB.srgbLerp(Math.min(1, gone * 2), ARGB.opaque(LACK_RGB), ResearchScreen.READOUT_COLOR));
        float phase = (bubble.seed() & 0xFF) / 40F;
        for (int sample = 0; sample < samples; sample++)
        {
            float angle = sample * Mth.TWO_PI / samples;
            if (Mth.sin(angle * 3 + phase) + 0.5F * Mth.sin(angle * 7 + phase * 2) < CRACK_GAP)
                continue;
            if (ResearchVoid.hash(sample, size, bubble.seed()) < gone * gone)
                continue;

            float shard = ResearchVoid.hash(size, sample, bubble.seed());
            float reach = radius + (shard - 0.5F) * SHARD_JAG + gone * shard * SHARD_DRIFT * 0.5F;
            if (shard > SHARD_INWARD)
                reach *= SHARD_INWARD_SHARE;
            int x = centerX + Math.round(Mth.cos(angle) * reach) * scale;
            int y = centerY + Math.round(Mth.sin(angle) * reach + gone * shard * SHARD_DRIFT) * scale;
            graphics.fill(x, y, x + scale, y + scale, color);
        }
    }

    /**
     * The way that stood open dies back from the bubble to the rings.
     */
    private void extractDying(GuiGraphicsExtractor graphics, long now)
    {
        if (dying == null)
            return;

        float t = (now - dyingAt) / (float) DYING_MILLIS;
        if (t >= 1)
        {
            dying = null;
            return;
        }

        int rgb = ARGB.srgbLerp(t, ARGB.opaque(LACK_RGB), ARGB.opaque(WITHERED_RGB));
        forGrown(dying, 1 - t, (x1, y1, x2, y2, along, bend) ->
                stroke(graphics, x1, y1, x2, y2, Mth.lerp(along, MAIN_CORE_WIDTH, MAIN_TIP_WIDTH), bend, ARGB.color((int) (255 * (1 - t * t)), rgb)));
    }

    /**
     * The three rings of the instrument itself, nested as they are in the world and turning at the same speeds. What is behind is darker, which is all it takes to read them as round.
     */
    private void extractRings(GuiGraphicsExtractor graphics, View view, float progress, float elapsedTicks, int lookAtX, int lookAtY, boolean lookingAtSomething, long now)
    {
        float ticks = Math.min(5, (now - lastFrame) / 50F);
        spin += (ArmillaryTile.spinOf(view.phaseValue(), view.stageValue()) - spin) * Math.min(1, SPIN_EASE * ticks);
        for (int ring = 0; ring < angles.length; ring++)
            angles[ring] += ArmillaryTile.ringSpeed(ring) * spin * ticks;

        int centerX = Math.round(screenX(SOURCE_X));
        float falter = view.phaseValue() == Phase.CONNECTED ? ArmillaryTile.falterOf(view.stageValue()) : 0;
        int centerY = chartTop - 1 + Math.round((float) Math.sin(now / 41D) * falter * RING_FALTER * scale);

        disc(graphics, centerX, centerY, MOUNT_RADIUS - 1, MOUNT_BACK);
        extractGauge(graphics, view, progress, elapsedTicks, centerX, centerY, now);

        float wantedX = Mth.clamp((lookAtX - centerX) / LOOK_DISTANCE, -1, 1) * LOOK_REACH;
        float wantedY = Mth.clamp((lookAtY - centerY) / LOOK_DISTANCE, -1, 1) * LOOK_REACH;
        lookX += (wantedX - lookX) * Math.min(1, LOOK_EASE * ticks);
        lookY += (wantedY - lookY) * Math.min(1, LOOK_EASE * ticks);

        for (int ring = 0; ring < angles.length; ring++)
            extractRing(graphics, ring, ringX(centerX, ring), ringY(centerY, ring), false);
        if (view.phaseValue() == Phase.CONNECTED)
            extractPortal(graphics, ringX(centerX, 2), ringY(centerY, 2), view);
        for (int ring = 0; ring < angles.length; ring++)
            extractRing(graphics, ring, ringX(centerX, ring), ringY(centerY, ring), true);
    }

    /**
     * The gimbals give a little towards what is being looked at, the inner one most: it is what the eye does on the repository's map.
     */
    private int ringX(int centerX, int ring)
    {
        return centerX + Math.round(lookX * ring / (angles.length - 1)) * scale;
    }

    private int ringY(int centerY, int ring)
    {
        return centerY + Math.round(lookY * ring / (angles.length - 1)) * scale;
    }

    /**
     * The mount the rings turn in, which holds its shape whichever way they face, and shows without a word how the search or the connection is doing.
     */
    private void extractGauge(GuiGraphicsExtractor graphics, View view, float progress, float elapsedTicks, int centerX, int centerY, long now)
    {
        arc(graphics, centerX, centerY, MOUNT_RADIUS + 1, 0, 1, ResearchVoid.LIP_SHADE_COLOR);
        arc(graphics, centerX, centerY, MOUNT_RADIUS, 0, 1, GAUGE_REST);

        switch (view.phaseValue())
        {
            case SCANNING ->
            {
                float from = now % (long) SWEEP_MILLIS / SWEEP_MILLIS;
                arc(graphics, centerX, centerY, MOUNT_RADIUS, from, from + SWEEP_LENGTH, ARGB.opaque(ResearchScreen.REVEALED_RGB));
            }
            case SEARCHING -> arc(graphics, centerX, centerY, MOUNT_RADIUS, 0, progress, ResearchScreen.ACTIVE_COLOR);
            case REACHED ->
            {
                float breath = 0.5F + 0.5F * (float) Math.sin(now * Math.PI * 2 / BREATH_MILLIS);
                arc(graphics, centerX, centerY, MOUNT_RADIUS, 0, 1, ARGB.srgbLerp(0.35F + 0.65F * breath, GAUGE_REST, ARGB.opaque(ResearchScreen.FRONTIER_RGB)));
            }
            case CONNECTED ->
            {
                float left = view.isStable() ? 1 : view.connectionDuration() > 0 ? Math.clamp((view.connectionTicks() - elapsedTicks) / view.connectionDuration(), 0, 1) : 0;
                arc(graphics, centerX, centerY, MOUNT_RADIUS, 0, left, failing(view) ? ResearchScreen.LACK_COLOR : ResearchScreen.ACTIVE_COLOR);
            }
        }
    }

    /**
     * Part of a circle clockwise from the top, whole pixels on the same grid as the rings.
     */
    private void arc(GuiGraphicsExtractor graphics, int centerX, int centerY, float radius, float from, float to, int color)
    {
        int samples = Math.round((to - from) * radius * Mth.TWO_PI * 1.3F);
        for (int sample = 0; sample <= samples; sample++)
        {
            float angle = (from + (to - from) * sample / Math.max(1, samples)) * Mth.TWO_PI - Mth.HALF_PI;
            int x = centerX + Math.round(Mth.cos(angle) * radius) * scale;
            int y = centerY + Math.round(Mth.sin(angle) * radius) * scale;
            graphics.fill(x, y, x + scale, y + scale, color);
        }
    }

    private void disc(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color)
    {
        for (int row = -radius; row <= radius; row++)
        {
            int half = Math.round(Mth.sqrt(radius * radius - row * row));
            graphics.fill(centerX - half * scale, centerY + row * scale, centerX + (half + 1) * scale, centerY + (row + 1) * scale, color);
        }
    }

    private void extractRing(GuiGraphicsExtractor graphics, int ring, int centerX, int centerY, boolean near)
    {
        float radius = RING_RADII[ring];
        int samples = Math.round(radius * 2 * Mth.PI * 1.3F);
        float cosPitch = Mth.cos(RING_PITCH);
        float sinPitch = Mth.sin(RING_PITCH);
        int color = near ? RING_NEAR[ring] : RING_FAR[ring];

        for (int sample = 0; sample < samples; sample++)
        {
            float along = sample * Mth.TWO_PI / samples;
            float x = Mth.cos(along);
            float y = Mth.sin(along);
            float z = 0;

            if (ring == 2)
            {
                float turned = x * Mth.cos(angles[2]);
                z = x * Mth.sin(angles[2]);
                x = turned;
            }
            if (ring >= 1)
            {
                float turned = y * Mth.cos(angles[1]) - z * Mth.sin(angles[1]);
                z = y * Mth.sin(angles[1]) + z * Mth.cos(angles[1]);
                y = turned;
            }
            float worldX = x * Mth.cos(angles[0]) + z * Mth.sin(angles[0]);
            float worldZ = z * Mth.cos(angles[0]) - x * Mth.sin(angles[0]);

            float depth = worldZ * cosPitch + y * sinPitch;
            if (depth >= 0 != near)
                continue;

            int pixelX = centerX + Math.round(worldX * radius) * scale;
            int pixelY = centerY + Math.round((worldZ * sinPitch - y * cosPitch) * radius) * scale;
            graphics.fill(pixelX, pixelY, pixelX + scale, pixelY + scale, color);
        }
    }

    private void extractPortal(GuiGraphicsExtractor graphics, int centerX, int centerY, View view)
    {
        int rgb;
        var target = target(view);
        if (target != null)
            rgb = themeRgb(DungeonTheme.byId(target.theme()));
        else if (targetsStrata(view))
            rgb = strataTheme(view) == null ? ArmillaryPortalRender.themeColor(null) : strataTheme(view).color();
        else
            return;

        disc(graphics, centerX, centerY, PORTAL_RADIUS, PORTAL_COLOR);
        arc(graphics, centerX, centerY, PORTAL_RADIUS + 1, 0, 1, ARGB.opaque(rgb));
    }

    /**
     * Depths are cut into the stone beside the hole, and the one being looked at wakes up.
     */
    private void extractNumerals(GuiGraphicsExtractor graphics, @Nullable BubbleView looked)
    {
        for (int depth = 1; depth <= DungeonAddress.MAX_DIFFICULTY; depth++)
        {
            String numeral = numeral(depth);
            int x = (left - font.width(numeral)) / 2;
            int y = (int) screenY(VoidBubble.bandY(depth - 0.5F)) - font.lineHeight / 2;
            boolean woken = looked != null && looked.difficulty() == depth;
            graphics.text(font, numeral, x - 1, y - 1, ResearchVoid.LIP_SHADE_COLOR, false);
            graphics.text(font, numeral, x, y, woken ? ResearchScreen.TEXT_COLOR : ResearchScreen.READOUT_COLOR, false);
        }
    }

    /**
     * A name only shows for what the cursor is on and for what the rings are reaching for, and never for what the tablet is already talking about.
     */
    private void extractLabel(GuiGraphicsExtractor graphics, View view, @Nullable BubbleView target, @Nullable BubbleView selected, @Nullable BubbleView hovered)
    {
        if (target != null && target != selected && target != hovered)
            extractLabel(graphics, view, target);
        if (hovered != null && hovered != selected)
            extractLabel(graphics, view, hovered);
    }

    private void extractLabel(GuiGraphicsExtractor graphics, View view, BubbleView bubble)
    {
        var text = Component.translatable("voidheart.armillary.bubble.depth", numeral(bubble.difficulty()));
        int half = font.width(text) / 2 + 2;
        int centerX = Math.clamp(bubbleX(bubble), holeLeft + half, Math.max(holeLeft + half, holeRight - half));
        int y = bubbleY(bubble) + radius(bubble) + LABEL_DROP;
        if (y + font.lineHeight > chartBottom)
            y = bubbleY(bubble) - radius(bubble) - LABEL_DROP - font.lineHeight;

        graphics.fill(centerX - half, y - 1, centerX + half, y + font.lineHeight - 1, ResearchMapView.LABEL_BACK_COLOR);
        graphics.centeredText(font, text, centerX, y, ARGB.opaque(stateRgb(view, bubble)));
    }

    /**
     * A ring of light leaving a bubble at the moment it matters, the same one research leaves when it is read or finished.
     */
    private void extractBlooms(GuiGraphicsExtractor graphics, long now)
    {
        blooms.removeIf(bloom -> now - bloom.start() > (bloom.finished() ? FINISH_MILLIS : BLOOM_MILLIS));
        for (var bloom : blooms)
        {
            float t = (now - bloom.start()) / (float) (bloom.finished() ? FINISH_MILLIS : BLOOM_MILLIS);
            extractBloomRing(graphics, bloom, t, bloom.finished() ? FINISH_REACH : BLOOM_REACH);
            if (bloom.finished())
            {
                extractBloomRing(graphics, bloom, t - 0.14F, FINISH_REACH * 0.8F);
                extractBloomRing(graphics, bloom, t - 0.28F, FINISH_REACH * 0.6F);
            }
        }
    }

    private static void extractBloomRing(GuiGraphicsExtractor graphics, Bloom bloom, float t, float reach)
    {
        if (t <= 0)
            return;

        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        int color = ARGB.color((int) (255 * Math.max(0, 1 - t)), bloom.rgb());
        ResearchMapView.dotRing(graphics, bloom.x(), bloom.y(), bloom.radius() + eased * reach, bloom.finished() ? BLOOM_DOTS * 2 : BLOOM_DOTS, eased, color);
    }
}
