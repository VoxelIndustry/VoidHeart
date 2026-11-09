package net.voxelindustry.voidheart.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.voxelindustry.voidheart.client.render.ScenePictureRender;
import net.voxelindustry.voidheart.client.scene.Scene;
import net.voxelindustry.voidheart.client.scene.SceneFrame;
import net.voxelindustry.voidheart.client.scene.SceneTimeline;
import net.voxelindustry.voidheart.client.scene.SceneView;

import java.util.List;

/**
 * Plays a scene on a large tablet over the map, or across the whole screen when more room is wanted.
 * The film runs by itself and holds each step long enough to read. Its marks jump between steps, and nothing else about it can be touched.
 */
final class ResearchSceneView
{
    private static final int HEADER      = 20;
    private static final int FOOTER      = 22;
    private static final int PAD         = 8;
    private static final int ICON        = 9;
    private static final int ICON_PITCH  = 16;
    private static final int ICON_V      = 141;
    private static final int PAUSE_U     = 0;
    private static final int PLAY_U      = 10;
    private static final int REPLAY_U    = 20;
    private static final int EXPAND_U    = 30;
    private static final int SHRINK_U    = 40;
    private static final int CLOSE_U     = 50;
    private static final int MOUSE_U     = 60;
    private static final int NOTE_MIN    = 96;
    private static final int NOTE_MAX    = 176;
    private static final int LINE        = 10;
    private static final int MARK        = 5;
    private static final int MARK_REACH  = 5;

    private static final int  WINDOW_MARGIN   = 6;
    private static final float WINDOW_WIDTH   = 0.86F;

    private static final float TICKS_PER_MILLI = 20 / 1000F;
    private static final float CAMERA_TICKS    = 30;
    private static final float LEADER_TICKS    = 12;
    private static final float NOTE_TICKS      = 8;
    private static final float HOLD_BEFORE_END = 15;
    private static final float STAGE_HUE       = 0.5F;
    private static final int   USE_ICON        = 18;

    // Light enough for the darkest stone and items in a scene to stand out from it
    private static final int STAGE_COLOR  = 0xFF3F3A4A;
    private static final int RULE_COLOR   = 0xFF4A3860;
    private static final int TABLET_COLOR = 0xFF0B0810;
    private static final int TEXT_COLOR   = 0xFFEDE6FF;
    private static final int MUTED_COLOR  = 0xFFA899BC;
    private static final int ICON_COLOR   = 0xFFA899BC;
    private static final int ICON_HOVER   = 0xFFEDE6FF;

    private record Rect(int x, int y, int width, int height)
    {
        boolean contains(double px, double py)
        {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        int right()
        {
            return x + width;
        }

        int bottom()
        {
            return y + height;
        }
    }

    private final Font              font;
    private final Scene             scene;
    private final SceneTimeline     timeline;
    private final ConstellationLook look;
    private final Component         title;

    private boolean fullScreen;
    private boolean paused;
    private float   ticks;
    private float   alive;
    private long    tickedAt = Util.getMillis();

    private Rect tablet = new Rect(0, 0, 0, 0);
    private Rect stage  = new Rect(0, 0, 0, 0);
    private Rect note   = new Rect(0, 0, 0, 0);
    private Rect marks  = new Rect(0, 0, 0, 0);
    private Rect play   = new Rect(0, 0, 0, 0);
    private Rect close  = new Rect(0, 0, 0, 0);
    private Rect expand = new Rect(0, 0, 0, 0);

    ResearchSceneView(Font font, Scene scene, ConstellationLook look)
    {
        this.font = font;
        this.scene = scene;
        this.look = look;
        title = Component.translatable(scene.titleKey());
        timeline = scene.timeline(key -> I18n.get(key).length());
    }

