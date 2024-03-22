package net.voxelindustry.voidheart.common.content.shatterforge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public record ShatterForgeCraftingSyncElement(ItemStack ingredient,
                                              ItemStack result) implements TileSyncElement<ShatterForgeCraftingSyncElement>
{
    public static final Identifier IDENTIFIER = new Identifier(MODID, "shatter_forge_crafting_sync");

    public static final Codec<ShatterForgeCraftingSyncElement> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    ItemUtils.FIXED_ITEMSTACK_CODEC.fieldOf("ingredient")
                            .forGetter(ShatterForgeCraftingSyncElement::ingredient),
                    ItemUtils.FIXED_ITEMSTACK_CODEC.fieldOf("result")
                            .forGetter(ShatterForgeCraftingSyncElement::result)
            ).apply(instance, ShatterForgeCraftingSyncElement::new));

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER;
    }

    @Override
    public Codec<ShatterForgeCraftingSyncElement> getCodec()
    {
        return CODEC;
    }
}
