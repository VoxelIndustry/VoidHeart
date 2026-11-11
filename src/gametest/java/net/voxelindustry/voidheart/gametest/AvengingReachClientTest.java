package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.client.render.AvengingReachRender;
import net.voxelindustry.voidheart.common.content.avengingreach.AvengingReach;
import net.voxelindustry.voidheart.common.content.avengingreach.ReachArms;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.ArrayList;
import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The Avenging Reach: arms are grown one at a time in order, each answers one blow from within reach with an iron sword's hit and a shove, then
 * recharges, and a blow from farther away goes unanswered.
 */
public class AvengingReachClientTest implements FabricClientGameTest
{
    private static final double PLAYER_X = 0.5;
    private static final double PLAYER_Z = 0.5;
    // Ahead and to the right of the player, so the camera behind them sees the arm reach it
    private static final double HUSK_X   = PLAYER_X - 1.5;
    private static final double HUSK_Z   = PLAYER_Z + 1.7;
    // An iron sword's 6, less what a husk's two points of armor turn aside
    private static final float MIN_DAMAGE = 5.5F;
    private static final float MAX_DAMAGE = 6.01F;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            clearArea(server, 0);
            // Facing south, toward the husks
            server.runCommand("tp @a " + PLAYER_X + " " + FLAT_FEET + " " + PLAYER_Z + " 0 15");
            // The scripted blows would otherwise push the player out of reach
            server.runCommand("attribute @p minecraft:knockback_resistance base set 1");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.getInput().pressKey(InputConstants.KEY_F1);

            grafts(server);

            var husk = server.computeOnServer(minecraft -> spawnHusk(minecraft, HUSK_X, HUSK_Z).getUUID());
            context.waitTicks(10);

