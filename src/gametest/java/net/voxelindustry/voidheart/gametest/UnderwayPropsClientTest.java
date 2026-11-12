package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;

import java.util.EnumSet;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * What was left in the Underway, on its own in daylight against plain concrete: what each piece drops, how it looks placed, and as items. Tabletop pieces stand on a table, ceiling pieces hang under a beam and wall pieces on the wall.
 */
public class UnderwayPropsClientTest implements FabricClientGameTest
{
    private static final int FLOOR_Y = -55;
    private static final int SPACING = 2;
    private static final int PROPS_PER_SHOT = 10;
    private static final int CHUNK_TIMEOUT_TICKS = 600;
    private static final int INVENTORY_SLOTS = 36;
    private static final int CLOSE_FOV = 30;
    private static final int BLOCKS_Z = 40;
    private static final String[] BLOCK_STATES = { "underway_concrete", "underway_formed_concrete", "underway_stained_concrete", "underway_cracked_concrete", "underway_armour",
            "underway_hazard_armour", "underway_blast_gate", "underway_firing_slit[axis=z]", "underway_grating", "underway_steel_stairs[facing=north]", "underway_trunk_rail[axis=z]",
            "underway_track_tie[axis=z]", "underway_feeder_rail[axis=z]", "underway_ballast", "underway_rubble", "underway_door[half=lower,facing=south]" };
    private static final int DEFAULT_FOV = 70;
    private static final EnumSet<UnderwayProp> TABLETOP = EnumSet.of(UnderwayProp.MESS_KIT, UnderwayProp.RADIO, UnderwayProp.FIELD_TELEPHONE, UnderwayProp.TYPEWRITER,
            UnderwayProp.PAPERS, UnderwayProp.DESK_LAMP, UnderwayProp.BOTTLES, UnderwayProp.CARD_GAME, UnderwayProp.LAB_GLASS);

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