    private void layout(int screenWidth, int screenHeight, int left, int right, int windowBottom)
    {
        if (fullScreen)
            tablet = new Rect(0, 0, screenWidth, screenHeight);
        else
        {
            int width = Math.min((int) (screenWidth * WINDOW_WIDTH), right - left);
            tablet = new Rect((left + right - width) / 2, WINDOW_MARGIN, width, windowBottom - WINDOW_MARGIN);
        }

        int bodyTop = tablet.y() + HEADER;
        int bodyHeight = tablet.height() - HEADER - FOOTER;
        int noteWidth = Math.clamp(tablet.width() / 3, NOTE_MIN, NOTE_MAX);
        stage = new Rect(tablet.x() + 1, bodyTop, tablet.width() - noteWidth - 2, bodyHeight);
        note = new Rect(stage.right(), bodyTop, noteWidth, bodyHeight);

        int footerY = bodyTop + bodyHeight;
        play = new Rect(tablet.x() + PAD - 3, footerY + 3, ICON_PITCH, ICON_PITCH);
        int countWidth = font.width(timeline.steps() + " / " + timeline.steps());
        marks = new Rect(play.right() + 6, footerY + 3, tablet.right() - PAD - countWidth - 8 - play.right() - 6, ICON_PITCH);

        close = new Rect(tablet.right() - ICON_PITCH - 2, tablet.y() + 2, ICON_PITCH, ICON_PITCH);
        expand = new Rect(close.x() - ICON_PITCH, tablet.y() + 2, ICON_PITCH, ICON_PITCH);
    }

    private boolean ended()
    {
        return ticks >= timeline.total();
    }

    private void advance()
    {
        long now = Util.getMillis();
        float passed = (now - tickedAt) * TICKS_PER_MILLI;
        tickedAt = now;
        alive += passed;
        if (!paused)
            ticks = Math.min(timeline.total(), ticks + passed);
    }

    private SceneView cameraAt(int step)
    {
        var view = scene.steps().get(step).view();
        if (step == 0)
            return view;

        float t = Math.min(1, (ticks - timeline.startOf(step)) / CAMERA_TICKS);
        float eased = t * t * (3 - 2 * t);
        return scene.steps().get(step - 1).view().lerp(view, eased);
    }

    void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int screenWidth, int screenHeight, int left, int right, int windowBottom)
    {
        advance();
        layout(screenWidth, screenHeight, left, right, windowBottom);

        int step = timeline.stepAt(ticks);
        var view = cameraAt(step);

        graphics.fill(tablet.x(), tablet.y(), tablet.right(), tablet.bottom(), TABLET_COLOR);
        if (!fullScreen)
            graphics.outline(tablet.x(), tablet.y(), tablet.width(), tablet.height(), RULE_COLOR);

        extractHeader(graphics, mouseX, mouseY);

        graphics.fillGradient(stage.x(), stage.y(), stage.right(), stage.bottom(), STAGE_COLOR, ARGB.srgbLerp(STAGE_HUE, STAGE_COLOR, 0xFF000000 | look.lightRgb()));
        graphics.guiRenderState.addPicturesInPictureState(new ScenePictureRender.State(scene, SceneFrame.of(scene, timeline, ticks), view,
                view.blockSize(stage.width(), stage.height()), alive, look.rgb(), stage.x(), stage.y(), stage.right(), stage.bottom(), null));

        extractNote(graphics, step, view);
        extractFooter(graphics, mouseX, mouseY, step);
    }

