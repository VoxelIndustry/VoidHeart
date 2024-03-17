package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

public record AltarCraftingSyncElement(ItemStack result,
                                       List<ItemStack> toConsume) implements TileSyncElement<AltarCraftingSyncElement>
{
    public static final Identifier IDENTIFIER = new Identifier(MODID, "altar_crafting_sync");

    public static final Codec<AltarCraftingSyncElement> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    ItemUtils.FIXED_ITEMSTACK_CODEC.fieldOf("result")
                            .forGetter(AltarCraftingSyncElement::result),
                    ItemUtils.ITEMSTACK_LIST_CODEC.fieldOf("toConsume")
                            .forGetter(AltarCraftingSyncElement::toConsume)
            ).apply(instance, AltarCraftingSyncElement::new));

    @Override
    public Identifier getIdentifier()
    {
        return IDENTIFIER;
    }

    @Override
    public Codec<AltarCraftingSyncElement> getCodec()
    {
        return CODEC;
    }
}
