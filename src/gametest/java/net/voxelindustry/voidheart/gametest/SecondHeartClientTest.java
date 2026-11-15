package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.secondheart.HeartRules;
import net.voxelindustry.voidheart.common.content.secondheart.HeartShown;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A second heart saves its player from a killing blow: they dissolve, reform where they fell at full health and knock back what is around them.
 * Its life then comes back after a recharge, a held totem goes first, a spent heart lets the player die, and a fall out of the world reforms them
 * on the last safe ground.
 */
public class SecondHeartClientTest implements FabricClientGameTest
{
    private static final double X = 0.5, Z = 0.5;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            server.runCommand("tp @a " + X + " " + FLAT_FEET + " " + Z + " 180 10");
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
                player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.GOLDEN_LEGGINGS));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
                player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
                SecondHeart.grant(player, true);
            });
            context.waitTicks(20);

            expectClient(context, "Expected the client to show a ready heart",
                    minecraft -> minecraft.player.getAttachedOrElse(SecondHeart.SHOWN, HeartShown.NONE) == HeartShown.READY);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.takeScreenshot("second-heart-icon-ready");

            lethalHitDissolvesAndReforms(context, server);
            spentHeartLetsThePlayerDie(context, server);
            rechargeBringsTheLifeBack(context, server);
            totemGoesFirst(context, server);
            dyingViewDarkens(context, server);
            voidFallReformsOnSafeGround(context, server);

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    private static void lethalHitDissolvesAndReforms(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        // A husk close in front, hunting the player
        server.runOnServer(minecraft ->
        {
            var husk = spawn(minecraft, EntityTypes.HUSK, X - 1.6, FLAT_FEET, Z);
            husk.setTarget(player(minecraft));
        });
        context.waitTicks(5);
        context.getInput().pressKey(InputConstants.KEY_F1);

        double huskBefore = server.computeOnServer(minecraft -> first(minecraft, EntityTypes.HUSK).distanceTo(player(minecraft)));
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var husk = first(minecraft, EntityTypes.HUSK);
            player.hurtServer(player.level(), player.damageSources().mobAttack(husk), 1000);
        });
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.isAlive() && !player.isDeadOrDying(), "Expected the heart to keep the player alive, health " + player.getHealth());
            expect(SecondHeart.sequence(player) != null, "Expected the heart to have fired");
            expect(first(minecraft, EntityTypes.HUSK).getTarget() == null, "Expected the husk to drop the dissolving player as its target");
            expect(SecondHeart.state(player).recharge() == HeartRules.RECHARGE_TICKS, "Expected the recharge to start at once");
        });

        // Unhurtable while it dissolves
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.hurtServer(player.level(), player.damageSources().generic(), 1000);
            expect(player.isAlive() && SecondHeart.sequence(player) != null, "Expected no harm while dissolving");
        });

        for (int tick : new int[]{6, 14, 24, 36, 46, 56})
        {
            waitForElapsed(context, server, tick);
            context.takeScreenshot("second-heart-dissolve-" + tick);
        }
        waitForElapsed(context, server, HeartRules.BURST_TICK + 2);
        context.takeScreenshot("second-heart-burst");
        waitForElapsed(context, server, HeartRules.BURST_TICK + 6);
        context.takeScreenshot("second-heart-burst-late");

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.getHealth() == player.getMaxHealth(), "Expected full health after the reform, got " + player.getHealth());
            double huskAfter = first(minecraft, EntityTypes.HUSK).distanceTo(player);
            expect(huskAfter > huskBefore + 0.8, "Expected the burst to push the husk away, " + huskBefore + " to " + huskAfter);
            expect(Math.abs(player.getX() - X) < 0.1 && Math.abs(player.getZ() - Z) < 0.1, "Expected the reform where the player fell, at " + player.position());
            // Grace: a hit right after the reform does nothing
            player.hurtServer(player.level(), player.damageSources().generic(), 5);
            expect(player.getHealth() == player.getMaxHealth(), "Expected grace after the reform");
        });
        waitFor(context, server, HeartRules.GRACE_TICKS + 20, "Expected the sequence to end", minecraft -> SecondHeart.sequence(player(minecraft)) == null);
        expectClient(context, "Expected the client to show a hollow heart while recharging",
                minecraft -> minecraft.player.getAttachedOrElse(SecondHeart.SHOWN, HeartShown.NONE) == HeartShown.HOLLOW);
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(2);
        context.takeScreenshot("second-heart-icon-hollow");
        clearScene(server);
    }

    private static void spentHeartLetsThePlayerDie(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamerule immediate_respawn true");
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.hurtServer(player.level(), player.damageSources().generic(), 1000);
            expect(player.isDeadOrDying(), "Expected a spent heart to let the player die");
        });
        waitFor(context, server, 100, "Expected the player to respawn", minecraft -> player(minecraft).isAlive());
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var state = SecondHeart.state(player(minecraft));
            expect(state.owned() && state.recharge() > 0, "Expected the heart kept through death and still recharging, got " + state);
        });
        server.runCommand("tp @a " + X + " " + FLAT_FEET + " " + Z + " 180 10");
    }

    private static void rechargeBringsTheLifeBack(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart secondheart recharge 2");
        waitFor(context, server, 60, "Expected the heart back after its recharge", minecraft -> SecondHeart.state(player(minecraft)).ready());
        context.waitFor(minecraft -> minecraft.player.getAttachedOrElse(SecondHeart.SHOWN, HeartShown.NONE) == HeartShown.READY, 20);
        context.takeScreenshot("second-heart-icon-flash");
    }

    private static void totemGoesFirst(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.hurtServer(player.level(), player.damageSources().generic(), 1000);
            expect(player.isAlive(), "Expected the totem to save the player");
            expect(player.getOffhandItem().isEmpty(), "Expected the totem to be used up");
            expect(SecondHeart.sequence(player) == null && SecondHeart.state(player).ready(), "Expected the heart to keep its charge behind a totem");
            heal(minecraft);
            player.removeAllEffects();
        });
        context.waitTicks(40);
    }

    private static void dyingViewDarkens(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("execute as @a run voidheart secondheart fire");
        for (int tick : new int[]{10, 22, 34, 50})
        {
            waitForElapsed(context, server, tick);
            context.takeScreenshot("second-heart-view-" + tick);
        }
        waitFor(context, server, HeartRules.SEQUENCE_TICKS, "Expected the sequence to end", minecraft -> SecondHeart.sequence(player(minecraft)) == null);
    }

    private static void voidFallReformsOnSafeGround(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart secondheart grant");
        server.runCommand("tp @a " + X + " " + FLAT_FEET + " " + Z);
        context.waitTicks(20);
        server.runCommand("tp @a " + X + " -200 " + Z);
        waitFor(context, server, 200, "Expected the void to fire the heart", minecraft -> SecondHeart.sequence(player(minecraft)) != null);
        waitFor(context, server, HeartRules.BURST_TICK + 5, "Expected the reform", minecraft -> SecondHeart.elapsed(player(minecraft), 0) > HeartRules.BURST_TICK);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            expect(player.isAlive(), "Expected the player alive after the void");
            expect(Math.abs(player.getY() - FLAT_FEET) < 0.6, "Expected the reform on the last safe ground, at " + player.position());
        });
        waitFor(context, server, HeartRules.SEQUENCE_TICKS, "Expected the sequence to end", minecraft -> SecondHeart.sequence(player(minecraft)) == null);
    }

    private static void waitForElapsed(ClientGameTestContext context, TestServerContext server, int ticks)
    {
        waitFor(context, server, HeartRules.SEQUENCE_TICKS + 20, "Expected the sequence to reach tick " + ticks,
                minecraft -> SecondHeart.elapsed(player(minecraft), 0) >= ticks);
        // The client draws a tick behind
        context.waitTicks(1);
    }
}