    private void icon(GuiGraphicsExtractor graphics, Rect at, int u, int mouseX, int mouseY)
    {
        int tint = at.contains(mouseX, mouseY) ? ICON_HOVER : ICON_COLOR;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ResearchMapView.SPRITES, at.x() + (at.width() - ICON) / 2, at.y() + (at.height() - ICON) / 2, u, ICON_V, ICON, ICON, ResearchMapView.ATLAS, ResearchMapView.ATLAS, tint);
    }

    private void extractHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        graphics.text(font, title, tablet.x() + PAD, tablet.y() + (HEADER - font.lineHeight) / 2 + 1, TEXT_COLOR);
        icon(graphics, expand, fullScreen ? SHRINK_U : EXPAND_U, mouseX, mouseY);
        icon(graphics, close, CLOSE_U, mouseX, mouseY);
    }

    /**
     * The text beside the stage so that it never covers what it talks about, with a line that reaches across to it.
     */
    private void extractNote(GuiGraphicsExtractor graphics, int step, SceneView view)
    {
        var current = scene.steps().get(step);
        float since = ticks - timeline.startOf(step);
        int alpha = (int) (255 * Math.min(1, since / NOTE_TICKS));
        int textX = note.x() + PAD;
        int y = note.y() + PAD;

        if (current.use())
        {
            graphics.blit(RenderPipelines.GUI_TEXTURED, ResearchMapView.SPRITES, textX, y - 1, MOUSE_U, ICON_V, USE_ICON, USE_ICON, ICON, ICON, ResearchMapView.ATLAS, ResearchMapView.ATLAS, ARGB.color(alpha, look.rgb()));
            y += USE_ICON + 3;
        }

        int firstLine = y + font.lineHeight / 2;
        List<FormattedCharSequence> lines = font.split(Component.translatable(scene.textKey(step)), note.width() - PAD - 6);
        for (var line : lines)
        {
            graphics.text(font, line, textX, y, ARGB.color(alpha, TEXT_COLOR), false);
            y += LINE;
        }

        extractLeader(graphics, current, view, firstLine, since);
    }

    /**
     * Runs out along the stage from the text, then up or down to the point, only as far as has been reached by now.
     */
    private void extractLeader(GuiGraphicsExtractor graphics, Scene.Step current, SceneView view, int fromY, float since)
    {
        var at = view.project((float) current.anchor().x, (float) current.anchor().y, (float) current.anchor().z, stage.width(), stage.height());
        int anchorX = Math.clamp(stage.x() + (int) at[0], stage.x() + 3, stage.right() - 3);
        int anchorY = Math.clamp(stage.y() + (int) at[1], stage.y() + 3, stage.bottom() - 3);

        float reach = Math.min(1, since / LEADER_TICKS);
        int color = ARGB.color(220, look.rgb());
        int startX = note.x() + PAD - 3;
        int across = startX - anchorX;
        int down = anchorY - fromY;
        int total = Math.abs(across) + Math.abs(down);
        int drawn = (int) (total * reach);

        int runX = Math.min(drawn, Math.abs(across));
        graphics.fill(startX - runX, fromY, startX, fromY + 1, color);

        int fall = drawn - runX;
        if (fall > 0)
        {
            int to = fromY + Integer.signum(down) * fall;
            graphics.fill(anchorX, Math.min(fromY, to), anchorX + 1, Math.max(fromY, to) + 1, color);
        }
        if (reach >= 1)
            graphics.fill(anchorX - 1, anchorY - 1, anchorX + 2, anchorY + 2, 0xFF000000 | look.rgb());
    }

    private int markX(int step)
    {
        return marks.x() + MARK / 2 + (marks.width() - MARK) * step / timeline.steps();
    }

    private void extractFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int step)
    {
        graphics.fill(tablet.x() + 1, marks.y() - 3, tablet.right() - 1, marks.y() - 2, RULE_COLOR);

        icon(graphics, play, ended() ? REPLAY_U : paused ? PLAY_U : PAUSE_U, mouseX, mouseY);

        int middle = marks.y() + marks.height() / 2;
        int reached = marks.x() + (int) ((marks.width() - MARK) * timeline.share(ticks));
        graphics.fill(marks.x(), middle, marks.right(), middle + 1, RULE_COLOR);
        graphics.fill(marks.x(), middle, reached + MARK / 2, middle + 1, 0xFF000000 | look.rgb());

        for (int index = 0; index < timeline.steps(); index++)
        {
            int x = markX(index);
            boolean hovered = markAt(mouseX, mouseY) == index;
            int color = index <= step || hovered ? 0xFF000000 | look.rgb() : RULE_COLOR;
            int half = index == step || hovered ? MARK / 2 + 1 : MARK / 2;
            graphics.fill(x - half, middle - half, x + half + 1, middle + half + 1, color);
        }

        var count = (step + 1) + " / " + timeline.steps();
        graphics.text(font, count, tablet.right() - PAD - font.width(count), marks.y() + (marks.height() - font.lineHeight) / 2 + 1, MUTED_COLOR, false);
    }

    private int markAt(double mouseX, double mouseY)
    {
        if (mouseY < marks.y() || mouseY >= marks.bottom())
            return -1;
        for (int index = 0; index < timeline.steps(); index++)
        {
            if (Math.abs(mouseX - markX(index)) <= MARK_REACH)
                return index;
        }
        return -1;
    }

    private static void pageTurn()
    {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.2F));
    }

    void jumpTo(int step)
    {
        ticks = timeline.startOf(Math.clamp(step, 0, timeline.steps() - 1));
        pageTurn();
    }

    private void togglePlay()
    {
        if (ended())
        {
            ticks = 0;
            paused = false;
            pageTurn();
        }
        else
            paused = !paused;
    }

    void toggleFullScreen()
    {
        fullScreen = !fullScreen;
    }

    boolean fullScreen()
    {
        return fullScreen;
    }

    /**
     * @return true when the click closes the scene
     */
    boolean mouseClicked(double mouseX, double mouseY)
    {
        if (close.contains(mouseX, mouseY))
            return true;

        if (expand.contains(mouseX, mouseY))
            toggleFullScreen();
        else if (play.contains(mouseX, mouseY))
            togglePlay();
        else if (markAt(mouseX, mouseY) >= 0)
            jumpTo(markAt(mouseX, mouseY));
        return false;
    }

    boolean keyPressed(int key)
    {
        switch (key)
        {
            case InputConstants.KEY_SPACE -> togglePlay();
            case InputConstants.KEY_LEFT -> jumpTo(timeline.stepAt(ticks) - 1);
            case InputConstants.KEY_RIGHT -> jumpTo(timeline.stepAt(ticks) + 1);
            case InputConstants.KEY_F -> toggleFullScreen();
            default ->
            {
                return false;
            }
        }
        return true;
    }

    Component tooltip(double mouseX, double mouseY)
    {
        if (close.contains(mouseX, mouseY))
            return Component.translatable("voidheart.scene.close");
        if (expand.contains(mouseX, mouseY))
            return Component.translatable(fullScreen ? "voidheart.scene.shrink" : "voidheart.scene.expand");
        if (play.contains(mouseX, mouseY))
            return Component.translatable(ended() ? "voidheart.scene.replay" : paused ? "voidheart.scene.play" : "voidheart.scene.pause");
        return null;
    }

    int[] stageCenter()
    {
        return new int[] { stage.x() + stage.width() / 2, stage.y() + stage.height() / 2 };
    }

    int[] expandPosition()
    {
        return new int[] { expand.x() + expand.width() / 2, expand.y() + expand.height() / 2 };
    }

    int[] closePosition()
    {
        return new int[] { close.x() + close.width() / 2, close.y() + close.height() / 2 };
    }

    int[] playPosition()
    {
        return new int[] { play.x() + play.width() / 2, play.y() + play.height() / 2 };
    }

    int[] markPosition(int step)
    {
        return new int[] { markX(step), marks.y() + marks.height() / 2 };
    }

    int currentStep()
    {
        return timeline.stepAt(ticks);
    }

    int steps()
    {
        return timeline.steps();
    }

    boolean paused()
    {
        return paused;
    }

    /**
     * For a test to stop on a moment: a pause on the last tick of a step, with every animation of it done.
     */
    void holdAtEndOf(int step)
    {
        paused = true;
        ticks = (step + 1 < timeline.steps() ? timeline.startOf(step + 1) : timeline.total()) - HOLD_BEFORE_END;
    }

    /**
     * For a test to stop partway through a step, on what happens there before it ends.
     */
    void holdAt(int step, int offset)
    {
        paused = true;
        ticks = timeline.at(step, offset);
    }
}
