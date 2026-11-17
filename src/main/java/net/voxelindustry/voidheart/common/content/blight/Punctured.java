package net.voxelindustry.voidheart.common.content.blight;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What rapiers left in one target: a wound for each wielder, and the fold it is in while being unseamed. Sent to everyone who sees the target, who draw
 * the slits and the fold from it.
 */
public record Punctured(Map<UUID, Wound> wounds, Optional<Fold> fold)
{
    public static final Punctured NONE = new Punctured(Map.of(), Optional.empty());

    public static final StreamCodec<ByteBuf, Punctured> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.<ByteBuf, UUID, Wound, Map<UUID, Wound>>map(HashMap::new, UUIDUtil.STREAM_CODEC, Wound.STREAM_CODEC), Punctured::wounds,
            ByteBufCodecs.optional(Fold.STREAM_CODEC), Punctured::fold,
            Punctured::new);

    /**
     * A target folded out of the world for a moment by one wielder's punctures, held where it was until it snaps back.
     *
     * @param slits the punctures that tore into each other, for the look
     */
    public record Fold(UUID wielder, long start, Vec3 anchor, List<Wound.Slit> slits)
    {
        public static final StreamCodec<ByteBuf, Fold> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Fold::wielder,
                ByteBufCodecs.VAR_LONG, Fold::start,
                Vec3.STREAM_CODEC, Fold::anchor,
                Wound.Slit.STREAM_CODEC.apply(ByteBufCodecs.list(Wound.HITS)), Fold::slits,
                Fold::new);

        public long end()
        {
            return start + BlightBalance.UNSEAMING_FOLD_TICKS;
        }
    }

    public Wound woundOf(UUID wielder)
    {
        return wounds.getOrDefault(wielder, Wound.NONE);
    }

    public Punctured with(UUID wielder, Wound wound)
    {
        var changed = new HashMap<>(wounds);
        changed.put(wielder, wound);
        return new Punctured(Map.copyOf(changed), fold);
    }

    public Punctured withFold(Optional<Fold> fold)
    {
        return new Punctured(wounds, fold);
    }

    /** Without the wounds that are spent by then. */
    public Punctured pruned(long now)
    {
        var kept = new HashMap<UUID, Wound>();
        wounds.forEach((wielder, wound) ->
        {
            if (!wound.isSpent(now))
                kept.put(wielder, wound);
        });
        return kept.size() == wounds.size() ? this : new Punctured(Map.copyOf(kept), fold);
    }

    public boolean isEmpty()
    {
        return wounds.isEmpty() && fold.isEmpty();
    }
}
