package net.voxelindustry.voidheart.common.content.crystal;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/**
 * Gives a broken bundle's drops its habit, whether it comes away whole or as shards.
 */
public class CopyCrystalHabitFunction extends LootItemConditionalFunction
{
    public static final MapCodec<CopyCrystalHabitFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .apply(instance, CopyCrystalHabitFunction::new));

    private CopyCrystalHabitFunction(Optional<Holder<LootItemCondition>> condition)
    {
        super(condition);
    }

    @Override
    public MapCodec<CopyCrystalHabitFunction> codec()
    {
        return MAP_CODEC;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context)
    {
        var state = context.getOptional(LootContextParams.BLOCK_STATE);
        return state == null ? stack : CrystalBundles.withHabit(stack, CrystalBundles.habitOf(state));
    }

    public static Builder<?> copy()
    {
        return simpleBuilder(CopyCrystalHabitFunction::new);
    }
}