            int length = UnderwayProp.values().length * SPACING;
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
            context.runOnClient(minecraft -> minecraft.options.fov().set(CLOSE_FOV));
            int stride = PROPS_PER_SHOT * SPACING;
            for (int shot = 0; shot * stride < length; shot++)
            {
                double x = stride * shot + stride / 2.0 - 1;
                server.runCommand("tp @a " + x + " " + (FLOOR_Y + 0.6) + " 10.5 180 2");
                context.waitTicks(10);
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
                buildShowcase(server, shot * PROPS_PER_SHOT, Math.min((shot + 1) * PROPS_PER_SHOT, UnderwayProp.values().length));
                context.waitTicks(10);
                world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
                context.takeScreenshot(TestScreenshotOptions.of("underway-props-" + shot).withSize(1920, 480));
            }
            showBlocks(context, world.getServer());
            context.runOnClient(minecraft -> minecraft.options.fov().set(DEFAULT_FOV));
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);

            checkItems(context, server);
        }
    }

    /** The blocks the Underway is built of, in a row: the shell that cannot be broken, then what can. */
    private static void showBlocks(ClientGameTestContext context, TestServerContext server)
    {
        int y = FLOOR_Y;
        int z = BLOCKS_Z;
        server.runCommand("tp @a " + (BLOCK_STATES.length * SPACING / 2.0 - 1) + " " + (y + 0.6) + " " + (z + 10.5) + " 180 2");
        context.waitTicks(10);
        server.runCommand("fill -2 " + (y - 1) + " " + (z - 3) + " " + (BLOCK_STATES.length * SPACING + 1) + " " + (y - 1) + " " + (z + 12) + " minecraft:smooth_stone");
        server.runCommand("fill -2 " + y + " " + (z - 3) + " " + (BLOCK_STATES.length * SPACING + 1) + " " + (y + 4) + " " + (z - 2) + " minecraft:light_gray_concrete");
        for (int index = 0; index < BLOCK_STATES.length; index++)
        {
            int x = index * SPACING;
            if (BLOCK_STATES[index].contains("rail") || BLOCK_STATES[index].contains("tie"))
                server.runCommand("setblock " + x + " " + y + " " + z + " voidheart:underway_ballast");
            boolean onBallast = BLOCK_STATES[index].contains("rail") || BLOCK_STATES[index].contains("tie");
            server.runCommand("setblock " + x + " " + (onBallast ? y + 1 : y) + " " + z + " voidheart:" + BLOCK_STATES[index]);
            if (BLOCK_STATES[index].startsWith("underway_door"))
                server.runCommand("setblock " + x + " " + (y + 1) + " " + z + " voidheart:underway_door[half=upper,facing=south]");
        }
        for (int shot = 0; shot * PROPS_PER_SHOT < BLOCK_STATES.length; shot++)
        {
            server.runCommand("tp @a " + (shot * PROPS_PER_SHOT * SPACING + PROPS_PER_SHOT * SPACING / 2.0 - 1) + " " + (y + 0.6) + " " + (z + 10.5) + " 180 2");
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("underway-blocks-" + shot).withSize(1920, 480));
        }

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            for (var block : new Block[] { VoidHeartBlocks.UNDERWAY_CONCRETE, VoidHeartBlocks.UNDERWAY_FORMED_CONCRETE, VoidHeartBlocks.UNDERWAY_STAINED_CONCRETE, VoidHeartBlocks.UNDERWAY_CRACKED_CONCRETE,
                    VoidHeartBlocks.UNDERWAY_ARMOUR, VoidHeartBlocks.UNDERWAY_HAZARD_ARMOUR, VoidHeartBlocks.UNDERWAY_BLAST_GATE, VoidHeartBlocks.UNDERWAY_EMBRASURE })
            {
                if (block.defaultBlockState().getDestroySpeed(level, BlockPos.ZERO) >= 0)
                    throw new AssertionError(block + " of the shell can be broken");
            }
            for (var block : new Block[] { VoidHeartBlocks.UNDERWAY_GRATING, VoidHeartBlocks.UNDERWAY_STEEL_STAIRS, VoidHeartBlocks.UNDERWAY_TRUNK_RAIL, VoidHeartBlocks.UNDERWAY_TRACK_TIE,
                    VoidHeartBlocks.UNDERWAY_FEEDER_RAIL, VoidHeartBlocks.UNDERWAY_BALLAST, VoidHeartBlocks.UNDERWAY_RUBBLE, VoidHeartBlocks.UNDERWAY_DOOR })
            {
                var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, null, null, pickaxe);
                if (drops.size() != 1 || !drops.getFirst().is(block.asItem()))
                    throw new AssertionError(block + " should drop itself, it drops " + drops);
            }
        });
    }

    private static void checkDrops(TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var pickaxe = new ItemStack(Items.IRON_PICKAXE);
            for (var prop : UnderwayProp.values())
            {
                var block = VoidHeartBlocks.underwayProp(prop);
                var drops = Block.getDrops(block.defaultBlockState(), level, BlockPos.ZERO, null, null, pickaxe);
                if (drops.size() != 1 || !drops.getFirst().is(block.asItem()))
                    throw new AssertionError(prop + " should drop itself, it drops " + drops);
            }
        });
    }

    private static void buildShowcase(TestServerContext server, int first, int end)
    {
        int y = FLOOR_Y;
        int fromX = first * SPACING - 2;
        int toX = end * SPACING + 1;
        server.runCommand("fill " + fromX + " " + (y - 1) + " -3 " + toX + " " + (y - 1) + " 12 minecraft:smooth_stone");
        server.runCommand("fill " + fromX + " " + y + " -3 " + toX + " " + (y + 4) + " -2 minecraft:light_gray_concrete");
        server.runCommand("fill " + fromX + " " + y + " -1 " + toX + " " + (y + 4) + " 6 air");
        for (int index = first; index < end; index++)
        {
            var prop = UnderwayProp.values()[index];
            int x = index * SPACING;
            String state = "voidheart:" + prop.id() + "[facing=south]";
            var pos = placeOf(prop, x);
            if (TABLETOP.contains(prop))
                server.runCommand("setblock " + x + " " + y + " 0 voidheart:underway_table[facing=south]");
            if (prop.mount() == UnderwayProp.Mount.CEILING)
                server.runCommand("fill " + x + " " + (y + 3) + " -1 " + x + " " + (y + 3) + " 0 minecraft:light_gray_concrete");
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state);
        }
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (int index = first; index < end; index++)
            {
                var prop = UnderwayProp.values()[index];
                var pos = placeOf(prop, index * SPACING);
                if (!level.getBlockState(pos).is(VoidHeartBlocks.underwayProp(prop)))
                    throw new AssertionError(prop + " was not placed at " + pos + ", found " + level.getBlockState(pos));
            }
        });
    }

    private static BlockPos placeOf(UnderwayProp prop, int x)
    {
        if (TABLETOP.contains(prop))
            return new BlockPos(x, FLOOR_Y + 1, 0);
        return switch (prop.mount())
        {
            case FLOOR -> new BlockPos(x, FLOOR_Y, 0);
            case WALL -> new BlockPos(x, FLOOR_Y + 1, -1);
            case CEILING -> new BlockPos(x, FLOOR_Y + 2, 0);
        };
    }

    private static void checkItems(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("gamemode creative @a");
        server.runCommand("tp @a 6 " + (FLOOR_Y + 1) + " 5 180 20");
        server.runOnServer(minecraft ->
        {
            var player = minecraft.getPlayerList().getPlayers().getFirst();
            player.getInventory().clearContent();
            for (int index = 0; index < INVENTORY_SLOTS; index++)
                player.getInventory().add(new ItemStack(VoidHeartBlocks.underwayProp(UnderwayProp.values()[index * 3])));
            player.getInventory().setSelectedSlot(0);
        });
        context.waitTicks(20);
        context.takeScreenshot("underway-props-in-hand");
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitTicks(10);
        context.takeScreenshot("underway-props-inventory");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);
        context.waitTicks(5);
        server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().getInventory().clearContent());
    }
}
