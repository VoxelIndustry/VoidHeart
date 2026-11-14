package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.voxelindustry.voidheart.common.content.underway.UnderwayMosaicBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.underway.UnderwayCanvas;
import net.voxelindustry.voidheart.common.world.underway.UnderwayFort;
import net.voxelindustry.voidheart.common.world.underway.UnderwayMaterial;
import net.voxelindustry.voidheart.common.world.underway.UnderwayPlans;
import net.voxelindustry.voidheart.common.world.underway.UnderwayProp;
import net.voxelindustry.voidheart.common.world.underway.UnderwayRuin;
import net.voxelindustry.voidheart.common.world.underway.UnderwayTemple;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * A temple of the Underway in real chunks, reached by its command: that the world holds its mosaic tile for tile as planned, its dais, altar and the rest of its props, and how it looks from its doorway, down its aisle and close to its mosaic. A dry fort's temple is seen lit for the camera and in its own dark, a sealed one's by its own flames.
 */
public class UnderwayTempleClientTest implements FabricClientGameTest
{
    private static final int CHUNK_TIMEOUT_TICKS = 1200;
    private static final int MOVE_TIMEOUT_TICKS = 200;
    /** How bright the flames of a sealed temple leave its altar, at the least. */
    private static final int MIN_ALTAR_LIGHT = 8;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode spectator @a");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            boolean hudWasHidden = context.computeOnClient(minecraft -> minecraft.gui.hud.isHidden());
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);

            var dry = visit(context, world, "dry");
            expect(dry != null, "No dry temple was found near the start");
            check(server, dry);
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            shots(context, world, dry, "dry");
            server.runCommand("effect clear @a");
            shot(context, world, dry, "temple-dry-dark", UnderwayTemple.AISLE, 0, UnderwayTemple.AISLE, UnderwayTemple.DEPTH, 5);

            var sealed = visit(context, world, "sealed");
            if (sealed == null)
                System.out.println("[UnderwayTempleClientTest] No sealed temple within reach of this world's seed, so its flames were not seen");
            else
            {
                check(server, sealed);
                shot(context, world, sealed, "temple-sealed-dark", UnderwayTemple.AISLE, 1, UnderwayTemple.AISLE, UnderwayTemple.DEPTH, 4);
                shot(context, world, sealed, "temple-sealed-dais-dark", 2, 6, 12, 9, 2);
                var altar = blockAt(sealed, UnderwayTemple.AISLE, sealed.temple().room().floorY() + 2, 9);
                int light = server.computeOnServer(minecraft -> VoidDaedalus.level(minecraft).getBrightness(LightLayer.BLOCK, altar));
                expect(light >= MIN_ALTAR_LIGHT, "The flames of the sealed temple light its altar to " + light);
                server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
                shots(context, world, sealed, "sealed");
                server.runCommand("effect clear @a");
            }
            if (!hudWasHidden)
                context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    /** Runs the command that sends the player to the nearest temple in a fort left in that state. @return its fort, or null when the command found none */
    private static UnderwayFort visit(ClientGameTestContext context, TestSingleplayerContext world, String ruin)
    {
        var server = world.getServer();
        var before = server.computeOnServer(minecraft -> player(minecraft).blockPosition());
        server.runCommand("execute as @a run voidheart underway temple " + ruin);
        for (int tick = 0; tick < MOVE_TIMEOUT_TICKS && server.computeOnServer(minecraft -> player(minecraft).blockPosition()).equals(before); tick++)
            context.waitTick();
        var fort = server.computeOnServer(minecraft ->
        {
            var player = player(minecraft);
            if (player.level().dimension() != VoidDaedalus.WORLD_KEY || player.blockPosition().equals(before))
                return null;
            return UnderwayPlans.fort(VoidDaedalus.level(minecraft).getSeed(), DistrictPos.at(player.getBlockX(), player.getBlockZ()));
        });
        if (fort == null)
            return null;
        expect(fort.temple() != null, "The command sent the player to the " + fort.node() + " under " + fort.pos() + ", which has no temple");
        expect(fort.ruin().name().equalsIgnoreCase(ruin), "Asked for a " + ruin + " temple, got a " + fort.ruin() + " one");
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        return fort;
    }

    /** Compares the temple in the world with its plan: every tile of its mosaic, its dais, and every prop it was furnished with. */
    private static void check(TestServerContext server, UnderwayFort fort)
    {
        var temple = fort.temple();
        server.runOnServer(minecraft ->
        {
            var level = VoidDaedalus.level(minecraft);
            var canvas = new UnderwayCanvas(DistrictPos.PITCH, DistrictPos.PITCH);
            canvas.reset(fort.pos().originX(), fort.pos().originZ());
            UnderwayPlans.paint(level.getSeed(), canvas);
            int tiles = 0;
            for (int w = UnderwayTemple.FIRST_COLUMN; w < UnderwayTemple.FIRST_COLUMN + UnderwayTemple.COLUMNS; w++)
            {
                for (int row = 0; row < UnderwayTemple.ROWS; row++)
                {
                    var pos = blockAt(fort, w, temple.rowY(row), UnderwayTemple.DEPTH);
                    var state = level.getBlockState(pos);
                    var material = canvas.get(pos.getX(), pos.getY(), pos.getZ());
                    if (material == UnderwayMaterial.MOSAIC_BED)
                        expect(state.is(VoidHeartBlocks.UNDERWAY_MOSAIC_BED), "Planned a fallen tile at " + pos + ", found " + state);
                    else
                    {
                        expect(material == UnderwayMaterial.MOSAIC, "Planned no mosaic at " + pos + " but " + material);
                        expect(state.is(VoidHeartBlocks.UNDERWAY_MOSAIC) && state.getValue(UnderwayMosaicBlock.TILE) == canvas.mosaicTile(pos.getX(), pos.getY(), pos.getZ())
                                && state.getValue(UnderwayMosaicBlock.CHIPPED) == canvas.isMosaicChipped(pos.getX(), pos.getY(), pos.getZ()),
                                "Planned tile " + canvas.mosaicTile(pos.getX(), pos.getY(), pos.getZ()) + " at " + pos + ", found " + state);
                        tiles++;
                    }
                }
            }
            expect(tiles > UnderwayTemple.COLUMNS * UnderwayTemple.ROWS / 2, "Only " + tiles + " tiles of the mosaic are left under " + fort.pos());
            var dais = blockAt(fort, UnderwayTemple.AISLE, temple.room().floorY() + 1, UnderwayTemple.DEPTH - 1);
            expect(level.getBlockState(dais).isCollisionShapeFullBlock(level, dais), "No dais at " + dais);

            var room = temple.room();
            int props = 0;
            for (var spot : fort.props())
            {
                if (spot.along() < room.a0() || spot.along() > room.a1() || spot.across() < room.c0() || spot.across() > room.c1() || spot.y() <= room.floorY() || spot.y() > room.floorY() + room.height())
                    continue;
                var pos = new BlockPos(fort.frame().x(spot.along(), spot.across()), spot.y(), fort.frame().z(spot.along(), spot.across()));
                var state = level.getBlockState(pos);
                expect(state.is(VoidHeartBlocks.underwayProp(spot.prop())), "Planned " + spot.prop() + " at " + pos + ", found " + state);
                props++;
            }
            expect(props > 20, "Only " + props + " props in the temple under " + fort.pos());
            boolean lit = fort.ruin() == UnderwayRuin.SEALED;
            expect(fort.props().stream().anyMatch(spot -> spot.prop() == (lit ? UnderwayProp.BRAZIER_LIT : UnderwayProp.BRAZIER)), "No brazier in the " + fort.ruin() + " temple");
        });
    }

    /** Looks from the doorway, from halfway down the aisle, close at the mosaic, across the dais, and back at the doorway. */
    private static void shots(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort, String ruin)
    {
        int middle = UnderwayTemple.AISLE;
        shot(context, world, fort, "temple-" + ruin + "-doorway", middle, 0, middle, UnderwayTemple.DEPTH, 5);
        shot(context, world, fort, "temple-" + ruin + "-aisle", middle, 3, middle, UnderwayTemple.DEPTH, 6);
        shot(context, world, fort, "temple-" + ruin + "-mosaic-close", middle, 7, middle, UnderwayTemple.DEPTH, 7);
        shot(context, world, fort, "temple-" + ruin + "-dais", 1, 6, 12, 9, 2);
        shot(context, world, fort, "temple-" + ruin + "-back", middle, UnderwayTemple.DEPTH - 1, middle, 0, 3);
    }

    /**
     * @param fromW where the camera stands across the temple, and {@code fromD} how far in
     * @param height how far over the floor it looks at
     */
    private static void shot(ClientGameTestContext context, TestSingleplayerContext world, UnderwayFort fort, String name, int fromW, int fromD, int toW, int toD, int height)
    {
        var temple = fort.temple();
        int floor = temple.room().floorY();
        // On the dais the camera stands a block higher
        int feet = floor + 1 + (fromD >= UnderwayTemple.DEPTH - 3 ? 1 : 0);
        var from = blockAt(fort, fromW, feet, fromD);
        var to = blockAt(fort, toW, floor + height, toD);
        world.getServer().runCommand("execute in voidheart:daedalus run tp @a " + aimedPose(from.getX() + 0.5, from.getY() + 0.2, from.getZ() + 0.5, to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5));
        context.waitTicks(10);
        world.getConnection().waitForChunksRender(false, CHUNK_TIMEOUT_TICKS);
        context.takeScreenshot(TestScreenshotOptions.of("underway-" + name).withSize(1280, 720));
    }

    private static BlockPos blockAt(UnderwayFort fort, int w, int y, int d)
    {
        var temple = fort.temple();
        int along = temple.along(w, d);
        int across = temple.across(w, d);
        return new BlockPos(fort.frame().x(along, across), y, fort.frame().z(along, across));
    }
}
