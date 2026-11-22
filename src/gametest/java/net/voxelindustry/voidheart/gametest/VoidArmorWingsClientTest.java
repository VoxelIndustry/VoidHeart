package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.armor.ArmorWings;
import net.voxelindustry.voidheart.common.content.armor.ArmorBalance;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids;
import net.voxelindustry.voidheart.common.content.armor.ArmorGrids.Placement;
import net.voxelindustry.voidheart.common.content.armor.ArmorWear;
import net.voxelindustry.voidheart.common.content.armor.LoadShedding;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBelts;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;

import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.CORE;
import static net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.LIMBS;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.BATTERY_CELL;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.EXOSKELETON_POWER;
import static net.voxelindustry.voidheart.common.content.armor.ArmorModule.LEVITATION;
import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expectClient;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * The Void Armor's levitation module, far from any pylon:
 * <ul>
 *     <li>Worn, the frame lets its wearer fly, and a double jump takes off.</li>
 *     <li>Standing costs only the frame's hum; flying costs the wings' heavy drain on top.</li>
 *     <li>Sneak held in the air sinks the wearer without stepping them out of the frame.</li>
 *     <li>The wings open on takeoff, trail when flying forward, fray when the frame cuts them, and are gone once the wearer has landed.</li>
 * </ul>
 */
