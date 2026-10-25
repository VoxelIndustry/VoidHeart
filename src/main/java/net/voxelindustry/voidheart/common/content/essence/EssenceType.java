package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public record EssenceType(ResourceLocation identifier, String translatableName, TagKey<Block> consumables, ItemStack catalyst)
{
}
