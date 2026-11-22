package net.voxelindustry.voidheart.gametest;

import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBalance;
import net.voxelindustry.voidheart.common.content.levitation.LevitationBelts;
import net.voxelindustry.voidheart.common.energy.grid.AzuriumPylonTile;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Levitation Belt worn in its slot:
 * <ul>
 *     <li>Using it puts it in the belt slot, and its jumps clear far more than a plain one.</li>
 *     <li>Outside any field it never gives flight. Inside the fields of a chain of pylons fed by a creative well far away, it does, and flying
 *     draws through that pylon.</li>
 *     <li>Flying out of the field, the lift fades: a drift, then a fall that hurts from high up, and none from low.</li>
 *     <li>Flying back in while it fades catches the wearer.</li>
 * </ul>
 */
public class LevitationBeltClientTest implements FabricClientGameTest
{
    private static final int Y = FLAT_FEET;

    // A creative well, whose own reach ends at x 16, feeds a small pylon, then a large one whose field covers the ground where the flying starts, then
    // a large one on a tall pillar whose field hangs in the air. A field is a ball, so a wearer leaving one above its middle falls back into it and is
    // caught: only leaving the high field below its middle lets them fall all the way
    private static final BlockPos WELL    = new BlockPos(0, Y, 10);
    private static final BlockPos CHAIN_1 = new BlockPos(2, Y, 10);
    private static final BlockPos CHAIN_2 = new BlockPos(13, Y, 10);
    private static final BlockPos CHAIN_3 = new BlockPos(24, Y + 29, 10);

    // Inside the last field, a few blocks from its bundle
    private static final double FIELD_X = 26.5;
    private static final double FIELD_Z = 13.5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            tp(server, 60.5, Y, 40.5);
            context.waitTicks(5);

            equip(context, server);
            jumps(context, server);
            noFlightWithoutField(context, server);

