package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.StairBlock;
import net.voxelindustry.voidheart.common.content.underway.HatchBalance;
import net.voxelindustry.voidheart.common.content.underway.HatchWaves;
import net.voxelindustry.voidheart.common.content.underway.KeyDefence;
import net.voxelindustry.voidheart.common.content.underway.UnderwayKeyEntity;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;
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
 * Opening a district's hatch with an Underway key: the key is set with a real click and spent, the hatch charges through it behind a shield that waves of the district's mobs wear down, and once full its leaves draw back and the shaft's stairs come up to the floor.
 */
public class UnderwayHatchClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int FLOOR = UnderwayLevels.HATCH_FLOOR;
    private static final int KEYS = 3;
    private static final String STRUCK_TAG = "struck_by_test";

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

            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            shield(context, server, fort);
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            String broken = overlay(context);
            expect(broken.contains("key is broken"), "No word that the key broke, the overlay says: " + broken);

            clickLeaf(context, server, fort);
            server.runOnServer(minecraft ->
            {
                expect(keys(minecraft).size() == 1 && heldKeys(minecraft) == KEYS - 2, "A hatch whose key broke should take a new one");
                expect(keys(minecraft).getFirst().charge() < 20, "The charge outlived the key that broke: " + keys(minecraft).getFirst().charge());
            });
            clickLeaf(context, server, fort);
            server.runOnServer(minecraft -> expect(keys(minecraft).size() == 1 && heldKeys(minecraft) == KEYS - 2, "A hatch already charging took a second key"));
            String busy = overlay(context);
            expect(busy.contains("already turning"), "No word that the hatch is busy, the overlay says: " + busy);

            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            waves(context, server, fort);
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
            server.runOnServer(minecraft -> expect(keys(minecraft).isEmpty() && heldKeys(minecraft) == KEYS - 2, "An open hatch took a key"));
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

    /**
     * The shield over a key at work: only the blows of mobs count, each darkens the pane it came through, the key widens to the shield while it holds,
     * a lull brings the shield back whole, and a bare key breaks after its own few hits, leaving the hatch shut.
     */
    private static void shield(ClientGameTestContext context, TestServerContext server, UnderwayFort fort)
    {
        double centreX = fort.hatchX() + DistrictPos.CELL / 2.0;
        double centreZ = fort.hatchZ() + DistrictPos.CELL / 2.0;
        context.waitTicks(20);
        server.runOnServer(minecraft ->
        {
            var key = keys(minecraft).getFirst();
            var level = (ServerLevel) key.level();
            expect(key.panes() == KeyDefence.ALL_PANES && key.keyHits() == HatchBalance.KEY_HITS, "A fresh key stands behind " + Integer.toBinaryString(key.panes()) + " panes with " + key.keyHits() + " hits");
            expect(key.getBbWidth() == UnderwayKeyEntity.SHIELD_WIDTH, "A shielded key is " + key.getBbWidth() + " wide, not as wide as its shield");
            expect(!key.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 100), "A blow from a player harmed the key");
            expect(!key.hurtServer(level, level.damageSources().fall(), 100), "A fall harmed the key");
            expect(key.panes() == KeyDefence.ALL_PANES, "Something other than a mob darkened a pane");
        });
        aim(context, server, fort.hatchX() - 3.5, FLOOR + 2, fort.hatchZ() - 3.5, centreX, FLOOR + 1.5, centreZ);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shield-up").withSize(1280, 720));
        aim(context, server, centreX - 1.5, FLOOR + 1, centreZ + 1.2, centreX + 1, FLOOR + 2.2, centreZ - 1);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shield-from-inside").withSize(1280, 720));
        server.runCommand("tick freeze");
        for (int step = 0; step < 6; step++)
        {
            aim(context, server, fort.hatchX() - 2.5 + step * 0.08, FLOOR + 1.2, centreZ - 1 + step * 0.06, centreX, FLOOR + 1.2, centreZ);
            context.takeScreenshot("underway-shield-sweep-" + step);
        }
        server.runCommand("tick unfreeze");

        aim(context, server, fort.hatchX() - 3.5, FLOOR + 2, fort.hatchZ() - 3.5, centreX, FLOOR + 1.5, centreZ);
        strike(server, 0, -4);
        server.runOnServer(minecraft ->
        {
            var key = keys(minecraft).getFirst();
            expect(key.panes() == (KeyDefence.ALL_PANES & ~1), "A blow from the north left panes " + Integer.toBinaryString(key.panes()));
            strike(minecraft, 0, -4);
            expect(Integer.bitCount(key.panes()) == KeyDefence.PANES - 1, "Two blows at once both counted");
        });
        context.waitTicks(2);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shield-flare").withSize(1280, 720));
        context.waitTicks(HatchBalance.HIT_INTERVAL_TICKS);
        strike(server, -4, 0);
        context.waitTicks(HatchBalance.HIT_INTERVAL_TICKS);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shield-two-dark").withSize(1280, 720));
        for (int hit = 2; hit < KeyDefence.PANES; hit++)
        {
            strike(server, 4, 1);
            context.waitTicks(HatchBalance.HIT_INTERVAL_TICKS);
        }
        server.runOnServer(minecraft ->
        {
            var key = keys(minecraft).getFirst();
            expect(!key.isShielded() && key.keyHits() == HatchBalance.KEY_HITS, "A hit per pane should leave the key bare and whole: panes " + Integer.toBinaryString(key.panes()) + ", hits " + key.keyHits());
            expect(key.getBbWidth() == UnderwayKeyEntity.WIDTH, "A bare key is still " + key.getBbWidth() + " wide");
        });
        context.waitTicks(20);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shield-down").withSize(1280, 720));
        strike(server, 4, 1);
        server.runOnServer(minecraft -> expect(keys(minecraft).getFirst().keyHits() == HatchBalance.KEY_HITS - 1, "A blow on the bare key did not count"));

        waitFor(context, server, HatchBalance.SHIELD_LULL_TICKS + 40, "The shield never came back after a lull", minecraft -> keys(minecraft).getFirst().isShielded());
        server.runOnServer(minecraft ->
        {
            var key = keys(minecraft).getFirst();
            expect(key.panes() == KeyDefence.ALL_PANES && key.keyHits() == HatchBalance.KEY_HITS - 1, "The shield should come back whole and the key stay marked: panes " + Integer.toBinaryString(key.panes()) + ", hits " + key.keyHits());
        });
        context.waitTicks(30);
        context.takeScreenshot(TestScreenshotOptions.of("underway-shield-reformed").withSize(1280, 720));

        for (int hit = 0; hit < KeyDefence.PANES + HatchBalance.KEY_HITS - 1; hit++)
        {
            strike(server, -1, 4);
            context.waitTicks(HatchBalance.HIT_INTERVAL_TICKS);
        }
        server.runOnServer(minecraft ->
        {
            expect(keys(minecraft).isEmpty(), "The key outlived its last hit");
            expect(!isAir(minecraft, fort, 3, FLOOR), "The hatch opened though its key broke");
        });
    }

    /**
     * The waves a charging hatch draws: the first comes on its own once the charge reaches it, its mobs arrive around the hatch room and some go for the key,
     * they reach the shield and darken a pane, one struck by a player turns on them for a while, and the next wave is larger.
     */
    private static void waves(ClientGameTestContext context, TestServerContext server, UnderwayFort fort)
    {
        double centreX = fort.hatchX() + DistrictPos.CELL / 2.0;
        double centreZ = fort.hatchZ() + DistrictPos.CELL / 2.0;
        server.runCommand("difficulty normal");
        server.runCommand("gamemode creative @a");
        aim(context, server, fort.hatchX() - 5.5, FLOOR + 1, fort.hatchZ() - 5.5, centreX, FLOOR + 1.5, centreZ);
        server.runOnServer(minecraft -> expect(keys(minecraft).getFirst().wavesSent() == 0, "A wave came before its time"));
        int beforeFirst = (HatchBalance.WAVE_FIRST_TICKS - 1) * 100 / HatchBalance.CHARGE_TICKS;
        server.runCommand("execute as @a run voidheart underway charge " + beforeFirst);
        waitFor(context, server, HatchBalance.CHARGE_TICKS / 100 + 40, "The first wave never came", minecraft -> keys(minecraft).getFirst().wavesSent() == 1);
        context.waitTicks(HatchBalance.STEER_INTERVAL_TICKS + 2);
        server.runOnServer(minecraft ->
        {
            var key = keys(minecraft).getFirst();
            var wave = waveMobs(minecraft);
            expect(!wave.isEmpty() && wave.size() <= HatchWaves.size(0) + 1, "The first wave brought " + wave.size() + " mobs");
            for (var mob : wave)
            {
                expect(mob.distanceTo(key) < 30, mob.getType() + " arrived " + mob.distanceTo(key) + " blocks from the key");
                expect(mob.getY() >= FLOOR + 1, mob.getType() + " arrived under the floor at " + mob.position());
            }
            expect(wave.stream().anyMatch(mob -> mob.getTarget() == key), "No mob of the wave goes for the key: " + wave.stream().map(mob -> mob.getType() + " -> " + mob.getTarget()).toList());
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-wave-arrives").withSize(1280, 720));
        waitFor(context, server, 900, () -> "No mob of the wave reached the shield: " + server.computeOnServer(minecraft -> waveMobs(minecraft).stream().map(mob -> mob.getType().toShortString() + " at " + mob.blockPosition() + " -> " + mob.getTarget()).toList()),
                minecraft -> keys(minecraft).isEmpty() || keys(minecraft).getFirst().panes() != KeyDefence.ALL_PANES);
        server.runOnServer(minecraft -> expect(!keys(minecraft).isEmpty(), "The first wave alone broke the key"));
        context.takeScreenshot(TestScreenshotOptions.of("underway-wave-strikes").withSize(1280, 720));

        server.runOnServer(minecraft ->
        {
            var key = keys(minecraft).getFirst();
            var player = player(minecraft);
            var breaker = waveMobs(minecraft).stream().filter(mob -> mob.getTarget() == key).findFirst().orElseThrow(() -> new AssertionError("No mob is at the key any more"));
            player.setGameMode(GameType.SURVIVAL);
            breaker.hurtServer((ServerLevel) key.level(), key.level().damageSources().playerAttack(player), 1);
            expect(breaker.getTarget() == player, "A mob struck by a player keeps at the key: " + breaker.getTarget());
            player.setGameMode(GameType.CREATIVE);
            breaker.addTag(STRUCK_TAG);
        });
        context.waitTicks(HatchBalance.STEER_INTERVAL_TICKS * 2);
        server.runOnServer(minecraft ->
        {
            var struck = waveMobs(minecraft).stream().filter(mob -> mob.entityTags().contains(STRUCK_TAG)).findFirst().orElseThrow();
            expect(struck.getTarget() != keys(minecraft).getFirst(), "A mob drawn off by a player was turned back to the key at once");
        });
        waitFor(context, server, HatchBalance.DISTRACTION_TICKS + 60, "A mob drawn off by a player never turned back to the key",
                minecraft -> waveMobs(minecraft).stream().anyMatch(mob -> mob.entityTags().contains(STRUCK_TAG) && mob.getTarget() == keys(minecraft).getFirst()));

        server.runCommand("kill @e[tag=voidheart_hatch_wave]");
        server.runCommand("execute as @a run voidheart underway wave");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            expect(keys(minecraft).getFirst().wavesSent() == 2, "Calling a wave did not count it");
            expect(!waveMobs(minecraft).isEmpty(), "The called wave brought nothing");
        });
        server.runCommand("kill @e[tag=voidheart_hatch_wave]");
        server.runCommand("kill @e[type=item,distance=..64]");
        server.runCommand("difficulty peaceful");
        server.runCommand("gamemode survival @a");
        context.waitTicks(5);
    }

    private static List<Mob> waveMobs(MinecraftServer minecraft)
    {
        var player = player(minecraft);
        return player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48), mob -> mob.isAlive() && mob.entityTags().contains("voidheart_hatch_wave"));
    }

    private static void strike(TestServerContext server, double dx, double dz)
    {
        server.runOnServer(minecraft -> strike(minecraft, dx, dz));
    }

    /** Lands the blow of a mob standing that far east and south of the key at work. */
    private static void strike(MinecraftServer minecraft, double dx, double dz)
    {
        var key = keys(minecraft).getFirst();
        var level = (ServerLevel) key.level();
        var mob = VoidHeartEntities.VOID_SKELETON.create(level, EntitySpawnReason.COMMAND);
        mob.snapTo(key.getX() + dx, key.getY(), key.getZ() + dz, 0, 0);
        key.hurtServer(level, level.damageSources().mobAttack(mob), 4);
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