            // One arm answers a blow from within reach
            float before = server.computeOnServer(minecraft -> health(minecraft, husk));
            hit(server, husk, 2);
            waitForLash(context);
            // The lash up to the blow, a frame every couple of ticks, for judging the motion
            for (int frame = 0; frame < ReachArms.IMPACT_TICK - 1; frame++)
            {
                context.takeScreenshot("avenging-reach-frame-" + frame);
                context.waitTick();
            }
            waitFor(context, server, ReachArms.IMPACT_TICK + 4, "Expected the arm to strike the husk", minecraft -> health(minecraft, husk) < before);
            server.runOnServer(minecraft ->
            {
                var hurt = (Husk) minecraft.overworld().getEntity(husk);
                float dealt = before - hurt.getHealth();
                expect(dealt >= MIN_DAMAGE && dealt <= MAX_DAMAGE, "Expected the arm to hit as an iron sword does, dealt " + dealt);
                // Pushed away from the player
                var away = new net.minecraft.world.phys.Vec3(HUSK_X - PLAYER_X, 0, HUSK_Z - PLAYER_Z).normalize();
                double thrown = hurt.getDeltaMovement().dot(away);
                expect(thrown > 0.3, "Expected the husk thrown back, moving " + hurt.getDeltaMovement());
                hurt.setHealth(hurt.getMaxHealth());
            });
            context.takeScreenshot("avenging-reach-impact");
            context.waitTicks(4);
            context.takeScreenshot("avenging-reach-retract");
            server.runOnServer(minecraft ->
            {
                var hurt = minecraft.overworld().getEntity(husk);
                hurt.setDeltaMovement(0, 0, 0);
            });
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                var hurt = minecraft.overworld().getEntity(husk);
                hurt.setDeltaMovement(0, 0, 0);
                hurt.snapTo(HUSK_X, FLAT_FEET, HUSK_Z, hurt.getYRot(), 0);
            });

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            // Still recharging: the next blow goes unanswered
            context.waitTicks(12);
            hit(server, husk, 2);
            context.waitTicks(ReachArms.IMPACT_TICK + 4);
            expect(server.computeOnServer(minecraft -> health(minecraft, husk) == 20), "Expected the only arm to be recharging");

            // Recharged, it answers again; seen from the player's eyes this time
            context.waitTicks(3 * 20);
            hit(server, husk, 2);
            context.waitTicks(3);
            context.takeScreenshot("avenging-reach-first-person-lash");
            waitFor(context, server, ReachArms.IMPACT_TICK + 4, "Expected the recharged arm to strike again", minecraft -> health(minecraft, husk) < 20);
            context.takeScreenshot("avenging-reach-first-person-impact");
            context.waitTicks(3);
            context.takeScreenshot("avenging-reach-first-person-retract");
            server.runOnServer(minecraft -> ((Husk) minecraft.overworld().getEntity(husk)).setHealth(20));
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            // Beyond 3 blocks, nothing reaches
            context.waitTicks(3 * 20 + 2);
            server.runOnServer(minecraft -> minecraft.overworld().getEntity(husk).snapTo(PLAYER_X, FLAT_FEET, PLAYER_Z + 4.5, 180, 0));
            context.waitTicks(2);
            hit(server, husk, 2);
            context.waitTicks(ReachArms.IMPACT_TICK + 4);
            expect(server.computeOnServer(minecraft -> health(minecraft, husk) == 20), "Expected a husk 4 blocks away to be out of reach");
            expectClient(context, "Expected no lash toward a husk out of reach", minecraft -> AvengingReachRender.activeLashes() == 0);
            server.runCommand("kill @e[type=husk]");

            fourArms(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
        }
    }

    /**
     * Each graft is refused out of turn and kept, and grows its arm in turn.
     */
    private static void grafts(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            var second = new ItemStack(VoidHeartItems.AVENGING_REACH[1]);
            second.finishUsingItem(player.level(), player);
            expect(AvengingReach.armsOf(player) == 0 && second.getCount() == 1, "Expected the second arm refused and kept before the first");

            var first = new ItemStack(VoidHeartItems.AVENGING_REACH[0]);
            first.finishUsingItem(player.level(), player);
            expect(AvengingReach.armsOf(player) == 1 && first.isEmpty(), "Expected the first arm grown and its graft eaten");

            var again = new ItemStack(VoidHeartItems.AVENGING_REACH[0]);
            again.finishUsingItem(player.level(), player);
            expect(AvengingReach.armsOf(player) == 1 && again.getCount() == 1, "Expected the first graft refused once its arm has grown");

            player.removeAllEffects();
            for (int arm = 0; arm < 4; arm++)
                player.getInventory().setItem(arm, new ItemStack(VoidHeartItems.AVENGING_REACH[arm]));
        });
    }

    /**
     * Four husks strike in the same tick: each of four arms answers one, and a fifth blow before they recharge goes unanswered.
     */
    private static void fourArms(ClientGameTestContext context, TestServerContext server)
    {
        // The later grafts each grow their arm in turn, the fourth refused once all four have grown
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            for (int arm = 1; arm < ReachArms.MAX_ARMS; arm++)
            {
                var graft = new ItemStack(VoidHeartItems.AVENGING_REACH[arm]);
                graft.finishUsingItem(player.level(), player);
                expect(AvengingReach.armsOf(player) == arm + 1 && graft.isEmpty(), "Expected graft " + (arm + 1) + " to grow its arm");
            }
            var extra = new ItemStack(VoidHeartItems.AVENGING_REACH[ReachArms.MAX_ARMS - 1]);
            extra.finishUsingItem(player.level(), player);
            expect(AvengingReach.armsOf(player) == ReachArms.MAX_ARMS && extra.getCount() == 1, "Expected a fifth arm refused");
            player.removeAllEffects();
        });
        context.waitTicks(3 * 20 + 2);
        var husks = server.computeOnServer(minecraft ->
        {
            heal(minecraft);
            List<java.util.UUID> spawned = new ArrayList<>();
            spawned.add(spawnHusk(minecraft, PLAYER_X - 1.6, PLAYER_Z + 1.6).getUUID());
            spawned.add(spawnHusk(minecraft, PLAYER_X + 1.6, PLAYER_Z + 1.6).getUUID());
            spawned.add(spawnHusk(minecraft, PLAYER_X + 2.2, PLAYER_Z - 0.4).getUUID());
            spawned.add(spawnHusk(minecraft, PLAYER_X - 2.2, PLAYER_Z - 0.4).getUUID());
            spawned.add(spawnHusk(minecraft, PLAYER_X, PLAYER_Z + 2.4).getUUID());
            return spawned;
        });
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            // Each blow harder than the last, so the hurt cooldown lets it through
            for (int i = 0; i < husks.size(); i++)
            {
                var husk = (Husk) minecraft.overworld().getEntity(husks.get(i));
                player.hurtServer(minecraft.overworld(), player.damageSources().mobAttack(husk), 1 + i);
            }
            heal(minecraft);
        });
        context.waitTicks(ReachArms.IMPACT_TICK - 2);
        context.takeScreenshot("avenging-reach-four-arms-lash");
        context.waitTicks(2);
        context.takeScreenshot("avenging-reach-four-arms-impact");
        context.waitTicks(4);
        server.runOnServer(minecraft ->
        {
            for (int i = 0; i < husks.size(); i++)
            {
                float health = health(minecraft, husks.get(i));
                if (i < ReachArms.MAX_ARMS)
                    expect(health < 20, "Expected arm " + (i + 1) + " to strike husk " + i);
                else
                    expect(health == 20, "Expected the fifth blow unanswered with all four arms recharging, health " + health);
            }
        });
        server.runCommand("kill @e[type=husk]");
    }

    private static void waitForLash(ClientGameTestContext context)
    {
        for (int i = 0; i < 5; i++)
        {
            if (context.computeOnClient(minecraft -> AvengingReachRender.activeLashes() > 0))
                return;
            context.waitTick();
        }
        throw new AssertionError("Expected the client to draw a lash after the blow");
    }

    private static Husk spawnHusk(MinecraftServer minecraft, double x, double z)
    {
        var husk = spawn(minecraft, EntityTypes.HUSK, x, FLAT_FEET, z);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        float yaw = (float) Math.toDegrees(Math.atan2(x - PLAYER_X, -(z - PLAYER_Z)));
        husk.snapTo(x, FLAT_FEET, z, yaw, 0);
        husk.setYBodyRot(yaw);
        husk.setYHeadRot(yaw);
        return husk;
    }

    private static float health(MinecraftServer minecraft, java.util.UUID husk)
    {
        return ((Husk) minecraft.overworld().getEntity(husk)).getHealth();
    }

    private static void hit(TestServerContext server, java.util.UUID husk, float damage)
    {
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            heal(minecraft);
            player.setInvulnerableTime(0);
            player.hurtServer(minecraft.overworld(), player.damageSources().mobAttack((Husk) minecraft.overworld().getEntity(husk)), damage);
        });
    }
}
