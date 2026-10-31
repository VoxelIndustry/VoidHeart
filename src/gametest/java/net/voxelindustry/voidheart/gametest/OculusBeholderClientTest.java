package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.voxelindustry.voidheart.client.render.oculus.OculusRenderer;
import net.voxelindustry.voidheart.common.content.oculus.OculusChunkServer;
import net.voxelindustry.voidheart.common.content.oculus.OculusSight;

/**
 * A table alone sees only around itself and shows the rest as generated relief. A beholder lets it see far land,
 * read from the save where nobody keeps it loaded, and tells mobs apart near it.
 */
public class OculusBeholderClientTest implements FabricClientGameTest
{
    private static final int GROUND = -61;
    private static final BlockPos TABLE = new BlockPos(0, GROUND + 1, 0);
    private static final BlockPos ANCHOR = new BlockPos(-2, GROUND + 1, -2);
    private static final BlockPos BEHOLDER = new BlockPos(24, GROUND + 1, 0);
    private static final long BEHOLDER_CHUNK = ChunkPos.pack(1, 0);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            var connection = world.getConnection();
            connection.waitForChunksRender();
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            context.runOnClient(minecraft -> minecraft.options.renderDistance().set(2));

            server.runCommand("forceload add -16 -16 112 16");
            // Four chunks east: unloaded once the force load is lifted, so only the save holds it
            server.runCommand("fill 66 " + (GROUND + 1) + " -3 72 " + (GROUND + 8) + " 3 gold_block");
            server.runCommand("summon cow 44 " + (GROUND + 1) + " 4 {NoAI:1b,PersistenceRequired:1b}");
            server.runCommand("summon pig -8 " + (GROUND + 1) + " -8 {NoAI:1b,PersistenceRequired:1b}");
            server.runCommand("fill -2 " + TABLE.getY() + " -2 2 " + TABLE.getY() + " 2 voidheart:oculus_table");
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0.5 " + (GROUND + 10) + " 0.5 180 90");
            server.runCommand("forceload remove -16 -16 112 16");

            context.waitTicks(120);
            context.takeScreenshot("oculus-beholder-none-1-32");
            var alone = context.computeOnClient(minecraft -> OculusRenderer.sight(ANCHOR).orElse(null));
            if (alone == null || alone.sources().stream().anyMatch(OculusSight.Source::beholder))
                throw new AssertionError("Expected the lone table to see by itself only, got " + alone);
            if (context.computeOnClient(minecraft -> OculusRenderer.reliefChunkCount()) == 0)
                throw new AssertionError("Expected the unseen land to show its generated relief");

            int savedBefore = OculusChunkServer.savedAnswers();
            setBlock(server, BEHOLDER, "voidheart:beholder");
            context.waitTicks(120);
            context.takeScreenshot("oculus-beholder-1-32");

            var seen = context.computeOnClient(minecraft -> OculusRenderer.sight(ANCHOR).orElse(null));
            if (seen == null || seen.sources().stream().noneMatch(source -> source.beholder() && source.pos().equals(BEHOLDER)))
                throw new AssertionError("Expected the table to see through the beholder, got " + seen);
            if (OculusChunkServer.savedAnswers() <= savedBefore)
                throw new AssertionError("Expected far land the server unloaded to be read from the save");
            server.runOnServer(minecraft ->
            {
                if (!minecraft.overworld().getForceLoadedChunks().contains(BEHOLDER_CHUNK))
                    throw new AssertionError("Expected the beholder to keep its chunk loaded");
                var sight = OculusSight.of(minecraft.overworld(), TABLE);
                if (sight.resolutionAt(44, 4) != OculusSight.Resolution.HIGH)
                    throw new AssertionError("Expected the cow near the beholder to be seen clearly");
                if (sight.resolutionAt(-8, -8) != OculusSight.Resolution.LOW)
                    throw new AssertionError("Expected the pig near the table to be seen dimly");
            });

            server.runCommand("tp @a 0.5 " + (GROUND + 4) + " 6 180 40");
            context.waitTicks(20);
            context.takeScreenshot("oculus-beholder-angled-1-32");

            setBlock(server, BEHOLDER, "air");
            context.waitTicks(40);
            var after = context.computeOnClient(minecraft -> OculusRenderer.sight(ANCHOR).orElse(null));
            if (after == null || after.sources().stream().anyMatch(OculusSight.Source::beholder))
                throw new AssertionError("Expected the table to lose the broken beholder's sight, got " + after);
            server.runOnServer(minecraft ->
            {
                if (minecraft.overworld().getForceLoadedChunks().contains(BEHOLDER_CHUNK))
                    throw new AssertionError("Expected the broken beholder to release its chunk");
            });
        }
    }

    private static void setBlock(TestServerContext server, BlockPos pos, String block)
    {
        server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + block);
    }
}
