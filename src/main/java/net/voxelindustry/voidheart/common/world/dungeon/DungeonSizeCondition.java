package net.voxelindustry.voidheart.common.world.dungeon;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;
import java.util.Set;

/**
 * Passes for loot rolled inside a void dungeon of one of the given sizes, so one chest table per theme can still give more to larger dungeons.
 * The dungeon is found from where the loot is rolled, as each one has its own slot of the dungeon dimension.
 */
public record DungeonSizeCondition(List<DungeonSize> sizes) implements LootItemCondition
{
    public static final MapCodec<DungeonSizeCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(DungeonSize::values).listOf().fieldOf("sizes").forGetter(DungeonSizeCondition::sizes)
    ).apply(instance, DungeonSizeCondition::new));

    @Override
    public MapCodec<DungeonSizeCondition> codec()
    {
        return MAP_CODEC;
    }

    @Override
    public Set<ContextKey<?>> getReferencedContextParams()
    {
        return Set.of(LootContextParams.ORIGIN);
    }

    @Override
    public boolean test(LootContext context)
    {
        var origin = context.getOptional(LootContextParams.ORIGIN);
        if (origin == null || !context.getLevel().dimension().equals(VoidDungeons.WORLD_KEY))
            return false;

        var address = DungeonAddress.at(Mth.floor(origin.x), Mth.floor(origin.z));
        return address != null && sizes.contains(address.size());
    }
}
