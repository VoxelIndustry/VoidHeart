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
        if (state == null)
            return stack;
        int soaked = CrystalBundles.soakOf(state);
        // Taken whole it keeps what it soaked; broken, a bundle that has drawn any taint in breaks into Fouled shards, so soaking can't start over
        if (CrystalBundles.bundleOf(stack) != null)
            return CrystalBundles.withSoak(CrystalBundles.withHabit(stack, CrystalBundles.habitOf(state)), soaked);
        return CrystalBundles.withHabit(stack, soaked > 0 ? CrystalHabit.FOULED : CrystalBundles.habitOf(state));
    }

    public static Builder<?> copy()
    {
        return simpleBuilder(CopyCrystalHabitFunction::new);
    }
}
