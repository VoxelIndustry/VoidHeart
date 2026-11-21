package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CradleResearch;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalCradleTile;
import net.voxelindustry.voidheart.common.content.crystal.CrystalHabit;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.crystal.CrystalRecordings;
import net.voxelindustry.voidheart.common.content.oculus.OculusTableStructure;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameCoreTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalFrameTile;
import net.voxelindustry.voidheart.common.content.portalframe.PortalLinker;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.energy.storage.CellSocketTile;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import net.voxelindustry.voidheart.common.energy.storage.RubrumContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartDataComponents;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;
import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Crystallography's last step: a cradle runs on void energy, growing plain in the overworld and Deep in the void's dungeons, where Deep memoriam records
 * the place it grew in; set on an Oculus Table at home, the table shows that place. A Deep pylon's field reaches on through an open portal pair and
 * powers a socket beyond it.
 */
public class CrystalDeepClientTest implements FabricClientGameTest
{
    private static final int Y = FLAT_FEET;

    private static final BlockPos WELL_HOME   = new BlockPos(0, Y, -40);
    private static final BlockPos CRADLE_HOME = new BlockPos(3, Y, -40);

    // Far out in the dungeons' void, on a platform of our own
    private static final int      DEEP_Y        = 120;
    private static final BlockPos DEEP_CRADLE   = new BlockPos(4000, DEEP_Y, 4000);
    private static final BlockPos DEEP_WELL     = DEEP_CRADLE.offset(-3, 0, 0);
    private static final BlockPos DEEP_TOWER    = DEEP_CRADLE.offset(8, 0, 6);

    private static final BlockPos TABLE = new BlockPos(0, Y, 40);

    private static final BlockPos PORTAL_A = new BlockPos(100, Y, 0);
    private static final BlockPos PORTAL_B = new BlockPos(100, Y, 64);
    private static final BlockPos CORE_A   = PORTAL_A.offset(2, 0, 0);
    private static final BlockPos CORE_B   = PORTAL_B.offset(2, 0, 0);
    private static final BlockPos PYLON    = PORTAL_A.offset(2, 0, -5);
    private static final BlockPos REACH_WELL = PORTAL_A.offset(2, -2, -8);
    private static final BlockPos FAR_SOCKET = CORE_B.offset(0, 0, 4);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -20 -60 130 90");
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(minecraft ->
            {
                var knowledge = Knowledge.of(minecraft, player(minecraft).getUUID());
                knowledge.grant(CradleResearch.CRYSTAL_CRADLE);
                knowledge.grant(CradleResearch.researchFor(CrystalHabit.DEEP));
            });
            context.waitTicks(10);

