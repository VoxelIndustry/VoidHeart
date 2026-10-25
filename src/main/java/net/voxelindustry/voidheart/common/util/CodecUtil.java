package net.voxelindustry.voidheart.common.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.ints.IntList;

import java.lang.reflect.Array;
import java.util.List;

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
        return Codec.list(Codec.INT).comapFlatMap(
                list -> DataResult.success(((IntList) list).toArray(new int[0])),
                IntList::of
        );
    }
}
