package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.render.SentinelEyeRender;
import net.voxelindustry.voidheart.client.render.state.SentinelEyeRenderState;
import net.voxelindustry.voidheart.common.content.underway.security.SentinelEye;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.UnderwayCanvas;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import net.voxelindustry.voidheart.common.world.underway.UnderwayMaterial;
import net.voxelindustry.voidheart.common.world.underway.UnderwayNetwork;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayRuin;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/** Small things of the Underway: a sentinel eye's cone stops at the wall it meets, a cell in hand shows the breaker cabinets around, and stairs the flood reaches hold its water. */
public class UnderwayDetailsClientTest implements FabricClientGameTest
{
    private static final int SCENE_Y = 120;
    private static final int WALL_X = 5;
    private static final int SEARCH = 12;
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    /** The wall in front of the eye runs this far to each side of its middle. */
    private static final int WALL_HALF_SPAN = 10;
    private static final double WALL_TOLERANCE = 0.02;
    private static final int SWEEP_STEPS = 8;
    /** How far around the stairs it finds the test looks for others the flood reaches. */
    private static final int AROUND = 24;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode spectator @a");
            freezeTime(server, "midnight");
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            // All tests share one client, so the camera goes back to what it was
            var cameraWas = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            try
            {
                cone(context, server);
                cabinetHighlight(context, server);
                floodedStairs(context, server);
            } finally
            {
                server.runCommand("effect clear @a");
                server.runCommand("gamemode creative @a");
                server.runCommand("gamerule advance_time true");
                if (!hudWasHidden)
                    context.getInput().pressKey(InputConstants.KEY_F1);
                context.runOnClient(minecraft -> minecraft.options.setCameraType(cameraWas));
            }
        }
    }

    /** Every line of the eye's cone stops at the wall or short of it, and some reach the wall rather than stopping at the lens. */
    private static void expectConeStopsAtWall(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var eyes = minecraft.level.getEntitiesOfClass(SentinelEye.class, new AABB(-2, SCENE_Y - 2, -2, 3, SCENE_Y + 2, 3));
            expect(eyes.size() == 1, "Expected one eye in the scene, found " + eyes.size());
            var eye = eyes.getFirst();
            @SuppressWarnings("unchecked")
            var renderer = (SentinelEyeRender<SentinelEye>) minecraft.getEntityRenderDispatcher().getRenderer(eye);
            var state = renderer.createRenderState();
            renderer.extractRenderState(eye, state, 0);
            int atWall = 0;
            for (int segment = 0; segment < SentinelEyeRenderState.CONE_LINES; segment++)
            {
                var end = eye.position().add(state.lens).add(SentinelEyeRender.coneEnd(state, segment));
                expect(end.x <= WALL_X + WALL_TOLERANCE || Math.abs(end.z) >= WALL_HALF_SPAN,
                        "Line " + segment + " of the cone reaches past the wall, to " + end);
                if (Math.abs(end.x - WALL_X) <= WALL_TOLERANCE && end.distanceTo(eye.position()) > 1)
                    atWall++;
            }
            expect(atWall > 0, "No line of the cone reaches the wall in front of the eye");
        });
    }

    /** An eye looking east at a wall: its cone shows on its own side and nothing of it shows on the far side. */
    private static void cone(ClientGameTestContext context, TestServerContext server)
    {
        int y = SCENE_Y;
        server.runCommand("fill -2 " + (y - 6) + " -10 " + (WALL_X + 12) + " " + (y - 6) + " 10 minecraft:smooth_stone");
        server.runCommand("fill " + WALL_X + " " + (y - 5) + " -10 " + WALL_X + " " + (y + 6) + " 10 minecraft:stone_bricks");
        server.runCommand("fill -1 " + (y - 1) + " -1 -1 " + (y + 1) + " 1 minecraft:stone_bricks");
        server.runCommand("summon voidheart:sentinel_eye 0.5 " + y + " 0.5 {Rotation:[-90f,0f]}");
        context.waitTicks(10);
        // Held still with its gaze on the wall
        server.runCommand("tick freeze");
        context.waitTicks(5);

        expectConeStopsAtWall(context);

        aimCamera(server, 2.5, y - 1, -6.5, 3.5, y, 0.5);
        context.waitTicks(20);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cone-before-wall").withSize(1280, 720));
        // Small steps close to the cone, so bands or gaps that come and go with the view show between frames
        for (int step = 0; step < SWEEP_STEPS; step++)
        {
            aimCamera(server, 2.5 + step * 0.05, y - 1, -3.5 + step * 0.08, 3.5, y, 0.5);
            context.waitTicks(3);
            context.takeScreenshot("underway-cone-sweep-" + step);
        }
        aimCamera(server, WALL_X + 9.5, y - 1, 0.5, WALL_X, y, 0.5);
        context.waitTicks(5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cone-past-wall").withSize(1280, 720));
        aimCamera(server, WALL_X + 5.5, y - 1, -7.5, WALL_X, y, 0.5);
        context.waitTicks(5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cone-past-wall-aside").withSize(1280, 720));
        server.runCommand("tick unfreeze");
        server.runCommand("kill @e[type=voidheart:sentinel_eye]");
    }

    /** A dark breaker cabinet behind the wall shows through it while a rubrum cell is in hand, and not otherwise. */
    private static void cabinetHighlight(ClientGameTestContext context, TestServerContext server)
    {
        int y = SCENE_Y;
        server.runCommand("setblock " + (WALL_X + 1) + " " + (y - 3) + " 0 voidheart:underway_breaker_cabinet[facing=east]");
        server.runCommand("gamemode creative @a");
        server.runCommand("clear @a");
        // Standing on the floor, since landing would end a creative flight
        aimCamera(server, 1.5, y - 5, 0.5, WALL_X + 1.5, y - 2.5, 0.5);
        context.waitTicks(20);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cabinet-no-cell").withSize(1280, 720));
        server.runCommand("item replace entity @a weapon.mainhand with voidheart:rubrum_cell");
        context.waitTicks(15);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cabinet-cell-through-wall").withSize(1280, 720));
        aimCamera(server, WALL_X + 4.5, y - 5, -2.5, WALL_X + 1.5, y - 2.5, 0.5);
        context.waitTicks(15);
        context.takeScreenshot(TestScreenshotOptions.of("underway-cabinet-cell-in-sight").withSize(1280, 720));
        server.runCommand("clear @a");
        server.runCommand("gamemode spectator @a");
    }

    /** Finds stairs of the Underway the flood reaches, and checks the world wrote them holding water. */
    private static void floodedStairs(ClientGameTestContext context, TestServerContext server)
    {
        BlockPos stairs = server.computeOnServer(minecraft -> findFloodedStairs(VoidDaedalus.level(minecraft).getSeed()));
        expect(stairs != null, "No stairs of the Underway reach a flood within " + SEARCH + " districts");
        var from = stairs.offset(-AROUND, 0, -AROUND);
        var to = stairs.offset(AROUND, 0, AROUND);
        server.runCommand("execute in voidheart:daedalus run forceload add " + from.getX() + " " + from.getZ() + " " + to.getX() + " " + to.getZ());
        waitFor(context, server, CHUNK_TIMEOUT_TICKS, "The chunks around the flooded stairs never loaded",
                minecraft -> VoidDaedalus.level(minecraft).isLoaded(from) && VoidDaedalus.level(minecraft).isLoaded(to) && VoidDaedalus.level(minecraft).isLoaded(stairs));
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var state = level.getBlockState(stairs);
            expect(state.getBlock() instanceof StairBlock, "No stairs at " + stairs + ": " + state);
            // Every stair the water touches, chunk edges included
            int checked = 0;
            for (var pos : BlockPos.betweenClosed(from.offset(0, -4, 0), to.offset(0, 4, 0)))
            {
                var at = level.getBlockState(pos);
                if (!(at.getBlock() instanceof StairBlock))
                    continue;
                boolean wet = level.getFluidState(pos.above()).is(FluidTags.WATER);
                for (var side : Direction.Plane.HORIZONTAL)
                    wet |= level.getBlockState(pos.relative(side)).is(Blocks.WATER);
                if (!wet)
                    continue;
                checked++;
                expect(at.getValue(StairBlock.WATERLOGGED), "Stairs in the flood at " + pos + " hold no water: " + at);
            }
            expect(checked > 0, "No stairs beside water around " + stairs);
        });
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(stairs.getX() + 2.5, stairs.getY() + 1, stairs.getZ() + 2.5, stairs.getX() + 0.5, stairs.getY() + 0.5, stairs.getZ() + 0.5));
        context.waitTicks(40);
        context.takeScreenshot(TestScreenshotOptions.of("underway-flooded-stairs").withSize(1280, 720));
        server.runCommand("execute in voidheart:daedalus run forceload remove all");
    }

    private static BlockPos findFloodedStairs(long seed)
    {
        for (int dz = -SEARCH; dz <= SEARCH; dz++)
        {
            for (int dx = -SEARCH; dx <= SEARCH; dx++)
            {
                var pos = new DistrictPos(dx, dz);
                if (UnderwayNetwork.regionRuin(seed, pos) != UnderwayRuin.FLOODED || !UnderwayNetwork.isLineRow(seed, dz) && !UnderwayNetwork.isLineColumn(seed, dx))
                    continue;
                var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
                canvas.reset(pos.originX(), pos.originZ());
                UnderwayPlans.paint(seed, canvas);
                for (int y = UnderwayLevels.MIN_Y; y <= UnderwayLevels.TOP_Y; y++)
                {
                    for (int z = canvas.minZ() + 1; z < canvas.maxZ(); z++)
                    {
                        for (int x = canvas.minX() + 1; x < canvas.maxX(); x++)
                        {
                            var material = canvas.get(x, y, z);
                            if (material.ordinal() < UnderwayMaterial.STAIRS_NORTH.ordinal() || material.ordinal() > UnderwayMaterial.STAIRS_WEST.ordinal())
                                continue;
                            if (canvas.get(x, y + 1, z) == UnderwayMaterial.WATER || canvas.get(x + 1, y, z) == UnderwayMaterial.WATER || canvas.get(x - 1, y, z) == UnderwayMaterial.WATER
                                    || canvas.get(x, y, z + 1) == UnderwayMaterial.WATER || canvas.get(x, y, z - 1) == UnderwayMaterial.WATER)
                                return new BlockPos(x, y, z);
                        }
                    }
                }
            }
        }
        return null;
    }
}
