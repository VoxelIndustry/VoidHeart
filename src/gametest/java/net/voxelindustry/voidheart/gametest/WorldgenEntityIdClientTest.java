package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * Mobs placed at generation are built on worldgen threads and ask the level for an id there, while the server thread keeps adding and removing tracked entities. Asking must not race that.
 */
public class WorldgenEntityIdClientTest implements FabricClientGameTest
{
    // Far from the player, so the churned entities are tracked without being sent anywhere
    private static final BlockPos CHURN = new BlockPos(4000, 0, 4000);
    private static final int ROUNDS = 40;
    private static final int ENTITIES = 1500;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("forceload add " + CHURN.getX() + " " + CHURN.getZ());
            waitFor(context, server, 400, "The churn chunk never ticked entities", minecraft -> minecraft.overworld().isPositionEntityTicking(CHURN));

            ServerLevel level = server.computeOnServer(MinecraftServer::overworld);
            var stop = new AtomicBoolean();
            var failure = new AtomicReference<Throwable>();
            var reads = new AtomicLong();
            var reader = new Thread(() ->
            {
                long count = 0;
                try
                {
                    while (!stop.get())
                    {
                        if (level.getNextEntityId() == 0)
                            throw new AssertionError("Got the id 0, which no entity may have");
                        count++;
                    }
                }
                catch (Throwable throwable)
                {
                    failure.set(throwable);
                }
                reads.set(count);
            }, "voidheart-worldgen-id-reader");
            reader.start();

            for (int round = 0; round < ROUNDS && failure.get() == null; round++)
                server.runOnServer(minecraft -> churn(minecraft.overworld()));
            stop.set(true);
            try
            {
                reader.join();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for the id reader", e);
            }
            server.runCommand("forceload remove " + CHURN.getX() + " " + CHURN.getZ());

            System.out.println("Worldgen entity ids: " + reads.get() + " ids taken off the server thread over " + ROUNDS + " rounds of " + ENTITIES + " tracked entities");
            if (failure.get() != null)
                throw new AssertionError("Taking an entity id off the server thread failed while the server thread changed its tracked entities", failure.get());
        }
    }

    /** Grows the chunk map's tracked entities to a few thousand and back, which rehashes it each way. */
    private static void churn(ServerLevel level)
    {
        var added = new ArrayList<Entity>(ENTITIES);
        for (int index = 0; index < ENTITIES; index++)
        {
            var stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
            if (stand == null)
                throw new AssertionError("No armor stand");
            stand.snapTo(CHURN.getX() + 0.5 + index % 8, -60, CHURN.getZ() + 0.5 + index / 8 % 8, 0, 0);
            level.addFreshEntity(stand);
            added.add(stand);
        }
        for (var entity : added)
            entity.discard();
    }
}
