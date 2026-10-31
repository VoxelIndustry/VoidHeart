package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;

/**
 * A table showing land past the client's render distance gets it from the server, which keeps it loaded here.
 */
public class OculusRemoteChunksClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksRender();

            server.runCommand("time set noon");
            server.runCommand("forceload add -96 -96 96 96");
            // Four chunks west, past a two chunk render distance but within the 1:32 view of a 5x5 table
            server.runCommand("fill -62 " + GROUND + " -8 -50 " + GROUND + " 8 gold_block");
            // Too far for this client to track: its marker can only come from the server
            server.runCommand("summon pig -56 " + (GROUND + 1) + " 0 {NoAI:1b}");

            context.runOnClient(minecraft -> minecraft.options.renderDistance().set(2));
            server.runCommand("fill -2 " + (GROUND + 1) + " -2 2 " + (GROUND + 1) + " 2 voidheart:oculus_table");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 10) + " 0.5 180 90");

            context.waitTicks(200);
            context.takeScreenshot("oculus-remote-top-1-32");
            context.runOnClient(minecraft -> System.out.println(OculusRenderer.describe()));

            int remote = context.computeOnClient(minecraft -> OculusRenderer.remoteChunkCount());
            if (remote == 0)
                throw new AssertionError("Expected land past the render distance to come from the server");
        }
    }
}
