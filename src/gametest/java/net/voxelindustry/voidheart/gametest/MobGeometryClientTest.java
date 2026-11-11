package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.voxelindustry.voidheart.common.content.mob.StalkingWraith;
import net.voxelindustry.voidheart.common.content.mob.WraithBeast;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Every mob close up, still, from the front, the back, a grazing angle, above, below and a small sweep, where faces sharing a plane fight and see-through texels show a
 * box from inside.
 */
public class MobGeometryClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final int FEET = GROUND + 1;
    private static final int ROW_Z = 4;
    private static final int SPACING = 8;
    private static final int CLOSE_FOV = 40;
    private static final int DEFAULT_FOV = 70;
    private static final int SWEEP_STEPS = 4;
    private static final int SHOT_WIDTH = 1280;
    private static final int SHOT_HEIGHT = 720;

    private static void shoot(ClientGameTestContext context, String name)
    {
        context.takeScreenshot(TestScreenshotOptions.of(name).withSize(SHOT_WIDTH, SHOT_HEIGHT));
    }

    private record Shot(String name, EntityType<? extends Mob> type) {}

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var shots = List.of(
                new Shot("void-skeleton", VoidHeartEntities.VOID_SKELETON),
                new Shot("void-ghoul", VoidHeartEntities.VOID_GHOUL),
                new Shot("wraith", VoidHeartEntities.WRAITH),
                new Shot("wraith-scarab", VoidHeartEntities.WRAITH_SCARAB),
                new Shot("wraith-beast", VoidHeartEntities.WRAITH_BEAST),
                new Shot("crab-large", VoidHeartEntities.CORRUPTED_CRAB_LARGE),
                new Shot("barnacle", VoidHeartEntities.CORRUPTED_BARNACLE),
                new Shot("fallen-novice", VoidHeartEntities.FALLEN_NOVICE),
                new Shot("fallen-adept", VoidHeartEntities.FALLEN_ADEPT),
                new Shot("fallen-elder", VoidHeartEntities.FALLEN_ELDER),
                new Shot("eye-maw", VoidHeartEntities.EYE_MAW),
                new Shot("large-eye-maw", VoidHeartEntities.LARGE_EYE_MAW),
                new Shot("stalking-wraith", VoidHeartEntities.STALKING_WRAITH),
                new Shot("fallen-bishop", VoidHeartEntities.FALLEN_BISHOP),
                new Shot("legionary", VoidHeartEntities.LEGIONARY),
                new Shot("centurion", VoidHeartEntities.CENTURION),
                new Shot("kernacian-dragoon", VoidHeartEntities.KERNACIAN_DRAGOON));

        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(CLOSE_FOV);
            });
            context.getInput().pressKey(InputConstants.KEY_F1);

            server.runCommand("fill -10 " + GROUND + " -6 " + (shots.size() * SPACING + 6) + " " + GROUND + " 14 voidheart:voidstone_tile");
            double[] heights = new double[shots.size()];
            for (int i = 0; i < shots.size(); i++)
            {
                var shot = shots.get(i);
                int x = i * SPACING;
                int index = i;
                server.runOnServer(minecraft ->
                {
                    var level = minecraft.overworld();
                    var mob = shot.type().create(level, EntitySpawnReason.COMMAND);
                    mob.snapTo(x + 0.5, FEET, ROW_Z + 0.5, 0, 0);
                    mob.setYHeadRot(0);
                    mob.setYBodyRot(0);
                    mob.setNoAi(true);
                    if (mob instanceof StalkingWraith stalker)
                        stalker.setRevealed(true);
                    if (mob instanceof WraithBeast beast)
                        beast.showSolid();
                    level.addFreshEntity(mob);
                    heights[index] = mob.getBbHeight();
                });
            }
            context.waitTicks(30);
            server.runCommand("tick freeze");

            for (int i = 0; i < shots.size(); i++)
            {
                var name = "geometry-" + shots.get(i).name();
                double x = i * SPACING + 0.5;
                double z = ROW_Z + 0.5;
                double height = heights[i];
                double y = FEET + height * 0.6;
                double reach = 0.8 + height * 0.9;
                aimFrom(context, server, x + reach * 0.4, y - 1.2, z + reach, x, y, z);
                shoot(context, name + "-front");
                aimFrom(context, server, x - reach * 0.5, y - 1.0, z - reach, x, y, z);
                shoot(context, name + "-back");
                // Nearly along the front, where faces in one plane fight worst
                aimFrom(context, server, x + reach, y - 1.4, z + reach * 0.25, x, y, z);
                shoot(context, name + "-grazing");
                // Down onto the shoulders and up under the hems, where open box ends show their insides
                aimFrom(context, server, x + 0.3, FEET + height + 0.4, z + reach * 0.7, x, FEET + height * 0.55, z);
                shoot(context, name + "-above");
                aimFrom(context, server, x + 0.2, FEET - 1.45, z + reach * 0.8, x, FEET + height * 0.5, z);
                shoot(context, name + "-below");
                for (int step = 0; step < SWEEP_STEPS; step++)
                {
                    aimFrom(context, server, x + reach * 0.4 + step * 0.03, y - 1.2 + step * 0.02, z + reach + step * 0.04, x, y, z, 2);
                    context.takeScreenshot(name + "-sweep-" + step);
                }
            }

            server.runCommand("tick unfreeze");
            server.runCommand("kill @e[type=!player]");
            server.runCommand("fill -10 " + GROUND + " -6 " + (shots.size() * SPACING + 6) + " " + GROUND + " 14 grass_block");
            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(DEFAULT_FOV);
            });
        }
    }
}
