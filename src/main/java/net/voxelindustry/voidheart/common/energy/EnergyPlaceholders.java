package net.voxelindustry.voidheart.common.energy;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.voxelindustry.voidheart.common.setup.VoidHeartBlocks;
import net.voxelindustry.voidheart.common.setup.VoidHeartItems;

/**
 * Plain stand-ins for energy content, so names, the creative tab and research unlocks work before each package builds the real thing.
 * A package replaces its own by registering its real block or item under the same id instead.
 */
public final class EnergyPlaceholders
{
    private EnergyPlaceholders()
    {
    }

    public static Block block(String id, MapColor color)
    {
        return VoidHeartBlocks.registerBlock(id, Block::new, BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.STONE), true);
    }

    public static Item item(String id)
    {
        return VoidHeartItems.registerItem(id, Item::new);
    }
}
