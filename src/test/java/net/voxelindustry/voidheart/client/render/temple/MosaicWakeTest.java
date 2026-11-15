package net.voxelindustry.voidheart.client.render.temple;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MosaicWakeTest
{
    private static final Path ASSETS = Path.of("src/main/resources/assets/voidheart");
    private static final long SEED = 1234;

    private static MosaicArt art() throws IOException
    {
        BufferedImage map = ImageIO.read(ASSETS.resolve("mosaic/temple_mosaic_map.png").toFile());
        return MosaicArt.parse((x, y) -> map.getRGB(x, y) & 0xFFFFFF, Files.readString(ASSETS.resolve("mosaic/temple_mosaic.json")));
    }

    private static int[] whole()
    {
        return new int[MosaicArt.WIDTH / MosaicArt.TILE * (MosaicArt.HEIGHT / MosaicArt.TILE)];
    }

    private static MosaicWake staredAt(int ticks)
    {
        var wake = new MosaicWake();
        for (int i = 0; i < ticks; i++)
            wake.tick(true);
        return wake;
    }

    @Test
    void itNoticesThePlayerAndKeepsLookingAfterTheyLookAway()
    {
        var wake = staredAt(MosaicWake.NOTICE - 1);
        assertThat(wake.noticed()).isFalse();
        wake.tick(true);
        assertThat(wake.noticed()).isTrue();
        for (int i = 0; i < 100; i++)
            wake.tick(false);
        assertThat(wake.stareTicks()).isZero();
        assertThat(wake.noticed()).isTrue();
        assertThat(wake.active()).isTrue();
        wake.forget();
        assertThat(wake.noticed()).isFalse();
        assertThat(wake.active()).isFalse();
    }

    @Test
    void itStirsThenReachesAndWithdrawsFasterThanItCame()
    {
        assertThat(staredAt(MosaicWake.STIR_START).stir(1)).isZero();
        assertThat(staredAt(MosaicWake.STIR_FULL).stir(1)).isEqualTo(1);
        assertThat(staredAt(MosaicWake.REACH_START).reach(1)).isZero();
        assertThat(staredAt(MosaicWake.REACH_FULL).reach(1)).isEqualTo(1);

        var wake = staredAt(1000);
        assertThat(wake.stareTicks()).isEqualTo(MosaicWake.MOST);
        int bound = (MosaicWake.MOST - MosaicWake.REACH_START) / MosaicWake.WITHDRAW + 1;
        for (int tick = 0; tick < bound && wake.reach(1) > 0; tick++)
            wake.tick(false);
        assertThat(wake.reach(1)).as("still reaching after %s ticks looking away", bound).isZero();
    }

    @Test
    void theMapAndFeaturesAgree() throws IOException
    {
        var art = art();
        assertThat(art.tendrils()).hasSize(5);
        assertThat(art.shut()).hasSize(3);
        assertThat(art.fleshPixels()).hasSizeGreaterThan(4000);
        for (int tendril = 0; tendril < art.tendrils().size(); tendril++)
        {
            var path = art.tendrils().get(tendril);
            assertThat(art.tendrilPixels(tendril)).isNotEmpty();
            assertThat(art.figure(MosaicArt.index(path.x()[path.length() / 2], path.y()[path.length() / 2]))).isEqualTo(MosaicArt.TENDRIL_BODY);
        }
        long fleshEyes = art.eyes().stream().filter(eye -> eye.figure() == MosaicArt.FLESH_BODY).count();
        assertThat(fleshEyes).isEqualTo(8);
        for (var eye : art.eyes().stream().filter(eye -> eye.figure() == MosaicArt.FLESH_BODY).toList())
            assertThat(art.region(MosaicArt.index(Math.round(eye.x()), Math.round(eye.y())))).as("eye at %s, %s", eye.x(), eye.y()).isEqualTo(MosaicArt.EYE);
    }

    @Test
    void atRestNothingIsDrawn() throws IOException
    {
        var art = art();
        var picture = new MosaicPicture();
        var wake = new MosaicWake();
        var gaze = new MosaicGaze(art, SEED);
        gaze.tick(wake, 120, 64, 160);
        picture.compute(art, whole(), wake, gaze, new MosaicMotion(), 1, 0, SEED);
        assertThat(picture.drawnCount()).isZero();
    }

    /** With its eyes awake but looking straight ahead, it lays them exactly as they were painted, so waking shows no seam. */
    @Test
    void eyesLookingAheadMatchThePaintedOnes() throws IOException
    {
        var art = art();
        BufferedImage painted = ImageIO.read(ASSETS.resolve("textures/misc/temple_mosaic.png").toFile());
        var wake = staredAt(MosaicWake.NOTICE + MosaicGaze.WAKE_SPREAD);
        var gaze = new MosaicGaze(art, SEED);
        for (int i = 0; i < 5; i++)
            gaze.tick(wake, MosaicArt.WIDTH / 2D, MosaicArt.HEIGHT / 2D, 1E12);
        var picture = new MosaicPicture();
        picture.compute(art, whole(), wake, gaze, new MosaicMotion(), 1, 0, SEED);

        int compared = 0;
        for (int i = 0; i < picture.drawnCount(); i++)
        {
            int index = picture.drawn(i);
            if (picture.kind(index) != MosaicPicture.FLAT || art.region(index) != MosaicArt.EYE)
                continue;
            int x = index % MosaicArt.WIDTH;
            int y = index / MosaicArt.WIDTH;
            assertThat(Integer.toHexString(picture.colour(index) & 0xFFFFFF)).as("pixel %s, %s", x, y).isEqualTo(Integer.toHexString(painted.getRGB(x, y) & 0xFFFFFF));
            compared++;
        }
        assertThat(compared).isGreaterThan(300);
    }

    @Test
    void eyesTurnTowardThePlayer() throws IOException
    {
        var art = art();
        var wake = staredAt(MosaicWake.NOTICE + MosaicGaze.WAKE_SPREAD);
        var gaze = new MosaicGaze(art, SEED);
        for (int i = 0; i < 5; i++)
            gaze.tick(wake, -400, 64, 80);
        for (int eye = 0; eye < art.eyes().size(); eye++)
            assertThat(gaze.offsetX(eye)).as("eye %s", eye).isNegative();
    }

    @Test
    void fullyReachedTheFleshStandsOutOfTheWallButNotOnAFallenTile() throws IOException
    {
        var art = art();
        var wake = staredAt(MosaicWake.MOST);
        var gaze = new MosaicGaze(art, SEED);
        gaze.tick(wake, 120, 64, 160);
        var wear = whole();
        int fallen = 7;
        wear[fallen] = MosaicWall.FALLEN;
        var picture = new MosaicPicture();
        picture.compute(art, wear, wake, gaze, new MosaicMotion(), 1, 0, SEED);

        assertThat(picture.highest()).isBetween(MosaicPicture.DOME_DEPTH * 0.8F,
                MosaicPicture.DOME_DEPTH + MosaicPicture.JITTER + MosaicPicture.TOOTH_JUT + MosaicPicture.EYE_BULGE + MosaicPicture.BREATH);
        int seams = 0;
        for (int i = 0; i < picture.drawnCount(); i++)
        {
            int index = picture.drawn(i);
            assertThat(MosaicArt.tileOf(index)).isNotEqualTo(fallen);
            if (picture.kind(index) == MosaicPicture.SEAM)
                seams++;
        }
        assertThat(seams).isGreaterThan(500);
        assertThat(picture.seamGlow()).isEqualTo(1);
    }

    /** The flesh swells as one body: neighbouring tesserae differ by a crack, never by a pillar's height, and its edge meets the wall. */
    @Test
    void theFleshSwellsWithoutPillars() throws IOException
    {
        var art = art();
        var wake = staredAt(MosaicWake.MOST);
        var gaze = new MosaicGaze(art, SEED);
        var picture = new MosaicPicture();
        picture.compute(art, whole(), wake, gaze, new MosaicMotion(), 1, 0, SEED);

        float steepest = 0;
        int where = 0;
        float edge = 0;
        for (int index : art.fleshPixels())
        {
            int x = index % MosaicArt.WIDTH;
            int y = index / MosaicArt.WIDTH;
            if (art.region(index) != MosaicArt.FLESH)
                continue;
            if (art.fleshDepth(x, y) <= 1.5F)
                edge = Math.max(edge, picture.height(index));
            for (int other : new int[]{index + 1, index + MosaicArt.WIDTH})
            {
                if (other % MosaicArt.WIDTH == 0 || other >= MosaicArt.WIDTH * MosaicArt.HEIGHT || art.region(other) != MosaicArt.FLESH)
                    continue;
                float step = Math.abs(picture.height(index) - picture.height(other));
                if (step > steepest)
                {
                    steepest = step;
                    where = index;
                }
            }
        }
        // Tesserae are about three pixels across, so a step is the mound's rise over three pixels, a sunk seam and a tessera standing proud
        assertThat(steepest).as("at %s, %s", where % MosaicArt.WIDTH, where / MosaicArt.WIDTH).isLessThan(5);
        assertThat(edge).isLessThan(1.5F);
    }

    @Test
    void wheelsStartOnTheirPaintingAndNeverTurnIntoTheWall() throws IOException
    {
        var art = art();
        var still = new float[art.wheels().size()];
        var flat = MosaicWheels.shape(art, 1, 0, still, still);
        for (int k = 0; k < art.wheels().size(); k++)
        {
            int wheel = k;
            var painted = art.eyes().stream().filter(eye -> eye.figure() == MosaicArt.WHEEL_BODY && eye.part() == wheel).toList();
            assertThat(flat.eyes()[k].length).isEqualTo(painted.size());
            for (int n = 0; n < painted.size(); n++)
            {
                assertThat(flat.eyes()[k][n][0]).isEqualTo(painted.get(n).x() + 0.5F, within(0.01F));
                assertThat(flat.eyes()[k][n][1]).isEqualTo(painted.get(n).y() + 0.5F, within(0.01F));
                assertThat(flat.eyes()[k][n][2]).isEqualTo(MosaicWheels.HUB_STIR, within(0.01F));
            }
        }
        for (float tumble = 0; tumble < 12; tumble += 0.37F)
        {
            var turned = new float[art.wheels().size()];
            Arrays.fill(turned, tumble);
            var out = MosaicWheels.shape(art, 1, 1, turned, turned);
            for (var ring : out.rings())
            {
                for (var point : ring)
                    assertThat(point[2]).isPositive();
            }
        }
    }

    /** Through a long stare and the withdrawal after it, the wheels' eyes never jump, and they come to rest where they were painted. */
    @Test
    void wheelsMoveSmoothlyAndSettleOnTheirPainting() throws IOException
    {
        var art = art();
        var wake = new MosaicWake();
        var motion = new MosaicMotion();
        float[][][] last = null;
        float fastest = 0;
        for (int tick = 0; tick < 1200; tick++)
        {
            wake.tick(tick < 900);
            motion.tick(wake, art);
            var eyes = MosaicWheels.shape(art, wake.stir(1), wake.reach(1), motion.spin(1), motion.tumble(1)).eyes();
            if (last != null)
            {
                for (int k = 0; k < eyes.length; k++)
                {
                    for (int n = 0; n < eyes[k].length; n++)
                        fastest = Math.max(fastest, (float) Math.sqrt(Math.pow(eyes[k][n][0] - last[k][n][0], 2) + Math.pow(eyes[k][n][1] - last[k][n][1], 2)
                                + Math.pow(eyes[k][n][2] - last[k][n][2], 2)));
                }
            }
            last = eyes;
        }
        // The hub comes out of the wall about this fast as the mosaic withdraws, and the wheels' turning adds a few pixels
        assertThat(fastest).isLessThan(MosaicWheels.HUB_OUT * MosaicWake.WITHDRAW / (MosaicWake.REACH_FULL - MosaicWake.REACH_START) * 1.6F + 6);
        var rest = MosaicWheels.shape(art, 1, 0, motion.spin(1), motion.tumble(1)).eyes();
        var painted = MosaicWheels.shape(art, 1, 0, new float[art.wheels().size()], new float[art.wheels().size()]).eyes();
        for (int k = 0; k < rest.length; k++)
        {
            for (int n = 0; n < rest[k].length; n++)
            {
                // Settled by a whole gap counts too: the wheel then looks just as painted
                float nearest = Float.MAX_VALUE;
                for (var other : painted[k])
                    nearest = Math.min(nearest, (float) Math.hypot(rest[k][n][0] - other[0], rest[k][n][1] - other[1]));
                assertThat(nearest).as("wheel %s eye %s", k, n).isLessThan(0.05F);
            }
        }
    }

    @Test
    void aTendrilKeepsToTheWallWhenThePlayerIsTooClose() throws IOException
    {
        var art = art();
        for (int tendril = 0; tendril < art.tendrils().size(); tendril++)
        {
            var path = art.tendrils().get(tendril);
            var close = MosaicTendrils.shape(art, tendril, 1, 1, 0, SEED, path.x()[0] + 0.5, path.y()[0] + 0.5, MosaicTendrils.STOP_SHORT - 4, 0);
            for (var point : close)
                assertThat(point[2]).as("tendril %s", tendril).isLessThanOrEqualTo(MosaicTendrils.LIFT + 1E-3F);
        }
    }

    @Test
    void whileItStirsTheBroodDripsAndTheFaithfulLook() throws IOException
    {
        var art = art();
        var wake = new MosaicWake();
        var motion = new MosaicMotion();
        for (int i = 0; i < MosaicWake.STIR_FULL + 200; i++)
        {
            wake.tick(true);
            motion.tick(wake, art);
        }
        var gaze = new MosaicGaze(art, SEED);
        var picture = new MosaicPicture();
        picture.compute(art, whole(), wake, gaze, motion, 1, 0, SEED);

        int moved = 0;
        for (int i = 0; i < picture.drawnCount(); i++)
        {
            int index = picture.drawn(i);
            if (picture.kind(index) == MosaicPicture.FLAT && picture.colour(index) == 0 && picture.source(index) != index)
            {
                assertThat(picture.source(index) % MosaicArt.WIDTH).isEqualTo(index % MosaicArt.WIDTH);
                assertThat(index).isGreaterThan(picture.source(index));
                moved++;
            }
        }
        assertThat(moved).isGreaterThan(art.brood().length);
        for (var head : art.heads())
        {
            int at = MosaicArt.index((int) Math.floor(head.x()), (int) Math.floor(head.y() + 0.5F));
            assertThat(picture.kind(at)).as("faithful at %s", head.x()).isEqualTo(MosaicPicture.FLAT);
            assertThat(picture.emission(at)).isPositive();
        }
    }

    @Test
    void tendrilsStayRootedAndStopShortOfThePlayer() throws IOException
    {
        var art = art();
        double[] player = {120, 64, 160};
        for (int tendril = 0; tendril < art.tendrils().size(); tendril++)
        {
            var path = art.tendrils().get(tendril);
            var still = MosaicTendrils.shape(art, tendril, 0, 0, 0, SEED, player[0], player[1], player[2], 0);
            for (int s = 0; s < still.length; s++)
            {
                assertThat(still[s][0]).isEqualTo(path.x()[s] + 0.5F, within(1E-4F));
                assertThat(still[s][2]).isZero();
            }

            var reached = MosaicTendrils.shape(art, tendril, 1, 1, 0, SEED, player[0], player[1], player[2], 0);
            assertThat(reached[0][0]).isEqualTo(path.x()[0] + 0.5F, within(1E-4F));
            var tip = reached[reached.length - 1];
            double distance = Math.sqrt(Math.pow(tip[0] - player[0], 2) + Math.pow(tip[1] - player[1], 2) + Math.pow(tip[2] - player[2], 2));
            assertThat(distance).as("tendril %s", tendril).isGreaterThanOrEqualTo(MosaicTendrils.STOP_SHORT - MosaicTendrils.WOBBLE);
            assertThat(tip[2]).as("tendril %s comes out of the wall", tendril).isGreaterThan(40);
            assertThat(Arrays.stream(reached).allMatch(point -> point[3] >= 1)).isTrue();
        }
    }
}
