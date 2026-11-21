package net.voxelindustry.voidheart.common.content.apparatus;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.voxelindustry.voidheart.common.content.mob.DragoonAdaptation.Kind;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * The Grudge Ward, grown from Wrung memoriam: an apparatus holding one remembers the kind of the last hit that broke its shield, and wards that kind until a
 * different kind breaks it. Kinds are the Kernacian Dragoon's, so it is that adaptation turned to the wearer's side. Pure.
 */
public final class GrudgeWard
{
    public static final Codec<Kind> KIND_CODEC = Codec.STRING.comapFlatMap(
            name -> Arrays.stream(Kind.values()).filter(kind -> name(kind).equals(name)).findFirst()
                    .map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown grudge " + name)),
            GrudgeWard::name);

    private GrudgeWard()
    {
    }

    public static String name(Kind kind)
    {
        return kind.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a hit of this kind is warded by what is held against.
     */
    public static boolean wards(@Nullable Kind held, @Nullable Kind hit)
    {
        return held != null && held == hit;
    }

    /**
     * What is held against once a hit of this kind breaks the shield: that kind, or the old grudge for a hit no one dealt, like a fall.
     */
    public static @Nullable Kind afterBreak(@Nullable Kind held, @Nullable Kind breaking)
    {
        return breaking != null ? breaking : held;
    }
}
