package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public record EssenceType(Identifier identifier, String translatableName, TagKey<Block> consumables, ItemStack catalyst)
{
}
