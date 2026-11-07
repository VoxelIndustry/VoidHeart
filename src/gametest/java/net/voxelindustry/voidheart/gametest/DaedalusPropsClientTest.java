package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.DaedalusProp;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * The furniture of the Daedalus on its own in daylight: what each piece drops, how it looks placed from the front and from above, and as items.
 */
public class DaedalusPropsClientTest implements FabricClientGameTest
{
    private static final int FLOOR_Y = -55;
    private static final int SPACING = 2;
    private static final int CHUNK_TIMEOUT_TICKS = 600;
    private static final int SHOTS = 10;
    private static final double LIGHT_DISTANCE = 1.7;
    private static final double LIGHT_RAISE = 0.45;
    private static final double EYE_HEIGHT = 1.62;
    private static final int FLAME_TICKS = 40;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            checkDrops(server);

            int length = DaedalusProp.values().length * SPACING;
            server.runCommand("tp @a " + length / 2 + " " + (FLOOR_Y + 2) + " 7 180 10");
            context.waitTicks(20);
            world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
            buildShowcase(server, length);
            context.waitTicks(20);

            context.getInput().pressKey(InputConstants.KEY_F1);
            int stride = (length + SHOTS - 1) / SHOTS;
            for (int shot = 0; shot < SHOTS; shot++)
            {
                double x = stride * shot + stride / 2.0;
                server.runCommand("tp @a " + x + " " + (FLOOR_Y + 1.2) + " 4.5 180 12");
                context.waitTicks(10);
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
                context.takeScreenshot("daedalus-props-front-" + shot);
                server.runCommand("tp @a " + (x + 1.5) + " " + (FLOOR_Y + 3.5) + " 3 160 45");
                context.waitTicks(10);
                context.takeScreenshot("daedalus-props-above-" + shot);
            }
            showLights(context, server);
            context.getInput().pressKey(InputConstants.KEY_F1);

            checkItems(context, server);
        }
    }

    private static void checkDrops(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            for (var prop : DaedalusProp.values())
            {
                var block = VoidHeartBlocks.daedalusProp(prop);
                var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, null, null, pickaxe);
                if (drops.size() != 1 || !drops.getFirst().is(block.asItem()))
                    throw new AssertionError(prop + " should drop itself, it drops " + drops);
            }
        });
    }

    /**
     * A tiled floor and a brick wall behind, with each prop as it would stand in a room: the bust on a plinth, the chandelier under a beam, the grate in the floor and the wall pieces on the wall.
     */
    private static void buildShowcase(TestServerContext server, int length)
    {
        int y = FLOOR_Y;
        server.runCommand("fill -2 " + (y - 1) + " -3 " + (length + 1) + " " + (y - 1) + " 6 voidheart:voidstone_tile");
        server.runCommand("fill -2 " + y + " -3 " + (length + 1) + " " + (y + 4) + " -2 voidheart:voidstone_bricks");
        server.runCommand("fill -2 " + y + " -1 " + (length + 1) + " " + (y + 4) + " 6 air");
        for (var prop : DaedalusProp.values())
        {
            int x = prop.ordinal() * SPACING;
            String state = "voidheart:" + prop.id() + "[facing=south]";
            if (prop.hangsOnWall())
            {
                server.runCommand("setblock " + x + " " + (y + 1) + " -1 " + state);
                continue;
            }
            switch (prop)
            {
                case GRATE -> server.runCommand("setblock " + x + " " + (y - 1) + " 0 " + state);
                case CHANDELIER, CAGE, LANTERN ->
                {
                    server.runCommand("fill " + x + " " + (y + 3) + " -1 " + x + " " + (y + 3) + " 0 voidheart:voidstone_bricks");
                    server.runCommand("setblock " + x + " " + (y + 2) + " 0 " + state);
                }
                case BUST, ORRERY ->
                {
                    server.runCommand("setblock " + x + " " + y + " 0 voidheart:daedalus_plinth[facing=south]");
                    server.runCommand("setblock " + x + " " + (y + 1) + " 0 " + state);
                }
                default -> server.runCommand("setblock " + x + " " + y + " 0 " + state);
            }
        }
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var prop : DaedalusProp.values())
            {
                int x = prop.ordinal() * SPACING;
                int placedY = prop.hangsOnWall() ? y + 1 : switch (prop)
                {
                    case GRATE -> y - 1;
                    case CHANDELIER, CAGE, LANTERN -> y + 2;
                    case BUST, ORRERY -> y + 1;
                    default -> y;
                };
                var pos = new BlockPos(x, placedY, prop.hangsOnWall() ? -1 : 0);
                if (!level.getBlockState(pos).is(VoidHeartBlocks.daedalusProp(prop)))
                    throw new AssertionError(prop + " was not placed at " + pos + ", found " + level.getBlockState(pos));
            }
        });
    }

    /**
     * Each light at midnight from close by, to see its soul flames burn over the wicks and coals.
     */
    private static void showLights(ClientGameTestContext context, TestServerContext server)
    {
        context.runOnClient(minecraft ->
        {
            if (minecraft.particleEngine.createParticle(VoidHeart.SMALL_SOUL_FLAME_PARTICLE, 0, FLOOR_Y, 0, 0, 0, 0) == null)
                throw new AssertionError("Small soul flames have no particle provider");
        });
        server.runCommand("time set midnight");
        for (var prop : new DaedalusProp[] { DaedalusProp.CANDLES, DaedalusProp.CANDELABRUM, DaedalusProp.CHANDELIER, DaedalusProp.BRAZIER, DaedalusProp.SCONCE })
        {
            double targetY = FLOOR_Y + switch (prop)
            {
                case CANDELABRUM -> 0.9;
                case CHANDELIER -> 2.5;
                case BRAZIER -> 0.7;
                case SCONCE -> 1.6;
                default -> 0.4;
            };
            double targetZ = (prop.hangsOnWall() ? -1 : 0) + 0.5;
            double pitch = Math.toDegrees(Math.atan2(LIGHT_RAISE, LIGHT_DISTANCE));
            server.runCommand("tp @a " + (prop.ordinal() * SPACING + 0.5) + " " + (targetY + LIGHT_RAISE - EYE_HEIGHT) + " " + (targetZ + LIGHT_DISTANCE) + " 180 " + pitch);
            context.waitTicks(FLAME_TICKS);
            context.takeScreenshot("daedalus-props-light-" + prop.id());
        }
        server.runCommand("time set noon");
    }

    private static void checkItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a 6 " + (FLOOR_Y + 1) + " 5 180 20");
        server.runOnServer(minecraft ->
        {
            var player = minecraft.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            for (var prop : DaedalusProp.values())
                player.getInventory().add(new ItemStack(VoidHeartBlocks.daedalusProp(prop)));
            player.getInventory().setSelectedSlot(DaedalusProp.CANDELABRUM.ordinal());
        });
        context.waitTicks(20);
        context.takeScreenshot("daedalus-props-in-hand");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(10);
        context.takeScreenshot("daedalus-props-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().getInventory().clearContent());
    }
}
