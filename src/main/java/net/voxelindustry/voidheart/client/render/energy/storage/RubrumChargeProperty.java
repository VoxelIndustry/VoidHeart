package net.voxelindustry.voidheart.client.render.energy.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.energy.storage.RubrumCellItem;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Item model property {@code voidheart:rubrum_charge}: how full a rubrum cell is, from 0 to 1, which picks its texture.
 */
public record RubrumChargeProperty() implements RangeSelectItemModelProperty
{
    public static final Identifier                     ID        = Identifier.fromNamespaceAndPath(MODID, "rubrum_charge");
    public static final MapCodec<RubrumChargeProperty> MAP_CODEC = MapCodec.unit(new RubrumChargeProperty());

    /**
     * Both the client and datagen need the id known before item models are read or written.
     */
    public static void register()
    {
        RangeSelectItemModelProperties.ID_MAPPER.put(ID, MAP_CODEC);
    }

    @Override
    public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed)
    {
        return RubrumCellItem.fill(stack);
    }

    @Override
    public MapCodec<RubrumChargeProperty> type()
    {
        return MAP_CODEC;
    }
}
