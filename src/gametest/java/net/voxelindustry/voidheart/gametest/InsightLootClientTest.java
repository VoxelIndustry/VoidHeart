package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.Knowledge;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;
import net.voxelindustry.voidheart.common.world.daedalus.DistrictPos;
import net.voxelindustry.voidheart.common.world.daedalus.VoidDaedalus;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonAddress;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonSize;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;
import net.voxelindustry.voidheart.common.world.dungeon.VoidDungeons;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Insight loot: rolled from real chest tables by where the chest stands, dropped by the Dragoon, stashed in Daedalus caches, and taken in by use.
 */
public class InsightLootClientTest implements FabricClientGameTest
{
    private static final int ROLLS = 600;
    private static final ResourceKey<LootTable> CACHE = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, "chests/daedalus_cache"));

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            context.waitTicks(10);

            server.runOnServer(minecraft ->
            {
                var dungeons = minecraft.getLevel(VoidDungeons.WORLD_KEY);
                expect(dungeons != null, "The dungeon dimension should exist");
                var table = table(minecraft, "chests/dungeon_cathedral");

                checkShare(table, dungeons, inside(DungeonSize.SMALL), VoidHeartItems.IDEA_ABOUT_THE_VOID, 0.5, 0.7, "small");
                checkShare(table, dungeons, inside(DungeonSize.MEDIUM), VoidHeartItems.IDEA_ABOUT_THE_VOID, 0.22, 0.38, "medium");
                checkShare(table, dungeons, inside(DungeonSize.LARGE), VoidHeartItems.SPARK_OF_UNDERSTANDING, 0.07, 0.17, "large");
                checkShare(table, dungeons, inside(DungeonSize.LARGE), VoidHeartItems.IDEA_ABOUT_THE_VOID, 0, 0, "large ideas");
                checkShare(table, dungeons, inside(DungeonSize.SMALL), VoidHeartItems.SPARK_OF_UNDERSTANDING, 0, 0, "small sparks");
                checkShare(table, minecraft.overworld(), inside(DungeonSize.SMALL), VoidHeartItems.IDEA_ABOUT_THE_VOID, 0, 0, "outside the dungeons");

                checkShare(minecraft.reloadableRegistries().getLootTable(CACHE), VoidDaedalus.level(minecraft), Vec3.ZERO, VoidHeartItems.SPARK_OF_UNDERSTANDING, 0.4, 0.6, "Daedalus cache");
            });

            // The Dragoon always carries a breakthrough
            server.runCommand("summon voidheart:kernacian_dragoon 4 -60 4");
            context.waitTicks(5);
            server.runCommand("kill @e[type=voidheart:kernacian_dragoon]");
            context.waitTicks(30);
            server.runOnServer(minecraft ->
            {
                var drops = minecraft.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(new BlockPos(4, -60, 4)).inflate(8));
                expect(drops.stream().anyMatch(item -> item.getItem().is(VoidHeartItems.TAINTED_BREAKTHROUGH)), "A slain Dragoon should drop a Tainted Breakthrough, dropped " + drops);
                drops.forEach(ItemEntity::discard);
            });

            // Taken in by use, for between its least and most, and in survival only one is used up
            server.runCommand("gamemode survival @a");
            context.waitTicks(5);
            server.runOnServer(minecraft ->
            {
                var player = minecraft.getPlayerList().getPlayers().getFirst();
                var data = Knowledge.of(minecraft, player.getUUID());
                int before = data.getInsights();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VoidHeartItems.TAINTED_BREAKTHROUGH, 2));
                VoidHeartItems.TAINTED_BREAKTHROUGH.use(player.level(), player, InteractionHand.MAIN_HAND);
                int gained = Knowledge.of(minecraft, player.getUUID()).getInsights() - before;
                expect(gained >= 10 && gained <= 15, "A Tainted Breakthrough should give 10 to 15 insights, gave " + gained);
                expect(player.getMainHandItem().getCount() == 1, "Using one should take only one");
            });

            // A district generated for real holds caches whose barrels roll the cache table
            server.runOnServer(minecraft ->
            {
                var level = VoidDaedalus.level(minecraft);
                int caches = 0;
                for (int districtX = 0; districtX < 2; districtX++)
                    caches += cachesIn(level, new DistrictPos(districtX, 0));
                System.out.println("[Insights] caches in two generated districts: " + caches);
                expect(caches > 0, "Two districts should hold at least one cache");
            });
        }
    }

    /** Somewhere inside the first dungeon of that size, as the chest would be. */
    private static Vec3 inside(DungeonSize size)
    {
        var origin = new DungeonAddress(size, DungeonTheme.CATHEDRAL, DungeonAddress.MIN_DIFFICULTY, 0).origin();
        var at = new Vec3(origin.getX() + 8.5, origin.getY(), origin.getZ() + 8.5);
        var found = DungeonAddress.at(origin.getX() + 8, origin.getZ() + 8);
        expect(found != null && found.size() == size, "The test spot should be inside a " + size + " dungeon, found " + found);
        return at;
    }

    private static void checkShare(LootTable table, ServerLevel level, Vec3 origin, Item item, double least, double most, String what)
    {
        int found = 0;
        for (int roll = 0; roll < ROLLS; roll++)
        {
            var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, origin).create(LootContextParamSets.CHEST);
            for (var stack : table.getRandomItems(params))
                if (stack.is(item))
                    found += stack.getCount();
        }
        double share = found / (double) ROLLS;
        System.out.println("[Insights] " + what + ": " + item + " in " + share + " of chests");
        expect(share >= least && share <= most, "Expected " + item + " in " + least + " to " + most + " of " + what + " chests, got " + share);
    }

    private static int cachesIn(ServerLevel level, DistrictPos district)
    {
        int caches = 0;
        int fromX = district.originX() >> 4;
        int fromZ = district.originZ() >> 4;
        int chunks = DistrictPos.SIZE >> 4;
        for (int cz = fromZ; cz < fromZ + chunks; cz++)
        {
            for (int cx = fromX; cx < fromX + chunks; cx++)
            {
                var chunk = level.getChunk(cx, cz);
                for (var entity : chunk.getBlockEntities().values())
                {
                    if (entity instanceof RandomizableContainerBlockEntity container && entity.getBlockState().is(Blocks.BARREL))
                    {
                        expect(CACHE.equals(container.getLootTable()), "A barrel in the Daedalus at " + entity.getBlockPos() + " should roll the cache table, it rolls " + container.getLootTable());
                        caches++;
                    }
                }
            }
        }
        return caches;
    }

    private static LootTable table(MinecraftServer minecraft, String path)
    {
        var table = minecraft.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, path)));
        expect(table != LootTable.EMPTY, "Missing loot table " + path);
        return table;
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
