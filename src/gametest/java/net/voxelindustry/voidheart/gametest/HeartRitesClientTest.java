package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.client.render.heart.FloorVeins;
import net.voxelindustry.voidheart.client.render.heart.HeartCage;
import net.voxelindustry.voidheart.client.render.heart.HeartShape;
import net.voxelindustry.voidheart.client.render.heart.HeartTwin;
import net.voxelindustry.voidheart.client.render.heart.HeartVeins;
import net.voxelindustry.voidheart.common.content.heart.VoidHeartTile;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.research.VoidGrafts;
import net.voxelindustry.voidheart.common.content.secondheart.SecondHeart;
import net.voxelindustry.voidheart.common.world.VoidPocketState;
import net.voxelindustry.voidheart.common.world.pocket.VoidHeartData;

import static net.voxelindustry.voidheart.gametest.GameTestKit.aimedPose;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;

/**
 * The body rites loosen the pocket by one flexion each, and the heart in it takes its owner's attunement to show their form, growing it only while they're
 * near to see it.
 */
public class HeartRitesClientTest implements FabricClientGameTest
{
    private static final double HEART_MIDDLE = 0.55;

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode spectator @a");
            server.runOnServer(minecraft -> VoidPocketState.getVoidPocketState(voidWorld(minecraft)).createPocket(voidWorld(minecraft), player(minecraft).getUUID()));
            var heart = server.computeOnServer(minecraft -> heartData(minecraft).pocketPos());

            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(InputConstants.KEY_F1);
            look(context, server, heart, 1.6, 0.25, 0.9);
            world.getConnection().waitForChunksRender();

            checkRite(context, server, heart, 1);
            captureHeart(context, server, heart);
            checkRite(context, server, heart, 2);
            captureVeins(context, server, heart);
            checkGrowsWhenSeen(context, server, heart);
            captureMarrow(context, server, heart);
            checkRite(context, server, heart, 3);
            server.runOnServer(minecraft -> SecondHeart.grant(player(minecraft), true));
            checkRite(context, server, heart, VoidGrafts.SECOND_HEART);
            captureTwin(context, server, heart);

