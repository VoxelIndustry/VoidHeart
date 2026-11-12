package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.StairBlock;
import net.voxelindustry.voidheart.common.content.underway.HatchBalance;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.HatchOpening;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayLevels;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Opening a district's hatch with an Underway key: the key is set with a real click and spent, the hatch charges through it, and once full its leaves draw back and the shaft's stairs come up to the floor.
 */
public class UnderwayHatchClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int FLOOR = UnderwayLevels.HATCH_FLOOR;
    private static final int KEYS = 3;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("difficulty peaceful");
            server.runCommand("gamemode survival @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());

            server.runCommand("execute as @a run voidheart underway hatch station");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            var fort = server.computeOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, new ItemStack(VoidHeartItems.UNDERWAY_KEY, KEYS));
                return UnderwayPlans.fort(VoidDaedalus.level(minecraft).getSeed(), DistrictPos.at(player.getBlockX(), player.getBlockZ()));
            });
            double centreX = fort.hatchX() + DistrictPos.CELL / 2.0;
            double centreZ = fort.hatchZ() + DistrictPos.CELL / 2.0;

            context.takeScreenshot("underway-key-in-hand");
            clickLeaf(context, server, fort);
            server.runOnServer(minecraft ->
            {
                expect(keys(minecraft).size() == 1, "Clicking the hatch with a key set " + keys(minecraft).size() + " keys to work");
                expect(heldKeys(minecraft) == KEYS - 1, "Setting a key should spend it, " + heldKeys(minecraft) + " left of " + KEYS);
                var key = keys(minecraft).getFirst();
                expect(key.getX() == centreX && key.getZ() == centreZ && key.getY() == FLOOR + 1, "The key stands at " + key.position() + ", not on the middle of the hatch");
            });
            waitFor(context, server, 100, "The hatch does not charge with a player beside it", minecraft -> keys(minecraft).getFirst().charge() > 20);

            clickLeaf(context, server, fort);
            server.runOnServer(minecraft -> expect(keys(minecraft).size() == 1 && heldKeys(minecraft) == KEYS - 1, "A hatch already charging took a second key"));
            String busy = overlay(context);
            expect(busy.contains("already turning"), "No word that the hatch is busy, the overlay says: " + busy);

            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            aim(context, server, centreX - 2.2, FLOOR + 1, centreZ - 1.6, centreX, FLOOR + 1.6, centreZ);
            context.takeScreenshot(TestScreenshotOptions.of("underway-key-lock-close").withSize(1280, 720));
            aim(context, server, fort.hatchX() - 3.5, FLOOR + 2, fort.hatchZ() - 3.5, centreX, FLOOR + 1.5, centreZ);
            context.takeScreenshot(TestScreenshotOptions.of("underway-key-charging").withSize(1280, 720));
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            context.takeScreenshot(TestScreenshotOptions.of("underway-key-charge-bar").withSize(1280, 720));
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);

            server.runCommand("execute as @a run voidheart underway charge 100");
            waitFor(context, server, 60, "A full charge does not start the leaves", minecraft -> isAir(minecraft, fort, 3, FLOOR));
            server.runOnServer(minecraft -> expect(!isAir(minecraft, fort, 1, FLOOR), "The leaves went all at once"));
            context.takeScreenshot(TestScreenshotOptions.of("underway-hatch-opening").withSize(1280, 720));
            waitFor(context, server, HatchBalance.LEAF_STAGE_TICKS * HatchOpening.STAGES + 40, "The hatch never finished opening", minecraft -> keys(minecraft).isEmpty());
            context.waitTicks(5);
            checkOpened(server, fort);
            context.takeScreenshot(TestScreenshotOptions.of("underway-hatch-open").withSize(1280, 720));
            aim(context, server, centreX + 0.5, FLOOR + 3.2, fort.hatchZ() + DistrictPos.CELL + 1.5, centreX, FLOOR - 1, centreZ);
            context.takeScreenshot(TestScreenshotOptions.of("underway-hatch-open-top").withSize(1280, 720));

            var landing = part(fort, HatchOpening.Part.LANDING);
            aim(context, server, landing.x() + 0.5, FLOOR + 1, landing.z() + 0.5, landing.x() + 0.5, FLOOR + 1, landing.z() + 0.5);
            server.runOnServer(minecraft -> expect(player(minecraft).onGround() && player(minecraft).getY() == FLOOR + 1, "The landing does not carry a player at floor level: " + player(minecraft).position()));
            var step = HatchOpening.stairs(fort).stream().filter(change -> change.part() == HatchOpening.Part.STEP && change.y() == FLOOR - 1).findFirst().orElseThrow();
            aim(context, server, landing.x() + 0.5, FLOOR + 1, landing.z() + 0.5, step.x() + 0.5, FLOOR - 2, step.z() + 0.5);
            context.takeScreenshot(TestScreenshotOptions.of("underway-hatch-open-stairs").withSize(1280, 720));

            aim(context, server, fort.hatchX() + 0.5, FLOOR + 1, fort.hatchZ() + 0.5, fort.hatchX() + 0.5, FLOOR, fort.hatchZ() + 0.5);
            rightClick(context);
            context.waitTicks(5);
            server.runOnServer(minecraft -> expect(keys(minecraft).isEmpty() && heldKeys(minecraft) == KEYS - 1, "An open hatch took a key"));
            String open = overlay(context);
            expect(open.contains("already stands open"), "No word that the hatch is open, the overlay says: " + open);

            server.runCommand("effect clear @a");
            server.runCommand("difficulty normal");
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /** Puts the player's feet at one point of the Daedalus and their eyes on another. A plain tp would send them to the overworld, where the server's commands run. */
    private static void aim(ClientGameTestContext context, TestServerContext server, double feetX, double feetY, double feetZ, double x, double y, double z)
    {
        server.runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(feetX, feetY, feetZ, x, y, z));
        context.waitTicks(5);
    }

    /** Stands on a corner of the leaves and clicks the plate underfoot with whatever is held. */
    private static void clickLeaf(ClientGameTestContext context, TestServerContext server, UnderwayFort fort)
    {
        aim(context, server, fort.hatchX() + 1.5, FLOOR + 1, fort.hatchZ() + 1.5, fort.hatchX() + 1.5, FLOOR, fort.hatchZ() + 1.5);
        rightClick(context);
        context.waitTicks(5);
    }

    private static void checkOpened(TestServerContext server, UnderwayFort fort)
    {
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var laid = HatchOpening.stairs(fort);
            int last = DistrictPos.CELL - 1;
            for (int dz = 0; dz <= last; dz++)
            {
                for (int dx = 0; dx <= last; dx++)
                {
                    boolean rim = dx == 0 || dz == 0 || dx == last || dz == last;
                    for (int y = FLOOR; y >= FLOOR - 1; y--)
                    {
                        var pos = new BlockPos(fort.hatchX() + dx, y, fort.hatchZ() + dz);
                        var state = level.getBlockState(pos);
                        if (rim)
                            expect(state.is(y == FLOOR ? VoidHeartBlocks.UNDERWAY_HAZARD_ARMOUR : VoidHeartBlocks.UNDERWAY_HATCH), "The coaming lost " + pos + " to " + state);
                        else if (laid.stream().noneMatch(change -> change.x() == pos.getX() && change.y() == pos.getY() && change.z() == pos.getZ()))
                            expect(state.isAir(), "The open hatch still holds " + state + " at " + pos);
                    }
                }
            }
            for (var change : laid)
            {
                var state = level.getBlockState(new BlockPos(change.x(), change.y(), change.z()));
                boolean step = change.part() == HatchOpening.Part.STEP;
                expect(state.is(step ? VoidHeartBlocks.UNDERWAY_STEEL_STAIRS : VoidHeartBlocks.UNDERWAY_GRATING), "Found " + state + " where the opening lays a " + change.part());
            }
            var lower = laid.stream().filter(change -> change.part() == HatchOpening.Part.STEP && change.y() == FLOOR - 1).findFirst().orElseThrow();
            var facing = level.getBlockState(new BlockPos(lower.x(), lower.y(), lower.z())).getValue(StairBlock.FACING);
            var under = level.getBlockState(new BlockPos(lower.x() - facing.getStepX(), lower.y() - 1, lower.z() - facing.getStepZ()));
            expect(under.is(VoidHeartBlocks.UNDERWAY_STEEL_STAIRS) && under.getValue(StairBlock.FACING) == facing, "The laid steps do not carry on from the shaft's, which ends in " + under);
        });
    }

    private static HatchOpening.Change part(UnderwayFort fort, HatchOpening.Part part)
    {
        return HatchOpening.stairs(fort).stream().filter(change -> change.part() == part).findFirst().orElseThrow();
    }

    private static boolean isAir(MinecraftServer minecraft, UnderwayFort fort, int dx, int y)
    {
        return VoidDaedalus.level(minecraft).getBlockState(new BlockPos(fort.hatchX() + dx, y, fort.hatchZ() + 3)).isAir();
    }

    private static List<UnderwayKeyEntity> keys(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        return player.level().getEntitiesOfClass(UnderwayKeyEntity.class, player.getBoundingBox().inflate(32));
    }

    private static int heldKeys(MinecraftServer minecraft)
    {
        return player(minecraft).getInventory().countItem(VoidHeartItems.UNDERWAY_KEY);
    }
}
