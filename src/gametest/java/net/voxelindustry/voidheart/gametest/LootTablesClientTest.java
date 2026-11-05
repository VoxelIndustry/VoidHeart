package net.voxelindustry.voidheart.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.voxelindustry.voidheart.common.content.research.KnowledgeColumnBlock;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.world.dungeon.DungeonTheme;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * What the mod's loot tables really hand out, since a field the game does not read fails silently and a table that cannot be read is simply empty.
 */
public class LootTablesClientTest implements FabricClientGameTest
{
    private static final int FEET = -60;
    private static final int CHEST_ROLLS = 40;
    private static final int EXPLOSION_ROLLS = 400;
    private static final float EXPLOSION_RADIUS = 8;
    private static final String FOLDER = "loot_table/";
    private static final String EXTENSION = ".json";

    @Override
    public void runTest(ClientGameTestContext context)
    {
        try (var world = context.worldBuilder().create())
        {
            var server = world.getServer();
            world.getConnection().waitForChunksRender();
            context.waitTicks(10);
            server.runOnServer(minecraft ->
            {
                checkEveryTableLoads(minecraft);
                checkChests(minecraft);
                checkExplosions(minecraft);
                checkKnowledgeColumn(minecraft);
            });
        }
    }

    private static void checkEveryTableLoads(MinecraftServer minecraft)
    {
        var files = minecraft.getResourceManager().listResources("loot_table", id -> id.getNamespace().equals(MODID) && id.getPath().endsWith(EXTENSION));
        expect(!files.isEmpty(), "The mod should have loot tables");
        for (var file : files.keySet())
        {
            var path = file.getPath().substring(FOLDER.length(), file.getPath().length() - EXTENSION.length());
            var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, path));
            expect(minecraft.reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "A loot table failed to load: " + key);
        }
    }

    /**
     * Every theme fills chests with stacks rather than single items.
     */
    private static void checkChests(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        for (var theme : DungeonTheme.values())
        {
            var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, "chests/dungeon_" + theme.getSerializedName()));
            var table = minecraft.reloadableRegistries().getLootTable(key);
            expect(table != LootTable.EMPTY, "No chest table for " + theme);

            int largest = 0;
            int items = 0;
            for (int roll = 0; roll < CHEST_ROLLS; roll++)
            {
                var params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
                for (var stack : table.getRandomItems(params))
                {
                    largest = Math.max(largest, stack.getCount());
                    items += stack.getCount();
                }
            }
            System.out.println("[Loot] " + theme + " chests hold " + (items / (double) CHEST_ROLLS) + " items on average, largest stack " + largest);
            expect(largest > 1, "Chests in " + theme + " should hold stacks, the largest was " + largest);
        }
    }

    private static void checkExplosions(MinecraftServer minecraft)
    {
        var level = minecraft.overworld();
        var state = VoidHeartBlocks.VOIDSTONE.defaultBlockState();

        var mined = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.ZERO).withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
        expect(state.getDrops(mined).size() == 1, "A mined block should always drop");

        int survived = 0;
        for (int roll = 0; roll < EXPLOSION_ROLLS; roll++)
        {
            var blown = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withParameter(LootContextParams.EXPLOSION_RADIUS, EXPLOSION_RADIUS);
            if (!state.getDrops(blown).isEmpty())
                survived++;
        }
        double expected = EXPLOSION_ROLLS / EXPLOSION_RADIUS;
        double spread = 4.5 * Math.sqrt(expected * (1 - 1 / EXPLOSION_RADIUS));
        expect(Math.abs(survived - expected) < spread, "A block should survive an explosion about " + expected + " times, did " + survived);
    }

    /**
     * A column block holds up to four columns, and gives back as many as it holds.
     */
    private static void checkKnowledgeColumn(MinecraftServer minecraft)
    {
        for (int quarters = 1; quarters <= 4; quarters++)
        {
            int dropped = breakColumn(minecraft, quarters);
            expect(dropped == quarters, "A block of " + quarters + " columns should drop " + quarters + ", dropped " + dropped);
        }
    }

    private static int breakColumn(MinecraftServer minecraft, int quarters)
    {
        var level = minecraft.overworld();
        var pos = new BlockPos(0, FEET + 20, 0);
        var state = VoidHeartBlocks.KNOWLEDGE_COLUMN.defaultBlockState()
                .setValue(KnowledgeColumnBlock.NORTH_WEST, quarters >= 1)
                .setValue(KnowledgeColumnBlock.NORTH_EAST, quarters >= 2)
                .setValue(KnowledgeColumnBlock.SOUTH_WEST, quarters >= 3)
                .setValue(KnowledgeColumnBlock.SOUTH_EAST, quarters >= 4);
        level.setBlockAndUpdate(pos, state);
        level.destroyBlock(pos, true);

        int count = 0;
        for (var item : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)))
        {
            count += item.getItem().getCount();
            item.discard();
        }
        return count;
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
            throw new AssertionError(message);
    }
}
