package net.voxelindustry.voidheart.common.content.altar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.voxelindustry.steamlayer.common.utils.ItemUtils;
import net.voxelindustry.steamlayer.network.tilesync.TileSyncElement;

import java.util.List;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

@Getter
@RequiredArgsConstructor
public class AltarCraftingSyncElement implements TileSyncElement<AltarCraftingSyncElement>
{
    public static final Identifier IDENTIFIER = new Identifier(MODID, "altar_crafting_sync");

    public static final Codec<AltarCraftingSyncElement> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    ItemUtils.FIXED_ITEMSTACK_CODEC.fieldOf("result")
                            .forGetter(AltarCraftingSyncElement::getResult),
                    ItemUtils.ITEMSTACK_LIST_CODEC.fieldOf("toConsume")
                            .forGetter(AltarCraftingSyncElement::getToConsume)
            ).apply(instance, AltarCraftingSyncElement::new));

    private final ItemStack       result;
    private final List<ItemStack> toConsume;

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
