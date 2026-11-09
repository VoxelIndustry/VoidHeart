package net.voxelindustry.voidheart.data.energy;

import net.minecraft.data.tags.TagAppender;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * The block tag builder, handed to a package's datagen. Blocks that need a pickaxe join its tag on their own through registerBlock.
 */
@FunctionalInterface
public interface EnergyTags
{
    TagAppender<Block> builder(TagKey<Block> tag);
}