public class VoidArmorWingsClientTest implements FabricClientGameTest
{
    private static final int    Y  = FLAT_FEET;
    private static final double X  = 0.5;
    private static final double Z  = 0.5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        var camera = context.computeOnClient(minecraft -> minecraft.options.getCameraType());
        // All tests share one client, so the HUD goes back to however it was found
        boolean hudHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
        try
        {
            try (var world = context.worldBuilder().create())
            {
                var server = world.getServer();
                world.getConnection().waitForChunksRender();
                server.runCommand("time set noon");
                server.runCommand("gamerule advance_time false");
                server.runCommand("gamerule spawn_mobs false");
                server.runCommand("gamemode survival @a");
                server.runCommand("clear @a");
                server.runCommand("tp @a " + X + " " + Y + " " + Z + " 180 10");
                context.waitTicks(10);
                // The captures want the HUD hidden
                if (!context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()))
                    context.getInput().pressKey(InputConstants.KEY_F1);

                server.runOnServer(minecraft -> ArmorWear.stepIn(player(minecraft), frame(), player(minecraft).position()));
                waitFor(context, server, 60, "The frame should close around its wearer",
                        minecraft -> ArmorWear.worn(player(minecraft)).phase() == net.voxelindustry.voidheart.common.content.armor.WornArmor.Phase.WORN);

                takesOff(context, server);
                drains(context, server);
                opens(context, server);
                trails(context, server);
                sneaks(context, server);
                frays(context, server);
                tablet(context, server);
            }
        }
        finally
        {
            context.runOnClient(minecraft -> minecraft.options.setCameraType(camera));
            if (context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden()) != hudHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static ItemStack frame()
    {
        var grids = ArmorGrids.EMPTY
                .with(CORE, new Placement(LEVITATION, 1, 0, 0))
                .with(CORE, new Placement(BATTERY_CELL, 0, 3, 0))
                .with(LIMBS, new Placement(EXOSKELETON_POWER, 0, 0, 0));
        var frame = new ItemStack(VoidArmorContent.VOID_ARMOR);
        frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
        frame.set(RubrumContent.VOID_CHARGE, grids.stats().capacity());
        return frame;
    }

    private static void takesOff(ClientGameTestContext context, TestServerContext server)
    {
        waitFor(context, server, 20, "Worn with wings, the frame should allow flight with no pylon near", minecraft -> player(minecraft).getAbilities().mayfly);
        expectClient(context, "The client should be allowed to fly", minecraft -> minecraft.player.getAbilities().mayfly);
        expect(!server.computeOnServer(minecraft -> player(minecraft).getAbilities().flying), "The wearer should not fly before jumping");

        tapJump(context);
        context.waitTicks(2);
        tapJump(context);
        waitFor(context, server, 10, "A double jump should take off", minecraft -> player(minecraft).getAbilities().flying);
        waitFor(context, server, 5, "The frame should know its wearer flies", minecraft -> LevitationBelts.state(player(minecraft)).flying());
    }

    /**
     * Charge spent over the same ticks, hovering and then standing.
     */
    private static void drains(ClientGameTestContext context, TestServerContext server)
    {
        hover(context, server, Y + 3);
        long before = charge(server);
        context.waitTicks(40);
        long flying = before - charge(server);
        expect(server.computeOnServer(minecraft -> player(minecraft).getAbilities().flying), "The wearer should still be flying");

        land(context, server);
        before = charge(server);
        context.waitTicks(40);
        long standing = before - charge(server);
        expect(flying - standing >= 40 * ArmorBalance.LEVITATION_DRAIN * 9 / 10,
                "Flying should cost the wings' drain on top of standing: flying spent " + flying + ", standing " + standing);
        expect(standing < 40 * ArmorBalance.LEVITATION_DRAIN / 4, "Standing should cost only the frame's hum, spent " + standing);
    }

    /**
     * The wings unfurling, seen from behind, then spread from the back, front and side.
     */
    private static void opens(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        server.runCommand("tp @a " + X + " " + Y + " " + Z + " 180 25");
        context.waitTicks(5);
        expectClient(context, "Grounded, the wings should be folded away", minecraft -> !ArmorWings.of(minecraft.player, 1).shown());
        tapJump(context);
        context.waitTicks(2);
        tapJump(context);
        waitFor(context, server, 10, "A double jump should take off again", minecraft -> player(minecraft).getAbilities().flying);
        look(context, 180, 180);
        for (int tick : new int[] { 3, 6, 9, 13 })
        {
            context.waitTicks(3);
            context.takeScreenshot("armor-wings-opening-" + tick);
        }
        hover(context, server, Y + 4);
        server.runCommand("tp @a " + X + " " + (Y + 4) + " " + Z + " 180 15");
        context.waitTicks(20);
        expectClient(context, "In flight, the wings should be fully open", minecraft -> ArmorWings.of(minecraft.player, 1).open() >= 0.99F);
        look(context, 180, 180);
        context.takeScreenshot("armor-wings-back");
        server.runCommand("tp @a " + X + " " + (Y + 4) + " " + Z + " 180 -20");
        context.waitTicks(3);
        context.takeScreenshot("armor-wings-back-below");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        server.runCommand("tp @a " + X + " " + (Y + 4) + " " + Z + " 180 10");
        context.waitTicks(3);
        look(context, 0, 0);
        context.takeScreenshot("armor-wings-front");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        look(context, 180, 225);
        context.waitTicks(1);
        context.takeScreenshot("armor-wings-side");
        // The wave runs on: a few frames apart
        for (int beat = 1; beat <= 3; beat++)
        {
            look(context, 180, 180);
            context.waitTicks(7);
            context.takeScreenshot("armor-wings-wave-" + beat);
        }
        // By night, where the glow is strongest
        server.runCommand("time set midnight");
        context.waitTicks(3);
        context.takeScreenshot("armor-wings-night");
        server.runCommand("time set noon");

        // Additive and tinted ribbons over the frame: frozen, the camera steps sideways, so passes fighting each other would flicker between frames
        server.runCommand("tick freeze");
        context.waitTicks(3);
        for (int step = 0; step < 6; step++)
        {
            int turn = step;
            look(context, 180, 180 + turn * 2.5F);
            context.waitTicks(1);
            context.takeScreenshot("armor-wings-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
        look(context, 180, 180);
    }

    private static void trails(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        hover(context, server, Y + 4);
        server.runCommand("tp @a " + X + " " + (Y + 4) + " " + Z + " 180 10");
        context.waitTicks(3);
        context.getInput().holdKey(options -> options.keySprint);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(25);
        expectClient(context, "Flying forward fast, the wings should trail", minecraft -> ArmorWings.of(minecraft.player, 1).trail() > 0.4F);
        context.takeScreenshot("armor-wings-trailing");
        context.runOnClient(minecraft -> minecraft.player.setYRot(minecraft.player.getYRot() + 45));
        context.waitTicks(1);
        context.takeScreenshot("armor-wings-trailing-side");
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().releaseKey(options -> options.keySprint);
        look(context, 180, 180);
        // Stopping, the filaments swing on past the frame with their own weight, then settle
        context.waitTicks(4);
        context.takeScreenshot("armor-wings-stopping");
        context.waitTicks(8);
        context.takeScreenshot("armor-wings-settling");
        // A sharp turn in place: the tips lag behind the body and catch up
        look(context, 250, 250);
        context.waitTicks(2);
        context.takeScreenshot("armor-wings-turning");
        look(context, 180, 180);
        context.waitTicks(48);
        expectClient(context, "Stopped, the wings should spread out again", minecraft -> ArmorWings.of(minecraft.player, 1).trail() < 0.15F);
    }

    private static void sneaks(ClientGameTestContext context, TestServerContext server)
    {
        hover(context, server, Y + 6);
        double top = server.computeOnServer(minecraft -> player(minecraft).getY());
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(ArmorBalance.STEP_OUT_HOLD_TICKS + 10);
        context.getInput().releaseKey(options -> options.keyShift);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(ArmorWear.wearing(player), "Sneak held in the air should not step out of the frame");
            expect(player.getY() < top - 1, "Sneak held in the air should sink the wearer, from " + top + " to " + player.getY());
        });
    }

    /**
     * Run low, the frame cuts its wings in flight: the lift fades with them fraying, and once landed they are gone.
     */
    private static void frays(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        server.runCommand("execute as @a run voidheart armor charge " + (ArmorBalance.SHED_LEVITATION + 0.002));
        context.waitTicks(3);
        hover(context, server, Y + 5);
        server.runCommand("tp @a " + X + " " + (Y + 5) + " " + Z + " 180 10");
        waitFor(context, server, 200, () -> "The frame should cut its wings, at stage " + server.computeOnServer(minecraft -> ArmorWear.stage(player(minecraft))),
                minecraft -> ArmorWear.stage(player(minecraft)).compareTo(LoadShedding.Stage.NO_LEVITATION) >= 0);
        waitFor(context, server, 5, "With the wings cut, the lift should fade", minecraft -> LevitationBelts.state(player(minecraft)).fading());
        context.waitTicks(30);
        expectClient(context, "Fading, the wings should fray", minecraft -> ArmorWings.of(minecraft.player, 1).fray() > 0.2F);
        context.takeScreenshot("armor-wings-fraying");
        waitFor(context, server, 200, "The wearer should come down", minecraft -> player(minecraft).onGround());
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(!player.getAbilities().mayfly, "With the wings cut, the frame should not allow flight");
            expect(player.getHealth() == player.getMaxHealth(), "A faded lift and the frame's legs should take the landing");
        });
        context.waitTicks(15);
        expectClient(context, "Landed, the wings should be gone", minecraft -> !ArmorWings.of(minecraft.player, 1).shown());
        server.runCommand("execute as @a run voidheart armor charge 1");
    }

    /**
     * A frame with wings and both sights docked on its base: its screen's stats tablet holds a seventh row for flight, and every row shows.
     */
    private static void tablet(ClientGameTestContext context, TestServerContext server)
    {
        var base = new net.minecraft.core.BlockPos(6, Y, 6);
        server.runCommand("execute as @a run voidheart armor leave");
        context.waitTicks(5);
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @a 6.5 " + Y + " 3.5 0 20");
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseBlock.build(level, base, net.minecraft.core.Direction.NORTH);
            var frame = frame();
            var grids = frame.get(VoidArmorContent.ARMOR_GRIDS)
                    .with(net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.HELM,
                            new Placement(net.voxelindustry.voidheart.common.content.armor.ArmorModule.NIGHT_VISION, 0, 0, 0))
                    .with(net.voxelindustry.voidheart.common.content.armor.ArmorGridKind.HELM,
                            new Placement(net.voxelindustry.voidheart.common.content.armor.ArmorModule.SPECTRAL_LENS, 0, 1, 0));
            frame.set(VoidArmorContent.ARMOR_GRIDS, grids);
            var tile = (net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile) level.getBlockEntity(base);
            tile.dock(frame);
            tile.sync();
        });
        // The screen reads the frame from the client's copy of the base
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(base) instanceof net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile tile
                                     && net.voxelindustry.voidheart.common.content.armor.VoidArmorItem.stats(tile.frame()).levitation(), 40);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var state = level.getBlockState(base);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(base), net.minecraft.core.Direction.NORTH, base, false);
            state.useWithoutItem(level, player(minecraft), hit);
        });
        context.waitFor(minecraft -> minecraft.gui.screen() instanceof net.voxelindustry.voidheart.client.screen.VoidArmorScreen, 40);
        server.runOnServer(minecraft ->
        {
            var stats = net.voxelindustry.voidheart.common.content.armor.VoidArmorItem.stats(
                    ((net.voxelindustry.voidheart.common.content.armor.VoidArmorBaseTile) minecraft.overworld().getBlockEntity(base)).frame());
            expect(stats.levitation() && stats.nightVision() && stats.spectralLens(), "Expected the docked frame to have wings and both sights, got " + stats);
        });
        context.getInput().setCursorPos(10, 10);
        context.waitTicks(3);
        context.takeScreenshot("armor-wings-tablet");
        context.runOnClient(minecraft -> minecraft.player.closeContainer());
        context.waitTicks(3);
    }

    /**
     * Holds the wearer flying at a height, since a teleport can drop flight for a tick.
     */
    private static void hover(ClientGameTestContext context, TestServerContext server, double y)
    {
        server.runCommand("tp @a " + X + " " + y + " " + Z);
        int steady = 0;
        for (int tick = 0; tick < 40 && steady < 5; tick++)
        {
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                if (player.getAbilities().mayfly && !player.getAbilities().flying)
                {
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
            });
            context.waitTick();
            steady = context.computeOnClient(minecraft -> minecraft.player.getAbilities().flying) ? steady + 1 : 0;
        }
        expect(steady >= 5, "The wearer should hover at " + y);
    }

    private static void land(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tp @a " + X + " " + Y + " " + Z);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        });
        waitFor(context, server, 20, "The wearer should stand", minecraft -> player(minecraft).onGround() && !player(minecraft).getAbilities().flying);
    }

    /**
     * Sets the body's facing and the view's on the client, so the wings stand square to the body whatever the camera.
     */
    private static void look(ClientGameTestContext context, float body, float view)
    {
        context.runOnClient(minecraft ->
        {
            var player = minecraft.player;
            player.setYRot(view);
            player.yRotO = view;
            player.setYHeadRot(view);
            player.yHeadRotO = view;
            player.yBodyRot = body;
            player.yBodyRotO = body;
        });
        context.waitTicks(1);
    }

    private static long charge(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> ArmorWear.charge(player(minecraft)));
    }

    private static void tapJump(ClientGameTestContext context)
    {
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyJump);
    }
}
