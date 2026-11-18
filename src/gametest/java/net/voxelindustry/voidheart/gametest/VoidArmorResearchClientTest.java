package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.voxelindustry.voidheart.common.content.altar.VoidAltarTile;
import net.voxelindustry.voidheart.common.content.pillar.VoidPillarTile;
import net.voxelindustry.voidheart.common.content.armor.VoidArmorContent;
import net.voxelindustry.voidheart.common.content.blight.Blueprints;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.content.repair.ExperienceSkullTile;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.FLAT_FEET;
import static net.voxelindustry.voidheart.gametest.GameTestKit.expect;
import static net.voxelindustry.voidheart.gametest.GameTestKit.knowledge;
import static net.voxelindustry.voidheart.gametest.GameTestKit.player;
import static net.voxelindustry.voidheart.gametest.GameTestKit.rightClick;
import static net.voxelindustry.voidheart.gametest.GameTestKit.waitFor;

/**
 * How the Void Armor is reached. Its research stays out of sight until the player studies a Kernacian Core, which teaches its blueprint and is kept, and a
 * blessed contraption never offers it. Its frame is built on the altar around the Core only once the research is done. The spectral lens's research waits
 * for the Beholder's as well.
 */
public class VoidArmorResearchClientTest implements FabricClientGameTest
{
    private static final Identifier VOID_ARMOR    = Identifier.fromNamespaceAndPath("voidheart", "void_armor");
    private static final Identifier SPECTRAL_LENS = Identifier.fromNamespaceAndPath("voidheart", "spectral_lens");

    private static final BlockPos       ALTAR   = new BlockPos(40, FLAT_FEET, 0);
    private static final BlockPos       SKULL   = ALTAR.offset(2, 0, 2);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));

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
            server.runCommand("tp @a 0.5 " + FLAT_FEET + " 0.5 0 -60");
            server.runCommand("execute as @a run voidheart research grant voidheart:void_energy");
            context.waitTicks(10);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            studyTheCore(context, server);
            buildTheFrame(context, server);
            spectralLensWaitsForTheBeholder(context, server);
        }
    }

    private static boolean discoverable(MinecraftServer minecraft, Identifier research)
    {
        return knowledge(minecraft).isDiscoverable(Knowledge.tree(minecraft.registryAccess()).get(research));
    }

    private static void studyTheCore(ClientGameTestContext context, TestServerContext server)
    {
        expect(server.computeOnServer(minecraft -> !discoverable(minecraft, VOID_ARMOR)), "Expected the Void Armor's research hidden before the Core is studied");
        boolean offered = server.computeOnServer(minecraft -> Blueprints.pool(Knowledge.tree(minecraft.registryAccess())).contains(Blueprints.VOID_ARMOR));
        expect(!offered, "Expected a blessed contraption never to offer the Void Armor");

        server.runOnServer(minecraft -> player(minecraft).setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.KERNACIAN_CORE)));
        context.waitTicks(3);
        rightClick(context);
        context.waitTicks(3);
        boolean known = server.computeOnServer(minecraft -> knowledge(minecraft).knowsBlueprint(Blueprints.VOID_ARMOR));
        int cores = server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().is(VoidHeartItems.KERNACIAN_CORE) ? player(minecraft).getMainHandItem().getCount() : 0);
        expect(known && cores == 1, "Expected studying the Core to teach the frame and keep the Core, known " + known + ", cores " + cores);
        expect(server.computeOnServer(minecraft -> discoverable(minecraft, VOID_ARMOR)), "Expected the Void Armor's research in sight once the Core is studied");
        context.takeScreenshot("void-armor-core-studied");

        // Studied again, it has nothing more to teach and is still kept
        rightClick(context);
        context.waitTicks(3);
        cores = server.computeOnServer(minecraft -> player(minecraft).getMainHandItem().getCount());
        expect(cores == 1, "Expected the Core kept when studied again, " + cores + " left");
    }

    private static void buildTheFrame(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
            for (var offset : PILLARS)
                level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
            level.setBlockAndUpdate(SKULL, VoidHeartBlocks.EXPERIENCE_SKULL.defaultBlockState());
            ((ExperienceSkullTile) level.getBlockEntity(SKULL)).setExperience(1000);
        });
        context.waitTicks(3);

        // Before the research, the altar won't take the frame's recipe
        server.runOnServer(minecraft ->
        {
            lay(minecraft);
            altar(minecraft).setStack(player(minecraft), new ItemStack(VoidHeartItems.KERNACIAN_CORE));
        });
        context.waitTicks(10);
        expect(server.computeOnServer(minecraft -> !altar(minecraft).isCrafting()), "Expected the altar to refuse the frame before its research is done");

        server.runCommand("execute as @a run voidheart research grant voidheart:void_armor");
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            altar(minecraft).setStack(player(minecraft), ItemStack.EMPTY);
            altar(minecraft).setStack(player(minecraft), new ItemStack(VoidHeartItems.KERNACIAN_CORE));
        });
        context.waitTicks(5);
        expect(server.computeOnServer(minecraft -> altar(minecraft).isCrafting()), "Expected the altar to start the frame once its research is done");
        waitFor(context, server, 2000, "Expected the altar to give a Void Armor frame", minecraft ->
                !altar(minecraft).isCrafting() && altar(minecraft).getStack().is(VoidArmorContent.VOID_ARMOR));
    }

    private static void lay(MinecraftServer minecraft)
    {
        Item[] items = { Items.NETHERITE_INGOT, Items.NETHERITE_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT, VoidHeartItems.SEETHING_STEEL_INGOT,
                VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RAVENOUS_GOLD_INGOT, VoidHeartItems.RUBRUM_SHARD, VoidHeartItems.RUBRUM_SHARD };
        var level = minecraft.overworld();
        for (int index = 0; index < PILLARS.size(); index++)
            ((VoidPillarTile) level.getBlockEntity(ALTAR.offset(PILLARS.get(index)))).setStack(new ItemStack(items[index]));
    }

    private static VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }

    private static void spectralLensWaitsForTheBeholder(ClientGameTestContext context, TestServerContext server)
    {
        expect(server.computeOnServer(minecraft -> !discoverable(minecraft, SPECTRAL_LENS)), "Expected the spectral lens hidden until the Beholder is researched");
        server.runCommand("execute as @a run voidheart research grant voidheart:beholder");
        context.waitTicks(3);
        expect(server.computeOnServer(minecraft -> discoverable(minecraft, SPECTRAL_LENS)), "Expected the spectral lens in sight once the Beholder is researched");
    }
}
