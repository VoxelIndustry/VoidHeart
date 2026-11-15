package net.voxelindustry.voidheart.client.render.temple;

import net.minecraft.util.Mth;

/**
 * Where the mosaic's eyes look. Once it has noticed the player they wake one by one, each turning to the player in quick jumps and holding there for a while
 * before the next, as real eyes do, so they never glide in step. Its closed eyes open as they wake.
 * <p>
 * The player is given in the picture's pixels: {@code u} rightward and {@code v} downward from its top left, and {@code h} out of the wall.
 */
public final class MosaicGaze
{
    /** Eyes wake over this many ticks after it notices the player. */
    static final int WAKE_SPREAD = 18;
    /** How long an eye holds a look before it may jump again, in ticks. */
    static final int MIN_HOLD = 5;
    static final int MAX_HOLD = 28;
    /** How much of the way to its new look an eye covers each tick. */
    static final float JUMP = 0.65F;
    /** How far an iris moves from the middle, against the eye's half width and half height. */
    static final float RANGE_X = 0.5F;
    static final float RANGE_Y = 0.35F;
    /** Turns the angle toward the player into the iris's travel, so it reaches the corner of the eye before the player reaches the edge of the room. */
    static final float GAIN = 1.8F;
    static final int OPEN_TICKS = 8;

    private final float[] offsetX;
    private final float[] offsetY;
    private final float[] targetX;
    private final float[] targetY;
    private final int[] nextJump;
    private final int[] openedAt;
    private final long seed;
    private final MosaicArt art;

    public MosaicGaze(MosaicArt art, long seed)
    {
        this.art = art;
        this.seed = seed;
        int eyes = art.eyes().size() + art.shut().size();
        offsetX = new float[eyes];
        offsetY = new float[eyes];
        targetX = new float[eyes];
        targetY = new float[eyes];
        nextJump = new int[eyes];
        openedAt = new int[art.shut().size()];
    }

    public void tick(MosaicWake wake, double playerU, double playerV, double playerH)
    {
        int since = wake.sinceNotice();
        for (int eye = 0; eye < offsetX.length; eye++)
        {
            if (!wake.noticed() || since < wakeAt(eye))
            {
                offsetX[eye] = offsetY[eye] = targetX[eye] = targetY[eye] = 0;
                nextJump[eye] = 0;
                continue;
            }
            if (since >= nextJump[eye])
            {
                float x = centreX(eye);
                float y = centreY(eye);
                double dx = playerU - x;
                double dy = playerV - y;
                double length = Math.sqrt(dx * dx + dy * dy + playerH * playerH);
                if (length > 1E-3)
                {
                    targetX[eye] = (float) Mth.clamp(dx / length * GAIN, -1, 1) * RANGE_X * radiusX(eye);
                    targetY[eye] = (float) Mth.clamp(dy / length * GAIN, -1, 1) * RANGE_Y * radiusY(eye);
                }
                nextJump[eye] = since + MIN_HOLD + (int) (MosaicArt.hash(eye, since, seed) * (MAX_HOLD - MIN_HOLD));
            }
            offsetX[eye] += (targetX[eye] - offsetX[eye]) * JUMP;
            offsetY[eye] += (targetY[eye] - offsetY[eye]) * JUMP;
        }
    }

    /** @return the tick after notice at which an eye wakes, painted eyes before closed ones */
    int wakeAt(int eye)
    {
        int spread = (int) (MosaicArt.hash(eye, 7, seed) * WAKE_SPREAD);
        return eye < art.eyes().size() ? spread : WAKE_SPREAD / 2 + spread;
    }

    public boolean awake(MosaicWake wake, int eye)
    {
        return wake.noticed() && wake.sinceNotice() >= wakeAt(eye);
    }

    public float offsetX(int eye)
    {
        return offsetX[eye];
    }

    public float offsetY(int eye)
    {
        return offsetY[eye];
    }

    /** @return from 0 to 1, how far a closed eye has opened */
    public float openness(MosaicWake wake, int shut, float partialTicks)
    {
        int eye = art.eyes().size() + shut;
        if (!awake(wake, eye))
            return 0;
        return Mth.clamp((wake.sinceNotice() - wakeAt(eye) + partialTicks) / OPEN_TICKS, 0, 1);
    }

    // The script counts a pixel by its corner, the player by the middle of pixels
    private float centreX(int eye)
    {
        return 0.5F + (eye < art.eyes().size() ? art.eyes().get(eye).x() : art.shut().get(eye - art.eyes().size()).x());
    }

    private float centreY(int eye)
    {
        return 0.5F + (eye < art.eyes().size() ? art.eyes().get(eye).y() : art.shut().get(eye - art.eyes().size()).y() + MosaicPicture.SHUT_DROP);
    }

    private float radiusX(int eye)
    {
        return eye < art.eyes().size() ? art.eyes().get(eye).rx() : MosaicPicture.SHUT_RX;
    }

    private float radiusY(int eye)
    {
        return eye < art.eyes().size() ? art.eyes().get(eye).ry() : MosaicPicture.SHUT_RY;
    }
}
