package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.voxelindustry.voidheart.common.content.underway.SecurityBalance;
import net.voxelindustry.voidheart.common.content.underway.security.Fitter;
import net.voxelindustry.voidheart.common.content.underway.security.Mantlet;
import net.voxelindustry.voidheart.common.content.underway.security.RamCart;
import net.voxelindustry.voidheart.common.content.underway.security.Sapper;
import net.voxelindustry.voidheart.common.content.underway.security.Searchlight;
import net.voxelindustry.voidheart.common.content.underway.security.TurretWell;
import net.voxelindustry.voidheart.common.setup.VoidHeartEntities;

import java.util.UUID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The machines of the Underway that the security test of a fort does not stage, each on a bare concrete floor, where a machine nobody posted is always awake: the Mantlet behind its shield, the Sapper and its fuse, the Ram cart on its track, the Fitter, the mortar of a turret well and the searchlight.
 */
public class UnderwayMachinesClientTest implements FabricClientGameTest
{
    private static final int X0 = 700;
    private static final int FLOOR = FLAT_GROUND;
    private static final int FEET = FLAT_FEET;
    private static final float KILL = 100_000;
    private static UUID stagedId;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("difficulty normal");
            server.runCommand("gamemode creative @a");
            tp(server, X0 + 0.5, FEET, 0.5);
            world.getConnection().waitForChunksRender();
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            clearArea(server, X0);
            server.runCommand("fill " + (X0 - 20) + " " + FLOOR + " -20 " + (X0 + 20) + " " + FLOOR + " 20 voidheart:underway_concrete");

            mantlet(context, server);
            sapper(context, server);
            ramCart(context, server);
            fitter(context, server);
            mortar(context, server);
            searchlight(context, server);

