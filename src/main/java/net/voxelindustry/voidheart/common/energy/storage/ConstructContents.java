package net.voxelindustry.voidheart.common.energy.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * What a broken construct core carries: its energy and the bundles from its sockets.
 */
public record ConstructContents(long stored, List<SocketSet.Entry> sockets)
{
    public static final Codec<ConstructContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.optionalFieldOf("stored", 0L).forGetter(ConstructContents::stored),
            SocketSet.Entry.CODEC.listOf().optionalFieldOf("sockets", List.of()).forGetter(ConstructContents::sockets)
    ).apply(instance, ConstructContents::new));

    public ConstructContents
    {
        sockets = List.copyOf(sockets);
    }

    public long capacity()
    {
        long capacity = 0;
        for (var entry : sockets)
            capacity += entry.bundle().capacity();
        return capacity;
    }

    public long rate()
    {
        long rate = 0;
        for (var entry : sockets)
            rate += entry.bundle().rate();
        return rate;
    }
}