            context.getInput().pressKey(InputConstants.KEY_F1);
        }
    }

    private static void checkRite(ClientGameTestContext context, TestServerContext server, BlockPos heart, int rite)
    {
        int expected = VoidHeartData.BASE_FLEXION + rite - 1;
        server.runOnServer(minecraft ->
        {
            Knowledge.attune(minecraft, player(minecraft).getUUID(), rite);
            int max = heartData(minecraft).maxFlexion(VoidGrafts.attunementOf(player(minecraft)));
            expect(max == expected, "At attunement " + rite + " the pocket should allow " + expected + " flexion, got " + max);
        });
        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getBlockEntity(heart) instanceof VoidHeartTile tile
                && tile.getOwnerAttunement() == rite && tile.heartData() != null, 100);
        int shown = context.computeOnClient(minecraft ->
        {
            var tile = (VoidHeartTile) minecraft.level.getBlockEntity(heart);
            return tile.heartData().maxFlexion(tile.getOwnerAttunement());
        });
        expect(shown == expected, "At attunement " + rite + " the heart should show " + expected + " flexion slots, got " + shown);
    }

    /** The heart alone, at rest and swelled, from the front, the side and above, then a frozen sweep close to it for flicker between flesh and core. */
    private static void captureHeart(ClientGameTestContext context, TestServerContext server, BlockPos heart)
    {
        server.runCommand("tick freeze");
        look(context, server, heart, 1.6, 0.25, 0.9);
        context.takeScreenshot("heart-rites-stage1-front");
        look(context, server, heart, -0.4, 0.1, -1.7);
        context.takeScreenshot("heart-rites-stage1-back");
        look(context, server, heart, 1.0, 1.4, 0.6);
        context.takeScreenshot("heart-rites-stage1-above");
        look(context, server, heart, 0.9, 0.1, 0.5);
        context.takeScreenshot("heart-rites-stage1-close");

        look(context, server, heart, 1.6, 0.25, 0.9);
        // Stepped through one beat, so one of the frames lands near its fullest
        for (int frame = 0; frame < 4; frame++)
        {
            server.runCommand("tick step 10");
            context.waitTicks(12);
            context.takeScreenshot("heart-rites-stage1-beat-" + frame);
        }

        for (int step = 0; step < 8; step++)
        {
            look(context, server, heart, 1.1 + step * 0.04, 0.2 + step * 0.02, 0.6 - step * 0.05);
            context.takeScreenshot("heart-rites-stage1-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
    }

    /** The veins growing out over the heart and down to the floor, then grown, from around it, with frames stepped through a beat's pulses. */
    private static void captureVeins(ClientGameTestContext context, TestServerContext server, BlockPos heart)
    {
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(heart) instanceof VoidHeartTile tile && tile.heartForm() == 2, 100);
        server.runCommand("tick freeze");
        look(context, server, heart, 1.6, 0.25, 0.9);
        long grownAt = server.computeOnServer(minecraft -> heartTile(minecraft, heart).grownAt(2));
        for (int grown : new int[] { 20, 50, 90, 140, 180 })
        {
            stepTo(context, server, grownAt + grown);
            context.takeScreenshot("heart-rites-stage2-grow-" + grown);
        }
        stepTo(context, server, grownAt + 240);
        String floor = context.computeOnClient(minecraft ->
        {
            var tile = (VoidHeartTile) minecraft.level.getBlockEntity(heart);
            var found = tile.floor();
            if (found == null)
                return "none";
            int count = 0;
            for (var row : found)
                for (boolean cell : row)
                    count += cell ? 1 : 0;
            var veins = HeartVeins.grow(HeartShape.voxels(false), tile.beatSeed());
            var feet = HeartVeins.feet(veins);
            var growth = FloorVeins.grow(found, feet, tile.beatSeed());
            return feet.size() + " feet, " + growth.pixels().size() + " floor pixels from " + growth.first() + " to " + growth.last() + ", " + count
                    + " cells, under the heart " + found[VoidHeartTile.FLOOR_REACH][VoidHeartTile.FLOOR_REACH];
        });
        expect(floor.endsWith("true") && !floor.contains(" 0 floor pixels"), "The heart's veins should creep over the floor under and around it, got " + floor);

        context.takeScreenshot("heart-rites-stage2-front");
        look(context, server, heart, -0.4, 0.1, -1.7);
        context.takeScreenshot("heart-rites-stage2-back");
        look(context, server, heart, 1.0, 1.4, 0.6);
        context.takeScreenshot("heart-rites-stage2-above");
        look(context, server, heart, 0.9, -0.2, 0.5);
        context.takeScreenshot("heart-rites-stage2-close");
        look(context, server, heart, 1.4, -0.35, -0.2);
        context.takeScreenshot("heart-rites-stage2-low");
        look(context, server, heart, 3.5, 4.5, 2.5);
        context.takeScreenshot("heart-rites-stage2-wide");
        look(context, server, heart, 0.3, 6.5, 0.2);
        context.takeScreenshot("heart-rites-stage2-overhead");
        look(context, server, heart, 0.15, 1.3, 0.1);
        context.takeScreenshot("heart-rites-stage2-feet");

        look(context, server, heart, 1.6, 0.25, 0.9);
        look(context, server, heart, 3.5, 4.5, 2.5);
        for (int frame = 0; frame < 10; frame++)
        {
            server.runCommand("tick step 2");
            context.waitTicks(4);
            context.takeScreenshot("heart-rites-stage2-pulse-" + frame);
        }

        for (int step = 0; step < 8; step++)
        {
            look(context, server, heart, 1.1 + step * 0.04, 0.2 + step * 0.02, 0.6 - step * 0.05);
            context.takeScreenshot("heart-rites-stage2-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
    }

    /** The ribs closing around the heart and the marrow warming its cracks, then grown, from around it, with frames stepped through its motes. */
    private static void captureMarrow(ClientGameTestContext context, TestServerContext server, BlockPos heart)
    {
        server.runCommand("tick freeze");
        look(context, server, heart, 1.6, 0.25, 0.9);
        long grownAt = server.computeOnServer(minecraft -> heartTile(minecraft, heart).grownAt(3));
        for (int grown : new int[] { 10, 25, 40, 55 })
        {
            stepTo(context, server, grownAt + grown);
            context.takeScreenshot("heart-rites-stage3-grow-" + grown);
        }
        stepTo(context, server, grownAt + 120);

        int bone = context.computeOnClient(minecraft ->
        {
            var tile = (VoidHeartTile) minecraft.level.getBlockEntity(heart);
            var veins = HeartVeins.grow(HeartShape.voxels(false), tile.beatSeed());
            var cage = HeartCage.grow(HeartShape.voxels(true), veins, tile.beatSeed());
            int count = 0;
            for (var plane : cage)
                for (var row : plane)
                    for (int along : row)
                        count += along != HeartCage.NONE ? 1 : 0;
            return count;
        });
        expect(bone > 60, "The heart's cage should hold a spine and ribs, got " + bone + " bone pixels");

        context.takeScreenshot("heart-rites-stage3-front");
        look(context, server, heart, -1.6, 0.25, -0.9);
        context.takeScreenshot("heart-rites-stage3-back");
        look(context, server, heart, 0.9, 0.25, -1.6);
        context.takeScreenshot("heart-rites-stage3-side");
        look(context, server, heart, 1.0, 1.4, 0.6);
        context.takeScreenshot("heart-rites-stage3-above");
        look(context, server, heart, 0.9, -0.2, 0.5);
        context.takeScreenshot("heart-rites-stage3-close");
        look(context, server, heart, 3.5, 4.5, 2.5);
        context.takeScreenshot("heart-rites-stage3-wide");

        look(context, server, heart, 1.3, 0.5, 0.8);
        for (int frame = 0; frame < 8; frame++)
        {
            server.runCommand("tick step 6");
            context.waitTicks(8);
            context.takeScreenshot("heart-rites-stage3-motes-" + frame);
        }

        for (int step = 0; step < 8; step++)
        {
            look(context, server, heart, 1.1 + step * 0.04, 0.2 + step * 0.02, 0.6 - step * 0.05);
            context.takeScreenshot("heart-rites-stage3-sweep-" + step);
        }
        server.runCommand("tick unfreeze");
    }

    /**
     * The vein reaching out past the ribs and the twin budding from it, then grown, from around it, with frames through its loose beats and its locked
     * ones, then while the second life recharges and once it is back.
     */
    private static void captureTwin(ClientGameTestContext context, TestServerContext server, BlockPos heart)
    {
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(heart) instanceof VoidHeartTile tile && tile.heartForm() == 4, 100);
        server.runCommand("tick freeze");
        look(context, server, heart, 2.2, 0.7, 1.5);
        long grownAt = server.computeOnServer(minecraft -> heartTile(minecraft, heart).grownAt(4));
        for (int grown : new int[] { 8, 16, 30, 50, 70, 100 })
        {
            stepTo(context, server, grownAt + grown);
            context.takeScreenshot("heart-rites-stage4-grow-" + grown);
        }

        int bridge = context.computeOnClient(minecraft ->
        {
            var tile = (VoidHeartTile) minecraft.level.getBlockEntity(heart);
            var veins = HeartVeins.grow(HeartShape.voxels(false), tile.beatSeed());
            var cage = HeartCage.grow(HeartShape.voxels(true), veins, tile.beatSeed());
            return Math.min(HeartTwin.bridge(HeartShape.voxels(false), HeartShape.voxels(true), veins, cage, 1).size(),
                    HeartTwin.bridge(HeartShape.voxels(false), HeartShape.voxels(true), veins, cage, -1).size());
        });
        expect(bridge > 0, "The owner's twin should be joined to their heart on either side");

        // Loose beats, then locked to the first heart's
        look(context, server, heart, 2.0, 0.5, 1.2);
        for (int frame = 0; frame < 8; frame++)
        {
            server.runCommand("tick step 3");
            context.waitTicks(5);
            context.takeScreenshot("heart-rites-stage4-loose-" + frame);
        }
        stepTo(context, server, grownAt + 260);
        for (int frame = 0; frame < 8; frame++)
        {
            server.runCommand("tick step 3");
            context.waitTicks(5);
            context.takeScreenshot("heart-rites-stage4-locked-" + frame);
        }

        look(context, server, heart, 2.2, 0.7, 1.5);
        context.takeScreenshot("heart-rites-stage4-a");
        look(context, server, heart, -2.2, 0.7, -1.5);
        context.takeScreenshot("heart-rites-stage4-b");
        look(context, server, heart, 1.5, 0.7, -2.2);
        context.takeScreenshot("heart-rites-stage4-c");
        look(context, server, heart, -1.5, 0.7, 2.2);
        context.takeScreenshot("heart-rites-stage4-d");
        look(context, server, heart, 0.8, 2.6, 0.5);
        context.takeScreenshot("heart-rites-stage4-above");
        look(context, server, heart, 3.5, 4.5, 2.5);
        context.takeScreenshot("heart-rites-stage4-wide");

        for (int step = 0; step < 8; step++)
        {
            look(context, server, heart, 1.6 + step * 0.05, 0.3 + step * 0.02, 1.0 - step * 0.06);
            context.takeScreenshot("heart-rites-stage4-sweep-" + step);
        }

        // Recharging, the twin stops and goes dark, then comes back once the second life is ready
        server.runOnServer(minecraft -> SecondHeart.setRecharge(player(minecraft), 100_000));
        long before = server.computeOnServer(minecraft -> voidWorld(minecraft).getGameTime());
        stepTo(context, server, before + 25);
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(heart) instanceof VoidHeartTile tile && !tile.isTwinBeats(), 100);
        stepTo(context, server, server.computeOnServer(minecraft -> voidWorld(minecraft).getGameTime()) + 30);
        look(context, server, heart, 2.2, 0.7, 1.5);
        context.takeScreenshot("heart-rites-stage4-hollow-a");
        look(context, server, heart, -1.5, 0.7, 2.2);
        context.takeScreenshot("heart-rites-stage4-hollow-d");
        look(context, server, heart, 3.5, 4.5, 2.5);
        context.takeScreenshot("heart-rites-stage4-hollow-wide");

        server.runOnServer(minecraft -> SecondHeart.setRecharge(player(minecraft), 0));
        before = server.computeOnServer(minecraft -> voidWorld(minecraft).getGameTime());
        stepTo(context, server, before + 25);
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(heart) instanceof VoidHeartTile tile && tile.isTwinBeats(), 100);
        stepTo(context, server, server.computeOnServer(minecraft -> voidWorld(minecraft).getGameTime()) + 30);
        look(context, server, heart, 2.2, 0.7, 1.5);
        context.takeScreenshot("heart-rites-stage4-ready-a");
        server.runCommand("tick unfreeze");
    }

    /** A rite taken far from the heart shows on it only once its owner comes back near. */
    private static void checkGrowsWhenSeen(ClientGameTestContext context, TestServerContext server, BlockPos heart)
    {
        look(context, server, heart, 40, 2, 0);
        server.runOnServer(minecraft -> Knowledge.attune(minecraft, player(minecraft).getUUID(), 3));
        context.waitTicks(60);
        int away = server.computeOnServer(minecraft -> heartTile(minecraft, heart).heartForm());
        expect(away == 2, "The heart should keep its veins while its owner is away, got form " + away);

        look(context, server, heart, 1.6, 0.25, 0.9);
        context.waitFor(minecraft -> minecraft.level.getBlockEntity(heart) instanceof VoidHeartTile tile && tile.heartForm() == 3, 100);
    }

    /** Steps the frozen game to a game time and waits for the client to reach it, then lets it draw. */
    private static void stepTo(ClientGameTestContext context, TestServerContext server, long time)
    {
        long now = server.computeOnServer(minecraft -> voidWorld(minecraft).getGameTime());
        if (time > now)
            server.runCommand("tick step " + (time - now));
        // The client's clock trails the server's while stepping, and the heart grows by the client's
        context.waitFor(minecraft -> minecraft.level.getGameTime() >= time, 200);
        context.waitTicks(4);
    }

    /** Eyes at the offset from the heart's middle, looking at it. */
    private static void look(ClientGameTestContext context, TestServerContext server, BlockPos heart, double dx, double dy, double dz)
    {
        double x = heart.getX() + 0.5, y = heart.getY() + HEART_MIDDLE, z = heart.getZ() + 0.5;
        server.runCommand("execute in voidheart:void run tp @a " + aimedPose(x + dx, y + dy - 1.62, z + dz, x, y, z));
        context.waitTicks(3);
    }

    private static ServerLevel voidWorld(MinecraftServer minecraft)
    {
        return minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
    }

    private static VoidHeartTile heartTile(MinecraftServer minecraft, BlockPos heart)
    {
        return (VoidHeartTile) voidWorld(minecraft).getBlockEntity(heart);
    }

    private static VoidHeartData heartData(MinecraftServer minecraft)
    {
        return VoidPocketState.getVoidPocketState(voidWorld(minecraft)).getHeartData(player(minecraft).getUUID());
    }
}