            clearScene(server);
            clearArea(server, X0);
            server.runCommand("effect clear @a");
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /** Blows from in front of its shield glance off, blows from behind land, and its slit lets a burst out and a blow in. */
    private static void mantlet(ClientGameTestContext context, TestServerContext server)
    {
        stage(server, VoidHeartEntities.MANTLET, X0 + 0.5, 0.5, 0);
        look(context, server, X0 + 0.5, 4.5, X0 + 0.5, FEET + 1.2, 0.5);
        context.waitTicks(Mantlet.WAKE_TICKS / 2);
        server.runOnServer(minecraft -> expect(mantlet(minecraft).isAwake() && !mantlet(minecraft).isUnderPressure(), "A Mantlet should stand building pressure before it moves"));
        context.takeScreenshot(TestScreenshotOptions.of("underway-mantlet-waking").withSize(1280, 720));
        waitFor(context, server, Mantlet.WAKE_TICKS + 10, "The Mantlet never came under pressure", minecraft -> mantlet(minecraft).isUnderPressure());
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-mantlet-front").withSize(1280, 720));
        look(context, server, X0 + 3.5, -3.5, X0 + 0.5, FEET + 1.2, 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-mantlet-back").withSize(1280, 720));

        // In front of its shield, far enough to be shot at
        look(context, server, X0 + 0.5, 9.5, X0 + 0.5, FEET + 1.4, 0.5);
        exposed(context, server);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var mantlet = mantlet(minecraft);
            expect(!mantlet.isSlitOpen(), "The slit is open with nobody to shoot at");
            expect(!mantlet.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 10) && mantlet.getHealth() == mantlet.getMaxHealth(), "A blow from in front of the shield landed");
        });
        server.runCommand("gamemode survival @a");
        waitFor(context, server, 120, "The Mantlet never opened its slit to aim", minecraft -> mantlet(minecraft).isSlitOpen() && mantlet(minecraft).aimTicks() > 3);
        context.takeScreenshot(TestScreenshotOptions.of("underway-mantlet-slit").withSize(1280, 720));
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var mantlet = mantlet(minecraft);
            expect(mantlet.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 4) && mantlet.getHealth() < mantlet.getMaxHealth(), "A blow through the open slit glanced off");
        });
        server.runOnServer(GameTestKit::heal);
        waitFor(context, server, 80, "The Mantlet never fired its burst", minecraft -> mantlet(minecraft).shotsFired() >= SecurityBalance.MANTLET_BURST_SHOTS);
        context.waitTicks(2);
        server.runOnServer(minecraft ->
        {
            // Bolts follow each other faster than a hit's grace lasts, and each must still count
            float lost = player(minecraft).getMaxHealth() - player(minecraft).getHealth();
            expect(lost > SecurityBalance.MANTLET_SHOT_DAMAGE * (SecurityBalance.MANTLET_BURST_SHOTS - 0.5F), "A burst of " + SecurityBalance.MANTLET_BURST_SHOTS + " bolts on a player standing still took " + lost + " health");
        });
        waitFor(context, server, 80, "The slit stayed open after the burst", minecraft -> !mantlet(minecraft).isSlitOpen());

        // Behind it before it has brought its shield around
        server.runCommand("gamemode creative @a");
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var mantlet = mantlet(minecraft);
            var behind = mantlet.position().subtract(net.minecraft.world.phys.Vec3.directionFromRotation(0, mantlet.shieldYaw()).scale(2));
            player(minecraft).snapTo(behind.x, behind.y, behind.z);
            float before = mantlet.getHealth();
            expect(mantlet.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 4) && mantlet.getHealth() < before, "A blow from behind the shield glanced off");
            mantlet.hurtServer(level, level.damageSources().genericKill(), KILL);
        });
        shielded(server);
        context.waitTicks(30);
    }

    /** It runs at a player, lights its fuse close by, swells and bursts, which harms and leaves nothing of it. */
    private static void sapper(ClientGameTestContext context, TestServerContext server)
    {
        look(context, server, X0 + 0.5, 12.5, X0 + 0.5, FEET + 0.3, 0.5);
        stage(server, VoidHeartEntities.SAPPER, X0 + 0.5, 0.5, 0);
        context.waitTicks(3);
        look(context, server, X0 + 1.4, 1.9, X0 + 0.5, FEET + 0.2, 0.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-sapper-folded").withSize(1280, 720));
        waitFor(context, server, Sapper.WAKE_TICKS + 10, "The Sapper never unfolded", minecraft -> staged(minecraft, Sapper.class).isUnderPressure());
        context.waitTicks(6);
        context.takeScreenshot(TestScreenshotOptions.of("underway-sapper-awake").withSize(1280, 720));

        // Looking down at the floor in front, where it comes to
        look(context, server, X0 + 0.5, 10.5, X0 + 0.5, FEET + 0.2, 8.6);
        exposed(context, server);
        server.runCommand("gamemode survival @a");
        waitFor(context, server, 200, "The Sapper never lit its fuse beside the player", minecraft -> staged(minecraft, Sapper.class).fuse() > SecurityBalance.SAPPER_FUSE_TICKS / 2);
        server.runOnServer(minecraft ->
        {
            var sapper = staged(minecraft, Sapper.class);
            var player = player(minecraft);
            expect(sapper.distanceTo(player) < SecurityBalance.SAPPER_ARM_RANGE + 2, "The Sapper lit its fuse " + sapper.distanceTo(player) + " blocks from the player");
            expect(player.getHealth() == player.getMaxHealth(), "The Sapper harmed the player before it burst");
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-sapper-fuse").withSize(1280, 720));
        waitFor(context, server, SecurityBalance.SAPPER_FUSE_TICKS + 10, "The Sapper never burst", minecraft -> minecraft.overworld().getEntity(stagedId) == null);
        server.runOnServer(minecraft -> expect(player(minecraft).getHealth() < player(minecraft).getMaxHealth(), "The burst of a Sapper beside the player did not harm them"));
        server.runCommand("gamemode creative @a");
        shielded(server);
    }

    /** It only minds whoever stands on its track: it winds up, charges along the track, runs them over, and turns blows from along the track. */
    private static void ramCart(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("fill " + X0 + " " + FEET + " -16 " + X0 + " " + FEET + " 16 voidheart:underway_feeder_rail[axis=z]");
        stage(server, VoidHeartEntities.RAM_CART, X0 + 0.5, -10.5, 0);
        look(context, server, X0 + 2.7, -8.3, X0 + 0.5, FEET + 0.6, -10.5);
        waitFor(context, server, RamCart.WAKE_TICKS + 10, "The Ram cart never came under pressure", minecraft -> staged(minecraft, RamCart.class).isUnderPressure());
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-ram-cart").withSize(1280, 720));

        // Beside the track it is left alone, and a blow from the side lands while one from along the track does not
        exposed(context, server);
        server.runCommand("gamemode survival @a");
        look(context, server, X0 + 3.5, 0.5, X0 + 0.5, FEET + 0.6, -10.5);
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var cart = staged(minecraft, RamCart.class);
            var player = player(minecraft);
            expect(cart.phase() == RamCart.Phase.PARKED && Math.abs(cart.getZ() + 10.5) < 0.1, "The Ram cart moved for a player standing beside its track, in phase " + cart.phase());
            player.snapTo(X0 + 0.5, FEET, -6.5);
            expect(!cart.hurtServer(level, level.damageSources().playerAttack(player), 10), "A blow against a ram plate landed");
            player.snapTo(X0 + 2.5, FEET, -10.5);
            expect(cart.hurtServer(level, level.damageSources().playerAttack(player), 4), "A blow against the flank of a Ram cart glanced off");
            heal(minecraft);
        });

        // On the track, far down it
        look(context, server, X0 + 0.5, 6.5, X0 + 0.5, FEET + 0.8, -10.5);
        waitFor(context, server, 40, "The Ram cart never wound up for a player on its track", minecraft -> staged(minecraft, RamCart.class).phase() == RamCart.Phase.WIND_UP);
        context.waitTicks(SecurityBalance.CART_WIND_UP_TICKS / 2);
        context.takeScreenshot(TestScreenshotOptions.of("underway-ram-cart-winding-up").withSize(1280, 720));
        waitFor(context, server, 120, "The Ram cart never ran over a player standing on its track", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        waitFor(context, server, 120, "The Ram cart never braked", minecraft -> staged(minecraft, RamCart.class).phase() == RamCart.Phase.BRAKE || staged(minecraft, RamCart.class).phase() == RamCart.Phase.PARKED);
        server.runOnServer(minecraft ->
        {
            var cart = staged(minecraft, RamCart.class);
            expect(cart.getZ() > 6 && cart.getZ() < 16.5 && Math.abs(cart.getX() - (X0 + 0.5)) < 0.05, "The Ram cart ended its charge at " + cart.position() + ", off its track or short of the player");
        });
        server.runCommand("gamemode creative @a");
        shielded(server);
        double stopped = server.computeOnServer(minecraft -> staged(minecraft, RamCart.class).getZ());
        look(context, server, X0 + 3.5, stopped - 3, X0 + 0.5, FEET + 0.6, stopped);
        context.takeScreenshot(TestScreenshotOptions.of("underway-ram-cart-braking").withSize(1280, 720));
        server.runOnServer(minecraft -> staged(minecraft, RamCart.class).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().genericKill(), KILL));
        context.waitTicks(30);
        server.runCommand("fill " + X0 + " " + FEET + " -16 " + X0 + " " + FEET + " 16 air");
    }

    /** It wakes and harms nobody. Its repairs are shown in a fort, where it has wrecks to go to. */
    private static void fitter(ClientGameTestContext context, TestServerContext server)
    {
        stage(server, VoidHeartEntities.FITTER, X0 + 0.5, 0.5, 0);
        look(context, server, X0 + 1.9, 1.9, X0 + 0.5, FEET + 0.4, 0.5);
        waitFor(context, server, Fitter.WAKE_TICKS + 10, "The Fitter never came under pressure", minecraft -> staged(minecraft, Fitter.class).isUnderPressure());
        context.waitTicks(4);
        context.takeScreenshot(TestScreenshotOptions.of("underway-fitter").withSize(1280, 720));
        exposed(context, server);
        server.runCommand("gamemode survival @a");
        context.waitTicks(60);
        server.runOnServer(minecraft ->
        {
            expect(staged(minecraft, Fitter.class).getTarget() == null, "A Fitter went for a player");
            expect(player(minecraft).getHealth() == player(minecraft).getMaxHealth(), "A Fitter harmed a player");
            staged(minecraft, Fitter.class).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().genericKill(), KILL);
        });
        server.runCommand("gamemode creative @a");
        shielded(server);
        context.waitTicks(30);
    }

    /** It rises for a player in sight, marks a circle where they stand and drops a shell on it, and cannot reach a player right beside it. */
    private static void mortar(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + X0 + " " + FEET + " 0 voidheart:underway_turret_well");
        stage(server, VoidHeartEntities.TURRET_WELL, X0 + 0.5, 0.5, 0);
        look(context, server, X0 + 2.5, 2.5, X0 + 0.5, FEET + 0.4, 0.5);
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var well = staged(minecraft, TurretWell.class);
            expect(well.phase() == TurretWell.Phase.DOWN, "A mortar rose for a player in creative, in phase " + well.phase());
            expect(!well.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 20), "A mortar under its plate was harmed");
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-mortar-down").withSize(1280, 720));

        // Right beside it: it rises, and has nothing to throw at
        exposed(context, server);
        server.runCommand("gamemode survival @a");
        waitFor(context, server, 60, "The mortar never rose for a player in sight", minecraft -> staged(minecraft, TurretWell.class).phase() == TurretWell.Phase.UP);
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var well = staged(minecraft, TurretWell.class);
            expect(well.shellsFired() == 0, "A mortar dropped a shell on a player right beside it");
            expect(well.hurtServer(level, level.damageSources().playerAttack(player(minecraft)), 4), "A risen mortar shrugged off a blow");
        });
        context.takeScreenshot(TestScreenshotOptions.of("underway-mortar-up").withSize(1280, 720));

        look(context, server, X0 + 0.5, 10.5, X0 + 0.5, FEET + 0.8, 0.5);
        waitFor(context, server, 100, "The mortar never threw a shell at a player in range", minecraft -> staged(minecraft, TurretWell.class).shellsFired() > 0);
        server.runOnServer(minecraft ->
        {
            var mark = staged(minecraft, TurretWell.class).mark();
            expect(mark.distanceTo(player(minecraft).position()) < 0.5, "The shell's mark is at " + mark + ", not where the player stood");
        });
        context.waitTicks(SecurityBalance.WELL_FLIGHT_TICKS / 2);
        // Seen from the side, as the shell comes down on its circle
        server.runCommand("gamemode creative @a");
        server.runCommand("tick freeze");
        look(context, server, X0 + 7.5, 5.5, X0 + 0.5, FEET + 1.0, 8.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-mortar-shell").withSize(1280, 720));
        look(context, server, X0 + 0.5, 10.5, X0 + 0.5, FEET + 0.8, 0.5);
        server.runCommand("gamemode survival @a");
        server.runCommand("tick unfreeze");
        waitFor(context, server, SecurityBalance.WELL_FLIGHT_TICKS + 10, "A shell that fell on a player standing still did not harm them", minecraft -> player(minecraft).getHealth() < player(minecraft).getMaxHealth());
        server.runCommand("gamemode creative @a");
        shielded(server);
        server.runOnServer(minecraft -> staged(minecraft, TurretWell.class).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().genericKill(), KILL));
        context.waitTicks(SecurityBalance.WELL_FLIGHT_TICKS + 10);
        server.runCommand("setblock " + X0 + " " + FEET + " 0 air");
    }

    /** Its beam sweeps at night, and holds on a player who walks into it. */
    private static void searchlight(ClientGameTestContext context, TestServerContext server)
    {
        freezeTime(server, "midnight");
        server.runCommand("setblock " + X0 + " " + FEET + " -12 voidheart:underway_searchlight[facing=south]");
        stage(server, VoidHeartEntities.SEARCHLIGHT, X0 + 0.5, -11.5, 0);
        look(context, server, X0 + 8.5, -6.5, X0 + 0.5, FEET + 0.8, -4.5);
        context.waitTicks(10);
        server.runOnServer(minecraft -> expect(staged(minecraft, Searchlight.class).isAwake() && staged(minecraft, Searchlight.class).notice() == 0, "A searchlight nobody posted should burn and have nobody in its beam"));
        context.takeScreenshot(TestScreenshotOptions.of("underway-searchlight-sweeping").withSize(1280, 720));

        // Straight in front of it, where the middle of its sweep passes
        server.runCommand("gamemode survival @a");
        look(context, server, X0 + 0.5, 0.5, X0 + 0.5, FEET + 0.6, -11.5);
        waitFor(context, server, SecurityBalance.SEARCHLIGHT_SWEEP_TICKS + 40, "The searchlight never caught a player standing in its sweep", minecraft -> staged(minecraft, Searchlight.class).hasNoticed());
        context.takeScreenshot(TestScreenshotOptions.of("underway-searchlight-caught").withSize(1280, 720));
        server.runCommand("gamemode creative @a");
        look(context, server, X0 + 8.5, -6.5, X0 + 0.5, FEET + 0.8, -4.5);
        context.takeScreenshot(TestScreenshotOptions.of("underway-searchlight-side").withSize(1280, 720));
        server.runOnServer(minecraft -> staged(minecraft, Searchlight.class).hurtServer(minecraft.overworld(), minecraft.overworld().damageSources().genericKill(), KILL));
        context.waitTicks(10);
        server.runCommand("setblock " + X0 + " " + FEET + " -12 air");
    }

    /** Stands a machine nobody posted on the floor, facing a yaw. */
    private static void stage(TestServerContext server, EntityType<? extends Mob> type, double x, double z, float yaw)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var mob = type.create(level, EntitySpawnReason.COMMAND);
            mob.snapTo(x, FEET, z, yaw, 0);
            mob.setYBodyRot(yaw);
            mob.setYHeadRot(yaw);
            stagedId = mob.getUUID();
            level.addFreshEntity(mob);
        });
    }

    private static <T> T staged(MinecraftServer minecraft, Class<T> kind)
    {
        var entity = minecraft.overworld().getEntity(stagedId);
        if (kind.isInstance(entity))
            return kind.cast(entity);
        throw new AssertionError("The staged " + kind.getSimpleName() + " is gone");
    }

    private static Mantlet mantlet(MinecraftServer minecraft)
    {
        return staged(minecraft, Mantlet.class);
    }

    private static void look(ClientGameTestContext context, TestServerContext server, double feetX, double feetZ, double x, double y, double z)
    {
        aimCamera(server, feetX, FEET, feetZ, x, y, z);
        context.waitTicks(5);
    }

    /** Takes the test's resistance away and gives the player health to spare, so that harm shows and does not kill. */
    private static void exposed(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("effect clear @a minecraft:resistance");
        server.runCommand("effect give @a minecraft:health_boost infinite 20 true");
        context.waitTicks(2);
        server.runOnServer(GameTestKit::heal);
    }

    private static void shielded(TestServerContext server)
    {
        server.runCommand("effect clear @a minecraft:health_boost");
        server.runCommand("effect give @a minecraft:resistance infinite 4 true");
    }
}
