package net.voxelindustry.voidheart.client.render.divining;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class LeySightPlanTest
{
    private static final float RADIUS = 64;
    private static final float[] FLAT = { 70, 70, 70, 70, 70, 70 };

    @Test
    void lineMeetsItsNodesAtTheirCenters()
    {
        var path = LeySightPlan.line(0, 0, 40, 0, FLAT, 20, 0, RADIUS, 42);

        assertThat(path.size()).isEqualTo(41);
        assertThat(path.x()[0]).isCloseTo(0.5, within(1.0E-6));
        assertThat(path.z()[0]).isCloseTo(0.5, within(1.0E-6));
        assertThat(path.x()[40]).isCloseTo(40.5, within(1.0E-6));
        assertThat(path.z()[40]).isCloseTo(0.5, within(1.0E-6));
        assertThat(path.along()[0]).isZero();
        assertThat(path.along()[40]).isEqualTo(1);
    }

    @Test
    void lineSwaysOnlyAwayFromItsNodes()
    {
        var path = LeySightPlan.line(0, 0, 200, 0, new float[] { 70, 70 }, 100, 0, 200, 7);

        double widest = 0;
        for (int i = 0; i < path.size(); i++)
            widest = Math.max(widest, Math.abs(path.z()[i] - 0.5));
        assertThat(widest).isGreaterThan(0.1).isLessThanOrEqualTo(LeySightPlan.SWAY_AMPLITUDE + 1.0E-6);
    }

    @Test
    void heightsBendSmoothlyThroughEverySample()
    {
        float[] heights = { 60, 64, 70, 66 };
        for (int i = 0; i < heights.length; i++)
            assertThat(LeySightPlan.height(heights, (float) i / (heights.length - 1))).isCloseTo(heights[i], within(1.0E-4F));

        float midway = LeySightPlan.height(heights, 0.5F);
        assertThat(midway).isBetween(64F, 70F);
    }

    @Test
    void lineIsCutToTheRadiusAroundTheRod()
    {
        var path = LeySightPlan.line(-500, 0, 500, 0, new float[] { 70, 70 }, 0, 0, RADIUS, 3);

        // One point is kept past each end of what lies inside, so the edge fade reaches its end
        float keep = RADIUS + LeySightPlan.CLIP_MARGIN + LeySightPlan.STEP;
        for (float distance : path.distance())
            assertThat(distance).isLessThanOrEqualTo(keep + 0.5F);
        assertThat(path.size()).isBetween(2 * (int) RADIUS, 2 * (int) keep + 3);
        assertThat(path.start()).isCloseTo(500 - RADIUS - LeySightPlan.CLIP_MARGIN - 1, within(1.5F));
    }

    @Test
    void lineOutOfReachKeepsNothing()
    {
        var path = LeySightPlan.line(0, 200, 100, 200, FLAT, 0, 0, RADIUS, 1);

        assertThat(path.isEmpty()).isTrue();
    }

    @Test
    void revealSpreadsOutFromTheRod()
    {
        assertThat(LeySightPlan.reveal(0, 5, RADIUS)).isZero();
        assertThat(LeySightPlan.reveal(LeySightPlan.SPREAD_TICKS / 2F, 5, RADIUS)).isEqualTo(1);
        assertThat(LeySightPlan.reveal(LeySightPlan.SPREAD_TICKS / 2F, 60, RADIUS)).isZero();
        assertThat(LeySightPlan.reveal(LeySightPlan.SPREAD_TICKS, 30, RADIUS)).isEqualTo(1);
    }

    @Test
    void revealFadesAtTheEdgeOfItsRadius()
    {
        float held = LeySightPlan.HOLD_END_TICKS - 1;

        assertThat(LeySightPlan.reveal(held, RADIUS, RADIUS)).isZero();
        assertThat(LeySightPlan.reveal(held, RADIUS - 7, RADIUS)).isBetween(0.2F, 0.8F);
    }

    @Test
    void revealFadesFromItsFarEdgeBackToTheRod()
    {
        float fading = LeySightPlan.HOLD_END_TICKS + LeySightPlan.FADE_TICKS / 2F;

        assertThat(LeySightPlan.reveal(fading, 5, RADIUS)).isEqualTo(1);
        assertThat(LeySightPlan.reveal(fading, 45, RADIUS)).isZero();
        assertThat(LeySightPlan.reveal(LeySightPlan.LIFE_TICKS, 0, RADIUS)).isZero();
    }

    @Test
    void frontFlashRunsWithTheSpreadingFrontOnly()
    {
        float age = LeySightPlan.SPREAD_TICKS / 2F;
        float front = RADIUS * LeySightPlan.easeOut(0.5F);

        assertThat(LeySightPlan.frontFlash(age, front, RADIUS)).isCloseTo(LeySightPlan.FRONT_FLASH, within(1.0E-5F));
        assertThat(LeySightPlan.frontFlash(age, front - 20, RADIUS)).isLessThan(0.01F);
        assertThat(LeySightPlan.frontFlash(LeySightPlan.SPREAD_TICKS + 5, 10, RADIUS)).isZero();
    }

    @Test
    void spurRunsStraightFromItsTapToItsStone()
    {
        var path = LeySightPlan.spur(10.25, 71.5, 4.75, 30.5, 65, 4.5, 20, 4, RADIUS, 11);
        int last = path.size() - 1;

        assertThat(path.x()[0]).isCloseTo(10.25, within(1.0E-6));
        assertThat(path.y()[0]).isCloseTo(71.5, within(1.0E-6));
        assertThat(path.z()[0]).isCloseTo(4.75, within(1.0E-6));
        assertThat(path.x()[last]).isCloseTo(30.5, within(1.0E-6));
        assertThat(path.y()[last]).isCloseTo(65, within(1.0E-6));
        assertThat(path.z()[last]).isCloseTo(4.5, within(1.0E-6));
        for (int i = 0; i < path.size(); i++)
            assertThat(path.y()[i]).isBetween(65D, 71.5D);
    }

    @Test
    void spurSwaysLessThanALine()
    {
        var spur = LeySightPlan.spur(0.5, 70, 0.5, 200.5, 70, 0.5, 100, 0, 200, 7);
        var line = LeySightPlan.line(0, 0, 200, 0, new float[] { 70, 70 }, 100, 0, 200, 7);

        double spurWidest = 0, lineWidest = 0;
        for (int i = 0; i < spur.size(); i++)
        {
            spurWidest = Math.max(spurWidest, Math.abs(spur.z()[i] - 0.5));
            lineWidest = Math.max(lineWidest, Math.abs(line.z()[i] - 0.5));
        }
        assertThat(spurWidest).isCloseTo(lineWidest * LeySightPlan.SPUR_SWAY_SHARE, within(1.0E-6));
    }

    @Test
    void shellMeetsFlatGroundWhereItsSphereDoes()
    {
        var flat = new float[70];
        java.util.Arrays.fill(flat, 64);
        // Center 1.5 above the ground, radius 20: the ground is reached at sqrt(20^2 - 1.5^2)
        var contact = LeySightPlan.shellContact(flat, 1, 65.5F, 20);

        assertThat(contact).isNotNull();
        assertThat(contact[0]).isCloseTo((float) Math.sqrt(400 - 2.25), within(0.05F));
        assertThat(contact[1]).isEqualTo(64);
    }

    @Test
    void shellMeetsAHillBeforeTheGroundBehindIt()
    {
        var heights = new float[70];
        java.util.Arrays.fill(heights, 64);
        for (int i = 10; i < 14; i++)
            heights[i] = 85;
        var contact = LeySightPlan.shellContact(heights, 1, 65.5F, 20);

        assertThat(contact).isNotNull();
        assertThat(contact[0]).isBetween(9F, 10F);
        assertThat(contact[1]).isGreaterThan(64);
    }

    @Test
    void shellBeyondTheSampledTerrainMeetsNothing()
    {
        var flat = new float[10];
        java.util.Arrays.fill(flat, 64);

        assertThat(LeySightPlan.shellContact(flat, 1, 65.5F, 30)).isNull();
    }

    @Test
    void shellGrowsWithTheFrontAndIsGoneSoonAfter()
    {
        assertThat(LeySightPlan.shellRadius(0, RADIUS)).isZero();
        assertThat(LeySightPlan.shellRadius(LeySightPlan.SPREAD_TICKS, RADIUS)).isEqualTo(RADIUS);
        assertThat(LeySightPlan.shellFade(0)).isZero();
        assertThat(LeySightPlan.shellFade(LeySightPlan.SHELL_FADE_IN)).isGreaterThan(0.5F);
        assertThat(LeySightPlan.shellFade(LeySightPlan.SPREAD_TICKS)).isLessThan(LeySightPlan.shellFade(LeySightPlan.SPREAD_TICKS / 2F));
        assertThat(LeySightPlan.shellFade(LeySightPlan.SHELL_TICKS)).isZero();
    }
}
