package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFormer;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.PocketBlueprint;
import net.voxelindustry.voidheart.common.world.pocket.PocketBuilder;
import net.voxelindustry.voidheart.common.world.pocket.PocketGrid;
import net.voxelindustry.voidheart.common.world.pocket.PocketPlan;
import net.voxelindustry.voidheart.common.world.pocket.PocketVariant;

import java.util.UUID;

/**
 * Every pocket variant, in the original pocket size, seen from inside.
 */
public class VoidPocketClientTest implements FabricClientGameTest
{
    private static final int PREVIEW_Z = 2048;
    private static final int SPACING = 256;
    private static final long SEED = 987654321L;
    private static final int WIDE_FOV = 110;
    private static final int DEFAULT_FOV = 70;

    private static ServerLevel voidWorld(MinecraftServer server)
    {
        return server.getLevel(VoidHeart.VOID_WORLD_KEY);
    }

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode spectator @a");
            context.waitTicks(10);
            context.runOnClient(minecraft ->
            {
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
                minecraft.options.fov().set(WIDE_FOV);
            });
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkRealPocket(server);

            for (var variant : PocketVariant.values())
            {
                var heart = new BlockPos(SPACING * (variant.ordinal() + 1), 64, PREVIEW_Z);
                var blueprint = new PocketBlueprint(variant, SEED + variant.ordinal() * 7919L);
                server.runOnServer(minecraft -> checkBuilt(minecraft, heart, blueprint));
                visit(context, server, world.getConnection()::waitForChunksRender, variant, heart, blueprint);
            }

            checkPreviewCommand(context, server);

            context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.fov().set(DEFAULT_FOV));
        }
    }

    private static void checkPreviewCommand(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("execute as @a run voidheart pocket_preview " + PocketVariant.JUNGLE.getSerializedName() + " 3");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = minecraft.getPlayerList().getPlayers().getFirst();
            if (player.level() != voidWorld(minecraft) || player.getX() < 1_000_000)
                throw new AssertionError("The preview should send its caller to a far away pocket, got " + player.position());
        });
    }

    private static void checkRealPocket(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = voidWorld(minecraft);
            var state = VoidPocketState.getVoidPocketState(level);
            var player = UUID.randomUUID();
            state.createPocket(level, player);

            var heart = state.getHeartData(player).pocketPos();
            if (!level.getBlockState(heart).is(VoidHeartBlocks.VOID_HEART))
                throw new AssertionError("A created pocket should hold its heart, got " + level.getBlockState(heart));

            for (var inside : new BlockPos[] { heart.above(), heart.offset(7, 2, 7), heart.offset(-7, 2, -7) })
            {
                if (!PortalFormer.isInPocket(level, inside, player))
                    throw new AssertionError("Portals at " + inside + " should count as inside the pocket");
            }
            if (PortalFormer.isInPocket(level, heart.offset(9, 0, 0), player) || PortalFormer.isInPocket(level, heart.offset(0, 30, 0), player))
                throw new AssertionError("Places beyond the pocket wall should not count as inside it");
        });
    }

    private static void checkBuilt(MinecraftServer minecraft, BlockPos heart, PocketBlueprint blueprint)
    {
        var level = voidWorld(minecraft);
        var plan = PocketBuilder.build(level, heart, blueprint);
        var grid = plan.grid();

        int azurium = 0, rubrum = 0, wallHoles = 0;
        for (int y = grid.bottom(); y <= grid.top(); y++)
        {
            for (int z = -PocketGrid.WALL_REACH; z <= PocketGrid.WALL_REACH; z++)
            {
                for (int x = -PocketGrid.WALL_REACH; x <= PocketGrid.WALL_REACH; x++)
                {
                    var state = level.getBlockState(heart.offset(x, y, z));
                    if (grid.isBoundary(x, y, z) && !state.is(VoidHeartBlocks.POCKET_WALL))
                        wallHoles++;
                    if (state.is(VoidHeartBlocks.SMALL_AZURIUM_BUNDLE) || state.is(VoidHeartBlocks.MEDIUM_AZURIUM_BUNDLE) || state.is(VoidHeartBlocks.LARGE_AZURIUM_BUNDLE))
                        azurium++;
                    if (state.is(VoidHeartBlocks.SMALL_RUBRUM_BUNDLE) || state.is(VoidHeartBlocks.MEDIUM_RUBRUM_BUNDLE) || state.is(VoidHeartBlocks.LARGE_RUBRUM_BUNDLE))
                        rubrum++;
                }
            }
        }

        var name = blueprint.variant().getSerializedName();
        VoidHeart.LOGGER.info("Pocket test: " + name + " holds " + azurium + " azurium and " + rubrum + " rubrum bundles, of " + plan.crystals().size() + " planned, with " + plan.decor().size() + " decorations");
        if (wallHoles > 0)
            throw new AssertionError(name + " has " + wallHoles + " holes in its pocket wall");
        if (azurium < PocketPlan.MINIMUM_CRYSTALS_PER_TYPE || rubrum < PocketPlan.MINIMUM_CRYSTALS_PER_TYPE)
            throw new AssertionError(name + " placed " + azurium + " azurium and " + rubrum + " rubrum bundles");
        if (!level.getBlockState(heart.above()).isAir() || !level.getBlockState(heart.above(2)).isAir())
            throw new AssertionError(name + " does not leave room to arrive above the heart");
        if (level.getBlockState(heart.below()).is(Blocks.AIR))
            throw new AssertionError(name + " leaves the heart without ground");
    }

    private static void visit(ClientGameTestContext context, TestServerContext server, Runnable waitForChunks, PocketVariant variant, BlockPos heart, PocketBlueprint blueprint)
    {
        var name = variant.getSerializedName();

        look(server, heart, 0.5, 1.2, 0.5, 8.5, 0.5, 6.5);
        context.waitTicks(10);
        waitForChunks.run();
        context.waitTicks(15);
        context.takeScreenshot("pocket-" + name + "-high");

        look(server, heart, 0.5, 1.2, 0.5, -7.5, 0.5, -6.5);
        context.waitTicks(10);
        context.takeScreenshot("pocket-" + name + "-high-opposite");

        look(server, heart, 0.5, 8 - variant.profile().heartRise(), 0.5, 5.5, -1.5, 3.5);
        context.waitTicks(10);
        context.takeScreenshot("pocket-" + name + "-arrival");
    }

    private static void look(TestServerContext server, BlockPos heart, double x, double y, double z, double targetX, double targetY, double targetZ)
    {
        server.runCommand("execute in voidheart:void run tp @a " + (heart.getX() + x) + " " + (heart.getY() + y) + " " + (heart.getZ() + z)
                + " facing " + (heart.getX() + targetX) + " " + (heart.getY() + targetY) + " " + (heart.getZ() + targetZ));
    }
}
