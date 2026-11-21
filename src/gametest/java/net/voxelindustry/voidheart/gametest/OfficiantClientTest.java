package net.voxelindustry.voidheart.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.voxelindustry.voidheart.VoidHeart;
import net.voxelindustry.voidheart.common.content.crystal.BundleSize;
import net.voxelindustry.voidheart.common.content.crystal.CrystalBundles;
import net.voxelindustry.voidheart.common.content.crystal.CrystalKind;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.RavenousCollectorTile;
import net.voxelindustry.voidheart.common.content.hoard.HoardContent;
import net.voxelindustry.voidheart.common.content.hoard.HoardCofferTile;
import net.voxelindustry.voidheart.common.content.hoard.PocketHoard;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.content.shatterforge.ShatterForgeLayout;
import net.voxelindustry.voidheart.common.content.works.OfficiantTile;
import net.voxelindustry.voidheart.common.content.works.WorksContent;
import net.voxelindustry.voidheart.common.content.works.WorksState;
import net.voxelindustry.voidheart.common.energy.creative.CreativeEnergyContent;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.VoidPocketState;

import java.util.List;

import static net.voxelindustry.voidheart.gametest.GameTestKit.*;

/**
 * Officiants work stations from the hoard for what is ordered: a crafting table cuts ender pearls into shards, a Void Pearl ordered from raw
 * obsidian and an ender pearl is made through the table and the altar, and a forge pinned by its Officiant makes Seething Steel.
 */