            buildChain(context, server);
            fliesInField(context, server);
            looks(context, server);
            fadesOutOfField(context, server, Y + 24, true);
            fadesOutOfField(context, server, Y + 4, false);
            caughtAgain(context, server);

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static void equip(ClientGameTestContext context, TestServerContext server)
    {
        // Using the belt, through the use key like a player, puts it in its slot
        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.LEVITATION_BELT)));
        context.waitFor(minecraft -> minecraft.player.getMainHandItem().is(VoidHeartItems.LEVITATION_BELT), 40);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        context.takeScreenshot("levitation-belt-in-hand");
        context.getInput().pressKey(options -> options.keyUse);
        waitFor(context, server, 20, "Using the belt should put it in its slot", minecraft -> worn(minecraft).is(VoidHeartItems.LEVITATION_BELT));
        server.runOnServer(minecraft -> expect(player(minecraft).getMainHandItem().isEmpty(), "The belt should have left the hand"));

        // Hovering the leggings opens the legs group, where the belt is worn
        context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
        context.waitTicks(3);
        double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
        int legsX = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledWidth() / 2 - 88 + 16);
        int legsY = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledHeight() / 2 - 83 + 16 + 36);
        context.getInput().setCursorPos(legsX * scale, legsY * scale);
        context.waitTicks(3);
        context.takeScreenshot("levitation-belt-slot");
        context.setScreen(() -> null);
        context.waitTicks(2);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expectNear(player.getAttributeValue(Attributes.JUMP_STRENGTH), 0.42 + LevitationBalance.JUMP_BONUS, "jump strength");
            expectNear(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE), 3 + LevitationBalance.SAFE_FALL_BONUS, "safe fall distance");
            expectNear(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER), LevitationBalance.FALL_DAMAGE_KEPT, "fall damage multiplier");
        });
    }

    private static void jumps(ClientGameTestContext context, TestServerContext server)
    {
        double ground = context.computeOnClient(minecraft -> minecraft.player.getY());
        // Held for a few ticks, as a tap is, and let go before landing so it jumps once
        context.getInput().holdKey(options -> options.keyJump);
        double highest = ground;
        for (int tick = 0; tick < 30; tick++)
        {
            context.waitTick();
            if (tick == 3)
                context.getInput().releaseKey(options -> options.keyJump);
            highest = Math.max(highest, context.computeOnClient(minecraft -> minecraft.player.getY()));
        }
        double height = highest - ground;
        expect(height > 2.5 && height < 3.5, "A belted jump should rise about 2.9 blocks, rose " + height);
        context.waitTicks(10);
        server.runOnServer(minecraft -> expect(player(minecraft).getHealth() == player(minecraft).getMaxHealth(), "Landing a belted jump should not hurt"));
    }

    private static void noFlightWithoutField(ClientGameTestContext context, TestServerContext server)
    {
        context.waitTicks(LevitationBalance.FIELD_CHECK_TICKS * 2);
        server.runOnServer(minecraft -> expect(!player(minecraft).getAbilities().mayfly, "No field around, the belt should not give flight"));
    }

    private static void buildChain(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + at(WELL) + " voidheart:creative_void_well");
        server.runCommand("fill " + CHAIN_3.getX() + " " + Y + " " + CHAIN_3.getZ() + " " + CHAIN_3.getX() + " " + (CHAIN_3.getY() - 1) + " " + CHAIN_3.getZ() + " stone");
        for (var pylon : new BlockPos[] { CHAIN_1, CHAIN_2, CHAIN_3 })
        {
            server.runCommand("setblock " + at(pylon) + " voidheart:azurium_pylon");
            server.runCommand("setblock " + at(pylon.above()) + " voidheart:" + (pylon == CHAIN_1 ? "small" : "large") + "_azurium_bundle[facing=up]");
        }
        context.waitTicks(60);
    }

    private static void fliesInField(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("tp @a " + FIELD_X + " " + Y + " " + FIELD_Z + " 150 10");
        waitFor(context, server, 30, "Inside the pylon's field the belt should give flight", minecraft -> player(minecraft).getAbilities().mayfly);
        expectClient(context, "The client should be allowed to fly", minecraft -> minecraft.player.getAbilities().mayfly);

        // Nothing is drawn while standing
        context.waitTicks(30);
        expect(throughput(server) == 0, "Standing in the field should draw nothing, the pylon passes " + throughput(server));

        fly(context, server, FIELD_X, Y + 2, FIELD_Z);
        context.waitTicks(40);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getAbilities().flying, "The wearer should still be flying");
            expect(LevitationBelts.state(player).flying(), "The belt should know its wearer flies");
            expect(Math.abs(player.getY() - (Y + 2)) < 0.5, "A flying wearer should hover, is at " + player.getY());
        });
        long passed = throughput(server);
        expect(passed >= LevitationBalance.FLIGHT_COST / 2, "Flying should draw through the pylon, it passes " + passed);
        expectClient(context, "The client should see the belt flying, for its motes", minecraft -> LevitationBelts.state(minecraft.player).flying());
    }

    private static void looks(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("levitation-belt-flying-front");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(5);
        context.takeScreenshot("levitation-belt-flying-back");

        // Over armor, the band shows outside the chestplate and leggings
        server.runCommand("item replace entity @a armor.chest with iron_chestplate");
        server.runCommand("item replace entity @a armor.legs with iron_leggings");
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(10);
        context.takeScreenshot("levitation-belt-over-armor-front");
        server.runCommand("item replace entity @a armor.chest with air");
        server.runCommand("item replace entity @a armor.legs with air");
        context.waitTicks(2);
    }

    /**
     * Flies out of the field at a height: flight is taken back, the lift fades from a drift to a fall, and only a fall from high up hurts.
     */
    private static void fadesOutOfField(ClientGameTestContext context, TestServerContext server, double height, boolean hurts)
    {
        server.runOnServer(GameTestKit::heal);
        flyOut(context, server, height);
        server.runOnServer(minecraft -> expect(!player(minecraft).getAbilities().mayfly, "Out of the field, the belt should take flight back"));

        double start = context.computeOnClient(minecraft -> minecraft.player.getY());
        context.waitTicks(20);
        double drift = start - context.computeOnClient(minecraft -> minecraft.player.getY());
        expect(drift > 0.1 && drift < 1.5, "The first second should be a slow drift, sank " + drift);
        if (hurts)
            context.takeScreenshot("levitation-belt-fading");

        waitFor(context, server, 200, () -> "The wearer should come down to the ground, is at " + server.computeOnServer(minecraft -> player(minecraft).position()),
                minecraft -> player(minecraft).onGround());
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            float lost = player.getMaxHealth() - player.getHealth();
            if (hurts)
                expect(lost >= 1 && lost <= 8, "A fall from " + (height - Y) + " blocks should hurt a little, through the belt, lost " + lost);
            else
                expect(lost == 0, "A drift from " + (height - Y) + " blocks should not hurt, lost " + lost);
            expect(!LevitationBelts.state(player).fading(), "Landing should end the fade");
            heal(minecraft);
        });
    }

    private static void caughtAgain(ClientGameTestContext context, TestServerContext server)
    {
        // Near the top of the high field, where the ball is narrow: drifting down, the wearer comes back into it below. A teleport can't stand in for
        // this, since the command puts the player on the ground, which ends the fade
        flyOut(context, server, CHAIN_3.getY() + 15);
        // Steering back west through the air as it drifts down
        context.getInput().holdKey(options -> options.keyDown);
        waitFor(context, server, 100, () -> server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            return "Back in the field, the belt should catch its wearer, at " + player.position() + " on ground " + player.onGround() + ", may fly "
                   + player.getAbilities().mayfly + ", belt " + LevitationBelts.state(player);
        }), minecraft -> player(minecraft).getAbilities().flying);
        context.getInput().releaseKey(options -> options.keyDown);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(!LevitationBelts.state(player).fading(), "A caught wearer should no longer fade");
            expect(player.getY() > CHAIN_3.getY(), "The wearer should be caught in the air, is at " + player.getY());
        });
    }

    /**
     * Flies east at a height until the field is behind, as a player would: a teleport ends creative flight before the server sees the wearer leave.
     */
    private static void flyOut(ClientGameTestContext context, TestServerContext server, double height)
    {
        fly(context, server, FIELD_X, height, FIELD_Z);
        context.getInput().holdKey(options -> options.keyUp);
        waitFor(context, server, 80, () -> server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            return "Flying out of the field at " + (height - Y) + " should start the lift fading, at " + player.position() + " flying "
                   + player.getAbilities().flying + ", may fly " + player.getAbilities().mayfly + ", belt " + LevitationBelts.state(player);
        }), minecraft -> LevitationBelts.state(player(minecraft)).fading());
        context.getInput().releaseKey(options -> options.keyUp);
    }

    /**
     * Puts the player in the air in the field, facing east, and has them fly there once the belt allows it.
     */
    private static void fly(ClientGameTestContext context, TestServerContext server, double x, double y, double z)
    {
        server.runCommand("tp @a " + x + " " + y + " " + z + " -90 10");
        waitFor(context, server, 30, "Inside the field the belt should allow flight", minecraft -> player(minecraft).getAbilities().mayfly);
        // The client can still drop flight in the ticks after a teleport, so keep asking until it has flown steadily for a few ticks
        int steady = 0;
        for (int tick = 0; tick < 40 && steady < 5; tick++)
        {
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                if (!player.getAbilities().flying)
                {
                    player.getAbilities().flying = true;
                    player.onUpdateAbilities();
                }
            });
            context.waitTick();
            steady = context.computeOnClient(minecraft -> minecraft.player.getAbilities().flying) ? steady + 1 : 0;
        }
        expect(steady >= 5, "The wearer should fly steadily in the field");
    }

    private static long throughput(TestServerContext server)
    {
        return server.computeOnServer(minecraft -> ((AzuriumPylonTile) minecraft.overworld().getBlockEntity(CHAIN_2)).throughput());
    }

    private static ItemStack worn(MinecraftServer minecraft)
    {
        return TrinketsApi.getAttachment(player(minecraft)).getInventory(LevitationBelts.SLOT).getItem(0);
    }

    private static void expectNear(double actual, double expected, String what)
    {
        expect(Math.abs(actual - expected) < 1e-6, "Expected " + what + " " + expected + ", got " + actual);
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
