package net.voxelindustry.voidheart.common.content.essence;

import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * @param consumables blocks a collector attuned to it harvests, null when it is not gathered from blocks
 * @param catalyst    item attuning a collector to it, null when no collector can be attuned to it
 */
public record EssenceType(Identifier identifier, String translatableName, @Nullable TagKey<Block> consumables, @Nullable Item catalyst)
{
}
