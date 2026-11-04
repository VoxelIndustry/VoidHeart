package net.voxelindustry.voidheart.common.world.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootTable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Writes the part of a dungeon falling in one chunk. Runs on world generation threads, so it reads the plan and writes only to this chunk.
 */
final class DungeonPlacer
{
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private DungeonPlacer()
    {
    }

    static void place(WorldGenLevel level, ChunkAccess chunk)
    {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        var address = DungeonAddress.at(minX, minZ);
        if (address == null)
            return;

        var plan = VoidDungeons.plan(level.getSeed(), address);
        var origin = address.origin();
        var random = RandomSource.create(plan.blueprint().seed() ^ chunk.getPos().pack());
        var lootTable = lootTable(address.theme());
        boolean hasTemplates = placeTemplates(level, plan, origin, minX, minZ, random, lootTable);
        placeCells(level, chunk, plan, origin, minX, minZ, random, lootTable, hasTemplates);
    }

    static ResourceKey<LootTable> lootTable(DungeonTheme theme)
    {
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(MODID, "chests/dungeon_" + theme.getSerializedName()));
    }

    private static void placeCells(WorldGenLevel level,
                                   ChunkAccess chunk,
                                   DungeonPlan plan,
                                   BlockPos origin,
                                   int minX,
                                   int minZ,
                                   RandomSource random,
                                   ResourceKey<LootTable> lootTable,
                                   boolean hasTemplates)
    {
        var grid = plan.grid();
        var palette = DungeonPalette.of(plan.blueprint().theme());
        var pos = new BlockPos.MutableBlockPos();
        int localX = minX - origin.getX();
        int localZ = minZ - origin.getZ();

        for (int y = 0; y < grid.height; y++)
        {
            int worldY = origin.getY() + y;
            for (int z = 0; z < 16; z++)
            {
                for (int x = 0; x < 16; x++)
                {
                    short cell = grid.cell(localX + x, y, localZ + z);
                    var material = DungeonCell.material(cell);
                    if (material == DungeonMaterial.UNSET || material == DungeonMaterial.TEMPLATE)
                        continue;

                    pos.set(minX + x, worldY, minZ + z);
                    if (material == DungeonMaterial.AIR)
                    {
                        if (hasTemplates)
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                        continue;
                    }

                    var state = palette.state(cell, pos.getX(), worldY, pos.getZ());
                    if (state.hasBlockEntity())
                        level.setBlock(pos, state, FLAGS);
                    else
                        chunk.setBlockState(pos, state, 0);

                    if (material.connects())
                        chunk.markPosForPostProcessing(pos);
                    if (material == DungeonMaterial.CHEST)
                        RandomizableContainer.setBlockEntityLootTable(level, random, pos, lootTable);
                }
            }
        }
    }

    private static boolean placeTemplates(WorldGenLevel level,
                                          DungeonPlan plan,
                                          BlockPos origin,
                                          int minX,
                                          int minZ,
                                          RandomSource random,
                                          ResourceKey<LootTable> lootTable)
    {
        var box = new BoundingBox(minX, origin.getY(), minZ, minX + 15, origin.getY() + plan.grid().height - 1, minZ + 15);
        boolean any = false;
        for (var placement : plan.templates())
        {
            var template = placement.template();
            var size = template.size();
            int x = origin.getX() + placement.x();
            int y = origin.getY() + placement.y();
            int z = origin.getZ() + placement.z();
            if (x > box.maxX() || x + size.getX() <= box.minX() || z > box.maxZ() || z + size.getZ() <= box.minZ())
                continue;

            var manager = level.getServer().getStructureTemplateManager();
            var structure = manager.get(Identifier.fromNamespaceAndPath(MODID, template.file()));
            if (structure.isEmpty())
                continue;

            any = true;
            var from = template.from();
            var at = new BlockPos(x - from.getX(), y - from.getY(), z - from.getZ());
            var settings = new StructurePlaceSettings()
                    .setBoundingBox(box)
                    .setIgnoreEntities(true)
                    .addProcessor(new DungeonTemplateProcessor(template, DungeonPalette.of(plan.blueprint().theme()), lootTable.identifier().toString(), plan.blueprint().seed()));
            structure.get().placeInWorld(level, at, at, settings, random, FLAGS);
        }
        return any;
    }
}
