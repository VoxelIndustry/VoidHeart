package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public record EssenceType(Identifier identifier, String translatableName, TagKey<Block> consumables, ItemStack catalyst)
{
}
