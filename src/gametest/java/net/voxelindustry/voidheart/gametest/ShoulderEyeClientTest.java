package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.phys.AABB;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeData;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyeEntity;
import net.voxelindustry.voidheart.common.content.shouldereye.ShoulderEyes;

import java.util.List;

/**
 * An eye bound to the player perches on their shoulder, burns a mob hunting them, and once slain grows back and flies home.
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
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 1) + " 0.5 0 10");

            server.runOnServer(minecraft -> ShoulderEyes.bind(player(minecraft)));
            context.waitTicks(60);

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

    private static ServerPlayer player(MinecraftServer minecraft)
    {
        return minecraft.getPlayerList().getPlayers().getFirst();
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
