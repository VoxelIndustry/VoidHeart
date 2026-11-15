package net.voxelindustry.voidheart.gametest;

import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeData;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyes;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * An eye worn in its slot perches on their shoulder, burns a mob hunting them, and once slain grows back and flies home.
 */
public class ShoulderEyeClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");

            // Using the eye, through the use key like a player, puts it in its own head slot, from which it is called
            server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.SHOULDER_EYE)));
            context.waitFor(minecraft -> minecraft.player.getMainHandItem().is(VoidHeartItems.SHOULDER_EYE), 40);
            context.getInput().pressKey(options -> options.keyUse);
            // The client predicts the equip at once, so wait for the server to have handled the use
            for (int i = 0; i < 20 && !server.computeOnServer(minecraft -> worn(minecraft).is(VoidHeartItems.SHOULDER_EYE)); i++)
                context.waitTick();
            server.runOnServer(minecraft ->
            {
                var player = player(minecraft);
                if (!player.getMainHandItem().isEmpty() || !worn(minecraft).is(VoidHeartItems.SHOULDER_EYE))
                    throw new AssertionError("Expected using the eye to put it in its slot, hand holds " + player.getMainHandItem() + ", slot " + worn(minecraft));
            });
            context.waitTicks(60);

            // Hovering the helmet opens the head group of slots, where the eye is worn
            context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            context.waitTicks(3);
            double scale = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScale());
            int helmetX = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledWidth() / 2 - 88 + 16);
            int helmetY = context.computeOnClient(minecraft -> minecraft.getWindow().getGuiScaledHeight() / 2 - 83 + 16);
            context.getInput().setCursorPos(helmetX * scale, helmetY * scale);
            context.waitTicks(3);
            context.takeScreenshot("shoulder-eye-head-slot");
            context.setScreen(() -> null);
            context.waitTicks(2);

            server.runOnServer(minecraft ->
            {
                var eye = eye(minecraft);
                if (!eye.isPerched())
                    throw new AssertionError("Expected the eye to have flown to the shoulder");
                var shoulder = ShoulderEyeEntity.shoulderPosition(player(minecraft), 1);
                if (eye.position().distanceTo(shoulder) > 0.01)
                    throw new AssertionError("Expected the eye on the shoulder at " + shoulder + ", got " + eye.position());
            });
            if (context.computeOnClient(minecraft -> clientEyes(minecraft).size()) != 1)
                throw new AssertionError("Expected the client to see one eye");

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("shoulder-eye-perched-back");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("shoulder-eye-perched-front");

            // Hidden from its owner in first person, it must not hide the water behind where it sits.
            // It perches at eye height, so the player stands in a pit walled with barriers to have water level with the eye all around.
            // Its body lags the head, so a head just turned toward the eye's shoulder brings it into view.
            server.runCommand("fill -8 " + (GROUND - 1) + " -8 8 " + GROUND + " 8 water");
            server.runCommand("fill -1 " + (GROUND - 1) + " -1 1 " + GROUND + " 1 barrier");
            server.runCommand("fill 0 " + (GROUND - 1) + " 0 0 " + GROUND + " 0 air");
            server.runCommand("tp @a 0.5 " + (GROUND - 1) + " 0.5 0 0");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(10);
            context.runOnClient(minecraft ->
            {
                var player = minecraft.player;
                player.setYRot(-60);
                player.setYHeadRot(-60);
                player.setXRot(0);
                player.yBodyRot = 0;
                player.yBodyRotO = 0;
            });
            context.takeScreenshot("shoulder-eye-first-person-water");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);
            context.takeScreenshot("shoulder-eye-first-person-water-pose");
            server.runCommand("fill -8 " + (GROUND - 1) + " -8 8 " + (GROUND - 1) + " 8 dirt");
            server.runCommand("fill -8 " + GROUND + " -8 8 " + GROUND + " 8 grass_block");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");
            context.waitTicks(5);

            server.runCommand("summon husk 3.5 " + (GROUND + 1) + " 7.5 {NoAI:1b,PersistenceRequired:1b}");
            server.runOnServer(minecraft -> husk(minecraft).setTarget(player(minecraft)));
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            float huskHealth = server.computeOnServer(minecraft -> husk(minecraft).getHealth());

            waitForBeam(context, server);
            context.waitTicks(6);
            context.takeScreenshot("shoulder-eye-charging");
            context.waitTicks(ShoulderEyeEntity.CHARGE_TICKS - 5);
            context.takeScreenshot("shoulder-eye-firing");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(30);
            context.takeScreenshot("shoulder-eye-firing-first-person");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            float hurtHealth = server.computeOnServer(minecraft -> husk(minecraft).getHealth());
            if (hurtHealth >= huskHealth)
                throw new AssertionError("Expected the husk hunting the player to be burnt, health " + huskHealth + " -> " + hurtHealth);

            server.runCommand("kill @e[type=husk]");
            server.runOnServer(minecraft ->
            {
                var eye = eye(minecraft);
                eye.hurtServer(minecraft.overworld(), eye.damageSources().generic(), 100);
                var data = player(minecraft).getAttached(ShoulderEyes.DATA);
                if (data == null || !data.isRegenerating())
                    throw new AssertionError("Expected the slain eye to start growing back, got " + data);
            });
            context.waitTicks(30);
            server.runOnServer(minecraft ->
            {
                if (!eyes(minecraft).isEmpty())
                    throw new AssertionError("Expected no eye while it grows back");
                // Skip most of the minute it takes
                player(minecraft).setAttached(ShoulderEyes.DATA, new ShoulderEyeData(ShoulderEyeEntity.MAX_HEALTH, 10));
            });

            context.waitTicks(16);
            server.runOnServer(minecraft ->
            {
                var eye = eye(minecraft);
                if (eye.isPerched())
                    throw new AssertionError("Expected the regrown eye to fly back rather than appear on the shoulder");
                if (eye.getHealth() != ShoulderEyeEntity.MAX_HEALTH)
                    throw new AssertionError("Expected the regrown eye at full health, got " + eye.getHealth());
            });
            context.takeScreenshot("shoulder-eye-returning");
            context.waitTicks(60);
            server.runOnServer(minecraft ->
            {
                if (!eye(minecraft).isPerched())
                    throw new AssertionError("Expected the regrown eye back on the shoulder");
                eye(minecraft).setHealth(5);
            });
            context.waitTick();

            // Taken out of its slot, the eye leaves, but its wounds are kept for when it is worn again
            server.runOnServer(minecraft -> slot(minecraft).set(ItemStack.EMPTY));
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                if (!eyes(minecraft).isEmpty())
                    throw new AssertionError("Expected the eye gone once taken out of its slot");
                slot(minecraft).set(new ItemStack(VoidHeartItems.SHOULDER_EYE));
            });
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                float health = eye(minecraft).getHealth();
                if (health > 6)
                    throw new AssertionError("Expected the eye worn again to keep its wounds, got health " + health);
            });
        }
    }

    private static void waitForBeam(ClientGameTestContext context, TestServerContext server)
    {
        for (int i = 0; i < 40; i++)
        {
            if (server.computeOnServer(minecraft -> eye(minecraft).getBeamTarget() != null))
                return;
            context.waitTick();
        }
        throw new AssertionError("Expected the eye to fire at the husk hunting the player");
    }

    private static TrinketSlotAccess slot(MinecraftServer minecraft)
    {
        return TrinketsApi.getAttachment(player(minecraft)).getInventory(ShoulderEyes.SLOT).getOrCreateSlotAccess(0);
    }

    private static ItemStack worn(MinecraftServer minecraft)
    {
        return slot(minecraft).get();
    }

    private static List<ShoulderEyeEntity> eyes(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntitiesOfClass(ShoulderEyeEntity.class, new AABB(player(minecraft).blockPosition()).inflate(32), LivingEntity::isAlive);
    }

    private static ShoulderEyeEntity eye(MinecraftServer minecraft)
    {
        var eyes = eyes(minecraft);
        if (eyes.size() != 1)
            throw new AssertionError("Expected one living eye, got " + eyes.size());
        return eyes.getFirst();
    }

    private static Husk husk(MinecraftServer minecraft)
    {
        return minecraft.overworld().getEntitiesOfClass(Husk.class, new AABB(player(minecraft).blockPosition()).inflate(32)).getFirst();
    }

    private static List<ShoulderEyeEntity> clientEyes(Minecraft minecraft)
    {
        return minecraft.level.getEntitiesOfClass(ShoulderEyeEntity.class, minecraft.player.getBoundingBox().inflate(32));
    }
}