            checkTranslations(context);
            checkTooltips(context);
            checkVoidGrowthAtHome(context, server);
            var memory = checkDeepRecording(context, server);
            checkTableMemory(context, server, memory);
            checkTableMerges(context, server, memory);
            checkDeepReach(context, server);
        }
    }

    private static String at(BlockPos pos)
    {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static void checkTranslations(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            var language = Language.getInstance();
            for (var key : List.of("research." + MODID + ".deep_growth", MODID + ".jade.cradle.fed.void_deep", MODID + ".oculus_table.memory.none",
                    MODID + ".recording.where.voidheart.dungeons", MODID + ".rei.habit.deep.memory"))
                expect(language.has(key), "Missing translation " + key);
        });
    }

    private static void checkTooltips(ClientGameTestContext context)
    {
        context.runOnClient(minecraft ->
        {
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.AZURIUM, BundleSize.LARGE, CrystalHabit.DEEP), "Deep", "Pylon: field reaches through portals");
            expectLines(minecraft, CrystalBundles.bundleItem(CrystalKind.MEMORIAM, BundleSize.SMALL, CrystalHabit.DEEP), "Oculus Table: shows where it grew",
                    "Records where it finishes growing");
        });
    }

    private static void expectLines(Minecraft minecraft, ItemStack stack, String... expected)
    {
        var lines = stack.getTooltipLines(Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
        for (var line : expected)
            expect(lines.contains(line), stack.getItem() + "'s tooltip should read \"" + line + "\": " + lines);
    }

    /**
     * Void energy grows a crystal at home too, but only plain: Deep belongs to the void's own places.
     */
    private static void checkVoidGrowthAtHome(ClientGameTestContext context, TestServerContext server)
    {
        server.runCommand("setblock " + at(WELL_HOME) + " voidheart:creative_void_well");
        server.runCommand("setblock " + at(CRADLE_HOME) + " voidheart:crystal_cradle");
        context.waitTicks(3);
        server.runOnServer(minecraft ->
        {
            var cradle = cradle(minecraft.overworld(), CRADLE_HOME);
            cradle.claim(player(minecraft).getUUID());
            cradle.seed(CrystalKind.AZURIUM, 1, CrystalHabit.NONE);
        });
        waitFor(context, server, 100, "A cradle by a void well should run on void energy",
                minecraft -> cradle(minecraft.overworld(), CRADLE_HOME).source() == CrystalCradleTile.Source.VOID);
        server.runOnServer(minecraft ->
        {
            var cradle = cradle(minecraft.overworld(), CRADLE_HOME);
            expect(cradle.feed() == CrystalHabit.PLAIN, "Void energy in the overworld should grow plain, got " + cradle.feed());
            expect(cradle.supply() > 0.99F, "A void well should feed the cradle fully, got " + cradle.supply());
        });
        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, CRADLE_HOME.getX() + 0.5, Y + 0.6, CRADLE_HOME.getZ() - 1.6, CRADLE_HOME.getX() + 0.5, Y + 0.25, CRADLE_HOME.getZ() + 0.5, 20);
        context.takeScreenshot("crystal-deep-void-pool-home");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }

    /**
     * In the dungeons, void energy grows memoriam Deep, and once grown it records the place: its land and the creatures standing in it.
     */
    private static ItemStack checkDeepRecording(ClientGameTestContext context, TestServerContext server)
    {
        String in = "execute in voidheart:dungeons run ";
        var from = DEEP_CRADLE.offset(-56, -1, -56);
        var to = DEEP_CRADLE.offset(56, -1, 56);
        server.runCommand(in + "forceload add " + from.getX() + " " + from.getZ() + " " + to.getX() + " " + to.getZ());
        for (int x = from.getX(); x <= to.getX(); x += 32)
        {
            for (int z = from.getZ(); z <= to.getZ(); z += 32)
                server.runCommand(in + "fill " + x + " " + from.getY() + " " + z + " " + Math.min(to.getX(), x + 31) + " " + from.getY() + " " + Math.min(to.getZ(), z + 31)
                                  + " minecraft:stone");
        }
        server.runCommand(in + "fill " + at(DEEP_TOWER) + " " + DEEP_TOWER.getX() + " " + (DEEP_Y + 6) + " " + DEEP_TOWER.getZ() + " minecraft:gold_block");
        server.runCommand(in + "setblock " + at(DEEP_WELL) + " voidheart:creative_void_well");
        server.runCommand(in + "setblock " + at(DEEP_CRADLE) + " voidheart:crystal_cradle");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var level = dungeons(minecraft);
            var cradle = cradle(level, DEEP_CRADLE);
            cradle.claim(player(minecraft).getUUID());
            cradle.seed(CrystalKind.MEMORIAM, 1, CrystalHabit.NONE);
            var pig = EntityTypes.PIG.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            pig.snapTo(DEEP_CRADLE.getX() - 6.5, DEEP_Y, DEEP_CRADLE.getZ() + 4.5, 0, 0);
            pig.setNoAi(true);
            level.addFreshEntity(pig);
        });
        waitFor(context, server, 200, "A cradle by a void well in the dungeons should grow Deep on void energy",
                minecraft -> cradle(dungeons(minecraft), DEEP_CRADLE).source() == CrystalCradleTile.Source.VOID
                             && cradle(dungeons(minecraft), DEEP_CRADLE).feed() == CrystalHabit.DEEP);

        return server.computeOnServer(minecraft ->
        {
            var cradle = cradle(dungeons(minecraft), DEEP_CRADLE);
            cradle.finish(CrystalHabit.DEEP);
            expect(cradle.recording() != null, "A Deep memoriam should record the place once grown");
            var items = cradle.harvest();
            expect(items.size() == 1, "Expected a single Deep memoriam, got " + items);
            var memory = items.getFirst();
            var ref = memory.get(VoidHeartDataComponents.CRYSTAL_RECORDING);
            expect(CrystalBundles.habitOf(memory) == CrystalHabit.DEEP && ref != null, "The bundle should carry its recording, got " + memory);
            var recording = CrystalRecordings.find(minecraft, ref.id());
            expect(recording != null && recording.dimension() == VoidDungeons.WORLD_KEY, "The recording should be kept on the server");
            expect(recording.chunks().size() == 49, "Within 48 blocks every way it should keep 7 by 7 chunks, kept " + recording.chunks().size());
            expect(recording.creatures().stream().anyMatch(creature -> creature.type() == EntityTypes.PIG), "The pig standing there should be recorded");
            return memory;
        });
    }

    private static ServerLevel dungeons(MinecraftServer minecraft)
    {
        var level = minecraft.getLevel(VoidDungeons.WORLD_KEY);
        if (level == null)
            throw new AssertionError("Expected the dungeons dimension");
        return level;
    }

    private static CrystalCradleTile cradle(ServerLevel level, BlockPos pos)
    {
        if (level.getBlockEntity(pos) instanceof CrystalCradleTile cradle)
            return cradle;
        throw new AssertionError("Expected a cradle at " + pos);
    }

    /**
     * Set on a table, the memory's place replaces the table's own land, and an empty hand takes it back.
     */
    private static void checkTableMemory(ClientGameTestContext context, TestServerContext server, ItemStack memory)
    {
        server.runCommand("fill " + (TABLE.getX() - 1) + " " + Y + " " + (TABLE.getZ() - 1) + " " + (TABLE.getX() + 1) + " " + Y + " " + (TABLE.getZ() + 1)
                          + " voidheart:oculus_table");
        context.waitTicks(10);
        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            // A recorded place can't be kept by a placed bundle, so it isn't placed
            var ground = TABLE.offset(4, -1, 0);
            player.setItemInHand(InteractionHand.MAIN_HAND, memory.copy());
            useOn(player, ground);
            expect(minecraft.overworld().getBlockState(ground.above()).isAir(), "A recorded Deep memoriam should not be placed as a block");
            player.setItemInHand(InteractionHand.MAIN_HAND, CrystalBundles.bundleItem(CrystalKind.MEMORIAM, BundleSize.SMALL, CrystalHabit.DEEP));
            useOn(player, TABLE);
            expect(OculusTableStructure.getTile(minecraft.overworld(), TABLE).getView().memory().isEmpty(),
                    "A Deep memoriam that recorded nothing should not be set on the table");
            player.setItemInHand(InteractionHand.MAIN_HAND, memory.copy());
            useOn(player, TABLE);
            var view = OculusTableStructure.getTile(minecraft.overworld(), TABLE.east()).getView();
            expect(view.memory().isPresent(), "The memory should be set on every block of the table");
        });

        // The recorded place, in a memory's cold monochrome, with the pig standing where it stood
        context.getInput().pressKey(InputConstants.KEY_F1);
        tp(server, TABLE.getX() + 0.5, Y, TABLE.getZ() - 3.5);
        context.waitTicks(60);
        aimFrom(context, server, TABLE.getX() + 0.5, Y + 1.2, TABLE.getZ() - 2.8, TABLE.getX() + 0.5, Y + 0.6, TABLE.getZ() + 0.5, 60);
        context.takeScreenshot("crystal-deep-table-memory");
        aimFrom(context, server, TABLE.getX() + 0.5, Y + 3.5, TABLE.getZ() - 0.5, TABLE.getX() + 0.5, Y + 0.6, TABLE.getZ() + 0.5, 10);
        context.takeScreenshot("crystal-deep-table-memory-top");
        context.getInput().pressKey(InputConstants.KEY_F1);

        server.runOnServer(minecraft ->
        {
            var player = player(minecraft);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            useOn(player, TABLE);
            expect(OculusTableStructure.getTile(minecraft.overworld(), TABLE).getView().memory().isEmpty(), "An empty hand should take the memory back");
            expect(player.getInventory().contains(stack -> stack.has(VoidHeartDataComponents.CRYSTAL_RECORDING)), "The memory should come back to the hand");
        });
    }

    /**
     * A memory is never left in a block beside the bundle it came back out as: not past the size a table projects at, and not when a block joins two
     * tables that each hold one, even copies of one recording.
     */
    private static void checkTableMerges(ClientGameTestContext context, TestServerContext server, ItemStack memory)
    {
        var big = new BlockPos(20, Y, 40);
        server.runCommand("fill " + at(big) + " " + (big.getX() + 9) + " " + Y + " " + (big.getZ() + 9) + " voidheart:oculus_table");
        var left = new BlockPos(40, Y, 40);
        var right = new BlockPos(44, Y, 40);
        for (var corner : new BlockPos[] { left, right })
            server.runCommand("fill " + at(corner) + " " + (corner.getX() + 2) + " " + Y + " " + (corner.getZ() + 2) + " voidheart:oculus_table");
        server.runCommand("kill @e[type=minecraft:item]");
        context.waitTicks(5);

        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            for (var at : new BlockPos[] { big, left, right })
            {
                player.setItemInHand(InteractionHand.MAIN_HAND, memory.copy());
                useOn(player, at);
            }
            var farCorner = big.offset(9, 0, 9);
            expect(OculusTableStructure.getTile(level, farCorner).getView().memory().isPresent(),
                    "A memory set on a table past the size it projects at should still reach its far corner");

            // The oversized table loses an end block: its memory falls once, and none of what remains keeps it
            level.removeBlock(big, false);
            expect(remembering(level, big, big.offset(9, 0, 9)) == 0, "No block of what remains should keep the memory that fell");
            expect(droppedMemories(level, big) == 1, "The memory should fall once, got " + droppedMemories(level, big));

            // A bridge between two tables each holding a copy: one stays shown, the other comes back out
            var bridge = new BlockPos(43, Y, 40);
            var state = VoidHeartBlocks.OCULUS_TABLE.defaultBlockState();
            level.setBlockAndUpdate(bridge, state);
            VoidHeartBlocks.OCULUS_TABLE.setPlacedBy(level, bridge, state, player, new ItemStack(VoidHeartBlocks.OCULUS_TABLE));
            expect(droppedMemories(level, bridge) == 1, "Joining two tables with a memory each should hand one back, got " + droppedMemories(level, bridge));
            expect(remembering(level, left, right.offset(2, 0, 2)) == 19, "Every block of the joined table should show the memory kept");
        });
        server.runCommand("kill @e[type=minecraft:item]");
    }

    /**
     * How many table blocks between the two corners still show a memory.
     */
    private static int remembering(ServerLevel level, BlockPos from, BlockPos to)
    {
        int count = 0;
        for (var pos : BlockPos.betweenClosed(from, to))
        {
            var tile = OculusTableStructure.getTile(level, pos);
            if (tile != null && tile.getView().memory().isPresent())
                count++;
        }
        return count;
    }

    private static int droppedMemories(ServerLevel level, BlockPos near)
    {
        return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(near).inflate(4),
                item -> item.getItem().has(VoidHeartDataComponents.CRYSTAL_RECORDING)).size();
    }

    /**
     * A Deep pylon's field reaches on through an open portal pair inside it: a socket past the far portal charges from it, where a plain pylon's never
     * reaches.
     */
    private static void checkDeepReach(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            for (var corner : new BlockPos[] { PORTAL_A, PORTAL_B })
            {
                for (int y = 0; y < 5; y++)
                {
                    for (int x = 0; x < 5; x++)
                    {
                        if (x == 0 || y == 0 || x == 4 || y == 4)
                            level.setBlockAndUpdate(corner.offset(x, y, 0), VoidHeartBlocks.PORTAL_FRAME.defaultBlockState());
                    }
                }
            }
        });
        server.runCommand("setblock " + at(REACH_WELL) + " voidheart:creative_void_well");
        server.runCommand("setblock " + at(PYLON) + " voidheart:azurium_pylon");
        server.runCommand("setblock " + at(PYLON.above()) + " voidheart:large_azurium_bundle[facing=up]");
        server.runCommand("setblock " + at(FAR_SOCKET) + " voidheart:cell_socket");
        server.runCommand("setblock " + at(FAR_SOCKET.below()) + " minecraft:redstone_block");
        context.waitTicks(5);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            var player = player(minecraft);
            var eye = new ItemStack(VoidHeartItems.PORTAL_CORE, 2);
            expect(PortalLinker.voidIronEyeInteract((PortalFrameTile) level.getBlockEntity(CORE_A), level, CORE_A, Direction.NORTH, player, eye), "The first portal should form");
            expect(PortalLinker.voidIronEyeInteract((PortalFrameTile) level.getBlockEntity(CORE_B), level, CORE_B, Direction.NORTH, player, eye), "The second portal should link");
            ((CellSocketTile) level.getBlockEntity(FAR_SOCKET)).setCell(new ItemStack(RubrumContent.RUBRUM_CELL));
        });
        context.waitTicks(120);
        server.runOnServer(minecraft ->
        {
            var level = minecraft.overworld();
            expect(level.getBlockEntity(CORE_A) instanceof PortalFrameCoreTile core && core.isLinked() && !core.isDark(), "The pair should be open");
            expect(RubrumCellItem.charge(((CellSocketTile) level.getBlockEntity(FAR_SOCKET)).getCell()) == 0,
                    "A plain pylon's field should not reach through the portal");
        });

        server.runCommand("setblock " + at(PYLON.above()) + " voidheart:large_azurium_bundle[facing=up,habit=deep]");
        waitFor(context, server, 200, "A Deep pylon's field should reach through the portal and charge the socket beyond",
                minecraft -> RubrumCellItem.charge(((CellSocketTile) minecraft.overworld().getBlockEntity(FAR_SOCKET)).getCell()) >= 1_000);
        // The far portal looks for reaches coming out of it once a second
        waitFor(context, server, 60, "The far portal should know the reach coming out of it",
                minecraft -> ((PortalFrameCoreTile) minecraft.overworld().getBlockEntity(CORE_B)).getDeepReach() > 0);

        context.getInput().pressKey(InputConstants.KEY_F1);
        aimFrom(context, server, PYLON.getX() + 4.5, Y + 1.5, PYLON.getZ() - 3.5, CORE_A.getX() + 0.5, Y + 1.5, CORE_A.getZ() + 0.5, 30);
        context.takeScreenshot("crystal-deep-reach-thread");
        tp(server, CORE_B.getX() + 0.5, Y + 10, CORE_B.getZ() + 14.5);
        context.waitTicks(40);
        aimFrom(context, server, CORE_B.getX() + 0.5, Y + 9, CORE_B.getZ() + 16.5, CORE_B.getX() + 0.5, Y, CORE_B.getZ() + 2.5, 30);
        context.takeScreenshot("crystal-deep-reach-far-ring");
        context.getInput().pressKey(InputConstants.KEY_F1);
    }
}
