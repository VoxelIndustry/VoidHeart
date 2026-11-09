package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.voxelindustry.voidheart.common.block.StateProperties;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Generated structures only ever hold broken monoliths, as lone ones do, since an intact one drops itself when mined.
 */
public class StructureMonolithClientTest implements FabricClientGameTest
{
    private static final int      GROUND    = -60;
    private static final BlockPos STRUCTURE = new BlockPos(40, GROUND, 40);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode spectator @a");
            server.runCommand("forceload add -16 -16 80 80");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);

            assertTemplate(context, server, "monolith_circle", new BlockPos(0, GROUND, 0), true);
            assertTemplate(context, server, "empty_ritual1", new BlockPos(16, GROUND, 0), false);
            assertTemplate(context, server, "empty_ritual2", new BlockPos(32, GROUND, 0), true);
            assertTemplate(context, server, "void_lamp_ritual", new BlockPos(48, GROUND, 0), false);
            assertTemplate(context, server, "void_pearl_ritual", new BlockPos(64, GROUND, 0), false);

            // Through the same structure pipeline as world generation, rather than a bare template
            server.runCommand("place structure " + MODID + ":monolith_circle " + STRUCTURE.getX() + " " + STRUCTURE.getY() + " " + STRUCTURE.getZ());
            context.waitTicks(5);
            assertBroken(server, "the generated monolith_circle", STRUCTURE.offset(-20, -4, -20), STRUCTURE.offset(20, 16, 20), true);

            server.runCommand("tp @a 4.5 " + (GROUND + 4) + " -7.5 facing 4.5 " + (GROUND + 1) + " 4.5");
            context.waitTicks(40);
            context.takeScreenshot("structure-monolith-circle");
            server.runCommand("tp @a 37.5 " + (GROUND + 4) + " -7.5 facing 37.5 " + (GROUND + 2) + " 3.5");
            context.waitTicks(40);
            context.takeScreenshot("structure-empty-ritual");
            server.runCommand("tp @a 40.5 " + (GROUND + 8) + " 28.5 facing " + (STRUCTURE.getX() + 4) + " " + (GROUND + 1) + " " + (STRUCTURE.getZ() + 4));
            context.waitTicks(40);
            context.takeScreenshot("structure-generated-circle");

            showCapstone(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /**
     * The monolith cap's model names its own capstone texture, which must exist rather than fall back to the missing texture.
     */
    private static void showCapstone(ClientGameTestContext context, TestServerContext server)
    {
        var texture = Identifier.fromNamespaceAndPath(MODID, "textures/block/monolith/capstone.png");
        if (!context.computeOnClient(minecraft -> minecraft.getResourceManager().getResource(texture).isPresent()))
            throw new AssertionError("The monolith cap's texture " + texture + " is missing");

        server.runCommand("setblock -8 " + GROUND + " 24 " + MODID + ":void_monolith");
        server.runCommand("setblock -8 " + (GROUND + 1) + " 24 " + MODID + ":void_monolith");
        server.runCommand("setblock -8 " + (GROUND + 2) + " 24 " + MODID + ":void_monolith_cap");
        server.runCommand("setblock -6 " + GROUND + " 24 " + MODID + ":void_monolith_cap");
        server.runCommand("tp @a -5.5 " + (GROUND + 2.5) + " 21.5 facing -7 " + (GROUND + 1.2) + " 24.5");
        context.waitTicks(40);
        context.takeScreenshot("monolith-capstone");
    }

    private static void assertTemplate(ClientGameTestContext context, TestServerContext server, String template, BlockPos at, boolean holdsMonoliths)
    {
        var id = Identifier.fromNamespaceAndPath(MODID, template);
        var size = server.computeOnServer(minecraft -> minecraft.getStructureTemplateManager().get(id).orElseThrow().getSize());

        server.runCommand("place template " + id + " " + at.getX() + " " + at.getY() + " " + at.getZ());
        context.waitTicks(5);
        assertBroken(server, template, at, at.offset(size), holdsMonoliths);
    }

    private static void assertBroken(TestServerContext server, String name, BlockPos from, BlockPos to, boolean holdsMonoliths)
    {
        int[] counts = server.computeOnServer(minecraft -> countMonoliths(minecraft, from, to));

        if (counts[1] != 0)
            throw new AssertionError("Expected only broken monoliths in " + name + ", found " + counts[1] + " intact out of " + counts[0]);
        if (holdsMonoliths != counts[0] > 0)
            throw new AssertionError("Expected " + name + (holdsMonoliths ? " to hold monoliths" : " to hold no monolith") + ", found " + counts[0]);
    }

    private static int[] countMonoliths(MinecraftServer minecraft, BlockPos from, BlockPos to)
    {
        int total = 0;
        int intact = 0;
        for (var pos : BlockPos.betweenClosed(from, to))
        {
            var state = minecraft.overworld().getBlockState(pos);
            if (!state.is(VoidHeartBlocks.VOID_MONOLITH))
                continue;

            total++;
            if (!state.getValue(StateProperties.BROKEN))
                intact++;
        }
        return new int[] { total, intact };
    }
}