public class OfficiantClientTest implements FabricClientGameTest
{
    private static final BlockPos WELL   = new BlockPos(0, FLAT_FEET - 3, 0);
    private static final BlockPos TABLE  = new BlockPos(8, FLAT_FEET, -6);
    private static final BlockPos ALTAR  = new BlockPos(0, FLAT_FEET, 8);
    private static final List<BlockPos> PILLARS = List.of(
            new BlockPos(-1, 0, 3), new BlockPos(1, 0, 3), new BlockPos(1, 0, -3), new BlockPos(-1, 0, -3),
            new BlockPos(3, 0, -1), new BlockPos(3, 0, 1), new BlockPos(-3, 0, -1), new BlockPos(-3, 0, 1));
    private static final BlockPos FORGE  = new BlockPos(-8, FLAT_FEET, 4);
    private static final Direction TRACK = Direction.NORTH;
    private static final int TRACK_PAIRS = 2;
    private static final BlockPos SOURCE = ShatterForgeLayout.endSlot(FORGE, TRACK, TRACK_PAIRS);

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            freezeTime(server, "noon");
            server.runCommand("gamemode creative @a");
            context.waitTicks(20);
            context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));

            server.runOnServer(OfficiantClientTest::buildBase);
            // Officiants make their stations known on their first ticks
            context.waitTicks(25);
            checkTable(context, server);
            checkAltarChain(context, server);
            checkForge(context, server);
        }
    }

    private static PocketHoard.Hoard hoard(MinecraftServer minecraft)
    {
        return PocketHoard.get(minecraft).of(player(minecraft).getUUID());
    }

    private static long held(MinecraftServer minecraft, net.minecraft.world.level.ItemLike item)
    {
        return hoard(minecraft).count(ItemVariant.of(item));
    }

    private static void officiant(ServerLevel level, BlockPos pos, MinecraftServer minecraft)
    {
        level.setBlockAndUpdate(pos, WorksContent.OFFICIANT.defaultBlockState());
        ((OfficiantTile) level.getBlockEntity(pos)).setOwner(player(minecraft).getUUID());
    }

    /**
     * A pocket with a large coffer, a field covering every station, and an Officiant under each.
     */
    private static void buildBase(MinecraftServer minecraft)
    {
        var owner = player(minecraft).getUUID();
        var pockets = VoidPocketState.getVoidPocketState(minecraft.overworld());
        pockets.createPocket(minecraft.getLevel(VoidHeart.VOID_WORLD_KEY), owner);
        var voidLevel = minecraft.getLevel(VoidHeart.VOID_WORLD_KEY);
        var coffer = pockets.getHeartData(owner).pocketPos().offset(3, 0, 3);
        voidLevel.setBlockAndUpdate(coffer, HoardContent.HOARD_COFFER.defaultBlockState());
        ((HoardCofferTile) voidLevel.getBlockEntity(coffer)).setBundle(new ItemStack(CrystalBundles.bundle(CrystalKind.RUBRUM, BundleSize.LARGE)));

        var level = minecraft.overworld();
        level.setBlockAndUpdate(WELL, CreativeEnergyContent.CREATIVE_VOID_WELL.defaultBlockState());

        level.setBlockAndUpdate(TABLE, Blocks.CRAFTING_TABLE.defaultBlockState());
        officiant(level, TABLE.below(), minecraft);

        level.setBlockAndUpdate(ALTAR, VoidHeartBlocks.VOID_ALTAR.defaultBlockState());
        for (var offset : PILLARS)
            level.setBlockAndUpdate(ALTAR.offset(offset), VoidHeartBlocks.VOID_PILLAR.defaultBlockState());
        officiant(level, ALTAR.below(), minecraft);

        level.setBlockAndUpdate(FORGE, VoidHeartBlocks.SHATTER_FORGE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z));
        for (var expected : ShatterForgeLayout.expected(FORGE, TRACK, TRACK_PAIRS))
        {
            var block = switch (expected.part())
            {
                case CONDUIT -> VoidHeartBlocks.VOID_CONDUIT;
                case PILLAR -> VoidHeartBlocks.VOID_PILLAR;
                default -> VoidHeartBlocks.VOID_MONOLITH;
            };
            level.setBlockAndUpdate(expected.pos(), block.defaultBlockState());
        }
        for (var spot : ShatterForgeLayout.optionalCollectors(FORGE, TRACK, TRACK_PAIRS))
        {
            level.setBlockAndUpdate(spot, VoidHeartBlocks.RAVENOUS_COLLECTOR.defaultBlockState());
            var collector = (RavenousCollectorTile) level.getBlockEntity(spot);
            collector.storage().attune(EssenceRegistry.FIERY);
            collector.storage().fill(EssenceRegistry.FIERY, RavenousCollectorTile.CAPACITY);
        }
        officiant(level, SOURCE.below(), minecraft);
    }

    /**
     * Four ender shards ordered from one ender pearl in the hoard come from the table's Officiant.
     */
    private static void checkTable(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.ENDER_SHARD), 4);
            expect(plan.isComplete() && plan.tasks().size() == 1, "the ender shards planned " + plan.tasks() + ", lacking " + plan.lacking());
        });
        waitFor(context, server, 200, "the table's Officiant made no ender shards", minecraft -> held(minecraft, VoidHeartItems.ENDER_SHARD) >= 4);
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.ENDER_PEARL) == 0, "the ender pearl is still in the hoard");
            expect(WorksState.get(minecraft).of(player(minecraft).getUUID()).jobs().isEmpty(), "the order is still queued");
            hoard(minecraft).extract(ItemVariant.of(VoidHeartItems.ENDER_SHARD), held(minecraft, VoidHeartItems.ENDER_SHARD));
        });
    }

    /**
     * A Void Pearl ordered from raw obsidian and an ender pearl: the table cuts both into shards, then the altar lays them out and makes the pearl.
     */
    private static void checkAltarChain(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            var pearl = id("altar/void_pearl");
            Knowledge.lockOf(minecraft.registryAccess(), pearl).ifPresent(knowledge(minecraft)::grant);
            hoard(minecraft).insert(ItemVariant.of(Items.ENDER_PEARL), 1);
            hoard(minecraft).insert(ItemVariant.of(Items.OBSIDIAN), 2);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.VOID_PEARL), 1);
            expect(plan.isComplete(), "the Void Pearl plan lacks " + plan.lacking());
        });

        aimFrom(context, server, ALTAR.getX() + 0.5, FLAT_FEET + 2, ALTAR.getZ() - 6.5, ALTAR.getX() + 0.5, FLAT_FEET + 0.5, ALTAR.getZ() + 0.5, 5);
        waitFor(context, server, 600, "the altar never started the Void Pearl", minecraft -> altar(minecraft).isCrafting());
        context.getInput().pressKey(InputConstants.KEY_F1);
        context.takeScreenshot("officiant-altar-rite");
        context.getInput().pressKey(InputConstants.KEY_F1);

        waitFor(context, server, 2000, "no Void Pearl reached the hoard", minecraft -> held(minecraft, VoidHeartItems.VOID_PEARL) == 1);
        server.runOnServer(minecraft ->
        {
            expect(held(minecraft, Items.OBSIDIAN) == 0 && held(minecraft, Items.ENDER_PEARL) == 0, "raw materials left in the hoard: " + hoard(minecraft).view());
            expect(altar(minecraft).isVacant(), "the altar still holds " + altar(minecraft).getStack());
        });
    }

    /**
     * Seething Steel ordered from an iron ingot: the forge's Officiant sets the iron on the source pillar, pinned, and takes the steel.
     */
    private static void checkForge(ClientGameTestContext context, TestServerContext server)
    {
        server.runOnServer(minecraft ->
        {
            hoard(minecraft).insert(ItemVariant.of(Items.IRON_INGOT), 1);
            var plan = WorksContent.order(minecraft, player(minecraft).getUUID(), ItemVariant.of(VoidHeartItems.SEETHING_STEEL_INGOT), 1);
            expect(plan.isComplete(), "the Seething Steel plan lacks " + plan.lacking() + ", tasks " + plan.tasks());
        });
        waitFor(context, server, 900, "no Seething Steel reached the hoard", minecraft -> held(minecraft, VoidHeartItems.SEETHING_STEEL_INGOT) == 1);
        server.runOnServer(minecraft -> expect(held(minecraft, Items.IRON_INGOT) == 0, "the iron is still in the hoard"));
    }

    private static net.voxelindustry.voidheart.common.content.altar.VoidAltarTile altar(MinecraftServer minecraft)
    {
        return (net.voxelindustry.voidheart.common.content.altar.VoidAltarTile) minecraft.overworld().getBlockEntity(ALTAR);
    }
}
