package net.voxelindustry.voidheart.common.content.beam;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.voxelindustry.voidheart.common.content.essence.EssenceRegistry;
import net.voxelindustry.voidheart.common.content.essence.EssenceType;

import java.util.HashMap;
import java.util.Map;

/**
 * Energy carried by a beam: an amount per attuned aspect plus an unattuned amount.
 * Amounts are expressed in monolith pairs passed.
 */
public record BeamComposition(Map<EssenceType, Integer> attuned, int unattuned)
{
    public static final BeamComposition EMPTY = new BeamComposition(Map.of(), 0);

    private static final Codec<EssenceType> ASPECT_CODEC = Identifier.CODEC.xmap(
            id -> EssenceRegistry.ESSENCE_REGISTRY.getValue(id), EssenceType::identifier);

    private static final StreamCodec<ByteBuf, EssenceType> ASPECT_STREAM_CODEC = Identifier.STREAM_CODEC.map(
            id -> EssenceRegistry.ESSENCE_REGISTRY.getValue(id), EssenceType::identifier);

    public static final Codec<BeamComposition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.unboundedMap(ASPECT_CODEC, Codec.INT).fieldOf("attuned").forGetter(BeamComposition::attuned),
                    Codec.INT.fieldOf("unattuned").forGetter(BeamComposition::unattuned)
            ).apply(instance, BeamComposition::new));

    public static final StreamCodec<ByteBuf, BeamComposition> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ASPECT_STREAM_CODEC, ByteBufCodecs.VAR_INT), BeamComposition::attuned,
            ByteBufCodecs.VAR_INT, BeamComposition::unattuned,
            BeamComposition::new);

    public BeamComposition
    {
        attuned = Map.copyOf(attuned);
    }

    public static BeamComposition unattuned(int amount)
    {
        return new BeamComposition(Map.of(), amount);
    }

    public static BeamComposition of(EssenceType aspect, int amount)
    {
        if (aspect == null || aspect == EssenceRegistry.EMPTY)
            return unattuned(amount);
        return new BeamComposition(Map.of(aspect, amount), 0);
    }

    public int amount(EssenceType aspect)
    {
        return attuned.getOrDefault(aspect, 0);
    }

    public int total()
    {
        return unattuned + attuned.values().stream().mapToInt(Integer::intValue).sum();
    }

    public BeamComposition plus(BeamComposition other)
    {
        var merged = new HashMap<>(attuned);
        other.attuned.forEach((aspect, amount) -> merged.merge(aspect, amount, Integer::sum));
        return new BeamComposition(merged, unattuned + other.unattuned);
    }

    public BeamComposition plusUnattuned(int amount)
    {
        return new BeamComposition(attuned, unattuned + amount);
    }

    /**
     * Collectors tune a beam completely: the whole energy becomes the given aspect.
     */
    public BeamComposition retune(EssenceType aspect)
    {
        return of(aspect, total());
    }

    /**
     * Minimum matching: every required aspect must be present in at least the required amount.
     * Unattuned energy and surplus are ignored.
     */
    public boolean satisfies(Map<EssenceType, Integer> required)
    {
        return required.entrySet().stream().allMatch(entry -> amount(entry.getKey()) >= entry.getValue());
    }
}
