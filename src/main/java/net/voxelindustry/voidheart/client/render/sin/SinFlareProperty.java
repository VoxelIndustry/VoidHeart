package net.voxelindustry.voidheart.client.render.sin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.voxelindustry.voidheart.common.content.sin.SinProficiencies;
import org.jspecify.annotations.Nullable;

import static net.voxelindustry.voidheart.VoidHeart.MODID;

/**
 * Item model property {@code voidheart:sin_flare}: whether a sin weapon's inlay is flaring, after its wielder passed a stage in its sin.
 */
public record SinFlareProperty() implements ConditionalItemModelProperty
{
    public static final Identifier                 ID        = Identifier.fromNamespaceAndPath(MODID, "sin_flare");
    public static final MapCodec<SinFlareProperty> MAP_CODEC = MapCodec.unit(new SinFlareProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext displayContext)
    {
        return SinFlare.isFlaring(SinProficiencies.sinOf(stack));
    }

    @Override
    public MapCodec<SinFlareProperty> type()
    {
        return MAP_CODEC;
    }
}
