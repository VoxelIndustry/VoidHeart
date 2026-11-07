package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.client.ClarityFog;
import net.voxelindustry.voidheart.client.InsightSight;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import net.voxelindustry.voidheart.common.content.mob.FallenBishop;
import net.voxelindustry.voidheart.common.content.mob.KernacianDragoon;
import net.voxelindustry.voidheart.common.content.mob.StalkingWraith;
import net.voxelindustry.voidheart.common.content.mob.Wraith;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.util.function.Predicate;

/**
 * Insight shows wraiths through a wall and nowhere else, gathers light at a Bishop's crook before its lance, and reads out a Dragoon's adaptation under its
 * boss bar. Clarity pushes the Daedalus fog back, easing in and out.
 */
public class InsightClientTest implements FabricClientGameTest
{
    private static final int FEET = -60;
    private static final int WALL_X = 6;
    private static final double MOB_X = 9.5;
    private static final int SWEEP_FRAMES = 6;
    private static final double BISHOP_X = 7.5;
    private static final double BISHOP_Z = -6.5;
    private static final int VIEW_TICKS = 10;
    private static final double SIDE_STEP = 5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            silhouettes(context, server);
            bishop(context, server);
            dragoon(context, server);
            clarity(context, server, world);
        }
    }

    private static void silhouettes(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill " + WALL_X + " " + FEET + " -4 " + WALL_X + " " + (FEET + 4) + " 6 voidheart:voidstone_bricks");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var wraith = VoidHeartEntities.WRAITH.create(level, EntitySpawnReason.COMMAND);
            wraith.setNoAi(true);
            wraith.snapTo(MOB_X, FEET, 0.5, 90, 0);
            level.addFreshEntity(wraith);
            var stalker = VoidHeartEntities.STALKING_WRAITH.create(level, EntitySpawnReason.COMMAND);
            stalker.setNoAi(true);
            stalker.snapTo(MOB_X, FEET, 3.5, 90, 0);
            level.addFreshEntity(stalker);
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, 0.5, FEET, 1.5, MOB_X, FEET + 1.2, 2, 20);

        expect(context.computeOnClient(minecraft -> silhouette(minecraft, Wraith.class)) == 0, "Without Insight nothing should show through the wall");
        context.takeScreenshot("insight-wall-before");

        server.runCommand("effect give @a voidheart:insight 600 0 true");
        context.waitTicks(5);
        expect(context.computeOnClient(minecraft -> silhouette(minecraft, Wraith.class)) > 0, "Insight should show the wraith behind the wall");
        expect(context.computeOnClient(minecraft -> silhouette(minecraft, StalkingWraith.class)) > 0, "Insight should show the stalking wraith behind the wall");
        context.takeScreenshot("insight-wall");

        server.runCommand("tick freeze");
        for (int step = 0; step < SWEEP_FRAMES; step++)
        {
            aimFrom(context, server, 0.5 + step * 0.06, FEET, 1.5 + step * 0.1, MOB_X, FEET + 1.2, 2, 2);
            context.takeScreenshot("insight-wall-sweep-" + step);
        }
        // Close to the wall, from the side, to see where the copy meets the stone
        aimFrom(context, server, 3.5, FEET, -2.5, MOB_X, FEET + 1.2, 1, 2);
        context.takeScreenshot("insight-wall-close");
        server.runCommand("tick unfreeze");

        server.runCommand("fill " + WALL_X + " " + FEET + " -4 " + WALL_X + " " + (FEET + 4) + " 6 air");
        aimFrom(context, server, 4.5, FEET, 1.5, MOB_X, FEET + 1.2, 2, 10);
        expect(context.computeOnClient(minecraft -> silhouette(minecraft, Wraith.class)) == 0, "A wraith in plain view should show no silhouette");
        context.takeScreenshot("insight-open");

        server.runCommand("kill @e[type=voidheart:wraith]");
        server.runCommand("kill @e[type=voidheart:stalking_wraith]");
        // Let their death clouds clear before the next scene
        context.waitTicks(40);
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    private static void bishop(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode survival @a");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            protect(player);
            var level = minecraft.overworld();
            var bishop = VoidHeartEntities.FALLEN_BISHOP.create(level, EntitySpawnReason.COMMAND);
            bishop.snapTo(BISHOP_X, FEET, BISHOP_Z, 90, 0);
            bishop.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100000, 10));
            level.addFreshEntity(bishop);
        });
        server.runCommand("tp @a 1.5 " + FEET + " " + BISHOP_Z + " -90 -12");
        context.getInput().pressKey(InputConstants.KEY_F1);

        waitFor(context, server, 200, "The bishop should get its lance ready", minecraft -> first(minecraft, VoidHeartEntities.FALLEN_BISHOP, FallenBishop::isLanceReady) != null);
        context.waitTicks(4);
        expect(context.computeOnClient(minecraft -> !minecraft.level.getEntitiesOfClass(FallenBishop.class, around(minecraft), FallenBishop::isLanceReady).isEmpty()),
                "The client should know the lance is ready");
        context.takeScreenshot("insight-bishop-gather");
        server.runCommand("kill @e[type=voidheart:fallen_bishop]");
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(10);
    }

    private static void dragoon(ClientGameTestContext context, TestServerContext server)
    {
        int packed = 0;
        for (int i = 0; i < 3; i++)
            packed = DragoonAdaptation.adapt(packed, Kind.BLADE);
        for (int i = 0; i < DragoonAdaptation.MAX_HITS; i++)
            packed = DragoonAdaptation.adapt(packed, Kind.FIRE);
        packed = DragoonAdaptation.adapt(packed, Kind.MAGIC);
        for (int i = 0; i < 4; i++)
            packed = DragoonAdaptation.adapt(packed, Kind.KERNACIAN);
        int adapted = packed;

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            protect(player);
            var level = minecraft.overworld();
            var dragoon = VoidHeartEntities.KERNACIAN_DRAGOON.create(level, EntitySpawnReason.COMMAND);
            dragoon.snapTo(MOB_X + 6, FEET, 0.5, 90, 0);
            dragoon.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100000, 10));
            dragoon.setAdaptation(adapted);
            level.addFreshEntity(dragoon);
        });
        server.runCommand("tp @a 0.5 " + FEET + " 0.5 -90 -5");
        waitFor(context, server, 200, "The dragoon's boss bar should show", minecraft ->
        {
            var dragoon = first(minecraft, VoidHeartEntities.KERNACIAN_DRAGOON, entity -> true);
            return dragoon != null && dragoon.isBossBarShownTo(player(minecraft));
        });
        context.waitTicks(5);
        context.takeScreenshot("insight-dragoon-readout");

        server.runOnServer(minecraft ->
        {
            var dragoon = first(minecraft, VoidHeartEntities.KERNACIAN_DRAGOON, entity -> true);
            dragoon.setAdaptation(DragoonAdaptation.adapt(dragoon.adaptation(), Kind.BLADE));
        });
        context.waitTicks(2);
        context.takeScreenshot("insight-dragoon-flash");

        server.runCommand("effect clear @a voidheart:insight");
        context.waitTicks(2);
        context.takeScreenshot("insight-dragoon-without");

        server.runCommand("kill @e[type=voidheart:kernacian_dragoon]");
        server.runOnServer(minecraft -> player(minecraft).removeAllEffects());
        server.runCommand("gamemode creative @a");
        context.waitTicks(10);
    }

    private static void clarity(ClientGameTestContext context, TestServerContext server, TestSingleplayerContext world)
    {
        server.runCommand("execute as @a run voidheart daedalus enter");
        context.waitTicks(40);
        world.getConnection().waitForChunksRender();
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.waitTicks(10);
        float[] base = context.computeOnClient(InsightClientTest::fog);
        views(context, server, "clarity-before");

        server.runCommand("effect give @a voidheart:clarity 15 0 true");
        context.waitTicks(ClarityFog.EASE_IN_TICKS / 2);
        float halfway = context.computeOnClient(minecraft -> ClarityFog.strength(1));
        expect(halfway > 0.3F && halfway < 0.7F, "Clarity should ease in, at " + halfway + " halfway");
        context.waitTicks(ClarityFog.EASE_IN_TICKS);
        float full = context.computeOnClient(minecraft -> ClarityFog.strength(1));
        expect(full == 1, "Clarity should have fully eased in, at " + full);
        float[] cleared = context.computeOnClient(InsightClientTest::fog);
        System.out.println("[Clarity] fog " + base[0] + "-" + base[1] + " became " + cleared[0] + "-" + cleared[1]);
        // The end can't go past the render distance, which the chasm's own fog may already reach
        float reach = context.computeOnClient(minecraft -> minecraft.options.getEffectiveRenderDistance() * 16F);
        expect(cleared[1] >= Math.min(base[1] * 1.5F, reach), "Clarity should push the fog's end out, from " + base[1] + " to " + cleared[1] + " with " + reach + " in view");
        expect(cleared[0] > base[0] * 2, "Clarity should push the fog's start out, from " + base[0] + " to " + cleared[0]);
        views(context, server, "clarity-after");

        // What is left of the 15 seconds runs under the last 200 ticks, so it fades with them
        context.waitTicks(300 - ClarityFog.EASE_IN_TICKS * 3 / 2 - ClarityFog.EASE_OUT_TICKS / 2 - 3 * VIEW_TICKS);
        float fading = context.computeOnClient(minecraft -> ClarityFog.strength(1));
        expect(fading > 0.3F && fading < 0.7F, "Clarity should ease out with its last ticks, at " + fading);
        context.waitTicks(ClarityFog.EASE_OUT_TICKS / 2 + 10);
        float gone = context.computeOnClient(minecraft -> ClarityFog.strength(1));
        expect(gone == 0, "Clarity should be gone with its effect, at " + gone);
        float[] after = context.computeOnClient(InsightClientTest::fog);
        expect(Math.abs(after[1] - base[1]) < 0.5F, "The fog should be back once Clarity is gone, " + after[1] + " against " + base[1]);

        context.getInput().pressKey(InputConstants.KEY_F1);
        server.runCommand("execute as @a run voidheart daedalus leave");
        context.waitTicks(20);
    }

    /**
     * Along the bridge, then off each side of it looking down into the chasm, the longest views a district entry gives.
     */
    private static void views(ClientGameTestContext context, TestServerContext server, String name)
    {
        float[] spot = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            return new float[] { (float) player.getX(), (float) player.getY(), (float) player.getZ(), player.getYRot() };
        });
        context.takeScreenshot(name + "-bridge");
        double side = Math.toRadians(spot[3] + 90);
        for (int sign = -1; sign <= 1; sign += 2)
        {
            double x = spot[0] - Math.sin(side) * SIDE_STEP * sign;
            double z = spot[2] + Math.cos(side) * SIDE_STEP * sign;
            server.runCommand("execute as @a at @s run tp @s " + x + " " + spot[1] + " " + z + " " + spot[3] + " 70");
            context.waitTicks(VIEW_TICKS);
            context.takeScreenshot(name + (sign < 0 ? "-left" : "-right"));
        }
        server.runCommand("execute as @a at @s run tp @s " + spot[0] + " " + spot[1] + " " + spot[2] + " " + spot[3] + " 0");
        context.waitTicks(VIEW_TICKS);
    }

    /**
     * The fog the last frame was drawn with, start and end.
     */
    private static float[] fog(Minecraft minecraft)
    {
        try
        {
            var field = LevelRenderer.class.getDeclaredField("levelRenderState");
            field.setAccessible(true);
            var fog = ((LevelRenderState) field.get(minecraft.levelRenderer)).cameraRenderState.fogData;
            return new float[] { fog.environmentalStart, fog.environmentalEnd };
        }
        catch (ReflectiveOperationException e)
        {
            throw new AssertionError("Could not read the fog", e);
        }
    }

    private static void protect(ServerPlayer player)
    {
        player.setHealth(player.getMaxHealth());
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 100000, 4));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100000, 4));
    }

    private static float silhouette(Minecraft minecraft, Class<? extends Entity> type)
    {
        var found = minecraft.level.getEntitiesOfClass(type, around(minecraft), entity -> true);
        expect(!found.isEmpty(), "Expected a " + type.getSimpleName() + " on the client");
        return InsightSight.silhouette(found.getFirst(), 1, false);
    }

    private static AABB around(Minecraft minecraft)
    {
        return minecraft.player.getBoundingBox().inflate(32);
    }

    private static <T extends Entity> T first(MinecraftServer minecraft, EntityType<T> type, Predicate<T> filter)
    {
        var found = minecraft.overworld().getEntities(type, entity -> entity.isAlive() && filter.test(entity));
        return found.isEmpty() ? null : found.getFirst();
    }

    private static void waitFor(ClientGameTestContext context, TestServerContext server, int maxTicks, String message, Predicate<MinecraftServer> condition)
    {
        for (int tick = 0; tick < maxTicks; tick++)
        {
            if (server.computeOnServer(condition::test))
                return;
            context.waitTick();
        }
        throw new AssertionError(message + " within " + maxTicks + " ticks");
    }

    private static void aimFrom(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z, int settleTicks)
    {
        double dx = x - feetX;
        double dy = y - (feetY + 1.62);
        double dz = z - feetZ;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        server.runCommand("tp @a " + feetX + " " + feetY + " " + feetZ + " " + yaw + " " + pitch);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
        context.waitTicks(settleTicks);
    }

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
