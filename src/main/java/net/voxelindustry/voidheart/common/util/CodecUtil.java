package net.voxelindustry.voidheart.common.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class CodecUtil
{
    @SuppressWarnings("unchecked")
    public static <E> Codec<E[]> array(final Codec<E> elementCodec, Class<? super E> typeClass)
    {
        return Codec.list(elementCodec).comapFlatMap(
                list -> DataResult.success(list.toArray(((E[]) Array.newInstance(typeClass, 0)))),
                List::of
        );
    }

    public static Codec<int[]> arrayInt()
    {
        return Codec.INT_STREAM.xmap(IntStream::toArray, Arrays::stream);
    }
}
